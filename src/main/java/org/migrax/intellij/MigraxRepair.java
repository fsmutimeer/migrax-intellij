package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Repair: reads {@code migrax status --json} and fixes the history of failed and missing
 * migrations with {@code migrax repair}, after a confirm dialog that shows the database. A
 * missing migration is removed from the history; for a failed one the user says whether the
 * database was restored or every statement took effect. The repair itself cannot be cancelled.
 */
final class MigraxRepair {

  // The checks may build the project first.
  private static final long CHECK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  // Only changes the history table, but waits while another process migrates.
  private static final long RUN_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);
  private static final String TITLE = "Migrax: Repair";

  /**
   * The answers for a failed migration, in the order of {@link #FAILED_ACTIONS}. Running it
   * again is first: when it did take effect, the rerun fails and says so, while recording a half
   * applied migration as applied would go unnoticed.
   */
  private static final List<String> FAILED_CHOICES =
      List.of(
          "I restored the database: run it again on the next migrate",
          "Every statement took effect: record it as applied");

  private static final List<String> FAILED_ACTIONS = List.of("retry", "applied");

  /** One run of {@code migrax repair}; only forget takes several migrations. */
  record Run(List<String> migrations, String action) {

    /** The dialog is the confirmation the command asks for. */
    List<String> args() {
      List<String> args = new ArrayList<>();
      args.add("repair");
      args.addAll(migrations);
      args.addAll(List.of("--action", action, "--yes", "--json"));
      return args;
    }

    String done(MigraxResult result) {
      String names = String.join(", ", migrations);
      return switch (action) {
        case "forget" -> {
          List<String> forgotten = MigraxResult.strings(result.json(), "forgotten");
          yield forgotten == null || forgotten.isEmpty()
              ? "Nothing was removed from the migration history."
              : "Removed " + String.join(", ", forgotten) + " from the migration history.";
        }
        case "applied" -> "Recorded " + names + " as applied.";
        default -> names + " will run again on the next migrate.";
      };
    }
  }

  private MigraxRepair() {}

  /** Editor thread. */
  static void repair(Project project) {
    MigraxCommands.runEach(
        project,
        TITLE + " (checking)",
        List.of(List.of("doctor", "--json"), List.of("status", "--json")),
        CHECK_TIMEOUT_MILLIS,
        true,
        results -> confirm(project, results.get(0), results.get(1)),
        message -> MigraxNotifications.error(project, message));
  }

  private static void confirm(Project project, MigraxResult doctor, MigraxResult result) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    if (!result.succeeded()) {
      MigraxNotifications.error(project, result.errorMessage());
      return;
    }
    MigraxStatus status = MigraxStatus.from(MigraxResult.parseJsonValue(result.stdout()));
    if (status == null) {
      MigraxNotifications.error(
          project, "The Migrax command gave an answer the plugin cannot read.");
      return;
    }
    List<String> missing = inState(status, "missing");
    List<String> failed = inState(status, "failed");
    if (missing.isEmpty() && failed.isEmpty()) {
      MigraxNotifications.info(project, nothingToRepair(status));
      return;
    }
    if (hasSchemas(status)) {
      // The command repairs by file name in the schema it connects to.
      MigraxNotifications.info(
          project,
          "This project has migrations in several schemas. Run 'migrax repair' in a terminal"
              + " for the schema that needs it.");
      return;
    }
    MigraxTarget target = MigraxTarget.fromDoctor(doctor.json());
    List<Run> runs = new ArrayList<>();
    if (!missing.isEmpty()
        && new MigraxConfirmDialog(
                project,
                TITLE,
                "Remove these migrations from the history? Their files are gone; the database"
                    + " keeps the changes they made.",
                target,
                missing,
                "Remove from History")
            .showAndGet()) {
      runs.add(new Run(missing, "forget"));
    }
    // The command repairs one failed migration at a time, and each may need another answer.
    for (String migration : failed) {
      MigraxConfirmDialog dialog =
          new MigraxConfirmDialog(
              project,
              TITLE,
              "This migration failed or was interrupted. Look at the database first: what"
                  + " happened?",
              target,
              List.of(migration),
              "Repair",
              FAILED_CHOICES);
      if (dialog.showAndGet()) {
        runs.add(new Run(List.of(migration), FAILED_ACTIONS.get(dialog.choice())));
      }
    }
    if (!runs.isEmpty()) {
      run(project, runs);
    }
  }

  private static void run(Project project, List<Run> runs) {
    MigraxCommands.runEach(
        project,
        TITLE,
        runs.stream().map(Run::args).toList(),
        RUN_TIMEOUT_MILLIS,
        false,
        results -> {
          for (int i = 0; i < results.size(); i++) {
            MigraxResult result = results.get(i);
            if (result.succeeded()) {
              MigraxNotifications.info(project, runs.get(i).done(result));
            } else {
              MigraxNotifications.error(project, result.errorMessage());
            }
          }
          MigraxToolWindowFactory.refreshStatus(project);
        },
        message -> MigraxNotifications.error(project, message));
  }

  /** The versions of the migrations in this state. */
  static List<String> inState(MigraxStatus status, String state) {
    return status.rows().stream()
        .filter(row -> row.state().equals(state))
        .map(MigraxStatus.Row::version)
        .distinct()
        .toList();
  }

  static boolean hasSchemas(MigraxStatus status) {
    return status.rows().stream().anyMatch(row -> !row.schema().isEmpty());
  }

  static String nothingToRepair(MigraxStatus status) {
    return inState(status, "changed").isEmpty()
        ? "Nothing to repair: no failed or missing migrations."
        : "Nothing to repair: no failed or missing migrations. A changed migration file is"
            + " fixed by putting it back as it was applied.";
  }
}
