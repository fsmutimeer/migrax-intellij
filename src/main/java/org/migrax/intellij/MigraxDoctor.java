package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/** Doctor: runs {@code migrax doctor --json} and shows every check in a dialog. */
final class MigraxDoctor {

  // Doctor may build the project first.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  private static final String TITLE = "Migrax: Doctor";

  private MigraxDoctor() {}

  /** Editor thread. */
  static void run(Project project) {
    MigraxCommands.run(
        project,
        TITLE,
        List.of("doctor", "--json"),
        TIMEOUT_MILLIS,
        result -> show(project, result),
        message -> MigraxNotifications.error(project, message));
  }

  private static void show(Project project, MigraxResult result) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    // Doctor exits with 1 when a check fails, but still lists them all.
    String text = result.timedOut() ? null : text(result.json());
    if (text == null) {
      // A run that exits 0 has no error message: an answer this plugin cannot read.
      String error = result.errorMessage();
      MigraxNotifications.error(
          project,
          error != null ? error : "The Migrax command gave an answer the plugin cannot read.");
      return;
    }
    new MigraxTextDialog(project, TITLE, text).show();
  }

  /**
   * One line per check, "[ok] Java 21", with its fix on the next line; null when the JSON has no
   * checks.
   */
  static @Nullable String text(@Nullable JsonObject json) {
    if (json == null || !(json.get("checks") instanceof JsonArray checks)) {
      return null;
    }
    StringBuilder out = new StringBuilder();
    for (JsonElement element : checks) {
      if (!(element instanceof JsonObject check)) {
        continue;
      }
      String message = string(check, "message");
      if (message == null) {
        continue;
      }
      String status = string(check, "status");
      out.append('[').append(status == null ? "?" : status).append("] ").append(message);
      String fix = string(check, "fix");
      if (fix != null && !fix.isBlank()) {
        out.append("\n      ").append(fix);
      }
      out.append('\n');
    }
    return out.toString().strip();
  }

  private static @Nullable String string(JsonObject object, String key) {
    return object.get(key) instanceof JsonPrimitive value && value.isString()
        ? value.getAsString()
        : null;
  }
}
