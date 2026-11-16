package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class MigraxTimedCacheTest {

  private final AtomicLong now = new AtomicLong(1_000);

  private MigraxTimedCache<String, String> cache(int entries, long maxAgeMillis) {
    return new MigraxTimedCache<>(entries, maxAgeMillis, now::get);
  }

  @Test
  public void returnsWhatWasStored() {
    MigraxTimedCache<String, String> cache = cache(5, 100);
    assertNull(cache.get("a"));
    cache.put("a", "one", cache.generation());
    assertEquals("one", cache.get("a"));
  }

  @Test
  public void forgetsAnEntryAfterTheTimeLimit() {
    MigraxTimedCache<String, String> cache = cache(5, 100);
    cache.put("a", "one", cache.generation());
    now.addAndGet(100);
    assertEquals("one", cache.get("a"));
    now.addAndGet(1);
    assertNull(cache.get("a"));
  }

  @Test
  public void storingAgainRestartsTheTime() {
    MigraxTimedCache<String, String> cache = cache(5, 100);
    cache.put("a", "one", cache.generation());
    now.addAndGet(90);
    cache.put("a", "two", cache.generation());
    now.addAndGet(90);
    assertEquals("two", cache.get("a"));
  }

  @Test
  public void forgetsTheLeastRecentlyUsedWhenFull() {
    MigraxTimedCache<String, String> cache = cache(2, 100);
    cache.put("a", "one", cache.generation());
    cache.put("b", "two", cache.generation());
    // Reading "a" makes "b" the one that goes.
    assertEquals("one", cache.get("a"));
    cache.put("c", "three", cache.generation());
    assertEquals("one", cache.get("a"));
    assertNull(cache.get("b"));
    assertEquals("three", cache.get("c"));
  }

  @Test
  public void clearForgetsEverything() {
    MigraxTimedCache<String, String> cache = cache(5, 100);
    cache.put("a", "one", cache.generation());
    cache.put("b", "two", cache.generation());
    cache.clear();
    assertNull(cache.get("a"));
    assertNull(cache.get("b"));
  }

  @Test
  public void resultFromBeforeAClearIsNotStored() {
    MigraxTimedCache<String, String> cache = cache(5, 100);
    long before = cache.generation();
    cache.clear();
    cache.put("a", "old settings", before);
    assertNull(cache.get("a"));
    cache.put("a", "new settings", cache.generation());
    assertEquals("new settings", cache.get("a"));
  }
}
