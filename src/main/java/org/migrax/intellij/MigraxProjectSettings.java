package org.migrax.intellij;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * The database of one project, for projects whose settings Migrax can't read from a file (or
 * to use another database in the IDE). Saved in the workspace file, which is not committed;
 * the password in the IDE's password store. Given to the command as its environment variables,
 * which win over the project's files and work with every Migrax version.
 */
@Service(Service.Level.PROJECT)
@State(name = "MigraxProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class MigraxProjectSettings
    implements PersistentStateComponent<MigraxProjectSettings.State> {

  /** What is saved. Public fields because the IDE writes them with reflection. */
  public static final class State {
    /** JDBC URL; empty means: from the project's files. */
    public String url = "";
    public String user = "";
    /** The migration folder, relative to the project folder; empty means: from the files. */
    public String locations = "";
  }

  private final Project project;
  private volatile State state = new State();

  public MigraxProjectSettings(Project project) {
    this.project = project;
  }

  static MigraxProjectSettings getInstance(Project project) {
    return project.getService(MigraxProjectSettings.class);
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State loaded) {
    state = loaded;
  }

  String url() {
    return text(state.url);
  }

  String user() {
    return text(state.user);
  }

  String locations() {
    return text(state.locations);
  }

  void set(String url, String user, String locations) {
    State next = new State();
    next.url = text(url);
    next.user = text(user);
    next.locations = text(locations);
    state = next;
  }

  /** True when a database is set here: the project then uses Migrax without any file. */
  boolean hasUrl() {
    return !url().isEmpty();
  }

  /** The saved password, or empty. Reads the password store: not on the editor thread. */
  String password() {
    String password = PasswordSafe.getInstance().getPassword(credentials());
    return password == null ? "" : password;
  }

  /** Saves the password; empty removes it. Not on the editor thread. */
  void setPassword(String password) {
    PasswordSafe.getInstance()
        .setPassword(credentials(), password == null || password.isEmpty() ? null : password);
  }

  /**
   * The environment variables for the command: only the settings that are set, so the
   * project's files keep the others. Reads the password store: not on the editor thread.
   */
  Map<String, String> environment() {
    // One save, read once: a save in between could otherwise mix two.
    State saved = state;
    String url = text(saved.url);
    String locations = text(saved.locations);
    if (url.isEmpty() && locations.isEmpty()) {
      return Map.of();
    }
    return environment(url, text(saved.user), url.isEmpty() ? "" : password(), locations);
  }

  /** The variables for these settings; see {@link #environment()}. */
  static Map<String, String> environment(
      String url, String user, String password, String locations) {
    Map<String, String> variables = new LinkedHashMap<>();
    if (!url.isEmpty()) {
      variables.put("MIGRAX_DATABASE_URL", url);
      // The command skips empty values: a user or password not set here comes from the files.
      if (!user.isEmpty()) {
        variables.put("MIGRAX_DATABASE_USER", user);
      }
      if (!password.isEmpty()) {
        variables.put("MIGRAX_DATABASE_PASSWORD", password);
      }
    }
    if (!locations.isEmpty()) {
      variables.put("MIGRAX_LOCATIONS", location(locations));
    }
    return variables;
  }

  /** A folder as the command reads it: without a prefix it would be a class path folder. */
  static String location(String folder) {
    String trimmed = folder.trim();
    return trimmed.startsWith("filesystem:") || trimmed.startsWith("classpath:")
        ? trimmed
        : "filesystem:" + trimmed;
  }

  private CredentialAttributes credentials() {
    return new CredentialAttributes(
        CredentialAttributesKt.generateServiceName("Migrax", project.getLocationHash()));
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }
}
