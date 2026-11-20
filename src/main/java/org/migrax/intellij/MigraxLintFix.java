package org.migrax.intellij;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Alt+Enter on a lint mark: write the directive the command reads ({@code SqlLinter} and
 * {@code SqlScript.hasDirective} in the main repository). The command takes a {@code
 * -- migrax:lint-ignore MX007} comment as part of the statement after it, a {@code
 * -- migrax:lint-ignore-file MX007} line anywhere in the file, and {@code -- migrax:no-transaction}
 * only as a line of its own.
 */
final class MigraxLintFix implements IntentionAction, PriorityAction {

  /** What the fix writes. */
  enum Kind {
    NO_TRANSACTION,
    IGNORE_HERE,
    IGNORE_IN_FILE
  }

  /** Text to insert at an offset of the file. */
  record Insert(int offset, String text) {}

  // The rule that a statement can't run inside a transaction: the directive fixes it.
  static final String NOT_IN_TRANSACTION = "MX012";
  // An unclosed quote or comment: a mistake to fix, not a finding to ignore.
  private static final String UNREADABLE = "MX000";

  private static final Pattern IGNORE_FILE_LINE =
      Pattern.compile(
          "(?im)^[ \\t]*--[ \\t]*migrax:lint-ignore-file[ \\t]+(?<codes>[\\w, ]*?)[ \\t]*$");

  private final Kind kind;
  private final String code;
  private final int statementStart;
  // The first word of the statement: the marks stay while the text changes until the next
  // lint, so the fix checks that the statement is still where it was.
  private final String statementWord;

  private MigraxLintFix(Kind kind, String code, int statementStart, String statementWord) {
    this.kind = kind;
    this.code = code;
    this.statementStart = statementStart;
    this.statementWord = statementWord;
  }

  /**
   * The fixes for a finding, the most useful first.
   *
   * @param statementStart where its statement starts in the file; -1 when it was not found
   * @param statement the statement as the command printed it
   */
  static List<MigraxLintFix> forFinding(String code, int statementStart, String statement) {
    List<MigraxLintFix> fixes = new ArrayList<>();
    if (code.isEmpty() || code.equals(UNREADABLE)) {
      return fixes;
    }
    if (code.equals(NOT_IN_TRANSACTION)) {
      fixes.add(new MigraxLintFix(Kind.NO_TRANSACTION, code, -1, ""));
    }
    // The command collapses whitespace only between words, so the first word is in the file as
    // printed.
    String word = statement.strip().split("\\s", 2)[0];
    if (statementStart >= 0 && !word.isEmpty()) {
      fixes.add(new MigraxLintFix(Kind.IGNORE_HERE, code, statementStart, word));
    }
    fixes.add(new MigraxLintFix(Kind.IGNORE_IN_FILE, code, -1, ""));
    return fixes;
  }

  Kind kind() {
    return kind;
  }

  @Override
  public @NotNull String getText() {
    return switch (kind) {
      case NO_TRANSACTION -> "Add '-- migrax:no-transaction' to this migration";
      case IGNORE_HERE -> "Ignore " + code + " for this statement";
      case IGNORE_IN_FILE -> "Ignore " + code + " in this file";
    };
  }

  @Override
  public @NotNull String getFamilyName() {
    return "Migrax lint";
  }

  @Override
  public @NotNull Priority getPriority() {
    return kind == Kind.NO_TRANSACTION ? Priority.TOP : Priority.NORMAL;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return editor != null && insert(editor.getDocument().getCharsSequence().toString()) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    Document document = editor.getDocument();
    Insert insert = insert(document.getCharsSequence().toString());
    if (insert != null) {
      document.insertString(insert.offset(), insert.text());
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  /**
   * What to insert into {@code text}; null when the text already has it, or the statement is no
   * longer where the mark was.
   */
  @Nullable
  Insert insert(String text) {
    return switch (kind) {
      case NO_TRANSACTION -> noTransaction(text);
      case IGNORE_HERE ->
          text.regionMatches(true, statementStart, statementWord, 0, statementWord.length())
              ? ignoreHere(text, statementStart, code)
              : null;
      case IGNORE_IN_FILE -> ignoreInFile(text, code);
    };
  }

  /** The directive as the first line, unless a line already has it. */
  static @Nullable Insert noTransaction(String text) {
    String directive = "-- migrax:no-transaction";
    boolean present =
        text.lines().anyMatch(line -> line.trim().equalsIgnoreCase(directive));
    return present ? null : new Insert(0, directive + "\n");
  }

  /**
   * An ignore comment on its own line above the statement, indented like it. When code comes
   * before the statement on its line (the end of the statement before it), the comment starts a
   * new line there instead, so it stays with this statement.
   */
  static @Nullable Insert ignoreHere(String text, int statementStart, String code) {
    if (statementStart < 0 || statementStart > text.length()) {
      return null;
    }
    int lineStart = text.lastIndexOf('\n', statementStart - 1) + 1;
    String before = text.substring(lineStart, statementStart);
    String comment = "-- migrax:lint-ignore " + code;
    if (before.isBlank()) {
      return new Insert(lineStart, before + comment + "\n");
    }
    return new Insert(statementStart, "\n" + comment + "\n");
  }

  /** The code added to the file's ignore line, or a new ignore line at the top. */
  static @Nullable Insert ignoreInFile(String text, String code) {
    Matcher line = IGNORE_FILE_LINE.matcher(text);
    if (line.find()) {
      String codes = line.group("codes");
      for (String present : codes.split("[,\\s]+")) {
        if (present.equalsIgnoreCase(code)) {
          return null;
        }
      }
      return new Insert(line.end("codes"), (codes.isBlank() ? "" : ", ") + code);
    }
    return new Insert(0, "-- migrax:lint-ignore-file " + code + "\n");
  }
}
