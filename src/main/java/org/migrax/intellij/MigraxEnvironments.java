package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The answer of {@code migrax environments --json}: one report per configured database. */
final class MigraxEnvironments {

  /**
   * What one environment answered. {@code drift} is null when the schema was not compared;
   * {@code problems} and {@code drift} hold one readable line per entry.
   */
  record Environment(
      String name,
      String url,
      boolean reachable,
      String error,
      int applied,
      List<String> pending,
      List<String> problems,
      @Nullable List<String> drift,
      String driftNote,
      String lastApplied) {

    /** One word for the table: unreachable, problems, drift, pending or in sync. */
    String status() {
      if (!reachable) {
        return "unreachable";
      }
      if (!problems.isEmpty()) {
        return "problems";
      }
      if (drift != null && !drift.isEmpty()) {
        return "drift";
      }
      return pending.isEmpty() ? "in sync" : "pending";
    }

    /** Everything about the environment, for the detail area below the table. */
    String details() {
      List<String> lines = new ArrayList<>();
      lines.add(name + "  " + url);
      if (!error.isEmpty()) {
        lines.add("Error: " + error);
      }
      if (!lastApplied.isEmpty()) {
        lines.add("Last applied: " + lastApplied);
      }
      section(lines, "Pending", pending);
      section(lines, "Problems", problems);
      if (drift != null) {
        section(lines, "Drift", drift);
      }
      if (!driftNote.isEmpty()) {
        lines.add(driftNote);
      }
      return String.join("\n", lines);
    }

    private static void section(List<String> lines, String title, List<String> entries) {
      if (!entries.isEmpty()) {
        lines.add(title + " (" + entries.size() + "):");
        entries.forEach(entry -> lines.add("  " + entry));
      }
    }
  }

  private final List<Environment> environments;

  private MigraxEnvironments(List<Environment> environments) {
    this.environments = environments;
  }

  /** Reads the output of the command; null when it has no environments list. */
  @Nullable
  static MigraxEnvironments from(@Nullable JsonElement json) {
    if (!(json instanceof JsonObject object)
        || !(object.get("environments") instanceof JsonArray list)) {
      return null;
    }
    List<Environment> environments = new ArrayList<>();
    for (JsonElement element : list) {
      if (element instanceof JsonObject e) {
        environments.add(read(e));
      }
    }
    return new MigraxEnvironments(environments);
  }

  List<Environment> environments() {
    return environments;
  }

  /** One line for the top of the tab. */
  String summary() {
    if (environments.isEmpty()) {
      return "No environments configured.";
    }
    long attention =
        environments.stream()
            .filter(e -> !e.status().equals("in sync") && !e.status().equals("pending"))
            .count();
    String text =
        environments.size() + (environments.size() == 1 ? " environment" : " environments");
    if (attention == 0) {
      return text + ", none needs attention.";
    }
    return text + ", " + attention + " need" + (attention == 1 ? "s" : "") + " attention.";
  }

  private static Environment read(JsonObject e) {
    JsonElement drift = e.get("drift");
    JsonElement last = e.get("lastApplied");
    String lastApplied = "";
    if (last instanceof JsonObject l) {
      lastApplied = (text(l, "version") + " " + text(l, "appliedAt")).trim();
    }
    return new Environment(
        text(e, "name"),
        text(e, "url"),
        e.get("reachable") instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean(),
        text(e, "error"),
        e.get("applied") instanceof JsonPrimitive n && n.isNumber() ? n.getAsInt() : 0,
        // "skipped" is left out on purpose: those migrations belong to other environments.
        strings(e.get("pending")),
        problems(e.get("problems")),
        drift instanceof JsonArray ? driftLines(drift) : null,
        text(e, "driftNote"),
        lastApplied);
  }

  private static List<String> strings(JsonElement array) {
    List<String> out = new ArrayList<>();
    if (array instanceof JsonArray list) {
      for (JsonElement element : list) {
        if (element instanceof JsonPrimitive p) {
          out.add(p.getAsString());
        }
      }
    }
    return out;
  }

  private static List<String> problems(JsonElement array) {
    List<String> out = new ArrayList<>();
    if (array instanceof JsonArray list) {
      for (JsonElement element : list) {
        if (element instanceof JsonObject row) {
          out.add(join(text(row, "version"), text(row, "state"), text(row, "detail")));
        }
      }
    }
    return out;
  }

  private static List<String> driftLines(JsonElement array) {
    List<String> out = new ArrayList<>();
    for (JsonElement element : array.getAsJsonArray()) {
      if (element instanceof JsonObject d) {
        out.add(join(text(d, "kind"), text(d, "object"), text(d, "detail")));
      }
    }
    return out;
  }

  private static String join(String first, String second, String third) {
    String text = (first + " " + second).trim();
    return third.isEmpty() ? text : text + ": " + third;
  }

  private static String text(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
  }
}
