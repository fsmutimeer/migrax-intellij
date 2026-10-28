package org.migrax.intellij;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/** Starts the Migrax command for a project, for the tool window tabs. */
final class MigraxCommands {

  private MigraxCommands() {}

  /** Runs the command with the default time limit; see the longer overload. */
  static void run(
      Project project,
      String title,
      List<String> args,
      Consumer<MigraxResult> onResult,
      Consumer<String> onProblem) {
    run(project, title, args, MigraxCli.DEFAULT_TIMEOUT_MILLIS, onResult, onProblem);
  }

  /**
   * Runs the command in the Migrax project folder as a cancellable background task. Finding the
   * folder and the command happens in the background too, so the editor thread never waits for
   * the disk.
   *
   * @param timeoutMillis time limit; give commands that may build the project more
   * @param onResult called on the editor thread with the result (also when cancelled)
   * @param onProblem called on the editor thread with a message when the command cannot be
   *     started at all (no folder, command not found)
   */
  static void run(
      Project project,
      String title,
      List<String> args,
      long timeoutMillis,
      Consumer<MigraxResult> onResult,
      Consumer<String> onProblem) {
    new Task.Backgroundable(project, title, true) {
      private MigraxResult result;
      private String problem;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Optional<Path> folder = MigraxHealthService.getInstance(project).findRoot();
        if (folder.isEmpty()) {
          problem = "This project has no folder on disk.";
          return;
        }
        Optional<Path> launcher =
            MigraxCliLocator.forThisMachine().locate(MigraxSettings.getInstance().getCliPath());
        if (launcher.isEmpty()) {
          problem =
              "The Migrax command was not found. Set its path in Settings > Tools > Migrax.";
          return;
        }
        result = new MigraxCli(launcher.get(), folder.get(), timeoutMillis).run(args, indicator);
      }

      @Override
      public void onFinished() {
        // Always report back, so a screen waiting for the answer is never left waiting.
        if (problem != null) {
          onProblem.accept(problem);
        } else {
          onResult.accept(result == null ? MigraxResult.cancelledRun() : result);
        }
      }
    }.queue();
  }
}
