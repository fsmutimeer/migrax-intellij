package org.migrax.intellij;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * A small cache that forgets the least recently used entry when full, and any entry older than
 * a time limit. Used for lint results: each lint pass starts a Java process, and the editor asks
 * again for a file whose text did not change (a tab opened again, an analysis restarted by
 * something else). Safe to use from several threads.
 */
final class MigraxTimedCache<K, V> {

  private record Entry<V>(V value, long storedAt) {}

  private final long maxAgeMillis;
  private final LongSupplier clock;
  private final Map<K, Entry<V>> entries;
  // Counts the clear() calls, so a result computed before a clear is not stored after it.
  private long generation;

  MigraxTimedCache(int maxEntries, long maxAgeMillis, LongSupplier clock) {
    this.maxAgeMillis = maxAgeMillis;
    this.clock = clock;
    this.entries =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
            return size() > maxEntries;
          }
        };
  }

  /** The stored value, or null when there is none or it is too old. */
  @Nullable
  synchronized V get(K key) {
    Entry<V> entry = entries.get(key);
    if (entry == null) {
      return null;
    }
    if (clock.getAsLong() - entry.storedAt() > maxAgeMillis) {
      entries.remove(key);
      return null;
    }
    return entry.value();
  }

  /** The current generation: read it before computing a value, pass it to {@link #put}. */
  synchronized long generation() {
    return generation;
  }

  /**
   * Stores the value, unless the cache was cleared since {@code generation} was read: the value
   * may then come from settings that changed in between.
   */
  synchronized void put(K key, V value, long generation) {
    if (generation == this.generation) {
      entries.put(key, new Entry<>(value, clock.getAsLong()));
    }
  }

  synchronized void clear() {
    entries.clear();
    generation++;
  }
}
