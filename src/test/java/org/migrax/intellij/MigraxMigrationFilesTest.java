package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class MigraxMigrationFilesTest {

  private static final Path ROOT = Path.of("shop").toAbsolutePath();
  private static final Path MIGRATION =
      ROOT.resolve("src/main/resources/db/migration/0002_x.sql");

  @Test
  public void skipsRollbackScriptsAndBuildOutput() {
    assertEquals(
        MIGRATION,
        MigraxMigrationFiles.choose(
            List.of(
                ROOT.resolve("src/main/resources/db/migration/rollback/0002_x.sql"),
                ROOT.resolve("target/classes/db/migration/0002_x.sql"),
                MIGRATION,
                ROOT.resolve("build/resources/main/db/migration/0002_x.sql")),
            ROOT));
  }

  @Test
  public void skipsFilesOfOtherProjects() {
    Path other = Path.of("other").toAbsolutePath().resolve("db/migration/0002_x.sql");
    assertEquals(MIGRATION, MigraxMigrationFiles.choose(List.of(other, MIGRATION), ROOT));
    assertNull(MigraxMigrationFiles.choose(List.of(other), ROOT));
  }

  @Test
  public void twoCandidatesOpenNeither() {
    // Two modules with a migration of the same name: either could be the wrong one.
    assertNull(
        MigraxMigrationFiles.choose(
            List.of(MIGRATION, ROOT.resolve("legacy/db/0002_x.sql")), ROOT));
  }

  @Test
  public void aMissingMigrationHasNoFile() {
    assertFalse(new MigraxStatus.Row("", "0004_fix.sql", "missing", "").hasFile());
    assertTrue(new MigraxStatus.Row("", "0004_fix.sql", "failed", "").hasFile());
  }
}
