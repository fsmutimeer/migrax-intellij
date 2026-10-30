package org.migrax.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * Set Up Migrax: runs {@code migrax init} in a folder the user picks (the service's folder, which
 * in a repository with several modules is not the project's), and shows what it detected. The
 * {@code .migrax} folder it creates makes the plugin find the project, so the actions appear.
 */
final class MigraxInit {

  // Init may build the project to find the entities.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  private static final String TITLE = "Migrax: Set Up";
  private static final String NEXT_STEPS = "Next steps:";
  private static final List<String> BUILD_FILES =
      List.of("pom.xml", "build.gradle", "build.gradle.kts");

  private MigraxInit() {}

  /** Editor thread. */
  static void run(Project project) {
    FileChooserDescriptor descriptor =
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(TITLE)
            .withDescription(
                "The folder of the service with the entities and application.properties (the one"
                    + " with its pom.xml or build.gradle)");
    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir != null) {
      // The plugin finds a Migrax project only inside the IDE project.
      descriptor.withRoots(projectDir);
    }
    VirtualFile folder = FileChooser.chooseFile(descriptor, project, projectDir);
    if (folder == null) {
      return;
    }
    Path picked = folder.toNioPath();
    MigraxCommands.run(
        project,
        TITLE,
        List.of("init", "--dir", folder.getPath()),
        TIMEOUT_MILLIS,
        result -> done(project, result, folder, picked),
        message -> MigraxNotifications.error(project, message));
  }

  private static void done(
      Project project, MigraxResult result, VirtualFile folder, Path picked) {
    if (project.isDisposed()) {
      return;
    }
    // The command made .migrax and the migration folder behind the IDE's back (also when it
    // failed halfway): the Project view shows them now, not at the IDE's next look at the disk.
    VfsUtil.markDirtyAndRefresh(true, true, true, folder);
    show(project, result, picked);
  }

  private static void show(Project project, MigraxResult result, Path picked) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    if (!result.succeeded()) {
      MigraxNotifications.error(project, result.errorMessage());
      return;
    }
    // The new .migrax folder makes the plugin find the project: check it now, not after the
    // dialog, which waits for the user.
    MigraxToolWindowFactory.refreshStatus(project);
    String text = text(result.stdout());
    String base = project.getBasePath();
    // The notes read the disk.
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              String shown = text + notes(picked, base == null ? null : path(base));
              ApplicationManager.getApplication()
                  .invokeLater(
                      () -> new MigraxTextDialog(project, TITLE, shown).show(),
                      project.getDisposed());
            });
  }

  /**
   * Warnings about the folder: without a build file the command sets up the nearest folder above
   * it that has one, and a folder the plugin does not search leaves the actions hidden.
   */
  static String notes(Path picked, @Nullable Path base) {
    StringBuilder notes = new StringBuilder();
    if (BUILD_FILES.stream().noneMatch(name -> Files.isRegularFile(picked.resolve(name)))) {
      notes.append(
          "\n\nThe folder you picked has no pom.xml or build.gradle, so Migrax may have set up"
              + " the folder above it that has one: see the first line.");
    }
    if (base == null || MigraxProjectDetector.findRoot(base).isEmpty()) {
      notes.append(
          "\n\nThe plugin looks for Migrax at most three folders below the project folder, and"
              + " finds none there. Open the service's folder as its own project to use the"
              + " Migrax actions.");
    }
    return notes.toString();
  }

  private static @Nullable Path path(String text) {
    try {
      return Path.of(text);
    } catch (InvalidPathException e) {
      return null;
    }
  }

  /**
   * What init detected, and its advice. Its next steps are terminal commands
   * ("  migrax doctor ..."), so the IDE's buttons replace them.
   */
  static String text(String stdout) {
    StringBuilder out = new StringBuilder();
    boolean steps = false;
    for (String line : stdout.split("\\R")) {
      if (line.strip().equals(NEXT_STEPS)) {
        steps = true;
        out.append(
            "Next steps: click Doctor in the Migrax tool window to check that everything is"
                + " ready, then Generate Migration for the first migration.\n");
      } else if (!(steps && line.startsWith("  migrax "))) {
        out.append(line).append('\n');
      }
    }
    return out.toString().strip();
  }
}
