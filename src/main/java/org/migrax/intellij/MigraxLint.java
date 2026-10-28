package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The answer of {@code migrax lint --json}: how many files were checked and the findings. */
final class MigraxLint {

  private final int files;
  private final List<MigraxFinding> findings;

  private MigraxLint(int files, List<MigraxFinding> findings) {
    this.files = files;
    this.findings = findings;
  }

  /** Reads the output of the command; null when it has no findings list. */
  @Nullable
  static MigraxLint from(@Nullable JsonElement json) {
    if (!(json instanceof JsonObject object) || !(object.get("findings") instanceof JsonArray)) {
      return null;
    }
    int files = 0;
    if (object.get("files") instanceof JsonPrimitive count && count.isNumber()) {
      files = count.getAsInt();
    }
    return new MigraxLint(files, MigraxFinding.listFrom(object.get("findings")));
  }

  List<MigraxFinding> findings() {
    return findings;
  }

  /** One line for the top of the tab. */
  String summary() {
    String checked = files + (files == 1 ? " file" : " files") + " checked";
    if (findings.isEmpty()) {
      return checked + ", no findings.";
    }
    return checked + ", " + findings.size() + (findings.size() == 1 ? " finding." : " findings.");
  }
}
