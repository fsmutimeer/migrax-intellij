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
 * Verify: runs {@code migrax verify --json}, which applies every migration to a throwaway
 * database (in memory, or in Docker), checks the schema against the entities and tests the
 * rollback scripts. The configured database is not touched.
 */
final class MigraxVerify {

  // Building the project and starting a database in Docker take a while.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(15);
  private static final String TITLE = "Migrax: Verify";

  private MigraxVerify() {}

  /** Editor thread. */
  static void run(Project project) {
    MigraxCommands.run(
        project,
        TITLE,
        List.of("verify", "--json"),
        TIMEOUT_MILLIS,
        result -> show(project, result),
        message -> MigraxNotifications.error(project, message));
  }

  private static void show(Project project, MigraxResult result) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    // A failed verification exits with 1 but still reports what it found.
    String text = result.timedOut() ? null : text(result.json());
    if (text == null) {
      MigraxNotifications.error(project, result.errorOrUnreadable());
      return;
    }
    new MigraxTextDialog(project, TITLE, text).show();
  }

  /**
   * What was applied, the schema problems and the rollback result, as the command prints them;
   * null for an error, which has no count of applied migrations.
   */
  static @Nullable String text(@Nullable JsonObject json) {
    if (json == null
        || !(json.get("applied") instanceof JsonPrimitive applied)
        || !applied.isNumber()) {
      return null;
    }
    String dialect = MigraxResult.string(json, "dialect");
    StringBuilder out = new StringBuilder("Applied ").append(applied.getAsLong());
    out.append(" migration(s) to a fresh ")
        .append(dialect == null ? "" : dialect + " ")
        .append("database.\n");
    String validation = MigraxResult.string(json, "validation");
    JsonArray problems =
        json.get("problems") instanceof JsonArray list ? list : new JsonArray();
    if (problems.isEmpty()) {
      out.append("The schema matches the entities");
      out.append(validation == null ? "" : " (" + validation + ")").append(".\n");
    } else {
      out.append("The migrated schema does not match the entities:\n");
      for (JsonElement problem : problems) {
        if (problem instanceof JsonPrimitive value && value.isString()) {
          out.append("  - ").append(value.getAsString()).append('\n');
        }
      }
    }
    String rollbacks = MigraxResult.string(json, "rollbacks");
    if (rollbacks != null) {
      out.append("Rollback scripts: ").append(rollbacks).append(".\n");
    }
    boolean ok =
        json.get("ok") instanceof JsonPrimitive value && value.isBoolean() && value.getAsBoolean();
    return out.append(ok ? "\nVerified." : "\nVerification failed.").toString();
  }
}
