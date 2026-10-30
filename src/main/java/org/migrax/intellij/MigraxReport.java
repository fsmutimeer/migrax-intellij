package org.migrax.intellij;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Report: runs {@code migrax report}, which writes one HTML page with the schema, the
 * migrations, the lint findings and the drift, and opens it in the browser. The page goes to
 * the IDE's temporary folder, not into the project, where it could end up in git.
 */
final class MigraxReport {

  // Report may build the project first.
  private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
  private static final String TITLE = "Migrax: Report";
  // "Wrote C:\...\report.html: 4 table(s), 3 migration(s), 1 lint finding(s), no drift."
  private static final Pattern WROTE = Pattern.compile("(?m)^Wrote .+?: (.+?)\\.?$");

  private MigraxReport() {}

  /** Editor thread. */
  static void run(Project project) {
    Path page = page(project);
    MigraxCommands.run(
        project,
        TITLE,
        args(page),
        TIMEOUT_MILLIS,
        result -> show(project, result, page),
        message -> MigraxNotifications.error(project, message));
  }

  /** One page per project, written again each time. */
  static Path page(Project project) {
    return Path.of(
        PathManager.getTempPath(), "migrax", project.getLocationHash() + "-report.html");
  }

  static List<String> args(Path page) {
    return List.of("report", "--html", page.toAbsolutePath().toString());
  }

  private static void show(Project project, MigraxResult result, Path page) {
    if (project.isDisposed() || result.cancelled()) {
      return;
    }
    if (!result.succeeded()) {
      MigraxNotifications.error(project, result.errorMessage());
      return;
    }
    BrowserUtil.browse(page.toFile());
    String summary = summary(result.stdout());
    MigraxNotifications.info(
        project, "Opened the report in the browser" + (summary == null ? "." : ": " + summary));
  }

  /** What the page holds, from "Wrote ...: 4 table(s), ..."; null without that line. */
  static @Nullable String summary(String stdout) {
    Matcher wrote = WROTE.matcher(stdout);
    return wrote.find() ? wrote.group(1) + "." : null;
  }
}
