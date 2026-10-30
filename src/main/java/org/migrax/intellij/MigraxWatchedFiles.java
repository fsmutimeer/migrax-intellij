package org.migrax.intellij;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Which changed files make the status bar entry and the margin icons out of date: sources (the
 * entities), migrations and the files the command reads its settings from, inside the Migrax
 * project folder. Build output and tool folders are left out, otherwise the build that {@code
 * check} starts would trigger the next check. Test sources hold no entities.
 */
final class MigraxWatchedFiles {

  private static final Set<String> EXTENSIONS = Set.of("java", "kt", "sql");
  private static final Set<String> SETTING_FILES =
      Set.of(
          "migrax.properties",
          "application.properties",
          "application.yml",
          "application.yaml",
          "microprofile-config.properties",
          "persistence.xml");

  private MigraxWatchedFiles() {}

  /** Whether a change of the file at this path from the IDE (with "/") needs a new check. */
  static boolean matters(Path root, String path) {
    try {
      return matters(root, Path.of(path).normalize());
    } catch (InvalidPathException e) {
      // A path the system cannot read as a file: not ours.
      return false;
    }
  }

  /** Whether a file in a folder makes it a Migrax project, which was not found before. */
  static boolean marksAProject(String path) {
    return path.endsWith("/migrax.properties") || path.contains("/.migrax/")
        || path.endsWith("/.migrax");
  }

  /** Whether a change of this file (both absolute and normalized) needs a new check. */
  static boolean matters(Path root, Path file) {
    if (!file.startsWith(root) || file.equals(root)) {
      return false;
    }
    Path relative = root.relativize(file);
    int folders = relative.getNameCount() - 1;
    for (int i = 0; i < folders; i++) {
      String folder = relative.getName(i).toString();
      if (MigraxProjectDetector.isSkipped(folder)
          || (folder.equals("test") && i > 0 && relative.getName(i - 1).toString().equals("src"))) {
        return false;
      }
    }
    String name = relative.getFileName().toString();
    if (SETTING_FILES.contains(name)) {
      return true;
    }
    int dot = name.lastIndexOf('.');
    return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
