package org.migrax.intellij;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import javax.swing.Action;
import javax.swing.JComponent;

/** Shows a longer text, such as the doctor's checks, in the editor font with an OK button. */
final class MigraxTextDialog extends DialogWrapper {

  private final String text;

  MigraxTextDialog(Project project, String title, String text) {
    super(project);
    this.text = text;
    setTitle(title);
    init();
  }

  @Override
  protected Action[] createActions() {
    return new Action[] {getOKAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    JBTextArea area = new JBTextArea(text);
    area.setEditable(false);
    area.setFont(
        EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
    area.setCaretPosition(0);
    JBScrollPane scroll = new JBScrollPane(area);
    scroll.setPreferredSize(JBUI.size(640, 320));
    return scroll;
  }
}
