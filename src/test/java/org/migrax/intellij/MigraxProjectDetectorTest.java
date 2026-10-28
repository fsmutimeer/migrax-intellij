package org.migrax.intellij;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MigraxProjectDetectorTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path write(String relative, String text) throws IOException {
    Path file = tmp.getRoot().toPath().resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
    return tmp.getRoot().toPath();
  }

  @Test
  public void anEmptyFolderIsNotAMigraxProject() {
    assertFalse(MigraxProjectDetector.isMigraxProject(tmp.getRoot().toPath()));
  }

  @Test
  public void theSnapshotFolderMakesItOne() throws IOException {
    Files.createDirectories(tmp.getRoot().toPath().resolve(".migrax"));
    assertTrue(MigraxProjectDetector.isMigraxProject(tmp.getRoot().toPath()));
  }

  @Test
  public void aMigraxPropertiesFileMakesItOne() throws IOException {
    assertTrue(MigraxProjectDetector.isMigraxProject(write("migrax.properties", "")));
  }

  @Test
  public void aMigraxSettingInTheApplicationFileMakesItOne() throws IOException {
    Path root = write("src/main/resources/application.properties", "migrax.url=jdbc:h2:mem:x");
    assertTrue(MigraxProjectDetector.isMigraxProject(root));
  }

  @Test
  public void aProfileFileCounts() throws IOException {
    Path root = write("src/main/resources/application-dev.yml", "migrax:\n  url: x\n");
    assertTrue(MigraxProjectDetector.isMigraxProject(root));
  }

  @Test
  public void anApplicationFileWithoutMigraxDoesNot() throws IOException {
    Path root = write("src/main/resources/application.properties", "server.port=8080");
    assertFalse(MigraxProjectDetector.isMigraxProject(root));
  }

  @Test
  public void theBareWordInAFileIsNotASetting() throws IOException {
    Path root =
        write("src/main/resources/application.properties", "# powered by migrax\\nname=x");
    assertFalse(MigraxProjectDetector.isMigraxProject(root));
  }
}
