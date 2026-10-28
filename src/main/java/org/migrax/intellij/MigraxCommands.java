package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Starts the Migrax command for a project, for the tool window tabs. */
final class MigraxCommands {

  private MigraxCommands() {}

  /**
   * Runs the command in the project folder as a background task.
   *
   * @param onResult called on the editor thread with the result (also when cancelled)
   * @param onProblem called at once, on the calling thread, with a message when the command
   *     cannot be started at all (no folder, command not found)
   * @return true when the task was started
   */
  static boolean run(
      Project project,
      String title,
      List<String> args,
      Consumer<MigraxResult> onResult,
      Consumer<String> onProblem) {
    return run(project, title, args, MigraxCli.DEFAULT_TIMEOUT_MILLIS, onResult, onProblem);
  }

  /** Like {@link #run} with a time limit of your own, for commands that may build the project. */
  static boolean run(
      Project project,
      String title,
      List<String> args,
      long timeoutMillis,
      Consumer<MigraxResult> onResult,
      Consumer<String> onProblem) {
    String base = project.getBasePath();
    if (base == null) {
      onProblem.accept("This project has no folder on disk.");
      return false;
    }
    Path folder;
    try {
      folder = Path.of(base);
    } catch (InvalidPathException e) {
      onProblem.accept("The project folder is not a valid path: " + base);
      return false;
    }
    Optional<Path> launcher =
        MigraxCliLocator.forThisMachine().locate(MigraxSettings.getInstance().getCliPath());
    if (launcher.isEmpty()) {
      onProblem.accept(
          "The Migrax command was not found. Set its path in Settings > Tools > Migrax.");
      return false;
    }
    new MigraxCli(launcher.get(), folder, timeoutMillis)
        .runInBackground(project, title, args, onResult);
    return true;
  }
}
