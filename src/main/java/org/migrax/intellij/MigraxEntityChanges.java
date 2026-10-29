package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The entity changes that are not in a migration yet, by entity class, from the {@code
 * operations} of {@code migrax check --json}. An older Migrax command has no {@code
 * operations}, and a change whose table no entity maps (a dropped table) has no {@code entity}:
 * those changes are left out.
 */
final class MigraxEntityChanges {

  static final MigraxEntityChanges NONE = new MigraxEntityChanges(Map.of());

  // Class name with dots (also for nested classes) to the summaries of its changes.
  private final Map<String, List<String>> byClass;

  private MigraxEntityChanges(Map<String, List<String>> byClass) {
    this.byClass = byClass;
  }

  /** Reads the output of {@code migrax check --json}; {@link #NONE} when it is not that. */
  static MigraxEntityChanges from(@Nullable JsonElement json) {
    if (!(json instanceof JsonObject object)
        || !(object.get("operations") instanceof JsonArray operations)) {
      return NONE;
    }
    Map<String, List<String>> byClass = new LinkedHashMap<>();
    for (JsonElement element : operations) {
      if (!(element instanceof JsonObject operation)) {
        continue;
      }
      String entity = text(operation, "entity");
      String summary = text(operation, "summary");
      if (entity == null || summary == null) {
        continue;
      }
      // The command gives the binary name (Outer$Inner); the editor knows Outer.Inner.
      byClass.computeIfAbsent(entity.replace('$', '.'), k -> new ArrayList<>()).add(summary);
    }
    if (byClass.isEmpty()) {
      return NONE;
    }
    byClass.replaceAll((name, summaries) -> List.copyOf(summaries));
    return new MigraxEntityChanges(Map.copyOf(byClass));
  }

  /** The summaries of the changes of a class, such as "add column customer.phone". */
  List<String> of(@Nullable String qualifiedName) {
    if (qualifiedName == null) {
      return List.of();
    }
    return byClass.getOrDefault(qualifiedName, List.of());
  }

  boolean isEmpty() {
    return byClass.isEmpty();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MigraxEntityChanges that && byClass.equals(that.byClass);
  }

  @Override
  public int hashCode() {
    return byClass.hashCode();
  }

  private static @Nullable String text(JsonObject object, String key) {
    return object.get(key) instanceof JsonPrimitive value && value.isString()
        ? value.getAsString()
        : null;
  }
}
