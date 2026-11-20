package org.migrax.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import java.util.Arrays;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/** The page Settings > Tools > Migrax > This Project: the database of this project. */
public final class MigraxProjectConfigurable implements Configurable {

  private final Project project;
  private JBTextField url;
  private JBTextField user;
  // The password store is read and written off the editor thread, so the field starts empty:
  // a typed password replaces the saved one, an empty field keeps it.
  private JBPasswordField password;
  private JBCheckBox forgetPassword;
  private JBLabel passwordState;
  private JBTextField locations;
  private JPanel panel;

  public MigraxProjectConfigurable(Project project) {
    this.project = project;
  }

  @Override
  public @Nls String getDisplayName() {
    return "This Project";
  }

  @Override
  public @Nullable JComponent createComponent() {
    url = new JBTextField();
    url.getEmptyText().setText("jdbc:postgresql://localhost:5432/shop");
    user = new JBTextField();
    password = new JBPasswordField();
    forgetPassword = new JBCheckBox("Forget the saved password");
    passwordState = hint("");
    locations = new JBTextField();
    locations.getEmptyText().setText("db/migrations");
    panel = FormBuilder.createFormBuilder()
        .addComponent(hint("For a project whose settings Migrax can't read from its files, such"
            + " as a Python, Node.js or Go project, or to use another database here. What is set"
            + " here wins over migrax.properties and application.properties."))
        .addLabeledComponent("Database URL:", url)
        .addComponentToRightColumn(hint("A JDBC URL. Empty: the one in the project's files."))
        .addLabeledComponent("User:", user)
        .addComponentToRightColumn(hint("Empty: the user in the project's files, if any."))
        .addLabeledComponent("Password:", password)
        .addComponentToRightColumn(passwordState)
        .addComponentToRightColumn(forgetPassword)
        .addLabeledComponent("Migrations folder:", locations)
        .addComponentToRightColumn(hint("Relative to the Migrax project folder. Empty: from the"
            + " project's files, else src/main/resources/db/migration."))
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();
    reset();
    return panel;
  }

  private static JBLabel hint(String text) {
    JBLabel label = new JBLabel(text);
    label.setForeground(UIUtil.getContextHelpForeground());
    label.setAllowAutoWrapping(true);
    return label;
  }

  @Override
  public boolean isModified() {
    MigraxProjectSettings settings = MigraxProjectSettings.getInstance(project);
    return !url.getText().trim().equals(settings.url())
        || !user.getText().trim().equals(settings.user())
        || !locations.getText().trim().equals(settings.locations())
        || password.getPassword().length > 0
        || forgetPassword.isSelected();
  }

  @Override
  public void apply() {
    MigraxProjectSettings settings = MigraxProjectSettings.getInstance(project);
    settings.set(url.getText(), user.getText(), locations.getText());
    char[] typed = password.getPassword();
    // Without a URL here the password has no use: it is not kept for a URL typed later.
    boolean forget = forgetPassword.isSelected() || !settings.hasUrl();
    String newPassword = forget ? "" : typed.length > 0 ? new String(typed) : null;
    Arrays.fill(typed, '\0');
    password.setText("");
    forgetPassword.setSelected(false);
    if (newPassword == null) {
      changed();
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      settings.setPassword(newPassword);
      showPasswordState();
      changed();
    });
  }

  /** The commands, tabs and marks use the new database at once. */
  private void changed() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!project.isDisposed()) {
        MigraxToolWindowFactory.refreshAfterFileChange(project, false);
        MigraxSqlLintAnnotator.forgetResults(project);
      }
    }, ModalityState.nonModal());
  }

  @Override
  public void reset() {
    MigraxProjectSettings settings = MigraxProjectSettings.getInstance(project);
    url.setText(settings.url());
    user.setText(settings.user());
    locations.setText(settings.locations());
    password.setText("");
    forgetPassword.setSelected(false);
    ApplicationManager.getApplication().executeOnPooledThread(this::showPasswordState);
  }

  /** Reads the password store (not on the editor thread) and says whether one is saved. */
  private void showPasswordState() {
    boolean saved = !MigraxProjectSettings.getInstance(project).password().isEmpty();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (passwordState != null) {
        passwordState.setText(saved
            ? "A password is saved: leave the field empty to keep it, or type a new one."
            : "No password saved. A password you type is stored in the IDE's password store,"
                + " not in the project.");
        forgetPassword.setVisible(saved);
      }
    }, ModalityState.any());
  }

  @Override
  public void disposeUIResources() {
    url = null;
    user = null;
    password = null;
    forgetPassword = null;
    passwordState = null;
    locations = null;
    panel = null;
  }
}
