package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
}
