package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MigraxInitTest {

  @Test
  public void givesTheIdeStepsInsteadOfTheTerminalOnes() {
    // Real output of init in an empty folder, shortened.
    String stdout =
        "Migrax is set up in D:\\shop\n"
            + "\n"
            + "  Migrations      src/main/resources/db/migration (created)\n"
            + "  Snapshot        .migrax/snapshot.json (written by the first generate)\n"
            + "\n"
            + "Next steps:\n"
            + "  migrax doctor      check that everything is ready\n"
            + "  migrax generate    create the first migration\n"
            + "  migrax migrate     apply it to the database\n"
            + "Commit the migration files and .migrax/snapshot.json.\n";
    assertEquals(
        "Migrax is set up in D:\\shop\n"
            + "\n"
            + "  Migrations      src/main/resources/db/migration (created)\n"
            + "  Snapshot        .migrax/snapshot.json (written by the first generate)\n"
            + "\n"
            + "Next steps: click Doctor in the Migrax tool window to check that everything is"
            + " ready, then Generate Migration for the first migration.\n"
            + "Commit the migration files and .migrax/snapshot.json.",
        MigraxInit.text(stdout));
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void warnsAboutAFolderThePluginWillNotFind() throws Exception {
    Path base = temp.getRoot().toPath();
    Path service = Files.createDirectories(base.resolve("shop"));
    Files.writeString(service.resolve("pom.xml"), "<project/>");
    // Before init made .migrax, neither the folder nor the project has Migrax.
    String notes = MigraxInit.notes(service, base);
    assertFalse(notes.contains("no pom.xml"));
    assertTrue(notes.contains("finds none there"));
    Files.createDirectories(service.resolve(".migrax"));
    assertEquals("", MigraxInit.notes(service, base));
    assertTrue(MigraxInit.notes(base, base).contains("no pom.xml or build.gradle"));
  }

  @Test
  public void keepsAnAnswerWithoutSteps() {
    assertEquals("Migrax is set up in D:\\shop", MigraxInit.text("Migrax is set up in D:\\shop\n"));
  }
}
