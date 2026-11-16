package org.migrax.intellij;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigraxActionsTest {

  @Test
  public void editorMenuShowsForMigrationsAndEntitySources() {
    assertTrue(MigraxActions.Menu.isMigraxFile("sql"));
    assertTrue(MigraxActions.Menu.isMigraxFile("SQL"));
    assertTrue(MigraxActions.Menu.isMigraxFile("java"));
    assertTrue(MigraxActions.Menu.isMigraxFile("kt"));
  }

  @Test
  public void editorMenuStaysOutOfOtherFiles() {
    assertFalse(MigraxActions.Menu.isMigraxFile("md"));
    assertFalse(MigraxActions.Menu.isMigraxFile("xml"));
    // build.gradle.kts holds no entity.
    assertFalse(MigraxActions.Menu.isMigraxFile("kts"));
    assertFalse(MigraxActions.Menu.isMigraxFile("class"));
    assertFalse(MigraxActions.Menu.isMigraxFile(null));
  }

  @Test
  public void contextMenusAreHiddenInProjectsWithoutMigrax() {
    assertFalse(MigraxActions.Menu.showInContextMenu(false, false, null));
    assertFalse(MigraxActions.Menu.showInContextMenu(false, false, "sql"));
    assertFalse(MigraxActions.Menu.showInContextMenu(false, true, "sql"));
  }

  @Test
  public void projectViewMenuShowsForAnySelection() {
    assertTrue(MigraxActions.Menu.showInContextMenu(true, false, null));
    assertTrue(MigraxActions.Menu.showInContextMenu(true, false, "md"));
  }

  @Test
  public void editorMenuShowsOnlyForMigraxFiles() {
    assertTrue(MigraxActions.Menu.showInContextMenu(true, true, "sql"));
    assertTrue(MigraxActions.Menu.showInContextMenu(true, true, "java"));
    assertTrue(MigraxActions.Menu.showInContextMenu(true, true, "kt"));
    assertFalse(MigraxActions.Menu.showInContextMenu(true, true, "md"));
    assertFalse(MigraxActions.Menu.showInContextMenu(true, true, "xml"));
    assertFalse(MigraxActions.Menu.showInContextMenu(true, true, null));
  }
}
