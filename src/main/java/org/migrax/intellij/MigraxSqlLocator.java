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
 * <p>Known limit: the command forgets an open quote at the end of each statement, this class
 * keeps it for the whole file. After a quote the two read differently (MySQL {@code 'it\'s'})
 * later statements may not be found; the caller then shows the finding above the file.
 */
final class MigraxSqlLocator {

  // The command cuts longer statements to 137 characters and adds "...".
  private static final int CUT_LENGTH = 140;
  private static final String CUT_MARK = "...";

  private final String reduced;
  // For each character of the reduced text, its offset in the file text.
  private final int[] offsets;
  // Where the next search for the same statement text starts, so equal statements that both
  // have a finding are found one after the other.
  private final Map<String, Integer> next = new HashMap<>();

  MigraxSqlLocator(String text) {
    StringBuilder out = new StringBuilder(text.length());
    int[] map = new int[text.length()];
    char quote = 0;
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
    }
    this.reduced = out.toString();
    this.offsets = map;
  }

  /**
   * The range of the statement in the file text, or null when it is not there (the file
   * changed, or the command reduced it differently). Only the part the command printed is
   * covered, so a cut statement is marked up to the cut.
   */
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
    for (int at = reduced.indexOf(wanted, from); at >= 0; at = reduced.indexOf(wanted, at + 1)) {
      int end = at + wanted.length();
      boolean starts = at == 0 || reduced.charAt(at - 1) == ';' || reduced.charAt(at - 1) == ' ';
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
