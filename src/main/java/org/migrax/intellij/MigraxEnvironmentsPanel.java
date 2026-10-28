package org.migrax.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** The Environments tab: what each configured database has applied, from the command. */
final class MigraxEnvironmentsPanel extends JPanel {

  // Each environment is asked over the network: allow for slow or unreachable databases.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

  private final Project project;
  private final JBLabel summary = new JBLabel(" ");
  private final JButton refresh = new JButton("Refresh", AllIcons.Actions.Refresh);
  private final List<MigraxEnvironments.Environment> shown = new ArrayList<>();
  private final DefaultTableModel model =
      new DefaultTableModel(
          new Object[] {"Environment", "Status", "Applied", "Pending", "Last applied"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final JBTable table = new JBTable(model);
  private final JBTextArea details = new JBTextArea();
  private boolean ran;

  MigraxEnvironmentsPanel(Project project) {
    super(new BorderLayout());
    this.project = project;
    table.setDefaultRenderer(Object.class, new StatusRenderer());
    table.setShowGrid(false);
    table.getEmptyText().setText("Nothing to show yet");
    table.getSelectionModel().addListSelectionListener(e -> showDetails());
    details.setEditable(false);
    details.setLineWrap(true);

    JPanel top = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    top.setBorder(JBUI.Borders.empty(4, 8));
    top.add(summary, BorderLayout.CENTER);
    top.add(refresh, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);

    JBSplitter split = new JBSplitter(true, 0.5f);
    split.setFirstComponent(new JBScrollPane(table));
    split.setSecondComponent(new JBScrollPane(details));
    add(split, BorderLayout.CENTER);

    refresh.addActionListener(e -> refresh());
  }

  /** True once a refresh was started, so the tab loads itself only the first time. */
  boolean hasRun() {
    return ran;
  }

  /** Runs {@code migrax environments --json} in the background and shows the result. */
  void refresh() {
    refresh.setEnabled(false);
    summary.setText("Asking the environments...");
    ran = true;
    MigraxCommands.run(
        project,
        "Migrax environments",
        List.of("environments", "--json"),
        TIMEOUT_MILLIS,
        this::showResult,
        message -> {
          ran = false;
          showError(message);
        });
  }

  private void showResult(MigraxResult result) {
    if (project.isDisposed()) {
      return;
    }
    if (result.cancelled()) {
      finish("Cancelled.");
      return;
    }
    // The command exits 1 or 2 for problems and drift but still prints every environment.
    MigraxEnvironments parsed =
        result.timedOut()
            ? null
            : MigraxEnvironments.from(MigraxResult.parseJsonValue(result.stdout()));
    if (parsed == null) {
      showError(result.errorMessage());
      return;
    }
    finish(parsed.summary());
    fill(parsed.environments());
  }

  private void showError(String message) {
    finish(message);
    fill(List.of());
  }

  private void finish(String text) {
    refresh.setEnabled(true);
    summary.setText(text);
  }

  private void fill(List<MigraxEnvironments.Environment> environments) {
    int selected = table.getSelectedRow();
    String keep = selected >= 0 && selected < shown.size() ? shown.get(selected).name() : null;
    shown.clear();
    model.setRowCount(0);
    shown.addAll(environments);
    for (MigraxEnvironments.Environment e : environments) {
      model.addRow(new Object[] {e.name(), e.status(), e.applied(), e.pending().size(),
          e.lastApplied()});
    }
    if (environments.isEmpty()) {
      showDetails();
      return;
    }
    int again = 0;
    for (int i = 0; i < environments.size(); i++) {
      if (environments.get(i).name().equals(keep)) {
        again = i;
      }
    }
    table.setRowSelectionInterval(again, again);
  }

  private void showDetails() {
    int row = table.getSelectedRow();
    int modelRow = row < 0 ? -1 : table.convertRowIndexToModel(row);
    boolean known = modelRow >= 0 && modelRow < shown.size();
    details.setText(known ? shown.get(modelRow).details() : "");
    details.setCaretPosition(0);
  }

  /** Unreachable and problems in red, drift in orange, the rest in the normal text color. */
  private final class StatusRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean selected, boolean focus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(table, value, selected, focus, row, column);
      if (!selected) {
        int modelRow = table.convertRowIndexToModel(row);
        String status = modelRow < shown.size() ? shown.get(modelRow).status() : "";
        if (status.equals("unreachable") || status.equals("problems")) {
          c.setForeground(JBColor.RED);
        } else if (status.equals("drift")) {
          c.setForeground(JBColor.ORANGE);
        } else {
          c.setForeground(table.getForeground());
        }
      }
      return c;
    }
  }
}
