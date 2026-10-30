package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Backups: lists the {@code migrax_backup_*} tables that migrations generated with a backup of
 * the dropped data keep, and drops them (all, or those of one migration) after a confirm dialog
 * that shows the database. Once dropped, Roll Back of those migrations cannot restore the data.
 */
final class MigraxBackups {

  // The checks may build the project first.
  private static final long CHECK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  // Dropping a big table can take a while.
  private static final long RUN_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);
  private static final String TITLE = "Migrax: Backups";
  // migrax_backup_0005_categories_summary: the number of the migration that made it.
  private static final Pattern NUMBER = Pattern.compile("^migrax_backup_(\\d+)_");

  private MigraxBackups() {}

  /** Editor thread. */
  static void show(Project project) {
    MigraxCommands.runEach(
        project,
        TITLE + " (checking)",
        List.of(List.of("doctor", "--json"), List.of("backups", "--json")),
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
    List<String> tables = MigraxResult.strings(result.json(), "backups");
    if (tables == null) {
      MigraxNotifications.error(
          project, "The Migrax command gave an answer the plugin cannot read.");
      return;
    }
    if (tables.isEmpty()) {
      MigraxNotifications.info(
          project, "No backup tables: no migration kept a backup of dropped data.");
      return;
    }
    List<String> migrations = migrations(tables);
    List<String> choices = new ArrayList<>();
    choices.add("All " + tables.size() + " backup tables");
    migrations.forEach(number -> choices.add("Only those of migration " + number));
    MigraxConfirmDialog dialog =
        new MigraxConfirmDialog(
            project,
            TITLE,
            "These tables keep data that Roll Back restores. Drop them only when you will not"
                + " roll those migrations back.",
            MigraxTarget.fromDoctor(doctor.json()),
            tables,
            "Drop Backups",
            migrations.size() > 1 ? choices : List.of());
    if (dialog.showAndGet()) {
      int choice = dialog.choice();
      drop(project, choice == 0 ? null : migrations.get(choice - 1));
    }
  }

  private static void drop(Project project, @Nullable String migration) {
    MigraxCommands.runEach(
        project,
        TITLE,
        List.of(args(migration)),
        RUN_TIMEOUT_MILLIS,
        false,
        results -> {
          MigraxResult result = results.get(0);
          if (result.succeeded()) {
            List<String> dropped = MigraxResult.strings(result.json(), "dropped");
            MigraxNotifications.info(
                project,
                dropped == null
                    ? "Drop finished."
                    : "Dropped " + dropped.size() + " backup table(s).");
          } else {
            MigraxNotifications.error(project, result.errorMessage());
          }
        },
        message -> MigraxNotifications.error(project, message));
  }

  /** The dialog is the confirmation the command asks for; null drops every backup. */
  static List<String> args(@Nullable String migration) {
    List<String> args = new ArrayList<>(List.of("backups", "--drop"));
    if (migration != null) {
      args.add(migration);
    }
    args.addAll(List.of("--yes", "--json"));
    return args;
  }

  /** The numbers of the migrations that made these tables, in order, without repeats. */
  static List<String> migrations(List<String> tables) {
    List<String> numbers = new ArrayList<>();
    for (String table : tables) {
      Matcher number = NUMBER.matcher(table);
      if (number.find() && !numbers.contains(number.group(1))) {
        numbers.add(number.group(1));
      }
    }
    return numbers;
  }
}
