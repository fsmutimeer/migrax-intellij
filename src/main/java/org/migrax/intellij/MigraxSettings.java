package org.migrax.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/** Settings shared by all projects, saved in the IDE's config folder. */
@Service(Service.Level.APP)
@State(
    name = "MigraxSettings",
    storages = @Storage(value = "migrax.xml", roamingType = RoamingType.DISABLED))
public final class MigraxSettings implements PersistentStateComponent<MigraxSettings.State> {

  /** What is saved. Public fields because the IDE writes them with reflection. */
  public static final class State {
    /** Path to the Migrax command (or its folder); empty means: look on PATH. */
    public String cliPath = "";
    /** Check again after an entity, migration or settings file changed. */
    public boolean checkOnChange = true;
  }

  private State state = new State();

  public static MigraxSettings getInstance() {
    return ApplicationManager.getApplication().getService(MigraxSettings.class);
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State loaded) {
    state = loaded;
  }

  public String getCliPath() {
    return state.cliPath == null ? "" : state.cliPath;
  }

  public void setCliPath(String cliPath) {
    state.cliPath = cliPath == null ? "" : cliPath.trim();
  }

  public boolean isCheckOnChange() {
    return state.checkOnChange;
  }

  public void setCheckOnChange(boolean checkOnChange) {
    state.checkOnChange = checkOnChange;
  }
}
