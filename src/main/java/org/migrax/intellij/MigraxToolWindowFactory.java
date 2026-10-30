package org.migrax.intellij;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Creates the Migrax tool window at the bottom of the IDE. */
public final class MigraxToolWindowFactory implements ToolWindowFactory, DumbAware {

  /** The id of the tool window in plugin.xml. */
  static final String ID = "Migrax";

  /** The toolbar's action group in plugin.xml. */
  private static final String TOOLBAR = "Migrax.ToolWindowToolbar";

  /** Opens the tool window on the Plan tab and loads the plan again. Editor thread. */
  static void showPlan(Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
    if (window == null) {
      return;
    }
    window.activate(
        () -> {
          JBTabbedPane tabs = tabs(window);
          if (tabs == null) {
            return;
          }
          for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof MigraxPlanPanel plan) {
              tabs.setSelectedIndex(i);
              // Selecting loads a plan that never ran; this one may be stale.
              if (plan.hasRun()) {
                plan.refresh();
              }
              return;
            }
          }
        });
  }

  /**
   * After something changed: loads the Status tab again and the status behind the status bar
   * entry, the margin icons and the greyed-out actions. Of the other tabs, the one on screen
   * loads again and the rest when they are next shown, so a tab nobody sees runs no command (Plan
   * may build the project). Editor thread.
   */
  static void refreshStatus(Project project) {
    if (project.isDisposed()) {
      return;
    }
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
    JBTabbedPane tabs = tabs(window);
    if (tabs != null) {
      Object onScreen = window.isVisible() ? tabs.getSelectedComponent() : null;
      for (int i = 0; i < tabs.getTabCount(); i++) {
        Object tab = tabs.getComponentAt(i);
        if (tab instanceof MigraxStatusPanel status) {
          status.refresh();
        } else if (tab != onScreen || !reloadIfLoaded(tab)) {
          // A tab still loading may finish with the old answer: it loads again when next shown.
          markStale(tab);
        }
      }
    }
    MigraxHealthService.getInstance(project).refreshAfterChange();
  }

  /** Loads a tab again if it was loaded before and is not loading now; false if not. */
  private static boolean reloadIfLoaded(Object tab) {
    if (tab instanceof MigraxPlanPanel plan && plan.hasRun() && !plan.isLoading()) {
      plan.refresh();
    } else if (tab instanceof MigraxLintPanel lint && lint.hasRun() && !lint.isLoading()) {
      lint.refresh();
    } else if (tab instanceof MigraxEnvironmentsPanel environments
        && environments.hasRun()
        && !environments.isLoading()) {
      environments.refresh();
    } else {
      return false;
    }
    return true;
  }

  private static void markStale(Object tab) {
    if (tab instanceof MigraxPlanPanel plan) {
      plan.markStale();
    } else if (tab instanceof MigraxLintPanel lint) {
      lint.markStale();
    } else if (tab instanceof MigraxEnvironmentsPanel environments) {
      environments.markStale();
    }
  }

  /**
   * Loads the tab that is open in the tool window again, unless it is still loading, and the
   * status that greys out actions with nothing to do. Editor thread.
   */
  static void refreshOpenTab(Project project) {
    JBTabbedPane tabs = tabs(ToolWindowManager.getInstance(project).getToolWindow(ID));
    Object selected = tabs == null ? null : tabs.getSelectedComponent();
    if (selected instanceof MigraxPlanPanel plan) {
      if (!plan.isLoading()) {
        plan.refresh();
      }
    } else if (selected instanceof MigraxLintPanel lint) {
      if (!lint.isLoading()) {
        lint.refresh();
      }
    } else if (selected instanceof MigraxEnvironmentsPanel environments) {
      if (!environments.isLoading()) {
        environments.refresh();
      }
    } else if (selected instanceof MigraxStatusPanel status) {
      if (!status.isLoading()) {
        status.refresh();
      }
    }
    MigraxHealthService.getInstance(project).refreshAfterChange();
  }

  /** Loads the tab if it never loaded or is out of date (see {@link #refreshStatus}). */
  private static void loadIfNeeded(Object tab) {
    if (tab instanceof MigraxPlanPanel plan && !plan.hasRun()) {
      plan.refresh();
    } else if (tab instanceof MigraxLintPanel lint && !lint.hasRun()) {
      lint.refresh();
    } else if (tab instanceof MigraxEnvironmentsPanel environments && !environments.hasRun()) {
      environments.refresh();
    }
  }

  private static @Nullable JBTabbedPane tabs(@Nullable ToolWindow window) {
    Content content = window == null ? null : window.getContentManager().getContent(0);
    return content != null
            && content.getComponent() instanceof SimpleToolWindowPanel panel
            && panel.getContent() instanceof JBTabbedPane tabs
        ? tabs
        : null;
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    MigraxStatusPanel status = new MigraxStatusPanel(project);
    JBTabbedPane tabs = new JBTabbedPane();
    MigraxPlanPanel plan = new MigraxPlanPanel(project);
    tabs.addTab("Status", status);
    tabs.addTab("Plan", plan);
    MigraxLintPanel lint = new MigraxLintPanel(project);
    tabs.addTab("Lint", lint);
    MigraxEnvironmentsPanel environments = new MigraxEnvironmentsPanel(project);
    tabs.addTab("Environments", environments);

    // The buttons go down the left side, as in the IDE's other bottom tool windows, which leaves
    // the tables their height.
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
    ActionToolbar toolbar =
        ActionManager.getInstance()
            .createActionToolbar(
                TOOLBAR, (ActionGroup) ActionManager.getInstance().getAction(TOOLBAR), false);
    toolbar.setTargetComponent(panel);
    panel.setToolbar(toolbar.getComponent());
    panel.setContent(tabs);

    Content content = ContentFactory.getInstance().createContent(panel, "", false);
    toolWindow.getContentManager().addContent(content);
    status.refresh();
    // Also for the greyed-out actions, in case the status bar entry is hidden; a check that
    // already runs is not started twice.
    MigraxHealthService.getInstance(project).refresh();
    // Each of these tabs runs the command: load it when it is first shown, or shown again after
    // a change made it out of date.
    tabs.addChangeListener(e -> loadIfNeeded(tabs.getSelectedComponent()));
    project
        .getMessageBus()
        .connect(toolWindow.getDisposable())
        .subscribe(
            ToolWindowManagerListener.TOPIC,
            new ToolWindowManagerListener() {
              @Override
              public void toolWindowShown(@NotNull ToolWindow shown) {
                if (shown == toolWindow) {
                  loadIfNeeded(tabs.getSelectedComponent());
                }
              }
            });
  }
}
