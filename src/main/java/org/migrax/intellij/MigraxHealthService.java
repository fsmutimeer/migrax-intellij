package org.migrax.intellij;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the latest {@link MigraxHealth} of a project for the status bar. It asks the command
 * for the migration list and then for the entity changes, one after the other, and tells its
 * listeners (on the editor thread) when the answer is in.
 */
@Service(Service.Level.PROJECT)
public final class MigraxHealthService {

  // check reads the entities and may build the project first.
  private static final long CHECK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

  private final Project project;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  // Only used on the editor thread: refresh() and every callback run there.
  private MigraxHealth health = MigraxHealth.unavailable("Not checked yet");
  private boolean running;

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

  /** Asks the command again; does nothing while a check is already running. Editor thread. */
  void refresh() {
    if (running || project.isDisposed()) {
      return;
    }
    running = true;
    boolean started =
        MigraxCommands.run(
            project, "Migrax status", List.of("status", "--json"), this::onStatus, this::onProblem);
    if (!started) {
      running = false;
    }
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
      return;
    }
    MigraxStatus status =
        result.cancelled() || result.timedOut()
            ? null
            : MigraxStatus.from(MigraxResult.parseJsonValue(result.stdout()));
    if (status == null) {
      done(MigraxHealth.unavailable(orDefault(result.errorMessage(), "Status unavailable")));
      return;
    }
    boolean started =
        MigraxCommands.run(
            project,
            "Migrax check",
            List.of("check", "--json"),
            CHECK_TIMEOUT_MILLIS,
            checked -> onCheck(status, checked),
            reason -> done(MigraxHealth.of(status, -1, reason)));
    if (!started) {
      running = false;
    }
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
      return;
    }
    // The command exits 2 when there are changes but it still prints them.
    int changes =
        result.cancelled() || result.timedOut()
            ? -1
            : MigraxHealth.changesIn(MigraxResult.parseJsonValue(result.stdout()));
    String note = changes >= 0 ? "" : orDefault(result.errorMessage(), "Check unavailable");
    done(MigraxHealth.of(status, changes, note));
  }

  private void onProblem(String reason) {
    done(MigraxHealth.unavailable(reason));
  }

  private void done(MigraxHealth latest) {
    running = false;
    health = latest;
    listeners.forEach(Runnable::run);
  }

  private static String orDefault(String text, String fallback) {
    return text == null || text.isBlank() ? fallback : text;
  }
}
