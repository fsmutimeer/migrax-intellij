package org.migrax.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * Generate: runs {@code migrax generate --no-input}, asks in a dialog about each change that
 * may be a rename and about drops, and runs it again with the answers. The command cannot ask
 * itself: it has no terminal here. Opens the new migration and its rollback script.
 */
final class MigraxGenerator {

  /** A plain migration, the expand half of a zero-downtime change, or its waiting contract. */
  enum Mode {
    NORMAL("Migrax: Generate"),
    ZERO_DOWNTIME("Migrax: Generate for Zero Downtime"),
    CONTRACT("Migrax: Generate Contract");

    private final String title;

    Mode(String title) {
      this.title = title;
    }
  }

  // Generate may build the project first.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  /**
   * Where generate --zero-downtime keeps the contract migrations until --contract. For a further
   * database it is .migrax/<database>/pending, but the plugin works on the default one only.
   */
  static final String PENDING_FOLDER = ".migrax/pending";

  private final Project project;
  private final Mode mode;
  private final List<MigraxGenerateOutput.Rename> renamed = new ArrayList<>();
  // The renames answered with No, by their option value, so they are not asked again.
  private final Set<String> notRenamed = new HashSet<>();
  private boolean allowDrops;
  private boolean keepData;

  private MigraxGenerator(Project project, Mode mode) {
    this.project = project;
    this.mode = mode;
  }

  /** Generates the next migration. Editor thread. */
  static void generate(Project project, Mode mode) {
    new MigraxGenerator(project, mode).attempt();
  }

  /** True when generate --zero-downtime left contract migrations to add. Reads the disk. */
  static boolean hasWaitingContract(@Nullable Path root) {
    Path folder = root == null ? null : root.resolve(PENDING_FOLDER);
    if (folder == null || !Files.isDirectory(folder)) {
      return false;
    }
    try (Stream<Path> files = Files.list(folder)) {
      return files.anyMatch(file -> file.getFileName().toString().endsWith(".sql"));
    } catch (IOException | UncheckedIOException e) {
      return false;
    }
  }

  private void attempt() {
    MigraxCommands.runEach(
        project,
        mode.title,
        List.of(MigraxGenerateOutput.args(mode, renamed, allowDrops, keepData)),
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
      created(output);
      return;
    }
    if (askAboutRenames(output.renames())) {
      // A rename changes what the command finds next: run it again with the answers.
      attempt();
      return;
    }
    if (output.needsDrops()) {
      MigraxDropDialog dialog =
          new MigraxDropDialog(project, mode.title, output.drops(), mode == Mode.NORMAL);
      if (dialog.showAndGet()) {
        allowDrops = true;
        keepData = dialog.keepData();
        attempt();
      }
      return;
    }
    if (!result.succeeded()) {
      MigraxNotifications.error(project, result.errorMessage());
    } else if (output.noChanges()) {
      MigraxNotifications.info(
          project,
          mode == Mode.CONTRACT
              ? "No contract migration is waiting."
              : "No changes: the entities match the last migration.");
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
              mode.title,
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

  private void created(MigraxGenerateOutput output) {
    List<String> files = output.created();
    String message = "Created " + String.join(", ", files) + ".";
    if (!output.notKept().isEmpty()) {
      message +=
          "\nNo backup of "
              + String.join(", ", output.notKept())
              + ": the table has no key to match its rows by, so Roll Back cannot restore it.";
    }
    if (!output.waiting().isEmpty()) {
      message +=
          "\nThe contract migration waits for the next release. When every running instance"
              + " has this one, use Generate Contract.";
    }
    MigraxNotifications.info(project, message);
    MigraxToolWindowFactory.refreshStatus(project);
    Path root = MigraxHealthService.getInstance(project).knownRoot();
    List<Path> paths = new ArrayList<>();
    for (String file : files) {
      Path migration = resolve(root, file);
      if (migration != null) {
        paths.add(migration);
        paths.add(rollbackOf(migration));
      }
    }
    // The command wrote behind the IDE's back: the new snapshot, a waiting contract and the
    // migration folders. The Project view shows them now, not at the IDE's next look at the disk.
    List<Path> changed = new ArrayList<>();
    if (root != null) {
      changed.add(root.resolve(".migrax"));
    }
    paths.stream().map(Path::getParent).distinct().forEach(changed::add);
    LocalFileSystem.getInstance().refreshNioFiles(changed, true, true, null);
    open(project, paths);
  }

  /** Opens the files the command just wrote, skipping any that are not there. Editor thread. */
  static void open(Project project, List<Path> paths) {
    // Finding the new files reads the disk: not on the editor thread.
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              List<VirtualFile> found = new ArrayList<>();
              for (Path path : paths) {
                VirtualFile virtual =
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
                if (virtual != null) {
                  found.add(virtual);
                }
              }
              ApplicationManager.getApplication()
                  .invokeLater(
                      () -> {
                        FileEditorManager editors = FileEditorManager.getInstance(project);
                        found.forEach(file -> editors.openFile(file, true));
                      },
                      project.getDisposed());
            });
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
