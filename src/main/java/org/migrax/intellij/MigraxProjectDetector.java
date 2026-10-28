package org.migrax.intellij;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tells whether a folder is a Migrax project: it has a {@code .migrax} folder (the snapshot), a
 * {@code migrax.properties} file, or a {@code migrax.} setting in the files the command reads
 * its settings from. Only the project folder and its {@code src/main/resources} are looked at, so
 * the check is cheap.
 */
final class MigraxProjectDetector {

  private static final List<String> SETTING_FILES =
      List.of(
          "application.properties",
          "application.yml",
          "application.yaml",
          "microprofile-config.properties",
          "META-INF/microprofile-config.properties",
          "META-INF/persistence.xml");
  /** A setting such as migrax.url=... or a "migrax:" section in a YAML file. */
  private static final Pattern SETTING = Pattern.compile("migrax[.:]");
  private static final long MAX_BYTES = 64 * 1024;

  private MigraxProjectDetector() {}

  static boolean isMigraxProject(Path folder) {
    if (Files.isDirectory(folder.resolve(".migrax"))
        || Files.isRegularFile(folder.resolve("migrax.properties"))) {
      return true;
    }
    Path resources = folder.resolve("src").resolve("main").resolve("resources");
    for (String name : SETTING_FILES) {
      if (mentionsMigrax(resources.resolve(name)) || mentionsMigrax(folder.resolve(name))) {
        return true;
      }
    }
    return hasProfileFile(resources);
  }

  /** Files such as application-dev.properties next to the plain ones. */
  private static boolean hasProfileFile(Path resources) {
    if (!Files.isDirectory(resources)) {
      return false;
    }
    try (Stream<Path> files = Files.list(resources)) {
      return files
          .filter(f -> f.getFileName().toString().startsWith("application-"))
          .anyMatch(MigraxProjectDetector::mentionsMigrax);
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean mentionsMigrax(Path file) {
    try {
      if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) {
        return false;
      }
      return SETTING.matcher(Files.readString(file, StandardCharsets.UTF_8)).find();
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }
}
