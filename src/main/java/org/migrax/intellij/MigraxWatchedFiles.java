package org.migrax.intellij;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

  // Profile files: application-dev.yml, microprofile-config-prod.properties.
  private static final Pattern PROFILE_SETTING_FILE =
      Pattern.compile(
          "application-[^/]+\\.(properties|yml|yaml)|microprofile-config-[^/]+\\.properties");

  /** Whether the path (with "/") is a file the command reads its settings from. */
  static boolean isSettingsFile(String path) {
    return isSettingsName(path.substring(path.lastIndexOf('/') + 1));
  }

  private static boolean isSettingsName(String name) {
    return SETTING_FILES.contains(name) || PROFILE_SETTING_FILE.matcher(name).matches();
  }

  /**
   * Whether a change of this file can change lint results: a settings file inside the project
   * (not a copy in build output, which the build rewrites after every check), or the snapshot,
   * where the command finds the database kind when no database is configured.
   */
  static boolean changesLintResults(Path root, String path) {
    if (path.endsWith("/.migrax/snapshot.json")) {
      try {
        return Path.of(path).normalize().equals(root.resolve(".migrax/snapshot.json"));
      } catch (InvalidPathException e) {
        return false;
      }
    }
    return isSettingsFile(path) && matters(root, path);
  }

  /** Whether the path (with "/") is a Java or Kotlin source: an entity, as far as we know. */
  static boolean isSource(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.endsWith(".java") || lower.endsWith(".kt");
  }

  /** Whether a file in a folder makes it a Migrax project, which was not found before. */
  static boolean marksAProject(String path) {
    return path.endsWith("/migrax.properties") || path.contains("/.migrax/")
        || path.endsWith("/.migrax");
  }

  /**
   * Whether the folder's Migrax markers themselves were added or removed (a deleted .migrax
   * folder). Not the files inside .migrax: the check writes those, which would check again.
   */
  static boolean movesAMarker(String path) {
    return path.endsWith("/migrax.properties") || path.endsWith("/.migrax");
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
    if (isSettingsName(name)) {
      return true;
    }
    int dot = name.lastIndexOf('.');
    return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
