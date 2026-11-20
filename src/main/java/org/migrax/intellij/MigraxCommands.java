package org.migrax.intellij;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Starts the Migrax command for a project, for the tool window tabs and the actions. */
final class MigraxCommands {

  static final String NOT_FOUND =
      "The Migrax command was not found. Download it from the notification, or set the path of"
          + " an installed one in Settings > Tools > Migrax.";

  // One notification at a time: the Status tab, the lint marks and the actions all run commands.
  private static final AtomicBoolean CLEAR_OFFERED = new AtomicBoolean();

  private MigraxCommands() {}

  /**
   * The Status tab's text when the path in the settings does not exist (moved, deleted, or
   * mistyped).
   */
  static String gone(String configured, boolean installedFound) {
    return "The Migrax command set in Settings > Tools > Migrax does not exist: "
        + configured
        + (installedFound
            ? ". Use the installed one from the notification, or clear the path in the settings."
            : ". Clear the setting from the notification, or in the settings, and the plugin"
                + " offers to download Migrax.");
  }

  /** The notification's text; HTML-escaped, since paths may hold {@code &} or {@code <}. */
  static String goneNotification(String configured, @Nullable Path installed) {
    return MigraxNotifications.html(
        "The Migrax command set in the settings does not exist: "
            + configured
            + (installed != null
                ? ". Migrax is installed at " + installed + "."
                : ". No other Migrax was found on this computer: clear the setting and the"
                    + " plugin offers to download one."));
  }

  /** Whether the setting still holds the path the notification was about. */
  static boolean stillSetTo(String setting, String configured) {
    return setting.trim().equals(configured);
  }

  /**
   * Offers to clear a path that does not exist, so the installed Migrax (or a download) is
   * used. Only on a click: the user may want that exact version back. Any thread.
   */
  private static void offerToClear(Project project, String configured, @Nullable Path installed) {
    if (!CLEAR_OFFERED.compareAndSet(false, true)) {
      return;
    }
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Migrax")
                    .createNotification(
                        "Migrax", goneNotification(configured, installed), NotificationType.WARNING)
                    .addAction(
                        NotificationAction.createSimpleExpiring(
                            installed != null ? "Use the Installed Migrax" : "Clear the Setting",
                            () -> {
                              MigraxSettings settings = MigraxSettings.getInstance();
                              // Unless it was changed meanwhile in the settings.
                              if (stillSetTo(settings.getCliPath(), configured)) {
                                settings.setCliPath("");
                              }
                              // After clearing: a command meanwhile would offer this again.
                              CLEAR_OFFERED.set(false);
                              // The setting is the IDE's, so every open project uses it.
                              for (Project open : ProjectManager.getInstance().getOpenProjects()) {
                                MigraxToolWindowFactory.refreshStatus(open);
                              }
                            }))
                    .addAction(
                        NotificationAction.createSimpleExpiring(
                            "Settings",
                            () -> {
                              CLEAR_OFFERED.set(false);
                              ShowSettingsUtil.getInstance()
                                  .showSettingsDialog(project, MigraxConfigurable.class);
                            }))
                    // Closed without an answer: offer again at the next command.
                    .whenExpired(() -> CLEAR_OFFERED.set(false))
                    .notify(project),
            project.getDisposed());
  }

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
    runEach(
        project,
        title,
        List.of(args),
        timeoutMillis,
        true,
        results -> onResult.accept(results.get(0)),
        onProblem);
  }

  /**
   * Runs the command once per argument list, one after the other, in one background task; see
   * {@link #run}. A cancelled run ends the task: the runs after it count as cancelled.
   *
   * @param cancellable false for a command that changes the database: stopping it halfway could
   *     leave a migration half applied, so once started it runs to the end (see {@link
   *     MigraxCli#run(List, ProgressIndicator, boolean)})
   * @param onResults one result per argument list, in their order
   */
  static void runEach(
      Project project,
      String title,
      List<List<String>> runs,
      long timeoutMillis,
      boolean cancellable,
      Consumer<List<MigraxResult>> onResults,
      Consumer<String> onProblem) {
    if (!MigraxTrust.isTrusted(project)) {
      // Later, as a run would answer: callers expect it after they set up their state.
      ApplicationManager.getApplication()
          .invokeLater(() -> onProblem.accept(MigraxTrust.UNTRUSTED), project.getDisposed());
      return;
    }
    new Task.Backgroundable(project, title, cancellable) {
      private final List<MigraxResult> results = new ArrayList<>();
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
          String configured = MigraxSettings.getInstance().getCliPath();
          if (configured == null || configured.isBlank()) {
            problem = NOT_FOUND;
            MigraxDownload.offer(project);
          } else if (MigraxCliLocator.isGone(configured)) {
            // Stale, e.g. a deleted folder: one click clears it, but never without asking,
            // since another Migrax version could write different migrations.
            Path installed = MigraxCliLocator.forThisMachine().locate("").orElse(null);
            problem = gone(configured.trim(), installed != null);
            offerToClear(project, configured.trim(), installed);
          } else {
            // A path set on purpose is not replaced by a download: the setting is wrong.
            problem =
                "The path in Settings > Tools > Migrax is not a Migrax command: "
                    + configured.trim()
                    + ". Correct it, or clear it to use the one on PATH or to download one.";
          }
          return;
        }
        // Only for the plugin's own download, once per IDE session.
        MigraxDownload.checkForUpdate(project, launcher.get());
        MigraxCli cli =
            new MigraxCli(
                launcher.get(),
                folder.get(),
                timeoutMillis,
                MigraxProjectSettings.getInstance(project).environment());
        for (List<String> args : runs) {
          MigraxResult result = cli.run(args, indicator, cancellable);
          results.add(result);
          if (result.cancelled()) {
            return;
          }
        }
      }

      @Override
      public void onFinished() {
        // Always report back, so a screen waiting for the answer is never left waiting.
        if (problem != null) {
          onProblem.accept(problem);
          return;
        }
        while (results.size() < runs.size()) {
          results.add(MigraxResult.cancelledRun());
        }
        onResults.accept(results);
      }
    }.queue();
  }
}
