package org.migrax.intellij;

import com.google.gson.JsonElement;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.util.Alarm;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the latest {@link MigraxHealth} of a project for the status bar. It asks the command
 * for the migration list and then for the entity changes, one after the other, and tells its
 * listeners (on the editor thread) when the answer is in. After a file change it asks again,
 * once the changes stop (see {@link MigraxFileChangeListener}).
 */
@Service(Service.Level.PROJECT)
public final class MigraxHealthService implements Disposable {

  // check reads the entities and may build the project first.
  private static final long CHECK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  // Saves come in bursts (save all, a pull, autosave while typing): wait for them to stop
  // before checking, the check may build the project.
  private static final int AFTER_CHANGE_DELAY_MILLIS = 5000;

  private final Project project;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  // Written on the editor thread only (refresh() and the callbacks); actions read it anywhere.
  private volatile MigraxHealth health = MigraxHealth.unavailable("Not checked yet");
  private boolean running;
  // A file changed while a check was running: that check may have missed it.
  private boolean againWhenDone;
  private final Alarm afterChange = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  // Read by the editor's line markers in background read actions.
  private volatile MigraxEntityChanges entityChanges = MigraxEntityChanges.NONE;
  // These are also used off the editor thread (findRoot runs in background tasks).
  private volatile boolean detected;
  private volatile Path root;

  public MigraxHealthService(Project project) {
    this.project = project;
  }

  public static MigraxHealthService getInstance(Project project) {
    return project.getService(MigraxHealthService.class);
  }

  void addListener(Runnable listener) {
    listeners.add(listener);
  }

  void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /** The text for the status bar. */
  String label() {
    return health.label();
  }

  String tooltip() {
    return health.tooltip();
  }

  /** The entity changes of the last check, by class; any thread. */
  MigraxEntityChanges entityChanges() {
    return entityChanges;
  }

  /** Asks the command again; does nothing while a check is already running. Editor thread. */
  void refresh() {
    if (running || project.isDisposed()) {
      return;
    }
    running = true;
    MigraxCommands.run(
        project, "Migrax status", List.of("status", "--json"), this::onStatus, this::onProblem);
  }

  /** Asks again a little later, after the last of a burst of file changes. Any thread. */
  void refreshSoon() {
    if (!MigraxSettings.getInstance().isCheckOnChange()) {
      return;
    }
    afterChange.cancelAllRequests();
    afterChange.addRequest(this::refreshAfterChange, AFTER_CHANGE_DELAY_MILLIS);
  }

  /**
   * Asks the command again after something changed; while a check is running, once more when it
   * is done, as that one may have started before the change. Editor thread.
   */
  void refreshAfterChange() {
    if (running) {
      againWhenDone = true;
    } else {
      refresh();
    }
  }

  /** The Migrax project folder once one was found, else null. Any thread. */
  @Nullable
  Path knownRoot() {
    Path known = root;
    return detected && known != null ? known.toAbsolutePath().normalize() : null;
  }

  /** The latest health; any thread. */
  MigraxHealth health() {
    return health;
  }

  /** True while a Migrax project is found in this IDE project (see {@link #findRoot}). */
  boolean isDetected() {
    return detected;
  }

