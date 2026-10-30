package org.migrax.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;

/**
 * Asks before a command changes the database. Shows the database it works on and the
 * migrations; a database on another computer (or one the plugin could not read) gets a red
 * warning, and then Cancel is the default button so Enter does not confirm.
 */
final class MigraxConfirmDialog extends DialogWrapper {

  private final String question;
  private final @Nullable MigraxTarget target;
  private final List<String> migrations;
  private final boolean warn;

  /**
   * @param question such as "Apply 2 migrations to this database?"
   * @param target the database, or null when doctor did not name one
   * @param okText the confirm button, such as "Migrate"
   */
  MigraxConfirmDialog(
      Project project,
      String title,
      String question,
      @Nullable MigraxTarget target,
      List<String> migrations,
      String okText) {
    super(project);
    this.question = question;
    this.target = target;
    this.migrations = migrations;
    this.warn = target == null || !target.isOnThisComputer();
    setTitle(title);
    setOKButtonText(okText);
    init();
  }

  @Override
  protected Action[] createActions() {
    if (warn) {
      getOKAction().putValue(DEFAULT_ACTION, null);
      getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    }
    return super.createActions();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel top = new JPanel();
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    top.add(new JBLabel(question));
    JBLabel url = new JBLabel(target == null ? "Database: not known" : target.url());
    url.setFont(url.getFont().deriveFont(Font.BOLD));
    url.setBorder(JBUI.Borders.empty(6, 0));
    top.add(url);
    if (warn) {
      top.add(warning());
    }

    JBTextArea list = new JBTextArea(String.join("\n", migrations));
    list.setEditable(false);
    list.setFont(
        EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
    JBScrollPane scroll = new JBScrollPane(list);
    scroll.setPreferredSize(JBUI.size(480, Math.min(200, 24 + 18 * migrations.size())));

    JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
    panel.add(top, BorderLayout.NORTH);
    panel.add(scroll, BorderLayout.CENTER);
    return panel;
  }

  private JComponent warning() {
    String text =
        target == null
            ? "Migrax could not tell which database this is. Run Doctor to check the settings."
            : "This database is not on this computer. Make sure it is the one you mean.";
    JBLabel label = new JBLabel(text, AllIcons.General.Error, JBLabel.LEFT);
    label.setForeground(JBColor.RED);
    label.setBorder(
        JBUI.Borders.compound(JBUI.Borders.customLine(JBColor.RED), JBUI.Borders.empty(6)));
    return label;
  }
}
