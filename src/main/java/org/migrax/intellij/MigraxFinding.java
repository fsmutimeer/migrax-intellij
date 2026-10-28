package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** One lint finding as {@code migrax plan --json} and {@code migrax lint --json} print it. */
record MigraxFinding(String code, String severity, String file, String message, String fix) {

  /** Reads a JSON array of findings; anything that is not an object is skipped. */
  static List<MigraxFinding> listFrom(JsonElement array) {
    List<MigraxFinding> findings = new ArrayList<>();
    if (array instanceof JsonArray list) {
      for (JsonElement element : list) {
        if (element instanceof JsonObject f) {
          findings.add(
              new MigraxFinding(
                  text(f, "code"),
                  text(f, "severity"),
                  text(f, "file"),
                  text(f, "message"),
                  text(f, "fix")));
        }
      }
    }
    return findings;
  }

  /** The finding as plain text: code, severity, message and the fix. */
  String describe() {
    String text = code + " " + severity + ": " + message;
    return fix.isEmpty() ? text : text + "\n  Fix: " + fix;
  }

  private static String text(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
  }
}
