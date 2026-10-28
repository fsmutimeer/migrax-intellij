package org.migrax.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** The Status tab: every migration with its state, filled by {@code migrax status --json}. */
final class MigraxStatusPanel extends JPanel {

  private final Project project;
  private final JBLabel summary = new JBLabel(" ");
  private final JButton refresh = new JButton("Refresh", AllIcons.Actions.Refresh);
  private final DefaultTableModel model =
      new DefaultTableModel(new Object[] {"Migration", "State", "Detail"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final List<MigraxStatus.Row> shown = new ArrayList<>();
  private final JBTable table = new JBTable(model);

  MigraxStatusPanel(Project project) {
    super(new BorderLayout());
    this.project = project;
    table.setDefaultRenderer(Object.class, new StateRenderer());
    table.setAutoCreateRowSorter(false);
    table.setShowGrid(false);
    table.getEmptyText().setText("Nothing to show yet");

    JPanel top = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    top.setBorder(JBUI.Borders.empty(4, 8));
    top.add(summary, BorderLayout.CENTER);
    top.add(refresh, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);
    add(new JBScrollPane(table), BorderLayout.CENTER);

    refresh.addActionListener(e -> refresh());
  }

  /** Runs {@code migrax status --json} in the background and shows the result. */
  void refresh() {
    refresh.setEnabled(false);
    summary.setText("Reading migrations...");
    MigraxCommands.run(
        project,
        "Migrax status",
        List.of("status", "--json"),
        this::showResult,
        message -> show(message, List.of()));
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
    // A failed migration makes the command exit 1 but it still prints the list.
    MigraxStatus status =
        result.timedOut() ? null : MigraxStatus.from(MigraxResult.parseJsonValue(result.stdout()));
    if (status != null) {
      show(status.summary(), status.rows());
      return;
    }
    show(result.errorMessage(), List.of());
  }

  private void show(String message, List<MigraxStatus.Row> rows) {
    refresh.setEnabled(true);
    summary.setText(message);
    model.setRowCount(0);
    shown.clear();
    shown.addAll(rows);
    for (MigraxStatus.Row row : rows) {
      model.addRow(new Object[] {row.label(), row.state(), row.detail()});
    }
  }

  /** Problems in red, applied migrations greyed, pending ones in the normal text color. */
  private final class StateRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean selected, boolean focus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(table, value, selected, focus, row, column);
      if (!selected) {
        MigraxStatus.Row shownRow = shown.get(table.convertRowIndexToModel(row));
        if (shownRow.isProblem()) {
          c.setForeground(JBColor.RED);
        } else if (shownRow.state().equals("applied")) {
          c.setForeground(JBColor.GRAY);
        } else {
          c.setForeground(table.getForeground());
        }
      }
      return c;
    }
  }
}
