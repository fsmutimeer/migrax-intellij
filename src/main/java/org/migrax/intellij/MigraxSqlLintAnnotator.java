package org.migrax.intellij;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.XmlStringUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marks lint findings inside migration files, from {@code migrax lint <file> --json}. The text
 * of the editor is linted through a temporary copy, so findings follow the text before it is
 * saved. Only {@code .sql} files inside the Migrax project folder are checked, not those in a
 * {@code rollback} folder (the command does not lint rollback scripts) or in build output.
 */
public final class MigraxSqlLintAnnotator
    extends ExternalAnnotator<MigraxSqlLintAnnotator.Input, List<MigraxSqlLintAnnotator.Mark>> {

  // Linting reads only the file, it never builds the project.
  private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

  /** What the background step needs: the file and the text to lint. */
  record Input(Project project, Path file, String text) {}

  /** A finding and where it is in the text; no range when the statement was not found. */
  record Mark(MigraxFinding finding, @Nullable TextRange range) {}

  @Override
  public @Nullable Input collectInformation(
      @NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return collect(file, editor.getDocument().getText());
  }

  @Override
  public @Nullable Input collectInformation(@NotNull PsiFile file) {
    return collect(file, file.getText());
  }

  private static @Nullable Input collect(PsiFile psi, String text) {
    VirtualFile file = psi.getVirtualFile();
    if (file == null
        || !file.isInLocalFileSystem()
        || !"sql".equalsIgnoreCase(file.getExtension())) {
      return null;
    }
    Project project = psi.getProject();
    // Build output is excluded from the content, so copies under target/ are skipped.
    if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
      return null;
    }
    VirtualFile folder = file.getParent();
    if (folder != null && folder.getName().equalsIgnoreCase("rollback")) {
      return null;
    }
    return new Input(project, file.toNioPath(), text);
  }

  @Override
  public @Nullable List<Mark> doAnnotate(Input input) {
    MigraxHealthService health = MigraxHealthService.getInstance(input.project());
    // Checked first: without a Migrax project findRoot searches the folders on every call. The
    // startup activity runs the first search.
    if (!health.isDetected()) {
      return null;
    }
    Optional<Path> root = health.findRoot();
    if (root.isEmpty()
        || !input
            .file()
            .toAbsolutePath()
            .normalize()
            .startsWith(root.get().toAbsolutePath().normalize())) {
      return null;
    }
    Optional<Path> launcher =
        MigraxCliLocator.forThisMachine().locate(MigraxSettings.getInstance().getCliPath());
    if (launcher.isEmpty()) {
      return null;
    }
    Path copy = null;
    try {
      copy = Files.createTempFile("migrax-lint-", ".sql");
      Files.writeString(copy, input.text(), StandardCharsets.UTF_8);
      MigraxResult result =
          new MigraxCli(launcher.get(), root.get(), TIMEOUT_MILLIS)
              .run(
                  List.of("lint", copy.toString(), "--json"),
                  ProgressManager.getInstance().getProgressIndicator());
      // The command exits 1 when it finds errors but it still prints the findings.
      MigraxLint lint =
          result.cancelled() || result.timedOut()
              ? null
              : MigraxLint.from(MigraxResult.parseJsonValue(result.stdout()));
      if (lint == null) {
        return null;
      }
      MigraxSqlLocator locator = new MigraxSqlLocator(input.text());
      List<Mark> marks = new ArrayList<>();
      for (MigraxFinding finding : lint.findings()) {
        marks.add(new Mark(finding, locator.find(finding.sql())));
      }
      return marks;
    } catch (IOException e) {
      return null;
    } finally {
      if (copy != null) {
        try {
          Files.deleteIfExists(copy);
        } catch (IOException e) {
          // A copy left in the temporary folder does no harm.
        }
      }
    }
  }

  @Override
  public void apply(
      @NotNull PsiFile file, @Nullable List<Mark> marks, @NotNull AnnotationHolder holder) {
    if (marks == null) {
      return;
    }
    // A finding without a place (an unclosed quote while typing, a repeated batch) can come
    // several times: show it once above the file.
    Set<String> aboveTheFile = new HashSet<>();
    for (Mark mark : marks) {
      MigraxFinding finding = mark.finding();
      TextRange range = mark.range();
      boolean placed = range != null && range.getEndOffset() <= file.getTextLength();
      if (!placed && !aboveTheFile.add(finding.code() + " " + finding.message())) {
        continue;
      }
      AnnotationBuilder builder =
          holder
              .newAnnotation(severity(finding), finding.code() + ": " + finding.message())
              .tooltip(tooltip(finding));
      if (placed) {
        builder = builder.range(range);
      } else {
        // The statement was not found: show the finding above the file instead.
        builder = builder.fileLevel();
      }
      builder.create();
    }
  }

  private static HighlightSeverity severity(MigraxFinding finding) {
    if (finding.severity().equalsIgnoreCase("error")) {
      return HighlightSeverity.ERROR;
    }
    if (finding.severity().equalsIgnoreCase("warning")) {
      return HighlightSeverity.WARNING;
    }
    return HighlightSeverity.WEAK_WARNING;
  }

  private static String tooltip(MigraxFinding finding) {
    String text =
        "<b>" + XmlStringUtil.escapeString(finding.code()) + "</b> "
            + XmlStringUtil.escapeString(finding.message());
    if (!finding.fix().isEmpty()) {
      text += "<br>Fix: " + XmlStringUtil.escapeString(finding.fix());
    }
    return XmlStringUtil.wrapInHtml(text);
  }
}
