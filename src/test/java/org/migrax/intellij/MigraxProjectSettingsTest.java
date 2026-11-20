package org.migrax.intellij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Map;

/** The database set in Settings > Tools > Migrax > This Project, as the command gets it. */
public class MigraxProjectSettingsTest extends BasePlatformTestCase {

  @Override
  protected void tearDown() throws Exception {
    try {
      // The light project is shared with later tests.
      MigraxProjectSettings.getInstance(getProject()).set("", "", "");
    } finally {
      super.tearDown();
    }
  }

  public void testOnlyWhatIsSetReachesTheCommand() {
    assertEquals(Map.of(), MigraxProjectSettings.environment("", "", "", ""));
    assertEquals(
        Map.of(
            "MIGRAX_DATABASE_URL", "jdbc:postgresql://localhost:5432/shop",
            "MIGRAX_DATABASE_USER", "shop",
            "MIGRAX_DATABASE_PASSWORD", "secret",
            "MIGRAX_LOCATIONS", "filesystem:db/migrations"),
        MigraxProjectSettings.environment(
            "jdbc:postgresql://localhost:5432/shop", "shop", "secret", "db/migrations"));
    // No user or password here: the command takes them from the project's files.
    assertEquals(
        Map.of("MIGRAX_DATABASE_URL", "jdbc:sqlite:shop.db"),
        MigraxProjectSettings.environment("jdbc:sqlite:shop.db", "", "", ""));
    assertEquals(
        Map.of("MIGRAX_LOCATIONS", "classpath:db/sql"),
        MigraxProjectSettings.environment("", "", "", "classpath:db/sql"));
  }

  public void testAFolderIsAFolderNotAClassPathEntry() {
    assertEquals("filesystem:db/migrations", MigraxProjectSettings.location(" db/migrations "));
    assertEquals("filesystem:/srv/sql", MigraxProjectSettings.location("filesystem:/srv/sql"));
  }

  public void testAUrlMakesTheProjectAMigraxProject() {
    MigraxProjectSettings settings = MigraxProjectSettings.getInstance(getProject());
    assertFalse(settings.hasUrl());
    assertEquals(Map.of(), settings.environment());
    settings.set(" jdbc:sqlite:shop.db ", "", "db/migrations");
    assertTrue(settings.hasUrl());
    assertEquals("jdbc:sqlite:shop.db", settings.url());
    assertEquals(
        Map.of(
            "MIGRAX_DATABASE_URL", "jdbc:sqlite:shop.db",
            "MIGRAX_LOCATIONS", "filesystem:db/migrations"),
        settings.environment());
  }
}
