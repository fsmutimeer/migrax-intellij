package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** The answer of {@code migrax status --json}: one row per migration. */
final class MigraxStatus {

  /** One migration: schema (may be empty), version (file name), state and detail. */
  record Row(String schema, String version, String state, String detail) {
    /** The name to show: the version, led by the schema when there is one. */
    String label() {
      return schema.isEmpty() ? version : schema + ": " + version;
    }

    /** Failed, changed and missing migrations need the user's attention. */
    boolean isProblem() {
      return state.equals("failed") || state.equals("changed") || state.equals("missing");
    }
  }

  private final List<Row> rows;

  private MigraxStatus(List<Row> rows) {
    this.rows = rows;
  }

  /**
   * Reads the output of the command: one object, or an array of objects when it ran for several
   * schemas. Returns null when the output has no migrations list.
   */
  @Nullable
  static MigraxStatus from(@Nullable JsonElement json) {
    List<Row> rows = new ArrayList<>();
    boolean found = false;
    List<JsonElement> parts = new ArrayList<>();
    if (json instanceof JsonArray array) {
      array.forEach(parts::add);
    } else if (json != null) {
      parts.add(json);
    }
    for (JsonElement part : parts) {
      if (part instanceof JsonObject object && object.get("migrations") instanceof JsonArray list) {
        found = true;
        String schema = text(object, "schema");
        for (JsonElement element : list) {
          if (element instanceof JsonObject row) {
            rows.add(new Row(schema, text(row, "version"),
                text(row, "state").toLowerCase(Locale.ROOT), text(row, "detail")));
          }
        }
      }
    }
    return found ? new MigraxStatus(rows) : null;
  }

  List<Row> rows() {
    return rows;
  }

  /** One line for the top of the tab, such as "3 applied, 2 pending, 1 failed". */
  String summary() {
    if (rows.isEmpty()) {
      return "No migrations found.";
    }
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Row row : rows) {
      counts.merge(row.state(), 1, Integer::sum);
    }
    List<String> parts = new ArrayList<>();
    counts.forEach((state, n) -> parts.add(n + " " + state));
    return String.join(", ", parts);
  }

  private static String text(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
  }
}
