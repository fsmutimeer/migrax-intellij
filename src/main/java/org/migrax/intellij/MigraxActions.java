package org.migrax.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * The actions of the Tools > Migrax menu, registered in plugin.xml. Shown only in projects that
 * use Migrax. Each one starts its command in the background.
 */
public final class MigraxActions {

  private MigraxActions() {}

  private static boolean usesMigrax(AnActionEvent e) {
    Project project = e.getProject();
    return project != null && MigraxHealthService.getInstance(project).isDetected();
  }

  /** The Tools > Migrax submenu; a group with hidden actions would still show, greyed out. */
  public static final class Menu extends DefaultActionGroup implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(usesMigrax(e));
    }
  }

  /** Shown and enabled only once a Migrax project was found. */
  abstract static class Base extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(usesMigrax(e));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        perform(project);
      }
    }

    abstract void perform(Project project);
  }

  public static final class Generate extends Base {
    @Override
    void perform(Project project) {
      MigraxGenerator.generate(project);
    }
  }

  public static final class Plan extends Base {
    @Override
    void perform(Project project) {
      MigraxToolWindowFactory.showPlan(project);
    }
  }

  public static final class Migrate extends Base {
    @Override
    void perform(Project project) {
      MigraxDatabaseChange.migrate(project);
    }
  }

  public static final class Rollback extends Base {
    @Override
    void perform(Project project) {
      MigraxDatabaseChange.rollback(project);
    }
  }

  public static final class Doctor extends Base {
    @Override
    void perform(Project project) {
      MigraxDoctor.run(project);
    }
  }
}
