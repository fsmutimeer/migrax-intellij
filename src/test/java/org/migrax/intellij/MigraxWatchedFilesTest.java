package org.migrax.intellij;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import org.junit.Test;

public class MigraxWatchedFilesTest {

  private static final Path ROOT = Path.of("/work/shop").toAbsolutePath().normalize();

  private static boolean matters(String relative) {
    return MigraxWatchedFiles.matters(ROOT, ROOT.resolve(relative).normalize());
  }

  @Test
  public void entitiesMigrationsAndSettingsMatter() {
    assertTrue(matters("src/main/java/com/example/Category.java"));
    assertTrue(matters("src/main/kotlin/com/example/Order.kt"));
    assertTrue(matters("src/main/resources/db/migration/0002_risky.sql"));
    assertTrue(matters("src/main/resources/db/migration/rollback/0002_risky.SQL"));
    assertTrue(matters("src/main/resources/application.properties"));
    assertTrue(matters("src/main/resources/META-INF/persistence.xml"));
    assertTrue(matters("migrax.properties"));
  }

  @Test
  public void buildOutputAndToolFoldersDoNot() {
    // check builds the project: its output must not start the next check.
    assertFalse(matters("target/classes/db/migration/0001_initial.sql"));
    assertFalse(matters("build/resources/main/application.properties"));
    assertFalse(matters(".migrax/snapshot.json"));
    assertFalse(matters(".idea/workspace.xml"));
    assertFalse(matters("module/target/generated-sources/A.java"));
  }

  @Test
  public void testSourcesAndOtherFilesDoNot() {
    assertFalse(matters("src/test/java/com/example/CategoryTest.java"));
    assertFalse(matters("src/main/resources/logback.xml"));
    assertFalse(matters("README.md"));
    assertFalse(matters("src/main/java/.java"));
    // A package named "test" outside src/test is still a source folder.
    assertTrue(matters("src/main/java/com/example/test/Probe.java"));
  }

  @Test
  public void readsThePathsTheIdeGives() {
    // The IDE uses "/" also on Windows.
    String root = ROOT.toString().replace('\\', '/');
    assertTrue(MigraxWatchedFiles.matters(ROOT, root + "/src/main/java/A.java"));
    assertFalse(MigraxWatchedFiles.matters(ROOT, root + "/target/classes/A.java"));
    assertFalse(MigraxWatchedFiles.matters(ROOT, "bad\0path/A.java"));
  }

  @Test
  public void aNewMigraxProjectIsNoticed() {
    assertTrue(MigraxWatchedFiles.marksAProject("/work/shop/migrax.properties"));
    assertTrue(MigraxWatchedFiles.marksAProject("/work/shop/.migrax"));
    assertTrue(MigraxWatchedFiles.marksAProject("/work/shop/.migrax/snapshot.json"));
    assertFalse(MigraxWatchedFiles.marksAProject("/work/shop/src/main/java/A.java"));
    assertFalse(MigraxWatchedFiles.marksAProject("/work/shop/notmigrax.properties"));
  }

  @Test
  public void aDeletedMarkerIsNoticedButNotTheFilesTheCheckWrites() {
    assertTrue(MigraxWatchedFiles.movesAMarker("/work/shop/.migrax"));
    assertTrue(MigraxWatchedFiles.movesAMarker("/work/shop/migrax.properties"));
    // The check writes these itself: reacting to them would check again and again.
    assertFalse(MigraxWatchedFiles.movesAMarker("/work/shop/.migrax/classpath.txt"));
    assertFalse(MigraxWatchedFiles.movesAMarker("/work/shop/.migrax/build.stamp"));
  }

  @Test
  public void filesOutsideTheProjectFolderDoNot() {
    assertFalse(MigraxWatchedFiles.matters(ROOT, ROOT.resolveSibling("other/A.java")));
    assertFalse(MigraxWatchedFiles.matters(ROOT, ROOT));
  }
}
