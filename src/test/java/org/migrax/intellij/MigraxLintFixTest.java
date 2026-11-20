package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class MigraxLintFixTest {

  private static final String DROP_B = "ALTER TABLE a DROP COLUMN b";

  @Test
  public void eachFindingGetsTheFixesThatFit() {
    assertEquals(
        List.of(
            MigraxLintFix.Kind.NO_TRANSACTION,
            MigraxLintFix.Kind.IGNORE_HERE,
            MigraxLintFix.Kind.IGNORE_IN_FILE),
        kinds(MigraxLintFix.forFinding("MX012", 10, "CREATE INDEX CONCURRENTLY i ON a (b)")));
    assertEquals(
        List.of(MigraxLintFix.Kind.IGNORE_HERE, MigraxLintFix.Kind.IGNORE_IN_FILE),
        kinds(MigraxLintFix.forFinding("MX007", 0, DROP_B)));
    // Shown above the file: there is no statement to put a comment before.
    assertEquals(
        List.of(MigraxLintFix.Kind.IGNORE_IN_FILE),
        kinds(MigraxLintFix.forFinding("MX007", -1, DROP_B)));
    assertEquals(
        List.of(MigraxLintFix.Kind.IGNORE_IN_FILE),
        kinds(MigraxLintFix.forFinding("MX007", 0, "")));
    // An unclosed quote is a mistake, not a finding to ignore.
    assertEquals(List.of(), kinds(MigraxLintFix.forFinding("MX000", 0, "SELECT '")));
    assertEquals(List.of(), kinds(MigraxLintFix.forFinding("", 0, "SELECT 1")));
  }

  @Test
  public void ignoreHereGoesOnTheLineAboveIndentedLikeTheStatement() {
    String text = "CREATE TABLE a (id INT);\n  ALTER TABLE a DROP COLUMN b;\n";
    assertEquals(
        "CREATE TABLE a (id INT);\n  -- migrax:lint-ignore MX007\n  ALTER TABLE a DROP COLUMN b;\n",
        apply(text, MigraxLintFix.ignoreHere(text, text.indexOf("ALTER"), "MX007")));
    String first = "DROP TABLE a;\n";
    assertEquals(
        "-- migrax:lint-ignore MX005\nDROP TABLE a;\n",
        apply(first, MigraxLintFix.ignoreHere(first, 0, "MX005")));
  }

  @Test
  public void ignoreHereAfterAnotherStatementOnTheSameLineStartsANewLine() {
    // On the line above, the comment would belong to the statement before.
    String text = "CREATE TABLE a (id INT); DROP TABLE b;\n";
    assertEquals(
        "CREATE TABLE a (id INT); \n-- migrax:lint-ignore MX005\nDROP TABLE b;\n",
        apply(text, MigraxLintFix.ignoreHere(text, text.indexOf("DROP"), "MX005")));
    assertNull(MigraxLintFix.ignoreHere(text, text.length() + 1, "MX005"));
  }

  @Test
  public void ignoreHereAfterABlockCommentOnTheSameLineStaysWithTheStatement() {
    String text = "CREATE TABLE a (id INT);\n/* old */ DROP TABLE b;\n";
    assertEquals(
        "CREATE TABLE a (id INT);\n/* old */ \n-- migrax:lint-ignore MX005\nDROP TABLE b;\n",
        apply(text, MigraxLintFix.ignoreHere(text, text.indexOf("DROP"), "MX005")));
  }

  @Test
  public void ignoreHereChecksTheStatementIsStillThere() {
    String text = "CREATE TABLE a (id INT);\nALTER TABLE a DROP COLUMN id;\n";
    int alter = text.indexOf("ALTER");
    MigraxLintFix fix =
        MigraxLintFix.forFinding("MX007", alter, "ALTER TABLE a DROP COLUMN id").get(0);
    assertEquals(MigraxLintFix.Kind.IGNORE_HERE, fix.kind());
    assertEquals(alter, fix.insert(text).offset());
    // A line typed above it before the next lint: the old offset points elsewhere now.
    assertNull(fix.insert("-- note\n" + text));
    assertNull(fix.insert(""));
  }

  @Test
  public void ignoreInFileAddsALineOrJoinsTheOneThere() {
    String text = "DROP TABLE a;\n";
    assertEquals(
        "-- migrax:lint-ignore-file MX005\nDROP TABLE a;\n",
        apply(text, MigraxLintFix.ignoreInFile(text, "MX005")));
    String withLine = "-- Note\n-- migrax:lint-ignore-file MX001  \nDROP TABLE a;\n";
    assertEquals(
        "-- Note\n-- migrax:lint-ignore-file MX001, MX005  \nDROP TABLE a;\n",
        apply(withLine, MigraxLintFix.ignoreInFile(withLine, "MX005")));
    assertNull(MigraxLintFix.ignoreInFile(withLine, "mx001"));
    String empty = "-- migrax:lint-ignore-file \nDROP TABLE a;\n";
    assertEquals(
        "-- migrax:lint-ignore-file MX005\nDROP TABLE a;\n",
        apply(empty, MigraxLintFix.ignoreInFile(empty, "MX005")));
    // A note after the codes: joining would put the new code after the note, where the command
    // may not read it, so it gets a line of its own.
    String withNote = "-- migrax:lint-ignore-file MX001 -- why\nDROP TABLE a;\n";
    assertEquals(
        "-- migrax:lint-ignore-file MX005\n" + withNote,
        apply(withNote, MigraxLintFix.ignoreInFile(withNote, "MX005")));
  }

  @Test
  public void noTransactionIsTheFirstLineOnce() {
    String text = "CREATE INDEX CONCURRENTLY i ON a (b);\n";
    assertEquals(
        "-- migrax:no-transaction\n" + text, apply(text, MigraxLintFix.noTransaction(text)));
    assertNull(MigraxLintFix.noTransaction("-- migrax:no-transaction\n" + text));
  }

  private static List<MigraxLintFix.Kind> kinds(List<MigraxLintFix> fixes) {
    return fixes.stream().map(MigraxLintFix::kind).toList();
  }

  private static String apply(String text, MigraxLintFix.Insert insert) {
    return text.substring(0, insert.offset()) + insert.text() + text.substring(insert.offset());
  }
}
