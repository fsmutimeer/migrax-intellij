package org.migrax.intellij;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** An untrusted project makes Migrax run nothing: not on opening, not on a click. */
public class MigraxTrustTest extends BasePlatformTestCase {

  @Override
  protected void tearDown() throws Exception {
    try {
      // The light project is shared with later tests.
      TrustedProjects.setTrusted(getProject(), true);
      MigraxHealthService.getInstance(getProject()).setDetected(false);
    } finally {
      super.tearDown();
    }
  }

  public void testNoCommandRunsInAnUntrustedProject() {
    TrustedProjects.setTrusted(getProject(), false);
    assertFalse(MigraxTrust.isTrusted(getProject()));
    List<String> problems = new ArrayList<>();
    List<MigraxResult> results = new ArrayList<>();
    MigraxCommands.run(
        getProject(), "Migrax status", List.of("status", "--json"), results::add, problems::add);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertEquals(List.of(MigraxTrust.UNTRUSTED), problems);
    assertEquals(List.of(), results);
  }

  public void testTheLintMarksDoNotEvenLookForTheProject() {
    // Detected, so without the trust check the marks would search the folders for the Migrax
    // project, find none in this test project and turn detection off.
    MigraxHealthService health = MigraxHealthService.getInstance(getProject());
    health.setDetected(true);
    TrustedProjects.setTrusted(getProject(), false);
    assertNull(
        new MigraxSqlLintAnnotator()
            .doAnnotate(
                new MigraxSqlLintAnnotator.Input(
                    getProject(), Path.of("V1__a.sql"), "DROP TABLE a;")));
    assertTrue("the marks went further than the trust check", health.isDetected());
  }

  public void testActionsAreOffWithTheReasonButRefreshStaysOn() {
    TrustedProjects.setTrusted(getProject(), false);
    MigraxHealthService.getInstance(getProject()).setDetected(true);
    AnAction migrate = ActionManager.getInstance().getAction("Migrax.Migrate");
    AnActionEvent event =
        TestActionEvent.createTestEvent(
            migrate, SimpleDataContext.getProjectContext(getProject()));
    migrate.update(event);
    assertFalse(event.getPresentation().isEnabled());
    assertEquals(MigraxTrust.UNTRUSTED, event.getPresentation().getDescription());

    AnAction refresh = ActionManager.getInstance().getAction("Migrax.Refresh");
    AnActionEvent refreshEvent =
        TestActionEvent.createTestEvent(
            refresh, SimpleDataContext.getProjectContext(getProject()));
    refresh.update(refreshEvent);
    assertTrue(refreshEvent.getPresentation().isEnabled());
  }

  public void testTheStatusBarSaysWhy() {
    assertEquals("Migrax: not trusted", MigraxHealth.unavailable(MigraxTrust.UNTRUSTED).label());
  }
}
