package org.migrax.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** The Lint tab: findings about pending migrations, filled by {@code migrax lint --json}. */
final class MigraxLintPanel extends JPanel {

  private final Project project;
  private final JBLabel summary = new JBLabel(" ");
  private final JBCheckBox all = new JBCheckBox("All migrations");
  private final JButton refresh = new JButton("Refresh", AllIcons.Actions.Refresh);
  private final List<MigraxFinding> shown = new ArrayList<>();
  private final DefaultTableModel model =
      new DefaultTableModel(new Object[] {"Code", "Severity", "File", "Message"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final JBTable table = new JBTable(model);
  private boolean ran;

  MigraxLintPanel(Project project) {
    super(new BorderLayout());
    this.project = project;
    table.setDefaultRenderer(Object.class, new SeverityRenderer());
    table.setShowGrid(false);
    table.getEmptyText().setText("Nothing to show yet");
    table.setToolTipText("Double-click a finding to open its migration file");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
    buttons.add(all);
    buttons.add(refresh);
    JPanel top = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    top.setBorder(JBUI.Borders.empty(4, 8));
    top.add(summary, BorderLayout.CENTER);
    top.add(buttons, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);
    add(new JBScrollPane(table), BorderLayout.CENTER);

    refresh.addActionListener(e -> refresh());
    all.addActionListener(e -> refresh());
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            int row = table.rowAtPoint(e.getPoint());
            if (e.getClickCount() == 2 && row >= 0) {
              open(shown.get(table.convertRowIndexToModel(row)));
            }
          }
        });
  }

  /** True once a refresh was started, so the tab loads itself only the first time. */
  boolean hasRun() {
    return ran;
  }

  /** Runs {@code migrax lint --json} in the background and shows the result. */
  void refresh() {
    setBusy(true);
    summary.setText("Checking migrations...");
    List<String> args =
        all.isSelected() ? List.of("lint", "--json", "--all") : List.of("lint", "--json");
    ran |= MigraxCommands.run(project, "Migrax lint", args, this::showResult, this::showError);
  }

  private void showResult(MigraxResult result) {
    if (project.isDisposed()) {
      return;
    }
    if (result.cancelled()) {
      finish("Cancelled.");
      return;
    }
    // The command exits 1 when it finds errors but it still prints the findings.
    MigraxLint lint =
        result.timedOut() ? null : MigraxLint.from(MigraxResult.parseJsonValue(result.stdout()));
    if (lint == null) {
      showError(result.errorMessage());
      return;
    }
    finish(lint.summary());
    fill(lint.findings());
  }

  private void showError(String message) {
    finish(message);
    fill(List.of());
  }

  /** The checkbox is locked with the button, so two runs never race for the table. */
  private void setBusy(boolean busy) {
    refresh.setEnabled(!busy);
    all.setEnabled(!busy);
  }

  private void finish(String text) {
    setBusy(false);
    summary.setText(text);
  }

  private void fill(List<MigraxFinding> findings) {
    shown.clear();
    model.setRowCount(0);
    shown.addAll(findings);
    for (MigraxFinding f : findings) {
      model.addRow(new Object[] {f.code(), f.severity(), f.file(), f.message()});
    }
  }

  /** Opens the migration file of a finding; the file name is relative to the project folder. */
  private void open(MigraxFinding finding) {
    String base = project.getBasePath();
    if (base == null || finding.file().isEmpty()) {
      return;
    }
    try {
      Path root = Path.of(base).normalize();
      Path path = root.resolve(finding.file()).normalize();
      if (!path.startsWith(root)) {
        return;
      }
      LocalFileSystem files = LocalFileSystem.getInstance();
      // A file that was just generated may not be known to the IDE yet: refresh only then.
      VirtualFile file = files.findFileByNioFile(path);
      if (file == null) {
        file = files.refreshAndFindFileByNioFile(path);
      }
      if (file != null) {
        FileEditorManager.getInstance(project).openFile(file, true);
      }
    } catch (InvalidPathException e) {
      // A file name the system cannot read as a path: nothing to open.
    }
  }

  /** Errors in red, warnings in orange, anything else in the normal text color. */
  private final class SeverityRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean selected, boolean focus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(table, value, selected, focus, row, column);
      if (!selected) {
        int modelRow = table.convertRowIndexToModel(row);
        String severity = modelRow < shown.size() ? shown.get(modelRow).severity() : "";
        if (severity.equalsIgnoreCase("error")) {
          c.setForeground(JBColor.RED);
        } else if (severity.equalsIgnoreCase("warning")) {
          c.setForeground(JBColor.ORANGE);
        } else {
          c.setForeground(table.getForeground());
        }
      }
      return c;
    }
  }
}
