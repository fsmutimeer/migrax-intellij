package org.migrax.intellij;

import com.intellij.util.EnvironmentUtil;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds the Migrax command: the path from the settings first, then the folders on PATH.
 *
 * <p>On Windows only {@code migrax.cmd}, {@code migrax.exe} and {@code migrax.bat} count: the
 * extensionless {@code migrax} next to them is a shell script that Windows cannot run.
 */
final class MigraxCliLocator {

  private final boolean windows;
  private final List<Path> pathFolders;

  MigraxCliLocator(boolean windows, String pathVariable) {
    this.windows = windows;
    this.pathFolders = splitPath(pathVariable);
  }

  static MigraxCliLocator forThisMachine() {
    return new MigraxCliLocator(File.separatorChar == '\\', System.getenv("PATH"));
  }

  /** The command to run, or empty when none is found. */
  Optional<Path> locate(String configuredPath) {
    if (configuredPath != null && !configuredPath.isBlank()) {
      return fromSetting(configuredPath.trim());
    }
    for (Path folder : pathFolders) {
      Optional<Path> found = inFolder(folder);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** A file is used as it is; a folder is searched for the launcher. */
  private Optional<Path> fromSetting(String configuredPath) {
    Path path;
    try {
      path = Paths.get(configuredPath);
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
    if (Files.isDirectory(path)) {
      return inFolder(path);
    }
    return isLauncher(path) ? Optional.of(path) : Optional.empty();
  }

  /** A file Migrax can be started from: a Windows launcher by extension, else executable. */
  private boolean isLauncher(Path file) {
    if (!Files.isRegularFile(file)) {
      return false;
    }
    if (windows) {
      String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
      return name.endsWith(".cmd") || name.endsWith(".exe") || name.endsWith(".bat");
    }
    return Files.isExecutable(file);
  }

  private Optional<Path> inFolder(Path folder) {
    for (String name : launcherNames()) {
      Path candidate = folder.resolve(name);
      if (isLauncher(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private List<String> launcherNames() {
    return windows ? List.of("migrax.cmd", "migrax.exe", "migrax.bat") : List.of("migrax");
  }

  /** Windows PATH entries are often quoted: "C:\Program Files\Migrax". */
  private static String stripQuotes(String entry) {
    if (entry.length() >= 2 && entry.startsWith("\"") && entry.endsWith("\"")) {
      return entry.substring(1, entry.length() - 1).trim();
    }
    return entry;
  }

  private static List<Path> splitPath(String pathVariable) {
    List<Path> folders = new ArrayList<>();
    if (pathVariable == null) {
      return folders;
    }
    for (String part : pathVariable.split(File.pathSeparator)) {
      String trimmed = stripQuotes(part.trim());
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        folders.add(Paths.get(trimmed));
      } catch (InvalidPathException e) {
        // A broken entry in PATH is skipped, like the shell does.
      }
    }
    return folders;
  }
}
