package org.migrax.intellij;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** Creates the Migrax tool window at the bottom of the IDE. */
public final class MigraxToolWindowFactory implements ToolWindowFactory, DumbAware {

  /** The id of the tool window in plugin.xml. */
  static final String ID = "Migrax";

  /** Opens the tool window on the Plan tab and loads the plan again. Editor thread. */
  static void showPlan(Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
    if (window == null) {
      return;
    }
    window.activate(
        () -> {
          Content content = window.getContentManager().getContent(0);
          if (content == null || !(content.getComponent() instanceof JBTabbedPane tabs)) {
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
    Content content = ContentFactory.getInstance().createContent(tabs, "", false);
    toolWindow.getContentManager().addContent(content);
    status.refresh();
    // Each of these tabs runs the command: load it when it is first opened.
    tabs.addChangeListener(
        e -> {
          Object selected = tabs.getSelectedComponent();
          if (selected == plan && !plan.hasRun()) {
            plan.refresh();
          } else if (selected == lint && !lint.hasRun()) {
            lint.refresh();
          } else if (selected == environments && !environments.hasRun()) {
            environments.refresh();
          }
        });
  }
}
