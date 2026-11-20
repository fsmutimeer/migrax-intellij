package org.migrax.intellij;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class MigraxDatabaseChangeTest {

  @Test
  public void readsWhatRollbackCantRestore() {
    // stderr of `rollback --dry-run --json` from Migrax 0.19.0.
    String stderr =
        "Would roll back 1 migration(s), newest first:\n"
            + "  0002_drop_notes.sql\n"
            + "warning: 0002_drop_notes.sql restores the structure of orders.notes but not its"
            + " data.\n"
            + "warning: 0002_drop_notes.sql restores the structure of orders.total but not its"
            + " data.\n";
    assertEquals(
        List.of("orders.notes (0002_drop_notes.sql)", "orders.total (0002_drop_notes.sql)"),
        MigraxDatabaseChange.lostData(stderr));
  }

  @Test
  public void everySchemaWarnsOnceAndWindowsLineEndsWork() {
    String warning =
        "warning: 0007_drop.sql restores the structure of orders but not its data.\r\n";
    String stderr = "Schema a:\r\n" + warning + "Schema b:\r\n" + warning;
    assertEquals(List.of("orders (0007_drop.sql)"), MigraxDatabaseChange.lostData(stderr));
  }

  @Test
  public void namesWithSpacesAreRead() {
    assertEquals(
        List.of("my orders.notes (0003_drop notes.sql)"),
        MigraxDatabaseChange.lostData(
            "warning: 0003_drop notes.sql restores the structure of my orders.notes but not its"
                + " data.\n"));
  }

  @Test
  public void olderVersionsAndOtherWarningsGiveNone() {
    // Migrax 0.17.0 to 0.19.0-rc.3 print no such line.
    assertEquals(
        List.of(),
        MigraxDatabaseChange.lostData(
            "Would roll back 1 migration(s), newest first:\n  0002_drop_notes.sql\n"));
    assertEquals(
        List.of(), MigraxDatabaseChange.lostData("warning: something else entirely.\n"));
    assertEquals(List.of(), MigraxDatabaseChange.lostData(""));
  }
}
