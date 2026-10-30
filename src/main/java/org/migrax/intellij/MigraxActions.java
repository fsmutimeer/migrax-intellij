package org.migrax.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The actions of the Tools > Migrax menu and the tool window's toolbar, registered in plugin.xml.
 * Shown only in projects that use Migrax. Each one starts its command in the background.
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

  /**
   * Shown only once a Migrax project was found, and greyed out when the latest status says there
   * is nothing to do; the description then says why.
   */
  abstract static class Base extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      if (!usesMigrax(e)) {
        presentation.setEnabledAndVisible(false);
        return;
      }
      String reason = nothingToDo(MigraxHealthService.getInstance(e.getProject()).health());
      presentation.setVisible(true);
      presentation.setEnabled(reason == null);
      presentation.setDescription(
          reason == null ? getTemplatePresentation().getDescription() : reason);
    }

    /** Why the action has nothing to do now, or null. */
    @Nullable
    String nothingToDo(MigraxHealth health) {
      return null;
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

  public static final class Refresh extends Base {
    @Override
    void perform(Project project) {
      MigraxToolWindowFactory.refreshOpenTab(project);
    }
  }

  public static final class Generate extends Base {
    @Override
    String nothingToDo(MigraxHealth health) {
      return health.nothingToGenerate();
    }

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
    String nothingToDo(MigraxHealth health) {
      return health.nothingToMigrate();
    }

    @Override
    void perform(Project project) {
      MigraxDatabaseChange.migrate(project);
    }
  }

  public static final class Rollback extends Base {
    @Override
    String nothingToDo(MigraxHealth health) {
      return health.nothingToRollBack();
    }

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
