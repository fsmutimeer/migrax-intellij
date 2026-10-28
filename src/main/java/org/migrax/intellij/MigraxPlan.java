package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/** The answer of {@code migrax plan --json}: the operations of the next migration and lint. */
final class MigraxPlan {

  /** One operation with the SQL it runs. */
  record Operation(String kind, String summary, String sql, boolean destructive) {}

  private final List<Operation> operations;
  private final List<MigraxFinding> findings;

  private MigraxPlan(List<Operation> operations, List<MigraxFinding> findings) {
    this.operations = operations;
    this.findings = findings;
  }

  /** Reads the output of the command; null when it has no operations list. */
  @Nullable
  static MigraxPlan from(@Nullable JsonElement json) {
    if (!(json instanceof JsonObject object)
        || !(object.get("operations") instanceof JsonArray ops)) {
      return null;
    }
    List<Operation> operations = new ArrayList<>();
    for (JsonElement element : ops) {
      if (element instanceof JsonObject op) {
        operations.add(
            new Operation(
                text(op, "kind"), text(op, "summary"), text(op, "sql"), flag(op, "destructive")));
      }
    }
    List<MigraxFinding> findings = MigraxFinding.listFrom(object.get("lint"));
    return new MigraxPlan(operations, findings);
  }

  List<Operation> operations() {
    return operations;
  }

  List<MigraxFinding> findings() {
    return findings;
  }

  /** One line for the top of the tab. */
  String summary() {
    if (operations.isEmpty()) {
      return "No changes: the entities match the last migration.";
    }
    long destructive = operations.stream().filter(Operation::destructive).count();
    String text = operations.size() + (operations.size() == 1 ? " operation" : " operations");
    if (destructive > 0) {
      text += ", " + destructive + " destructive";
    }
    if (!findings.isEmpty()) {
      text += ", " + findings.size() + (findings.size() == 1 ? " lint finding" : " lint findings");
    }
    return text;
  }

  /** The SQL of every operation, each led by a comment line with what it does. */
  String sqlText() {
    StringBuilder out = new StringBuilder();
    for (Operation op : operations) {
      out.append("-- ").append(op.summary());
      if (op.destructive()) {
        out.append("  (destructive)");
      }
      out.append('\n').append(op.sql()).append("\n\n");
    }
    return out.toString().stripTrailing();
  }

  /** The lint findings, one block each; empty when there are none. */
  String lintText() {
    return findings.stream().map(MigraxFinding::describe).collect(Collectors.joining("\n"));
  }

  private static String text(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
  }

  private static boolean flag(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive() && value.getAsBoolean();
  }
}
