package org.migrax.intellij;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** Creates the Migrax tool window at the bottom of the IDE. */
public final class MigraxToolWindowFactory implements ToolWindowFactory, DumbAware {

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    MigraxStatusPanel status = new MigraxStatusPanel(project);
    JBTabbedPane tabs = new JBTabbedPane();
    MigraxPlanPanel plan = new MigraxPlanPanel(project);
    tabs.addTab("Status", status);
    tabs.addTab("Plan", plan);
    MigraxLintPanel lint = new MigraxLintPanel(project);
    tabs.addTab("Lint", lint);
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
          }
        });
  }
}
