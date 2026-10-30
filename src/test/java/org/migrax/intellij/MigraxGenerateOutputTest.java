package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class MigraxGenerateOutputTest {

  private static MigraxGenerateOutput output(int exitCode, String stdout, String stderr) {
    return MigraxGenerateOutput.of(new MigraxResult(exitCode, stdout, stderr, false, false));
  }

  @Test
  public void readsARenameAndTheDropsItStoppedFor() {
    // Real output of generate --no-input after renaming Category.description to color.
    MigraxGenerateOutput output =
        output(
            1,
            "Possible rename: categories.description to categories.color. To keep the data,"
                + " rerun with --rename categories.description=color.\n"
                + "3 change(s) for mysql:\n"
                + "  1. add column categories.color\n"
                + "     ALTER TABLE categories ADD COLUMN color varchar(255);\n"
                + "  3. drop column categories.description  [DESTRUCTIVE]\n"
                + "     ALTER TABLE categories DROP COLUMN description;\n",
            "error[MXE108]: 1 destructive change(s) detected (marked [DESTRUCTIVE] above).\n");
    assertEquals(
        List.of(
            new MigraxGenerateOutput.Rename(
                "categories.description to categories.color",
                "--rename",
                "categories.description=color")),
        output.renames());
    assertEquals(
        "Did you rename categories.description to categories.color?",
        output.renames().get(0).question());
    assertTrue(output.needsDrops());
    assertEquals(List.of("drop column categories.description"), output.drops());
    assertTrue(output.created().isEmpty());
  }

  @Test
  public void readsATableRename() {
    MigraxGenerateOutput output =
        output(
            1,
            "Possible rename: table orders to customer_orders. To keep the data, rerun with"
                + " --rename-table orders=customer_orders.\n",
            "");
    assertEquals(
        new MigraxGenerateOutput.Rename(
            "table orders to customer_orders", "--rename-table", "orders=customer_orders"),
        output.renames().get(0));
  }

  @Test
  public void readsTheCreatedFiles() {
    MigraxGenerateOutput output =
        output(
            0,
            "Created src/main/resources/db/migration/0002_add_color.sql:\n"
                + "  - add column categories.color\n"
                + "Rollback script written to src/main/resources/db/migration/rollback.\n"
                + "Review the SQL, then run 'migrax migrate'.\n",
            "");
    assertEquals(List.of("src/main/resources/db/migration/0002_add_color.sql"), output.created());
    assertFalse(output.needsDrops());
    assertFalse(output.noChanges());
  }

  @Test
  public void readsTheOtherFormsOfCreated() {
    // First run without a snapshot: no entity changes, but the database becomes the baseline.
    MigraxGenerateOutput baseline =
        output(
            0,
            "No changes detected.\n"
                + "Created src/main/resources/db/migration/0001_baseline.sql with the 3 table(s)"
                + " the database already has.\n",
            "");
    assertEquals(
        List.of("src/main/resources/db/migration/0001_baseline.sql"), baseline.created());
    assertEquals(
        List.of("db/migration/0003_contract.sql"),
        output(0, "Created db/migration/0003_contract.sql.\n", "").created());
    assertEquals(
        List.of("db/migration/0002_expand.sql"),
        output(0, "Created db/migration/0002_expand.sql (expand: safe while ...)\n", "")
            .created());
    assertEquals(
        List.of("D:\\other\\db\\0002_x.sql"),
        output(0, "Created D:\\other\\db\\0002_x.sql:\n", "").created());
  }

  @Test
  public void readsNoChanges() {
    assertTrue(output(0, "No changes detected.\n", "").noChanges());
  }

  @Test
  public void argsCarryTheAnswers() {
    List<MigraxGenerateOutput.Rename> renames =
        List.of(
            new MigraxGenerateOutput.Rename("a", "--rename", "t.a=b"),
            new MigraxGenerateOutput.Rename("c", "--rename-table", "x=y"),
            new MigraxGenerateOutput.Rename("d", "--rename", "t.c=d"));
    assertEquals(
        List.of(
            "generate",
            "--no-input",
            "--rename-table",
            "x=y",
            "--rename",
            "t.a=b,t.c=d",
            "--allow-destructive"),
        MigraxGenerateOutput.args(MigraxGenerator.Mode.NORMAL, renames, true, false));
    assertEquals(
        List.of("generate", "--no-input"),
        MigraxGenerateOutput.args(MigraxGenerator.Mode.NORMAL, List.of(), false, true));
  }

  @Test
  public void argsKeepTheDroppedDataOnlyWhereTheCommandCan() {
    assertEquals(
        List.of("generate", "--no-input", "--allow-destructive", "--keep-data"),
        MigraxGenerateOutput.args(MigraxGenerator.Mode.NORMAL, List.of(), true, true));
    // The command refuses --zero-downtime with --keep-data.
    List<MigraxGenerateOutput.Rename> renames =
        List.of(new MigraxGenerateOutput.Rename("a", "--rename", "categories.summary=note"));
    assertEquals(
        List.of(
            "generate",
            "--no-input",
            "--zero-downtime",
            "--rename",
            "categories.summary=note",
            "--allow-destructive"),
        MigraxGenerateOutput.args(MigraxGenerator.Mode.ZERO_DOWNTIME, renames, true, true));
    assertEquals(
        List.of("generate", "--no-input", "--contract"),
        MigraxGenerateOutput.args(MigraxGenerator.Mode.CONTRACT, renames, true, true));
  }

  @Test
  public void readsTheZeroDowntimeSplit() {
    // Real output of generate --zero-downtime with a renamed column.
    MigraxGenerateOutput output =
        output(
            0,
            "Created src/main/resources/db/migration/0003_rename_categories_summary_expand.sql"
                + " (expand: safe while the old version runs):\n"
                + "  - add column categories.note\n"
                + "Waiting in .migrax/pending/0003_rename_categories_summary_contract.sql"
                + " (contract: for the next release):\n"
                + "  - drop column categories.summary\n",
            "");
    assertEquals(
        List.of("src/main/resources/db/migration/0003_rename_categories_summary_expand.sql"),
        output.created());
    assertEquals(
        List.of(".migrax/pending/0003_rename_categories_summary_contract.sql"),
        output.waiting());
    assertTrue(output(0, "No contract migrations are waiting.\n", "").noChanges());
  }

  @Test
  public void readsWhatCouldNotBeKept() {
    MigraxGenerateOutput output =
        output(
            0,
            "Created db/migration/0003_x.sql:\n",
            "warning: --keep-data can't keep audit.note: the table has no key and no other"
                + " column to match its rows by.\n");
    assertEquals(List.of("audit.note"), output.notKept());
    assertTrue(output(0, "Created db/migration/0003_x.sql:\n", "").notKept().isEmpty());
  }

  @Test
  public void findsTheFilesAndTheirRollbackScripts() {
    Path root = Path.of("project").toAbsolutePath();
    Path migration = MigraxGenerator.resolve(root, "db/migration/0002_x.sql");
    assertEquals(root.resolve("db/migration/0002_x.sql"), migration);
    assertEquals(
        root.resolve("db/migration/rollback/0002_x.sql"), MigraxGenerator.rollbackOf(migration));
    assertEquals(migration, MigraxGenerator.resolve(null, migration.toString()));
  }
}
