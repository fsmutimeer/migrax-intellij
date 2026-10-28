package org.migrax.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the Migrax command in a folder, one call at a time.
 *
 * <p>Never call {@link #run} on the editor thread: it waits for the process. Use {@link
 * #runInBackground} from the UI. Cancelling the progress stops the process and its children.
 * Passwords never go on the command line: the command reads them from its own settings and
 * environment variables, which the process inherits (the environment a shell would give it,
 * so an IDE started from the dock sees the same variables as a terminal).
 *
 * <p>One call at a time per working folder, across all objects: two runs in the same project
 * would fight over the migration lock. The wait is not reentrant, so a nested call from the
 * same thread waits for itself until cancelled; callers must not nest.
 *
 * <p>Exit code 2 (changes detected) counts as a finished run for every command: the caller
 * reads the JSON to see what it means.
 */
final class MigraxCli {

  private static final Map<Path, Semaphore> TURNS = new ConcurrentHashMap<>();

  static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);
  private static final long LOCK_POLL_MILLIS = 100;

  private final Path launcher;
  private final Path workDir;
  private final long timeoutMillis;
  private final Semaphore oneAtATime;

  MigraxCli(Path launcher, Path workDir) {
    this(launcher, workDir, DEFAULT_TIMEOUT_MILLIS);
  }

  MigraxCli(Path launcher, Path workDir, long timeoutMillis) {
    if (timeoutMillis <= 0 || timeoutMillis > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Timeout out of range: " + timeoutMillis);
    }
    this.launcher = launcher;
    this.workDir = workDir;
    this.oneAtATime =
        TURNS.computeIfAbsent(workDir.toAbsolutePath().normalize(), key -> new Semaphore(1));
    this.timeoutMillis = timeoutMillis;
  }

  /**
   * Runs the command with these arguments and waits for it. Other calls in this folder wait
   * their turn; cancelling the indicator while waiting gives a cancelled result.
   *
   * @param indicator checked while waiting and while the process runs; may be null
   */
  MigraxResult run(List<String> args, @Nullable ProgressIndicator indicator) {
    try {
      if (!acquire(indicator)) {
        return MigraxResult.cancelledRun();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return MigraxResult.cancelledRun();
    }
    try {
      return execute(args, indicator);
    } finally {
      oneAtATime.release();
    }
  }

  /**
   * Runs the command as a cancellable background task and hands the result to {@code onDone} on
   * the editor thread.
   */
  void runInBackground(
      @NotNull Project project,
      @NotNull String title,
      @NotNull List<String> args,
      @NotNull Consumer<MigraxResult> onDone) {
    new Task.Backgroundable(project, title, true) {
      private MigraxResult result;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        result = MigraxCli.this.run(args, indicator);
      }

      @Override
      public void onFinished() {
        if (result != null) {
          onDone.accept(result);
        }
      }
    }.queue();
  }

  private boolean acquire(@Nullable ProgressIndicator indicator) throws InterruptedException {
    while (!oneAtATime.tryAcquire(LOCK_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
      if (indicator != null && indicator.isCanceled()) {
        return false;
      }
    }
    return true;
  }

  private MigraxResult execute(List<String> args, @Nullable ProgressIndicator indicator) {
    GeneralCommandLine command =
        new GeneralCommandLine(launcher.toString())
            .withParameters(args)
            .withWorkDirectory(workDir.toFile())
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(StandardCharsets.UTF_8);
    try {
      // The launcher starts a JVM behind a script: stop the whole tree, not just the script.
      CapturingProcessHandler handler =
          new CapturingProcessHandler(command) {
            @Override
            protected boolean shouldDestroyProcessRecursively() {
              return true;
            }

            // The platform's own tree kill needs native code that is not always there, so the
            // children are also stopped through the JDK before the script itself.
            @Override
            public void destroyProcess() {
              List<ProcessHandle> children =
                  getProcess().toHandle().descendants().collect(Collectors.toList());
              super.destroyProcess();
              children.forEach(ProcessHandle::destroyForcibly);
            }
          };
      ProcessOutput output =
          indicator == null
              ? handler.runProcess((int) timeoutMillis, true)
              : handler.runProcessWithProgressIndicator(indicator, (int) timeoutMillis, true);
      return new MigraxResult(
          output.getExitCode(),
          output.getStdout(),
          output.getStderr(),
          output.isCancelled(),
          output.isTimeout());
    } catch (ExecutionException e) {
      return MigraxResult.failedToStart(launcher + ": " + e.getMessage());
    }
  }
}
