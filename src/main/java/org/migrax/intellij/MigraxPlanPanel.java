package org.migrax.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JPanel;

/** The Plan tab: the SQL of the next migration, filled by {@code migrax plan --json}. */
final class MigraxPlanPanel extends JPanel {

  private static final long PLAN_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

  private final Project project;
  private final JBLabel summary = new JBLabel(" ");
  private final JButton refresh = new JButton("Refresh", AllIcons.Actions.Refresh);
  private final JBTextArea sql = new JBTextArea();
  private final JBTextArea lint = new JBTextArea();
  private final JBScrollPane lintScroll = new JBScrollPane(lint);
  private boolean ran;

  MigraxPlanPanel(Project project) {
    super(new BorderLayout());
    this.project = project;
    for (JBTextArea area : List.of(sql, lint)) {
      area.setEditable(false);
      area.setFont(
          EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
    }
    lintScroll.setPreferredSize(JBUI.size(100, 110));
    lintScroll.setVisible(false);

    JPanel top = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    top.setBorder(JBUI.Borders.empty(4, 8));
    top.add(summary, BorderLayout.CENTER);
    top.add(refresh, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);
    add(new JBScrollPane(sql), BorderLayout.CENTER);
    add(lintScroll, BorderLayout.SOUTH);

    refresh.addActionListener(e -> refresh());
  }

  /** True once a refresh was started, so the tab loads itself only the first time. */
  boolean hasRun() {
    return ran;
  }

  /** Runs {@code migrax plan --json} in the background and shows the result. */
  void refresh() {
    refresh.setEnabled(false);
    summary.setText("Planning...");
    // Not marked as run when the command could not start, so the tab tries again once the
    // path is fixed in the settings. Planning may build the project first: allow ten minutes.
    ran |=
        MigraxCommands.run(
            project,
            "Migrax plan",
            List.of("plan", "--json"),
            PLAN_TIMEOUT_MILLIS,
            this::showResult,
            this::showError);
  }

  private void showResult(MigraxResult result) {
    if (project.isDisposed()) {
      return;
    }
    if (result.cancelled()) {
      refresh.setEnabled(true);
      summary.setText("Cancelled.");
      return;
    }
    MigraxPlan plan =
        result.timedOut() ? null : MigraxPlan.from(MigraxResult.parseJsonValue(result.stdout()));
    if (plan == null) {
      showError(result.errorMessage());
      return;
    }
    refresh.setEnabled(true);
    summary.setText(plan.summary());
    sql.setText(plan.sqlText());
    sql.setCaretPosition(0);
    lint.setText(plan.lintText());
    lint.setCaretPosition(0);
    lintScroll.setVisible(!plan.findings().isEmpty());
    revalidate();
  }

  private void showError(String message) {
    refresh.setEnabled(true);
    summary.setText(message);
    sql.setText("");
    lint.setText("");
    lintScroll.setVisible(false);
  }
}
