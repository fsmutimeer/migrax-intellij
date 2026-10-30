package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Migrate and Rollback: first a dry run and {@code migrax doctor} (which names the database), then
 * a confirm dialog, then the real run. The real run cannot be cancelled: stopping it halfway
 * could leave a migration half applied.
 */
final class MigraxDatabaseChange {

  // The checks may build the project first.
  private static final long CHECK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  // A migration on a big table can take long; the command waits for the lock itself.
  private static final long RUN_TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(12);

  /** What differs between Migrate and Rollback. */
  private record Kind(
      String title,
      List<String> dryRun,
      String dryRunKey,
      String nothingToDo,
      Function<Integer, String> question,
      String okText,
      List<String> run,
      Function<MigraxResult, String> done) {}

  private static final Kind MIGRATE =
      new Kind(
          "Migrax: Migrate",
          List.of("migrate", "--dry-run", "--json"),
          "wouldApply",
          "Nothing to migrate: every migration is applied.",
          count -> "Apply " + migrations(count) + " to this database?",
          "Migrate",
          List.of("migrate", "--json"),
          result -> {
            int applied = MigraxSchemaResults.applied(result.json());
            return applied < 0 ? "Migrate finished." : "Applied " + migrations(applied) + ".";
          });

  private static final Kind ROLLBACK =
      new Kind(
          "Migrax: Roll Back",
          List.of("rollback", "--dry-run", "--json"),
          "wouldRollBack",
          "Nothing to roll back: no migration is applied.",
          count ->
              "Roll back "
                  + migrations(count)
                  + " in this database? Data written to removed tables and columns is lost.",
          "Roll Back",
          // The dialog is the confirmation the command asks for.
          List.of("rollback", "--yes", "--json"),
          result -> {
            List<String> rolledBack = MigraxSchemaResults.migrations(result.json(), "rolledBack");
            return rolledBack == null
                ? "Rollback finished."
                : "Rolled back " + String.join(", ", rolledBack) + ".";
          });

  private MigraxDatabaseChange() {}

  /** Applies the pending migrations after the user confirms. Editor thread. */
  static void migrate(Project project) {
    start(project, MIGRATE);
  }

  /** Rolls back the newest applied migration after the user confirms. Editor thread. */
  static void rollback(Project project) {
    start(project, ROLLBACK);
  }

  private static void start(Project project, Kind kind) {
    MigraxCommands.runEach(
        project,
        kind.title() + " (checking)",
        List.of(List.of("doctor", "--json"), kind.dryRun()),
        CHECK_TIMEOUT_MILLIS,
        true,
        results -> confirm(project, kind, results.get(0), results.get(1)),
        message -> MigraxNotifications.error(project, message));
  }

  private static void confirm(
      Project project, Kind kind, MigraxResult doctor, MigraxResult dryRun) {
    if (project.isDisposed() || dryRun.cancelled()) {
      return;
    }
    if (!dryRun.succeeded()) {
      MigraxNotifications.error(project, dryRun.errorMessage());
      return;
    }
    List<String> migrations = MigraxSchemaResults.migrations(dryRun.json(), kind.dryRunKey());
    if (migrations == null) {
      MigraxNotifications.error(
          project, "The Migrax command gave an answer the plugin cannot read.");
      return;
    }
    if (migrations.isEmpty()) {
      MigraxNotifications.info(project, kind.nothingToDo());
      return;
    }
    // Doctor fails when the database cannot be reached, but still names it.
    MigraxTarget target = MigraxTarget.fromDoctor(doctor.json());
    boolean ok =
        new MigraxConfirmDialog(
                project,
                kind.title(),
                kind.question().apply(migrations.size()),
                target,
                migrations,
                kind.okText())
            .showAndGet();
    if (ok) {
      run(project, kind);
    }
  }

  private static void run(Project project, Kind kind) {
    MigraxCommands.runEach(
        project,
        kind.title(),
        List.of(kind.run()),
        RUN_TIMEOUT_MILLIS,
        false,
        results -> {
          MigraxResult result = results.get(0);
          if (result.succeeded()) {
            MigraxNotifications.info(project, kind.done().apply(result));
          } else {
            MigraxNotifications.error(
                project,
                result.errorMessage()
                    + "\nPart of it may have run before the failure: see the Status tab.");
          }
          // Also after a failure: some migrations may have run.
          MigraxToolWindowFactory.refreshStatus(project);
        },
        message -> MigraxNotifications.error(project, message));
  }

  private static String migrations(int count) {
    return count + (count == 1 ? " migration" : " migrations");
  }
}
