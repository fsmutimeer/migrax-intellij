package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MigraxFindRootTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path migraxFolder(String relative) throws IOException {
    Path folder = tmp.getRoot().toPath().resolve(relative);
    Files.createDirectories(folder.resolve(".migrax"));
    return folder;
  }

  @Test
  public void theProjectFolderItselfWins() throws IOException {
    Path root = migraxFolder("");
    migraxFolder("service");
    assertEquals(root, MigraxProjectDetector.findRoot(root).orElseThrow());
  }

  @Test
  public void aSubModuleIsFound() throws IOException {
    Path module = migraxFolder("backend/orders");
    assertEquals(module, MigraxProjectDetector.findRoot(tmp.getRoot().toPath()).orElseThrow());
  }

  @Test
  public void theShallowestThenTheFirstByNameIsChosen() throws IOException {
    migraxFolder("b/deep/er");
    Path a = migraxFolder("a");
    migraxFolder("b");
    assertEquals(a, MigraxProjectDetector.findRoot(tmp.getRoot().toPath()).orElseThrow());
  }

  @Test
  public void buildOutputAndHiddenFoldersAreNotSearched() throws IOException {
    migraxFolder("target/copy");
    migraxFolder(".git/x");
    migraxFolder("node_modules/m");
    assertTrue(MigraxProjectDetector.findRoot(tmp.getRoot().toPath()).isEmpty());
  }

  @Test
  public void tooDeepIsNotSearched() throws IOException {
    migraxFolder("a/b/c/d");
    assertTrue(MigraxProjectDetector.findRoot(tmp.getRoot().toPath()).isEmpty());
  }
}
