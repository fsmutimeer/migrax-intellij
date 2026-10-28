package org.migrax.intellij;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import org.jetbrains.annotations.Nullable;

/**
 * What one run of the Migrax command gave back.
 *
 * <p>Exit codes of the command: 0 OK, 1 error, 2 changes detected. The JSON is read tolerantly:
 * unknown fields are ignored, and output that is not a JSON object leaves {@link #json()} null.
 */
final class MigraxResult {

  static final int EXIT_OK = 0;
  static final int EXIT_ERROR = 1;
  static final int EXIT_CHANGES = 2;

  private final int exitCode;
  private final String stdout;
  private final String stderr;
  private final boolean cancelled;
  private final boolean timedOut;
  private final JsonObject json;

  MigraxResult(
      int exitCode, String stdout, String stderr, boolean cancelled, boolean timedOut) {
    this.exitCode = exitCode;
    this.stdout = stdout == null ? "" : stdout;
    this.stderr = stderr == null ? "" : stderr;
    this.cancelled = cancelled;
    this.timedOut = timedOut;
    this.json = parseJson(this.stdout);
  }

  static MigraxResult cancelledRun() {
    return new MigraxResult(-1, "", "", true, false);
  }

  static MigraxResult failedToStart(String detail) {
    return new MigraxResult(-1, "", "Could not start " + detail, false, false);
  }

  int exitCode() {
    return exitCode;
  }

  String stdout() {
    return stdout;
  }

  String stderr() {
    return stderr;
  }

  boolean cancelled() {
    return cancelled;
  }

  boolean timedOut() {
    return timedOut;
  }

  /** The JSON object the command printed, or null when there is none. */
  @Nullable
  JsonObject json() {
    return json;
  }

  /** True when the command ran to the end and did not report an error. */
  boolean succeeded() {
    return !cancelled && !timedOut && (exitCode == EXIT_OK || exitCode == EXIT_CHANGES);
  }

  /** A message for the user when the run failed, or null when it did not. */
  @Nullable
  String errorMessage() {
    if (cancelled) {
      return "Cancelled.";
    }
    if (timedOut) {
      return "The Migrax command took too long and was stopped.";
    }
    if (succeeded()) {
      return null;
    }
    if (json != null && json.get("error") instanceof JsonObject) {
      JsonObject error = json.getAsJsonObject("error");
      String code = text(error, "code");
      String message = text(error, "message");
      String hint = text(error, "hint");
      StringBuilder out = new StringBuilder();
      if (!code.isEmpty()) {
        out.append(code).append(": ");
      }
      out.append(message.isEmpty() ? "The Migrax command failed." : message);
      if (!hint.isEmpty()) {
        out.append(' ').append(hint);
      }
      return out.toString();
    }
    String text = stderr.isBlank() ? stdout : stderr;
    return text.isBlank()
        ? "The Migrax command failed (exit code " + exitCode + ")."
        : text.trim();
  }

  private static String text(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
  }

  /** The first JSON object that starts a line; see {@link #parseJsonValue}. */
  @Nullable
  static JsonObject parseJson(String output) {
    JsonElement value = parseJsonValue(output);
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
  }

  /**
   * Parses the first JSON object or array that starts a line; text before it (a note) and after
   * it is ignored. Returns null when there is none.
   */
  @Nullable
  static JsonElement parseJsonValue(String output) {
    if (output == null) {
      return null;
    }
    String text = output.trim();
    int start = startsJson(text) ? 0 : firstJsonLine(text);
    if (start < 0) {
      return null;
    }
    try {
      JsonReader reader = new JsonReader(new StringReader(text.substring(start)));
      reader.setLenient(true);
      return JsonParser.parseReader(reader);
    } catch (JsonParseException e) {
      return null;
    }
  }

  private static boolean startsJson(String text) {
    return text.startsWith("{") || text.startsWith("[");
  }

  private static int firstJsonLine(String text) {
    int at = text.indexOf("\n{");
    int array = text.indexOf("\n[");
    if (at < 0 || (array >= 0 && array < at)) {
      at = array;
    }
    return at < 0 ? -1 : at + 1;
  }
}
