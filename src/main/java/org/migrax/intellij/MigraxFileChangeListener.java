package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Checks again after an entity, a migration or a settings file changed: saved in the editor,
 * or changed on disk (a generated migration, a pull). The check waits until the changes stop.
 * Before a Migrax project was found, only a new {@code migrax.properties} or {@code .migrax}
 * folder counts: the check then finds the project. After, a deleted one counts too: the check
 * then finds it gone.
 */
public final class MigraxFileChangeListener implements BulkFileListener {

  private final Project project;

  public MigraxFileChangeListener(Project project) {
    this.project = project;
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    if (project.isDisposed()) {
      return;
    }
    MigraxHealthService health = MigraxHealthService.getInstance(project);
    Path root = health.knownRoot();
    boolean matters = false;
    boolean lintResultsOld = false;
    boolean notOnlySources = false;
    for (VFileEvent event : events) {
      for (String path : paths(event)) {
        if (root == null) {
          matters |= MigraxWatchedFiles.marksAProject(path);
          continue;
        }
        boolean inProject = MigraxWatchedFiles.matters(root, path);
        // Lint reads the database kind from the settings or the snapshot: results kept so far
        // may be wrong.
        lintResultsOld |= MigraxWatchedFiles.changesLintResults(root, path);
        matters |= inProject || MigraxWatchedFiles.movesAMarker(path);
        // An entity source changes only what the Plan tab shows, not the other tabs.
        notOnlySources |= MigraxWatchedFiles.movesAMarker(path)
            || inProject && !MigraxWatchedFiles.isSource(path);
      }
    }
    if (lintResultsOld) {
      MigraxSqlLintAnnotator.forgetResults(project);
    }
    if (matters) {
      // Before a project was found, a new marker changes everything.
      health.refreshSoon(root != null && !notOnlySources);
    }
  }

  /** The path of the file, and for a rename also its new path (the event has the old one). */
  private static List<String> paths(VFileEvent event) {
    if (event instanceof VFilePropertyChangeEvent change
        && change.getPropertyName().equals(VirtualFile.PROP_NAME)) {
      VirtualFile parent = change.getFile().getParent();
      if (parent != null && change.getNewValue() instanceof String name) {
        return List.of(event.getPath(), parent.getPath() + "/" + name);
      }
    }
    return List.of(event.getPath());
  }
}
