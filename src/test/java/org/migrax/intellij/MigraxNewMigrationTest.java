package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class MigraxNewMigrationTest {

  @Test
  public void namesBecomeTheFileNameTheCommandWouldMake() {
    assertEquals("add_seed_data", MigraxNewMigration.fileName("  Add seed data "));
    // Nothing a launcher script could read as a command reaches the command line.
    assertEquals("x_del_y", MigraxNewMigration.fileName("x & del \"y\""));
    assertEquals("fix_x", MigraxNewMigration.fileName("--fix-x"));
    assertEquals("", MigraxNewMigration.fileName("/.."));
    assertEquals("", MigraxNewMigration.fileName(null));
  }

  @Test
  public void readsTheCreatedFiles() {
    // Real output of migrax new.
    String json =
        "{\"ok\":true,\"exitCode\":0,\"created\":"
            + "[\"src/main/resources/db/migration/0003_add.sql\","
            + "\"src/main/resources/db/migration/rollback/0003_add.sql\"]}";
    assertEquals(
        List.of(
            "src/main/resources/db/migration/0003_add.sql",
            "src/main/resources/db/migration/rollback/0003_add.sql"),
        MigraxResult.strings(MigraxResult.parseJson(json), "created"));
    assertNull(MigraxResult.strings(MigraxResult.parseJson("{\"ok\":true}"), "created"));
    assertNull(MigraxResult.strings(null, "created"));
  }
}
