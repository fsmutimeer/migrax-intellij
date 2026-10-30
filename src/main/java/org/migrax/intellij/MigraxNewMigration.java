package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * New Migration: asks for a name, runs {@code migrax new <name> --json} and opens the empty
 * migration and its rollback script, for hand-written changes such as data migrations.
 */
final class MigraxNewMigration {

  // Only writes two files, but may wait for another command in the same folder.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);
  private static final String TITLE = "Migrax: New Migration";

  private static final InputValidator HAS_LETTERS =
      new InputValidator() {
        @Override
        public boolean checkInput(String input) {
          return !fileName(input).isEmpty();
        }

        @Override
        public boolean canClose(String input) {
          return checkInput(input);
        }
      };

  private MigraxNewMigration() {}

  /** Editor thread. */
  static void create(Project project) {
    String name =
        Messages.showInputDialog(
            project,
            "What the migration does, such as \"add seed data\" (letters a to z and digits):",
            TITLE,
            null,
            "",
            HAS_LETTERS);
    if (name == null) {
      return;
    }
    MigraxCommands.run(
        project,
        TITLE,
        List.of("new", fileName(name), "--json"),
        TIMEOUT_MILLIS,
        result -> created(project, result),
        message -> MigraxNotifications.error(project, message));
  }

  private static void created(Project project, MigraxResult result) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    if (!result.succeeded()) {
      MigraxNotifications.error(project, result.errorMessage());
      return;
    }
    List<String> files = MigraxResult.strings(result.json(), "created");
    if (files == null || files.isEmpty()) {
      MigraxNotifications.error(
          project, "The Migrax command gave an answer the plugin cannot read.");
      return;
    }
    MigraxNotifications.info(project, "Created " + files.get(0) + ". Write its SQL there.");
    MigraxToolWindowFactory.refreshStatus(project);
    Path root = MigraxHealthService.getInstance(project).knownRoot();
    List<Path> paths = new ArrayList<>();
    for (String file : files) {
      Path path = MigraxGenerator.resolve(root, file);
      if (path != null) {
        paths.add(path);
      }
    }
    MigraxGenerator.open(project, paths);
  }

  /**
   * The name the command turns into the file name: lower case letters, digits and underscores.
   * Done here too, so nothing else reaches the command line.
   */
  static String fileName(@Nullable String name) {
    return name == null
        ? ""
        : name.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
  }
}
