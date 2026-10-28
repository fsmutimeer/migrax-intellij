package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Looks for a Migrax project once the IDE project is open, off the editor thread. */
public final class MigraxStartupActivity implements ProjectActivity {

  @Override
  public @Nullable Object execute(
      @NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
    // Finding the folder also switches the status bar widget on when there is a Migrax project.
    MigraxHealthService.getInstance(project).findRoot();
    return Unit.INSTANCE;
  }
}
