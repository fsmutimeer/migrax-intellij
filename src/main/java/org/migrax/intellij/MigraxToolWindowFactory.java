package org.migrax.intellij;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

/** Creates the Migrax tool window at the bottom of the IDE. */
public final class MigraxToolWindowFactory implements ToolWindowFactory, DumbAware {

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    JBLabel label = new JBLabel(
        "Migrax is set up. Pending migrations will show here.", SwingConstants.CENTER);
    Content content = ContentFactory.getInstance().createContent(label, "", false);
    toolWindow.getContentManager().addContent(content);
  }
}
