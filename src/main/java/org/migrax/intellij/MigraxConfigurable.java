package org.migrax.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/** The page Settings > Tools > Migrax. */
public final class MigraxConfigurable implements Configurable {

  private TextFieldWithBrowseButton cliPath;
  private JBLabel status;
  private JBCheckBox checkOnChange;
  private JPanel panel;
  private Timer pause;
  private int lookups;

  @Override
  public @Nls String getDisplayName() {
    return "Migrax";
  }

  @Override
  public @Nullable JComponent createComponent() {
    cliPath = new TextFieldWithBrowseButton();
    // The overload with title and description is scheduled for removal; a listener with a
    // descriptor exists in every supported IDE version.
    cliPath.addBrowseFolderListener(
        new TextBrowseFolderListener(
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
                .withTitle("Migrax Command")
                .withDescription("Select the migrax launcher, or the folder that contains it")));
    cliPath.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        scheduleStatus();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        scheduleStatus();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        scheduleStatus();
      }
    });
    status = new JBLabel();
    pause = new Timer(300, event -> showStatus());
    pause.setRepeats(false);
    JBLabel hint = new JBLabel(
        "Leave empty to use the migrax command found on PATH, else the one the plugin downloaded.");
    hint.setForeground(UIUtil.getContextHelpForeground());
    checkOnChange =
        new JBCheckBox("Check again after an entity, migration or settings file changes");
    JBLabel buildHint =
        new JBLabel("The check may build the project. Without it, click the status bar entry.");
    buildHint.setForeground(UIUtil.getContextHelpForeground());
    panel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Migrax command:", cliPath)
        .addComponentToRightColumn(hint)
        .addComponentToRightColumn(status)
        .addComponent(checkOnChange)
        .addComponent(buildHint)
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();
    reset();
    showStatus();
    return panel;
  }

  /** Waits for a pause in typing before the lookup starts. */
  private void scheduleStatus() {
    if (pause != null) {
      pause.restart();
    }
  }

  /** Looks for the command off the UI thread: PATH can contain slow network drives. */
  private void showStatus() {
    if (cliPath == null) {
      return;
    }
    String configured = cliPath.getText();
    int lookup = ++lookups;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Optional<Path> found = MigraxCliLocator.forThisMachine().locate(configured);
      String text = found
          .map(path -> path.startsWith(MigraxDownload.root())
              ? "Found: the Migrax the plugin downloaded (" + path + ")"
              : "Found: " + path)
          .orElse("Migrax command not found. Set the path above, add it to PATH, or download it"
              + " from the notification the plugin shows when it first needs it.");
      ApplicationManager.getApplication().invokeLater(() -> {
        if (status != null && lookup == lookups) {
          status.setText(text);
        }
      }, ModalityState.any());
    });
  }

  @Override
  public boolean isModified() {
    MigraxSettings settings = MigraxSettings.getInstance();
    return !cliPath.getText().trim().equals(settings.getCliPath())
        || checkOnChange.isSelected() != settings.isCheckOnChange();
  }

  @Override
  public void apply() {
    MigraxSettings settings = MigraxSettings.getInstance();
    settings.setCliPath(cliPath.getText());
    settings.setCheckOnChange(checkOnChange.isSelected());
  }

  @Override
  public void reset() {
    MigraxSettings settings = MigraxSettings.getInstance();
    cliPath.setText(settings.getCliPath());
    checkOnChange.setSelected(settings.isCheckOnChange());
  }

  @Override
  public void disposeUIResources() {
    if (pause != null) {
      pause.stop();
    }
    pause = null;
    cliPath = null;
    status = null;
    checkOnChange = null;
    panel = null;
  }
}
