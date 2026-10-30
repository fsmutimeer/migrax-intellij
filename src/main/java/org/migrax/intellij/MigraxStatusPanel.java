package org.migrax.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Status tab: every migration with its state, filled by {@code migrax status --json}. A
 * double-click opens the migration; the right-click menu also opens its rollback script.
 */
final class MigraxStatusPanel extends JPanel {

  private final Project project;
  private final JBLabel summary = new JBLabel(" ");
  // The command runs; Refresh in the toolbar waits for it.
  private boolean loading;
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
    add(top, BorderLayout.NORTH);
    add(new JBScrollPane(table), BorderLayout.CENTER);

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            // The right-click menu works on the row under the mouse, as in the Project view.
            int row = table.rowAtPoint(e.getPoint());
            if (SwingUtilities.isRightMouseButton(e) && row >= 0) {
              table.setRowSelectionInterval(row, row);
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            // Not on empty space below the rows, which keeps the old selection.
            MigraxStatus.Row row = table.rowAtPoint(e.getPoint()) < 0 ? null : selected();
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && row != null) {
              open(row, false);
            }
          }
        });
    DefaultActionGroup menu = new DefaultActionGroup();
    menu.add(new RowAction("Open Migration", true, row -> open(row, false)));
    menu.add(new RowAction("Open Rollback Script", true, row -> open(row, true)));
    menu.addSeparator();
    menu.add(
        new RowAction(
            "Copy File Name",
            // A missing migration has no file left; its name can still be copied.
            false,
            row -> CopyPasteManager.getInstance().setContents(new StringSelection(row.version()))));
    PopupHandler.installPopupMenu(table, menu, "MigraxStatusPopup");
  }

  /** The selected migration, or null. */
  private @Nullable MigraxStatus.Row selected() {
    int row = table.getSelectedRow();
    return row < 0 ? null : shown.get(table.convertRowIndexToModel(row));
  }

  private void open(MigraxStatus.Row row, boolean rollback) {
    if (row.hasFile()) {
      MigraxMigrationFiles.open(project, row.version(), rollback);
    }
  }

  /** True while the command runs. */
  boolean isLoading() {
    return loading;
  }

  /** Runs {@code migrax status --json} in the background and shows the result. */
  void refresh() {
    loading = true;
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
      loading = false;
      summary.setText("Cancelled.");
      return;
    }
    // A failed migration makes the command exit 1 but it still prints the list.
    MigraxStatus status =
        result.timedOut() ? null : MigraxStatus.from(MigraxResult.parseJsonValue(result.stdout()));
    if (status != null) {
      // The status bar is brought up to date by whoever asked for this (refreshStatus,
      // refreshOpenTab): checking it again from here ran a second check, which may build.
      show(status.summary(), status.rows());
      return;
    }
    show(result.errorMessage(), List.of());
  }

  private void show(String message, List<MigraxStatus.Row> rows) {
    loading = false;
    summary.setText(message);
    model.setRowCount(0);
    shown.clear();
    shown.addAll(rows);
    for (MigraxStatus.Row row : rows) {
      model.addRow(new Object[] {row.label(), row.state(), row.detail()});
    }
    if (MigraxHealthService.getInstance(project).isDetected()) {
      table.getEmptyText().setText("Nothing to show yet");
    } else {
      table.getEmptyText().setText("This project does not use Migrax yet.");
      table
          .getEmptyText()
          .appendSecondaryText(
              "Set up Migrax",
              SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
              e -> MigraxInit.run(project));
    }
  }

  /** An entry of the right-click menu for the selected migration. */
  private final class RowAction extends DumbAwareAction {
    private final boolean needsFile;
    private final Consumer<MigraxStatus.Row> run;

    RowAction(String text, boolean needsFile, Consumer<MigraxStatus.Row> run) {
      super(text);
      this.needsFile = needsFile;
      this.run = run;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      // Reads the table's selection.
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      MigraxStatus.Row row = selected();
      e.getPresentation().setEnabled(row != null && (!needsFile || row.hasFile()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      MigraxStatus.Row row = selected();
      if (row != null) {
        run.accept(row);
      }
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
