package org.migrax.intellij;

import com.google.gson.JsonElement;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
  // health and running are only used on the editor thread: refresh() and the callbacks.
  private MigraxHealth health = MigraxHealth.unavailable("Not checked yet");
  private boolean running;
  // A file changed while a check was running: that check may have missed it.
  private boolean againWhenDone;
  private final Alarm afterChange = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  // Read by the editor's line markers in background read actions.
  private volatile MigraxEntityChanges entityChanges = MigraxEntityChanges.NONE;
  // These are also used off the editor thread (findRoot runs in background tasks).
  private volatile boolean detected;
  private volatile Path root;
  private final AtomicBoolean detectedOnce = new AtomicBoolean();

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

  private void refreshAfterChange() {
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

  /** True once a Migrax project was found in this IDE project (see {@link #findRoot}). */
  boolean isDetected() {
    return detected;
  }

  /**
   * Finds the folder to run the command in: the Migrax project folder (also in a sub-module),
   * or the project folder when there is none, so the command can say what is missing. Reads the
   * disk: call it from a background thread. When a Migrax project shows up for the first time
   * the status bar widget is switched on.
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
    root = found.orElse(null);
    if (found.isPresent() && detectedOnce.compareAndSet(false, true)) {
      detected = true;
      ApplicationManager.getApplication().invokeLater(this::showWidget, project.getDisposed());
    }
    return Optional.of(found.orElse(folder));
  }

  private void showWidget() {
    project.getService(StatusBarWidgetsManager.class)
        .updateWidget(MigraxStatusBarWidgetFactory.class);
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
  private void showEntityChanges(MigraxEntityChanges latest) {
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