  /**
   * Finds the folder to run the command in: the Migrax project folder (also in a sub-module),
   * or the project folder when there is none, so the command can say what is missing. Reads the
   * disk: call it from a background thread. When a Migrax project shows up, or is gone (its
   * .migrax folder deleted), the status bar widget and the actions follow at once.
   */
  Optional<Path> findRoot() {
    String base = project.getBasePath();
    if (base == null) {
      return Optional.empty();
    }
    Path folder;
    try {
      folder = Path.of(base);
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
    // The folder found earlier is used again while it is still a Migrax project: searching the
    // tree for every command would be slow in a big repository.
    Path known = root;
    Optional<Path> found =
        known != null && MigraxProjectDetector.isMigraxProject(known)
            ? Optional.of(known)
            : MigraxProjectDetector.findRoot(folder);
    // Commands and the lint marks call this at the same time: one of them reports the change.
    synchronized (this) {
      root = found.orElse(null);
      if (found.isPresent() != detected) {
        detected = found.isPresent();
        ApplicationManager.getApplication()
            .invokeLater(this::detectionChanged, project.getDisposed());
      }
    }
    return Optional.of(found.orElse(folder));
  }

  /** Editor thread. Reads {@link #detected} again: it may have changed back meanwhile. */
  private void detectionChanged() {
    project.getService(StatusBarWidgetsManager.class)
        .updateWidget(MigraxStatusBarWidgetFactory.class);
    if (detected) {
      // Also when the status bar entry is hidden, which would otherwise ask for it.
      refresh();
    } else {
      // Gone: no margin icons, and nothing greyed out by an answer about a project that is gone.
      showEntityChanges(MigraxEntityChanges.NONE);
      health = MigraxHealth.unavailable("This project does not use Migrax");
      listeners.forEach(Runnable::run);
    }
    // The actions show or hide by it: update the toolbar and menus now, not at the next click.
    ActivityTracker.getInstance().inc();
  }

  private void onStatus(MigraxResult result) {
    try {
      handleStatus(result);
    } catch (RuntimeException e) {
      done(MigraxHealth.unavailable("Unexpected answer: " + e.getMessage()));
    }
  }

  private void handleStatus(MigraxResult result) {
    if (project.isDisposed()) {
      running = false;
      againWhenDone = false;
      return;
    }
    if (result.cancelled()) {
      // Cancelled by the user: do not start again because of a file change.
      againWhenDone = false;
    }
    MigraxStatus status =
        result.cancelled() || result.timedOut()
            ? null
            : MigraxStatus.from(MigraxResult.parseJsonValue(result.stdout()));
    if (status == null) {
      done(MigraxHealth.unavailable(orDefault(result.errorMessage(), "Status unavailable")));
      return;
    }
    MigraxCommands.run(
        project,
        "Migrax check",
        List.of("check", "--json"),
        CHECK_TIMEOUT_MILLIS,
        checked -> onCheck(status, checked),
        reason -> done(MigraxHealth.of(status, -1, reason)));
  }

  private void onCheck(MigraxStatus status, MigraxResult result) {
    try {
      handleCheck(status, result);
    } catch (RuntimeException e) {
      done(MigraxHealth.of(status, -1, "Unexpected answer: " + e.getMessage()));
    }
  }

  private void handleCheck(MigraxStatus status, MigraxResult result) {
    if (project.isDisposed()) {
      running = false;
      againWhenDone = false;
      return;
    }
    if (result.cancelled()) {
      againWhenDone = false;
    }
    // The command exits 2 when there are changes but it still prints them.
    JsonElement json =
        result.cancelled() || result.timedOut()
            ? null
            : MigraxResult.parseJsonValue(result.stdout());
    int changes = MigraxHealth.changesIn(json);
    String note = changes >= 0 ? "" : orDefault(result.errorMessage(), "Check unavailable");
    // A check that did not answer keeps the icons of the last one that did.
    if (changes >= 0) {
      showEntityChanges(MigraxEntityChanges.from(json));
    }
    done(MigraxHealth.of(status, changes, note));
  }

  /** Stores the changes and redraws the editor's margin icons when they differ. */
  void showEntityChanges(MigraxEntityChanges latest) {
    if (latest.equals(entityChanges)) {
      return;
    }
    entityChanges = latest;
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  private void onProblem(String reason) {
    done(MigraxHealth.unavailable(reason));
  }

  private void done(MigraxHealth latest) {
    running = false;
    health = latest;
    listeners.forEach(Runnable::run);
    // The actions grey out by the latest health: update the toolbar now, not at the next click.
    ActivityTracker.getInstance().inc();
    if (againWhenDone) {
      againWhenDone = false;
      refresh();
    }
  }

  @Override
  public void dispose() {
    // Nothing to release: the alarm is disposed with this service.
  }

  private static String orDefault(String text, String fallback) {
    return text == null || text.isBlank() ? fallback : text;
  }
}
