package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MigraxHealthTest {

  private static MigraxStatus status(String states) {
    StringBuilder rows = new StringBuilder();
    for (String state : states.split(",")) {
      if (rows.length() > 0) {
        rows.append(',');
      }
      rows.append("{\"version\":\"v\",\"state\":\"").append(state).append("\"}");
    }
    return MigraxStatus.from(MigraxResult.parseJsonValue("{\"migrations\":[" + rows + "]}"));
  }

  @Test
  public void allAppliedAndNoChangesIsInSync() {
    assertEquals("Migrax: in sync", MigraxHealth.of(status("applied,applied"), 0, "").label());
  }

  @Test
  public void showsProblemsPendingAndChangesTogether() {
    MigraxHealth health = MigraxHealth.of(status("applied,pending,pending,failed"), 1, "");
    assertEquals("Migrax: 1 problem, 2 pending, 1 change", health.label());
    assertTrue(health.tooltip().contains("2 pending migrations"));
    assertTrue(health.tooltip().contains("1 entity changes to generate"));
  }

  @Test
  public void pluralChanges() {
    assertEquals("Migrax: 3 changes", MigraxHealth.of(status("applied"), 3, "").label());
  }

  @Test
  public void anUncheckedChangeCountWithANoteIsUnavailableWhenNothingElseShows() {
    MigraxHealth health = MigraxHealth.of(status("applied"), -1, "No entities found");
    assertEquals("Migrax: unavailable", health.label());
    assertTrue(health.tooltip().contains("Entity changes: not checked"));
    assertTrue(health.tooltip().contains("No entities found"));
  }

  @Test
  public void pendingStillShowsWhenTheCheckFailed() {
    assertEquals("Migrax: 1 pending", MigraxHealth.of(status("pending"), -1, "x").label());
  }

  @Test
  public void unavailableCarriesTheReason() {
    MigraxHealth health = MigraxHealth.unavailable("The Migrax command was not found.");
    assertEquals("Migrax: unavailable", health.label());
    assertTrue(health.tooltip().contains("The Migrax command was not found."));
  }

  @Test
  public void countsTheChangesOfCheckOutput() {
    assertEquals(
        2,
        MigraxHealth.changesIn(
            MigraxResult.parseJsonValue(
                "{\"ok\":false,\"changes\":[\"add column a.b\",\"create table t\"],"
                    + "\"conflicts\":[]}")));
    assertEquals(-1, MigraxHealth.changesIn(MigraxResult.parseJsonValue("{\"ok\":false}")));
    assertEquals(-1, MigraxHealth.changesIn(null));
  }

  @Test
  public void greysOutWhatHasNothingToDo() {
    MigraxHealth synced = MigraxHealth.of(status("applied,skipped"), 0, "");
    assertEquals("No pending migrations at the last check", synced.nothingToMigrate());
    assertNull(synced.nothingToRollBack());
    assertEquals("No entity changes at the last check", synced.nothingToGenerate());

    MigraxHealth fresh = MigraxHealth.of(status("pending,skipped"), 2, "");
    assertNull(fresh.nothingToMigrate());
    assertEquals("No applied migrations at the last check", fresh.nothingToRollBack());
    assertNull(fresh.nothingToGenerate());
  }

  @Test
  public void otherStatesKeepTheActionsOn() {
    // A changed repeatable migration runs again; a failed one is repaired or resumed.
    for (String state : List.of("outdated", "failed", "changed", "missing", "unknown")) {
      MigraxHealth health = MigraxHealth.of(status("applied," + state), 0, "");
      assertNull(state, health.nothingToMigrate());
      assertNull(state, health.nothingToRollBack());
    }
  }

  @Test
  public void repairIsOnForFailedAndMissingMigrationsOnly() {
    assertEquals(
        "No failed or missing migrations at the last check",
        MigraxHealth.of(status("applied,pending,outdated,changed,skipped"), 0, "")
            .nothingToRepair());
    for (String state : List.of("failed", "missing", "unknown")) {
      assertNull(state, MigraxHealth.of(status("applied," + state), 0, "").nothingToRepair());
    }
    assertNull(MigraxHealth.unavailable("x").nothingToRepair());
  }

  @Test
  public void keepsEverythingOnWhenNotKnown() {
    MigraxHealth unknown = MigraxHealth.unavailable("The Migrax command was not found.");
    assertNull(unknown.nothingToMigrate());
    assertNull(unknown.nothingToRollBack());
    assertNull(unknown.nothingToGenerate());
    // No migrations yet: the first Generate writes the baseline of an existing database.
    MigraxStatus none = MigraxStatus.from(MigraxResult.parseJsonValue("{\"migrations\":[]}"));
    assertNull(MigraxHealth.of(none, 0, "").nothingToGenerate());
    // Entity changes not checked.
    assertNull(MigraxHealth.of(status("applied"), -1, "x").nothingToGenerate());
  }
}
