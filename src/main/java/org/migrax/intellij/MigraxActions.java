package org.migrax.intellij;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The actions of the Tools > Migrax menu, the right-click menus and the tool window's toolbar,
 * registered in plugin.xml.
 * Shown only in projects that use Migrax. Each one starts its command in the background.
 */
public final class MigraxActions {

  private MigraxActions() {}

  private static boolean usesMigrax(AnActionEvent e) {
    Project project = e.getProject();
    return project != null && MigraxHealthService.getInstance(project).isDetected();
  }

  /**
   * The Migrax submenu of Tools: in a project without Migrax it holds only Set Up Migrax. A group
   * with hidden actions would still show, greyed out. The same submenu is in the right-click
   * menus of the Project view and the editor, there only in projects that use Migrax, and in the
   * editor only for the files Migrax works with, so other projects and files keep short menus.
   */
  public static final class Menu extends DefaultActionGroup implements DumbAware {

    private static final Set<String> MIGRAX_FILES = Set.of("sql", "java", "kt");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      // By place: on 2024.2 AnActionEvent.isFromContextMenu() is marked for removal, and its
      // replacement only came later. The IDE itself decides by place in the same way.
      if (!ActionPlaces.isPopupPlace(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
        return;
      }
      if (!usesMigrax(e)) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      // Only a right-click inside an editor provides EDITOR; the Project view's popup has none,
      // even while an editor is open.
      boolean inEditor = e.getData(CommonDataKeys.EDITOR) != null;
      VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
      e.getPresentation()
          .setEnabledAndVisible(
              showInContextMenu(true, inEditor, file == null ? null : file.getExtension()));
    }

    /** The rule for the right-click menus: Migrax projects, and in the editor Migrax files. */
    static boolean showInContextMenu(
        boolean usesMigrax, boolean inEditor, @Nullable String extension) {
      return usesMigrax && (!inEditor || isMigraxFile(extension));
    }

    /** Whether a file with this extension is a migration or may hold an entity. */
    static boolean isMigraxFile(@Nullable String extension) {
      return extension != null && MIGRAX_FILES.contains(extension.toLowerCase(Locale.ROOT));
    }
  }

  /**
   * Set Up Migrax: shown only while no Migrax project was found, so it is gone once init made
   * one. Until the first check after opening the project it shows in Migrax projects too.
   */
  public static final class Init extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null && !usesMigrax(e));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        MigraxInit.run(project);
      }
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
      Project project = e.getProject();
      String reason = nothingToDo(project, MigraxHealthService.getInstance(project).health());
      presentation.setVisible(true);
      presentation.setEnabled(reason == null);
      presentation.setDescription(
          reason == null ? getTemplatePresentation().getDescription() : reason);
    }

    /** Why the action has nothing to do now, or null. Background thread. */
    @Nullable
    String nothingToDo(Project project, MigraxHealth health) {
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
    String nothingToDo(Project project, MigraxHealth health) {
      return health.nothingToGenerate();
    }

    @Override
    void perform(Project project) {
      MigraxGenerator.generate(project, MigraxGenerator.Mode.NORMAL);
    }
  }

  public static final class GenerateZeroDowntime extends Base {
    @Override
    void perform(Project project) {
      MigraxGenerator.generate(project, MigraxGenerator.Mode.ZERO_DOWNTIME);
    }
  }

  public static final class GenerateContract extends Base {
    @Override
    String nothingToDo(Project project, MigraxHealth health) {
      // A look into one folder, on the background thread actions update on.
      return MigraxGenerator.hasWaitingContract(
              MigraxHealthService.getInstance(project).knownRoot())
          ? null
          : "No contract migration is waiting";
    }

    @Override
    void perform(Project project) {
      MigraxGenerator.generate(project, MigraxGenerator.Mode.CONTRACT);
    }
  }

  public static final class NewMigration extends Base {
    @Override
    void perform(Project project) {
      MigraxNewMigration.create(project);
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
    String nothingToDo(Project project, MigraxHealth health) {
      return health.nothingToMigrate();
    }

    @Override
    void perform(Project project) {
      MigraxDatabaseChange.migrate(project);
    }
  }

  public static final class Rollback extends Base {
    @Override
    String nothingToDo(Project project, MigraxHealth health) {
      return health.nothingToRollBack();
    }

    @Override
    void perform(Project project) {
      MigraxDatabaseChange.rollback(project);
    }
  }

  public static final class Repair extends Base {
    @Override
    String nothingToDo(Project project, MigraxHealth health) {
      return health.nothingToRepair();
    }

    @Override
    void perform(Project project) {
      MigraxRepair.repair(project);
    }
  }

  public static final class Backups extends Base {
    @Override
    void perform(Project project) {
      MigraxBackups.show(project);
    }
  }

  public static final class Drift extends Base {
    @Override
    void perform(Project project) {
      MigraxDrift.run(project);
    }
  }

  public static final class Verify extends Base {
    @Override
    void perform(Project project) {
      MigraxVerify.run(project);
    }
  }

  public static final class Report extends Base {
    @Override
    void perform(Project project) {
      MigraxReport.run(project);
    }
  }

  public static final class Doctor extends Base {
    @Override
    void perform(Project project) {
      MigraxDoctor.run(project);
    }
  }
}
