package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * What the status bar says about the project: migrations that failed or wait to run, and
 * entity changes that are not in a migration yet.
 */
final class MigraxHealth {

  private final int problems;
  private final int pending;
  // From the status; all false when it is not known, so nothing is greyed out.
  private final boolean hasMigrations;
  private final boolean nothingToApply;
  private final boolean nothingApplied;
  private final boolean nothingBroken;
  private final int changes;
  private final String note;

  /**
   * @param changes entity changes not in a migration; -1 when that could not be checked
   * @param note why something could not be checked, or empty
   */
  MigraxHealth(int problems, int pending, int changes, String note) {
    this(problems, pending, false, false, false, false, changes, note);
  }

  private MigraxHealth(
      int problems,
      int pending,
      boolean hasMigrations,
      boolean nothingToApply,
      boolean nothingApplied,
      boolean nothingBroken,
      int changes,
      String note) {
    this.problems = problems;
    this.pending = pending;
    this.hasMigrations = hasMigrations;
    this.nothingToApply = nothingToApply;
    this.nothingApplied = nothingApplied;
    this.nothingBroken = nothingBroken;
    this.changes = changes;
    this.note = note;
  }

  /** The text of the status bar widget, such as "Migrax: 2 pending, 1 change". */
  String label() {
    List<String> parts = new ArrayList<>();
    if (problems > 0) {
      parts.add(problems + (problems == 1 ? " problem" : " problems"));
    }
    if (pending > 0) {
      parts.add(pending + " pending");
    }
    if (changes > 0) {
      parts.add(changes + (changes == 1 ? " change" : " changes"));
    }
    if (!parts.isEmpty()) {
      return "Migrax: " + String.join(", ", parts);
    }
    return note.isEmpty() ? "Migrax: in sync" : "Migrax: unavailable";
  }

  /** The tooltip: the same facts in words, and the reason when a check failed. */
  String tooltip() {
    List<String> lines = new ArrayList<>();
    lines.add(problems + " failed, changed or missing migrations");
    lines.add(pending + " pending migrations");
    lines.add(
        changes < 0 ? "Entity changes: not checked" : changes + " entity changes to generate");
    if (!note.isEmpty()) {
      lines.add(note);
    }
    lines.add("Click to refresh and open the Migrax tool window");
    return String.join("\n", lines);
  }

  /** Why Migrate has nothing to do, or null when it may have (also when not known). */
  @Nullable
  String nothingToMigrate() {
    return nothingToApply ? "No pending migrations at the last check" : null;
  }

  /** Why Roll Back has nothing to do, or null when it may have. */
  @Nullable
  String nothingToRollBack() {
    return nothingApplied ? "No applied migrations at the last check" : null;
  }

  /** Why Repair has nothing to do, or null when it may have. */
  @Nullable
  String nothingToRepair() {
    return nothingBroken ? "No failed or missing migrations at the last check" : null;
  }

  /**
   * Why Generate has nothing to do, or null when it may have. Without any migration it stays on:
   * the first run writes the baseline of an existing database.
   */
  @Nullable
  String nothingToGenerate() {
    return changes == 0 && hasMigrations ? "No entity changes at the last check" : null;
  }

  /** Used when the command could not be started or answered at all. */
  static MigraxHealth unavailable(String reason) {
    return new MigraxHealth(0, 0, -1, reason);
  }

  /** Combines the migration list and the number of entity changes (-1: unknown). */
  static MigraxHealth of(MigraxStatus status, int changes, String note) {
    int pending = (int) status.rows().stream().filter(r -> r.state().equals("pending")).count();
    int problems = (int) status.rows().stream().filter(MigraxStatus.Row::isProblem).count();
    // Only states that surely leave nothing to do count, so a state this plugin does not know
    // keeps the action on. Skipped migrations belong to other environments.
    boolean nothingToApply = status.rows().stream().allMatch(r -> is(r, "applied", "skipped"));
    boolean nothingApplied = status.rows().stream().allMatch(r -> is(r, "pending", "skipped"));
    // Repair fixes failed and missing migrations only.
    boolean nothingBroken =
        status.rows().stream()
            .allMatch(r -> is(r, "applied", "skipped", "pending", "outdated", "changed"));
    return new MigraxHealth(
        problems,
        pending,
        !status.rows().isEmpty(),
        nothingToApply,
        nothingApplied,
        nothingBroken,
        changes,
        note);
  }

  private static boolean is(MigraxStatus.Row row, String... states) {
    return List.of(states).contains(row.state());
  }

  /** Counts the changes of {@code migrax check --json}; -1 when the output is not that. */
  static int changesIn(@Nullable JsonElement json) {
    if (json instanceof JsonObject object && object.get("changes") instanceof JsonArray list) {
      return list.size();
    }
    return -1;
  }
}
