package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MigraxDriftTest {

  @Test
  public void listsTheDifferencesWithWhatToDo() {
    // Real output of drift --json on a database changed by hand, with two pending migrations.
    String json =
        "{\"against\":\"snapshot\",\"pending\":2,\"differences\":["
            + "{\"kind\":\"nullability\",\"object\":\"categories.color\","
            + "\"detail\":\"expected NULL but the database has NOT NULL\"},"
            + "{\"kind\":\"extra_column\",\"object\":\"categories.description\","
            + "\"detail\":\"column exists in the database but not in the expected schema\"}]}";
    assertEquals(
        "2 migration(s) are pending: differences they cover are expected.\n\n"
            + "2 difference(s) between the database and the migrations:\n"
            + "  - categories.color: expected NULL but the database has NOT NULL\n"
            + "  - categories.description: column exists in the database but not in the"
            + " expected schema\n"
            + "\nSomeone changed the database outside the migrations. For a change to keep,"
            + " write a migration with New Migration; otherwise undo it in the database.",
        MigraxDrift.text(MigraxResult.parseJson(json)));
  }

  @Test
  public void saysWhenThereIsNoDrift() {
    assertEquals(
        "No drift: the database matches the migrations.",
        MigraxDrift.text(
            MigraxResult.parseJson(
                "{\"against\":\"snapshot\",\"pending\":0,\"differences\":[]}")));
    assertEquals(
        "No drift: the database matches the entities.",
        MigraxDrift.text(
            MigraxResult.parseJson("{\"against\":\"entities\",\"differences\":[]}")));
  }

  @Test
  public void anErrorHasNoText() {
    assertNull(MigraxDrift.text(null));
    assertNull(
        MigraxDrift.text(
            MigraxResult.parseJson(
                "{\"ok\":false,\"exitCode\":1,\"error\":{\"code\":\"MXE001\","
                    + "\"message\":\"There is no snapshot to compare with.\"}}")));
  }
}
