package org.migrax.intellij;

import com.intellij.openapi.util.TextRange;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the statement of a lint finding in a migration file. The command prints the statement
 * text without comments and with whitespace collapsed, cut at 140 characters ({@code SqlLinter}
 * and {@code SqlScript.stripComments} in the main repository). Splitting the file into
 * statements again would depend on the database; instead the file text is reduced the same way
 * and the statement text is searched in it, so the result does not depend on how the command
 * splits statements.
 *
 * <p>The command removes comments from each statement on its own, so a quote left open at the
 * end of a statement does not reach the next one. The reduced text does the same: a semicolon
 * outside a quote ends the statement. MySQL, MariaDB and TiDB read {@code \'} as an escaped
 * quote, the other databases as a backslash and the end of the string; the file does not say
 * which database it is for, so a text with {@code \'} is reduced both ways and the statement is
 * searched in each.
 *
 * <p>Known limits: only a semicolon ends a statement here, not a {@code GO} or {@code DELIMITER}
 * line, and {@code $$ ... $$} bodies and {@code [...]} names are not read as quotes, so an
 * apostrophe inside them opens a quote. The statement is then not found and the caller shows
 * the finding above the file. In the escaped reading, {@code --} inside {@code 'it\'s -- x'} is
 * skipped as a comment, as the command does, which also skips the closing quote: rare enough.
 */
final class MigraxSqlLocator {

  private final Reduced standard;
  // Only for a text with an escaped quote, the one place the two readings differ.
  private final Reduced withBackslashEscapes;

  MigraxSqlLocator(String text) {
    this.standard = new Reduced(text, false);
    this.withBackslashEscapes = text.contains("\\'") ? new Reduced(text, true) : null;
  }

  /**
   * The range of the statement in the file text, or null when it is not there (the file
   * changed, or the command reduced it differently). Only the part the command printed is
   * covered, so a cut statement is marked up to the cut.
   */
  @Nullable
  TextRange find(String sql) {
    TextRange range = standard.find(sql);
    if (range == null && withBackslashEscapes != null) {
      range = withBackslashEscapes.find(sql);
    }
    return range;
  }

  /** The text reduced the way the command prints it, for one reading of backslashes. */
  private static final class Reduced {

    // The command cuts longer statements to 137 characters and adds "...".
    private static final int CUT_LENGTH = 140;
    private static final String CUT_MARK = "...";

    private final String reduced;
    // For each character of the reduced text, its offset in the file text.
    private final int[] offsets;
    // Where the next search for the same statement text starts, so equal statements that both
    // have a finding are found one after the other.
    private final Map<String, Integer> next = new HashMap<>();

    Reduced(String text, boolean backslashEscapes) {
      StringBuilder out = new StringBuilder(text.length());
      int[] map = new int[text.length()];
      // The quote as the command's comment remover reads it: no backslash escapes.
      char quote = 0;
      // The quote as the command's statement splitter reads it, to see where a statement ends.
      char splitQuote = 0;
      boolean splitSkips = false;
      boolean space = false;
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        char after = i + 1 < text.length() ? text.charAt(i + 1) : 0;
        if (quote == 0 && ch == '-' && after == '-') {
          int end = text.indexOf('\n', i);
          if (end < 0) {
            break;
          }
          i = end - 1;
          continue;
        }
        if (quote == 0 && ch == '/' && after == '*') {
          int end = text.indexOf("*/", i + 2);
          if (end < 0) {
            break;
          }
          i = end + 1;
          space = true;
          continue;
        }
        boolean statementEnds = false;
        if (splitSkips) {
          splitSkips = false;
        } else if (splitQuote != 0) {
          if (backslashEscapes && ch == '\\' && splitQuote == '\'') {
            splitSkips = true;
          } else if (ch == splitQuote) {
            // A doubled quote closes and opens again: the same as staying inside.
            splitQuote = 0;
          }
        } else if (ch == '\'' || ch == '"' || ch == '`') {
          splitQuote = ch;
        } else if (ch == ';') {
          statementEnds = true;
        }
        // The command collapses whitespace everywhere, also inside quotes, with the regex \s:
        // ASCII whitespace only.
        if (ch == ' ' || (ch >= '\t' && ch <= '\r')) {
          space = true;
          continue;
        }
        if (quote != 0) {
          if (ch == quote) {
            quote = 0;
          }
        } else if (ch == '\'' || ch == '"' || ch == '`') {
          quote = ch;
        }
        if (space && out.length() > 0) {
          map[out.length()] = i;
          out.append(' ');
        }
        space = false;
        map[out.length()] = i;
        out.append(ch);
        if (statementEnds) {
          // The next statement starts without a quote, whatever this one left open.
          quote = 0;
        }
      }
      this.reduced = out.toString();
      this.offsets = map;
    }

    @Nullable
    TextRange find(String sql) {
      String wanted = sql.strip();
      if (wanted.length() == CUT_LENGTH && wanted.endsWith(CUT_MARK)) {
        wanted = wanted.substring(0, wanted.length() - CUT_MARK.length()).stripTrailing();
      }
      if (wanted.isEmpty()) {
        return null;
      }
      boolean cut = !wanted.equals(sql.strip());
      int from = next.getOrDefault(wanted, 0);
      // First a match that ends where a statement ends, then one followed by a space: a
      // statement without a ";" (a "GO" or "/" line follows). A cut text may end anywhere.
      int at = search(wanted, from, cut, false);
      if (at < 0 && !cut) {
        at = search(wanted, from, false, true);
      }
      if (at < 0) {
        return null;
      }
      int last = at + wanted.length() - 1;
      next.put(wanted, last + 1);
      return new TextRange(offsets[at], offsets[last] + 1);
    }

    /** The first match that starts a statement, so "drop table a" is not found in "...ab". */
    private int search(String wanted, int from, boolean endsAnywhere, boolean spaceEnds) {
      for (int at = reduced.indexOf(wanted, from);
          at >= 0;
          at = reduced.indexOf(wanted, at + 1)) {
        int end = at + wanted.length();
        boolean starts =
            at == 0 || reduced.charAt(at - 1) == ';' || reduced.charAt(at - 1) == ' ';
        boolean ends =
            endsAnywhere
                || end == reduced.length()
                || reduced.charAt(end) == ';'
                || reduced.startsWith(" ;", end)
                || (spaceEnds && reduced.charAt(end) == ' ');
        if (starts && ends) {
          return at;
        }
      }
      return -1;
    }
  }
}
