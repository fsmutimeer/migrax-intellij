package org.migrax.intellij;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a migration file by the name {@code migrax status} prints, such as 0002_add_color.sql.
 * The command does not print the folder, which the project's settings may move, so the file is
 * looked up by name in the IDE's file index, like Go to File.
 */
final class MigraxMigrationFiles {

  // Build output keeps copies of the migrations (target/classes/db/migration).
  private static final List<String> OUTPUT = List.of("target", "build", "out", "bin");

  private MigraxMigrationFiles() {}

  /**
   * Opens the migration, or its rollback script, in the editor; does nothing when there is no
   * such file. Editor thread; the search runs in the background once indexing is done.
   */
  static void open(Project project, String name, boolean rollback) {
    Path root = MigraxHealthService.getInstance(project).knownRoot();
    if (root == null) {
      // Not found yet: without the folder, build output and rollback scripts look alike.
      return;
    }
    ReadAction.nonBlocking(() -> find(project, root, name, rollback))
        .inSmartMode(project)
        .expireWith(project)
        .finishOnUiThread(
            ModalityState.defaultModalityState(),
            file -> {
              if (file == null) {
                MigraxNotifications.info(
                    project,
                    rollback
                        ? "No rollback script for " + name + " found."
                        : "No single file named " + name + " found in the Migrax project (none,"
                            + " or several with that name).");
              } else {
                MigraxGenerator.open(project, List.of(file));
              }
            })
        .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static @Nullable Path find(
      Project project, Path root, String name, boolean rollback) {
    List<Path> found = new ArrayList<>();
    for (VirtualFile file :
        FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))) {
      try {
        found.add(file.toNioPath());
      } catch (UnsupportedOperationException | InvalidPathException e) {
        // Not a file on disk, such as one inside a jar.
      }
    }
    Path migration = choose(found, root);
    if (migration == null || !rollback) {
      return migration;
    }
    Path script = MigraxGenerator.rollbackOf(migration);
    return Files.isRegularFile(script) ? script : null;
  }

  /**
   * The migration among the files with its name: inside the Migrax project folder, not a
   * rollback script and not a copy in build output. Null when none or several qualify.
   */
  static @Nullable Path choose(List<Path> files, Path root) {
    Path chosen = null;
    for (Path file : files) {
      Path relative = relativeTo(file, root);
      if (relative == null || isRollback(relative) || isOutput(relative)) {
        continue;
      }
      if (chosen != null) {
        // Two migrations with the same name: opening either one could be the wrong one.
        return null;
      }
      chosen = file;
    }
    return chosen;
  }

  private static @Nullable Path relativeTo(Path file, Path root) {
    Path absolute = file.toAbsolutePath().normalize();
    Path base = root.toAbsolutePath().normalize();
    return absolute.startsWith(base) ? base.relativize(absolute) : null;
  }

  private static boolean isRollback(Path relative) {
    Path parent = relative.getParent();
    return parent != null && parent.getFileName().toString().equals("rollback");
  }

  private static boolean isOutput(Path relative) {
    return relative.getNameCount() > 1 && OUTPUT.contains(relative.getName(0).toString());
  }
}
