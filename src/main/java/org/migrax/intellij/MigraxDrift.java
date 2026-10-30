package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * Drift: runs {@code migrax drift --json}, which compares the database with the schema the
 * migrations build, and shows the differences: changes someone made outside the migrations.
 * Only reads the database.
 */
final class MigraxDrift {

  // Drift may build the project first.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  private static final String TITLE = "Migrax: Drift";

  private MigraxDrift() {}

  /** Editor thread. */
  static void run(Project project) {
    MigraxCommands.run(
        project,
        TITLE,
        List.of("drift", "--json"),
        TIMEOUT_MILLIS,
        result -> show(project, result),
        message -> MigraxNotifications.error(project, message));
  }

  private static void show(Project project, MigraxResult result) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    // Drift exits with 2 when it finds differences: a finished run.
    String text = result.succeeded() ? text(result.json()) : null;
    if (text == null) {
      MigraxNotifications.error(project, result.errorOrUnreadable());
      return;
    }
    new MigraxTextDialog(project, TITLE, text).show();
  }

  /** The differences, one per line, with what to do; null when the JSON has no list of them. */
  static @Nullable String text(@Nullable JsonObject json) {
    if (json == null || !(json.get("differences") instanceof JsonArray differences)) {
      return null;
    }
    // The snapshot is what the migrations build.
    String against =
        "entities".equals(MigraxResult.string(json, "against")) ? "the entities" : "the migrations";
    StringBuilder out = new StringBuilder();
    if (json.get("pending") instanceof JsonPrimitive pending
        && pending.isNumber()
        && pending.getAsLong() > 0) {
      out.append(pending.getAsLong())
          .append(" migration(s) are pending: differences they cover are expected.\n\n");
    }
    if (differences.isEmpty()) {
      return out.append("No drift: the database matches ").append(against).append('.').toString();
    }
    out.append(differences.size())
        .append(" difference(s) between the database and ")
        .append(against)
        .append(":\n");
    for (JsonElement element : differences) {
      if (element instanceof JsonObject difference) {
        String object = MigraxResult.string(difference, "object");
        String detail = MigraxResult.string(difference, "detail");
        out.append("  - ")
            .append(object == null ? "?" : object)
            .append(detail == null ? "" : ": " + detail)
            .append('\n');
      }
    }
    return out.append(
            "\nSomeone changed the database outside the migrations. For a change to keep, write"
                + " a migration with New Migration; otherwise undo it in the database.")
        .toString();
  }
}
