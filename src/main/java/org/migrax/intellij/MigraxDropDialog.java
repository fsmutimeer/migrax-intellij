package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Asks before Generate writes a migration that drops tables or columns. Offers to keep the
 * dropped data in backup tables that Roll Back restores; on by default, so losing data takes a
 * click. Cancel is the default button.
 */
final class MigraxDropDialog extends DialogWrapper {

  private final List<String> drops;
  private final JBCheckBox keepData =
      new JBCheckBox("Keep a backup of the dropped data (Roll Back restores it)", true);
  private final boolean canKeepData;

  /** @param canKeepData false for the zero-downtime split, which cannot keep a backup */
  MigraxDropDialog(Project project, String title, List<String> drops, boolean canKeepData) {
    super(project);
    this.drops = drops;
    this.canKeepData = canKeepData;
    setTitle(title);
    setOKButtonText("Generate with Drops");
    init();
  }

  @Override
  protected Action[] createActions() {
    getOKAction().putValue(DEFAULT_ACTION, null);
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    return super.createActions();
  }

  @Override
  protected JComponent createCenterPanel() {
    StringBuilder text = new StringBuilder("<html>These changes delete data:<ul>");
    drops.forEach(drop -> text.append("<li>").append(StringUtil.escapeXmlEntities(drop)));
    text.append("</ul>Generate the migration with them? Review its SQL before you migrate.");
    if (!canKeepData) {
      text.append("<br><br>A zero-downtime change cannot keep a backup: the drops go into the")
          .append(" contract migration for the next release.");
    }
    JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
    panel.add(new JBLabel(text.append("</html>").toString()), BorderLayout.CENTER);
    if (canKeepData) {
      panel.add(keepData, BorderLayout.SOUTH);
    }
    return panel;
  }

  /** True when the dropped data goes into backup tables first. */
  boolean keepData() {
    return canKeepData && keepData.isSelected();
  }
}
