package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the {@code schemas} list of {@code migrax migrate --json} and {@code rollback --json}:
 * one entry per schema (one with {@code "schema": null} without --schemas), each with the
 * migrations it would apply or roll back, or did.
 */
final class MigraxSchemaResults {

  private MigraxSchemaResults() {}

  /**
   * The migrations named under {@code key} ({@code wouldApply}, {@code wouldRollBack}, {@code
   * rolledBack}) in every schema, in order and without repeats; null when the JSON has no
   * schemas list.
   */
  static @Nullable List<String> migrations(@Nullable JsonObject json, String key) {
    JsonArray schemas = schemas(json);
    if (schemas == null) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (JsonElement schema : schemas) {
      if (schema instanceof JsonObject object && object.get(key) instanceof JsonArray list) {
        for (JsonElement name : list) {
          if (name instanceof JsonPrimitive text && text.isString()
              && !names.contains(text.getAsString())) {
            names.add(text.getAsString());
          }
        }
      }
    }
    return names;
  }

  /** The sum of {@code applied} over every schema; -1 when the JSON has no schemas list. */
  static int applied(@Nullable JsonObject json) {
    JsonArray schemas = schemas(json);
    if (schemas == null) {
      return -1;
    }
    int applied = 0;
    for (JsonElement schema : schemas) {
      if (schema instanceof JsonObject object
          && object.get("applied") instanceof JsonPrimitive count
          && count.isNumber()) {
        applied += count.getAsInt();
      }
    }
    return applied;
  }

  private static @Nullable JsonArray schemas(@Nullable JsonObject json) {
    return json != null && json.get("schemas") instanceof JsonArray schemas ? schemas : null;
  }
}
