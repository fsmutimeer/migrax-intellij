package org.migrax.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * Generate: runs {@code migrax generate --no-input}, asks in a dialog about each change that
 * may be a rename and about drops, and runs it again with the answers. The command cannot ask
 * itself: it has no terminal here. Opens the new migration and its rollback script.
 */
final class MigraxGenerator {

  // Generate may build the project first.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  private static final String TITLE = "Migrax: Generate";

  private final Project project;
  private final List<MigraxGenerateOutput.Rename> renamed = new ArrayList<>();
  // The renames answered with No, by their option value, so they are not asked again.
  private final Set<String> notRenamed = new HashSet<>();
  private boolean allowDrops;

  private MigraxGenerator(Project project) {
    this.project = project;
  }

  /** Generates the next migration. Editor thread. */
  static void generate(Project project) {
    new MigraxGenerator(project).attempt();
  }

  private void attempt() {
    MigraxCommands.runEach(
        project,
        TITLE,
        List.of(MigraxGenerateOutput.args(renamed, allowDrops)),
        TIMEOUT_MILLIS,
        true,
        results -> handle(results.get(0)),
        message -> MigraxNotifications.error(project, message));
  }

  private void handle(MigraxResult result) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    MigraxGenerateOutput output = MigraxGenerateOutput.of(result);
    if (!output.created().isEmpty()) {
      created(output.created());
      return;
    }
    if (askAboutRenames(output.renames())) {
      // A rename changes what the command finds next: run it again with the answers.
      attempt();
      return;
    }
    if (output.needsDrops()) {
      if (confirmDrops(output.drops())) {
        allowDrops = true;
        attempt();
      }
      return;
    }
    if (!result.succeeded()) {
      MigraxNotifications.error(project, result.errorMessage());
    } else if (output.noChanges()) {
      MigraxNotifications.info(project, "No changes: the entities match the last migration.");
    } else {
      MigraxNotifications.info(project, "Generate finished. See the Status tab.");
      MigraxToolWindowFactory.refreshStatus(project);
    }
  }

  /**
   * Asks about each rename not answered yet; true when at least one was a rename. A renamed
   * table ends the round: the command finds the column renames only after the table renames, so
   * the column questions of this round may be wrong.
   */
  private boolean askAboutRenames(List<MigraxGenerateOutput.Rename> renames) {
    boolean any = false;
    for (MigraxGenerateOutput.Rename rename : renames) {
      if (notRenamed.contains(rename.value()) || renamed.contains(rename)) {
        continue;
      }
      int answer =
          Messages.showYesNoDialog(
              project,
              rename.question()
                  + "\n\nRenamed keeps the data. Not renamed drops the old one with its data"
                  + " and adds a new, empty one.",
              TITLE,
              "Renamed",
              "Not Renamed",
              Messages.getQuestionIcon());
      if (answer == Messages.YES) {
        renamed.add(rename);
        any = true;
        if (rename.option().equals("--rename-table")) {
          return true;
        }
      } else {
        notRenamed.add(rename.value());
      }
    }
    return any;
  }

  private boolean confirmDrops(List<String> drops) {
    StringBuilder message = new StringBuilder("These changes delete data:\n");
    drops.forEach(drop -> message.append("\n  ").append(drop));
    message.append("\n\nGenerate the migration with them? Review its SQL before you migrate.");
    return Messages.showOkCancelDialog(
            project,
            message.toString(),
            TITLE,
            "Generate with Drops",
            "Cancel",
            Messages.getWarningIcon())
        == Messages.OK;
  }

  private void created(List<String> files) {
    MigraxNotifications.info(project, "Created " + String.join(", ", files) + ".");
    MigraxToolWindowFactory.refreshStatus(project);
    Path root = MigraxHealthService.getInstance(project).knownRoot();
    // Finding the new files reads the disk: not on the editor thread.
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              List<VirtualFile> found = new ArrayList<>();
              for (String file : files) {
                Path migration = resolve(root, file);
                if (migration == null) {
                  continue;
                }
                for (Path path : List.of(migration, rollbackOf(migration))) {
                  VirtualFile virtual =
                      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
                  if (virtual != null) {
                    found.add(virtual);
                  }
                }
              }
              ApplicationManager.getApplication()
                  .invokeLater(() -> open(found), project.getDisposed());
            });
  }

  private void open(List<VirtualFile> files) {
    FileEditorManager editors = FileEditorManager.getInstance(project);
    files.forEach(file -> editors.openFile(file, true));
  }

  /** The command prints paths relative to the Migrax project folder, or absolute ones. */
  static @Nullable Path resolve(@Nullable Path root, String printed) {
    try {
      Path path = Path.of(printed);
      if (path.isAbsolute()) {
        return path;
      }
      return root == null ? null : root.resolve(path).normalize();
    } catch (InvalidPathException e) {
      return null;
    }
  }

  /** generate writes the rollback script to rollback/ next to the migration, same name. */
  static Path rollbackOf(Path migration) {
    return migration.resolveSibling("rollback").resolve(migration.getFileName());
  }
}
