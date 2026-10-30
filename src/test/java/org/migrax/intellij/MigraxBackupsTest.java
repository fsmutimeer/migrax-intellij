package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MigraxBackupsTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void groupsTheTablesByTheirMigration() {
    // Real output of migrax backups --json.
    List<String> tables =
        MigraxResult.strings(
            MigraxResult.parseJson(
                "{\"ok\":true,\"exitCode\":0,\"backups\":[\"migrax_backup_0005_items\","
                    + "\"migrax_backup_0005_items_note\",\"migrax_backup_0006_old\"]}"),
            "backups");
    assertEquals(List.of("0005", "0006"), MigraxBackups.migrations(tables));
    assertEquals(List.of(), MigraxBackups.migrations(List.of("other_table")));
  }

  @Test
  public void argsCarryTheConfirmation() {
    assertEquals(
        List.of("backups", "--drop", "0005", "--yes", "--json"), MigraxBackups.args("0005"));
    assertEquals(List.of("backups", "--drop", "--yes", "--json"), MigraxBackups.args(null));
  }

  @Test
  public void seesAWaitingContractMigration() throws Exception {
    Path root = tmp.getRoot().toPath();
    assertFalse(MigraxGenerator.hasWaitingContract(root));
    assertFalse(MigraxGenerator.hasWaitingContract(null));
    Path pending = Files.createDirectories(root.resolve(MigraxGenerator.PENDING_FOLDER));
    Files.writeString(pending.resolve("0003_x_contract.json"), "{}");
    assertFalse(MigraxGenerator.hasWaitingContract(root));
    Files.writeString(pending.resolve("0003_x_contract.sql"), "");
    assertTrue(MigraxGenerator.hasWaitingContract(root));
  }
}
