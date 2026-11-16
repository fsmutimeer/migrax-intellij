package org.migrax.intellij;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.ArrayList;
import java.util.List;

/** The Migrax menu in the IDE's real menus, with real editors. */
public class MigraxMenuPlacesTest extends BasePlatformTestCase {

  @Override
  protected void tearDown() throws Exception {
    try {
      // The light project is shared with later tests.
      setDetected(false);
    } finally {
      super.tearDown();
    }
  }

  public void testMenuIsInToolsAndBothRightClickMenus() {
    ActionManager actions = ActionManager.getInstance();
    assertNotNull(actions.getAction("Migrax.Menu"));
    for (String parent : new String[] {"ToolsMenu", "ProjectViewPopupMenu", "EditorPopupMenu"}) {
      // The parents may hold a not yet loaded stand-in: compare by id.
      List<String> children = new ArrayList<>();
      for (AnAction child :
          ((DefaultActionGroup) actions.getAction(parent)).getChildActionsOrStubs()) {
        children.add(actions.getId(child));
      }
      assertTrue(parent + ": " + children, children.contains("Migrax.Menu"));
    }
  }

  public void testEditorMenuInMigraxProject() {
    setDetected(true);
    assertTrue(inEditor("V1__init.sql", "create table t (id int);"));
    assertTrue(inEditor("Customer.java", "class Customer {}"));
    assertTrue(inEditor("Customer.kt", "class Customer"));
    assertFalse(inEditor("application.yml", "a: b"));
    assertFalse(inEditor("build.gradle.kts", "plugins {}"));
  }

  public void testProjectViewMenuInMigraxProjectForAnyFile() {
    setDetected(true);
    // Outside the editor the file type does not matter.
    myFixture.configureByText("application.yml", "a: b");
    assertTrue(visible(ActionPlaces.PROJECT_VIEW_POPUP, selected()));
  }

  public void testRightClickMenusHiddenWithoutMigrax() {
    setDetected(false);
    assertFalse(inEditor("V1__init.sql", "create table t (id int);"));
    assertFalse(visible(ActionPlaces.PROJECT_VIEW_POPUP, selected()));
  }

  public void testToolsMenuShowsWithoutMigrax() {
    setDetected(false);
    assertTrue(visible(ActionPlaces.MAIN_MENU, projectContext()));
  }

  private boolean inEditor(String name, String text) {
    myFixture.configureByText(name, text);
    DataContext context =
        DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent());
    assertNotNull(name, context.getData(CommonDataKeys.EDITOR));
    assertEquals(name, myFixture.getFile().getVirtualFile(),
        context.getData(CommonDataKeys.VIRTUAL_FILE));
    return visible(ActionPlaces.EDITOR_POPUP, context);
  }

  /** What the Project view gives: the selected file, no editor. */
  private DataContext selected() {
    return SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, getProject())
        .add(CommonDataKeys.VIRTUAL_FILE, myFixture.getFile().getVirtualFile())
        .build();
  }

  private DataContext projectContext() {
    return SimpleDataContext.getProjectContext(getProject());
  }

  private static boolean visible(String place, DataContext context) {
    Presentation presentation = new Presentation();
    // Hidden first, so an update that does nothing cannot pass as "shown".
    presentation.setEnabledAndVisible(false);
    AnActionEvent event = AnActionEvent.createFromDataContext(place, presentation, context);
    ActionManager.getInstance().getAction("Migrax.Menu").update(event);
    return presentation.isVisible();
  }

  private void setDetected(boolean detected) {
    MigraxHealthService.getInstance(getProject()).setDetected(detected);
  }
}
