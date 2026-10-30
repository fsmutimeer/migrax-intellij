package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MigraxRepairTest {

  private static MigraxStatus status(String json) {
    return MigraxStatus.from(MigraxResult.parseJsonValue(json));
  }

  // Real output of migrax status after the file of an applied migration was deleted.
  private static final MigraxStatus MISSING =
      status(
          "{\"schema\":null,\"migrations\":["
              + "{\"version\":\"0001_a.sql\",\"state\":\"applied\",\"detail\":\"applied\"},"
              + "{\"version\":\"0002_b.sql\",\"state\":\"missing\","
              + "\"detail\":\"recorded in the database but the file is missing\"}]}");

  @Test
  public void findsTheMigrationsToRepair() {
    assertEquals(List.of("0002_b.sql"), MigraxRepair.inState(MISSING, "missing"));
    assertEquals(List.of(), MigraxRepair.inState(MISSING, "failed"));
  }

  @Test
  public void argsCarryTheConfirmation() {
    assertEquals(
        List.of("repair", "0002_b.sql", "0003_c.sql", "--action", "forget", "--yes", "--json"),
        new MigraxRepair.Run(List.of("0002_b.sql", "0003_c.sql"), "forget").args());
  }

  @Test
  public void saysWhatWasRemoved() {
    MigraxRepair.Run forget = new MigraxRepair.Run(List.of("0002_b.sql"), "forget");
    // Real output of migrax repair --action forget.
    String json =
        "{\"ok\":true,\"exitCode\":0,\"forgotten\":[\"0002_b.sql\"],\"action\":\"forget\"}";
    assertEquals(
        "Removed 0002_b.sql from the migration history.",
        forget.done(new MigraxResult(0, json, "", false, false)));
    String none = "{\"ok\":true,\"exitCode\":0,\"forgotten\":[],\"action\":\"forget\"}";
    assertEquals(
        "Nothing was removed from the migration history.",
        forget.done(new MigraxResult(0, none, "", false, false)));
    assertEquals(
        "0003_c.sql will run again on the next migrate.",
        new MigraxRepair.Run(List.of("0003_c.sql"), "retry")
            .done(new MigraxResult(0, "{}", "", false, false)));
  }

  @Test
  public void leavesSeveralSchemasToTheTerminal() {
    assertFalse(MigraxRepair.hasSchemas(MISSING));
    assertTrue(
        MigraxRepair.hasSchemas(
            status(
                "[{\"schema\":\"audit\",\"migrations\":[{\"version\":\"0001_a.sql\","
                    + "\"state\":\"failed\",\"detail\":\"\"}]}]")));
  }

  @Test
  public void saysWhyThereIsNothingToRepair() {
    MigraxStatus changed =
        status(
            "{\"migrations\":[{\"version\":\"0001_a.sql\",\"state\":\"changed\","
                + "\"detail\":\"\"}]}");
    assertTrue(MigraxRepair.nothingToRepair(changed).contains("putting it back"));
    assertEquals(
        "Nothing to repair: no failed or missing migrations.",
        MigraxRepair.nothingToRepair(status("{\"migrations\":[]}")));
  }
}
