package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.intellij.openapi.util.TextRange;
import org.junit.Test;

public class MigraxSqlLocatorTest {

  private static String marked(String text, String sql) {
    TextRange range = new MigraxSqlLocator(text).find(sql);
    return range == null ? null : range.substring(text);
  }

  @Test
  public void findsAStatementOverSeveralLines() {
    String text =
        "-- add a column\n"
            + "alter table categories\n"
            + "  add column color varchar(20) not null;\n";
    assertEquals(
        "alter table categories\n  add column color varchar(20) not null",
        marked(text, "alter table categories add column color varchar(20) not null"));
  }

  @Test
  public void skipsCommentsLikeTheCommand() {
    // Real finding from the testbed: a block comment before the statement becomes a space.
    String text = "/* index */ create index idx on t (name);\n";
    assertEquals("create index idx on t (name)", marked(text, "create index idx on t (name)"));
    String inside = "alter table t /* why */ add c int -- note\n not null;";
    assertEquals(
        "alter table t /* why */ add c int -- note\n not null",
        marked(inside, "alter table t add c int not null"));
  }

  @Test
  public void commentMarksInsideQuotesAreText() {
    String text = "insert into t values ('a -- b', '/* c */');";
    assertEquals(text.substring(0, text.length() - 1), marked(text, text.replace(";", "")));
  }

  @Test
  public void collapsesWhitespaceInsideQuotesToo() {
    String text = "insert into t values ('a\n  b');";
    assertEquals("insert into t values ('a\n  b')", marked(text, "insert into t values ('a b')"));
  }

  @Test
  public void aCutStatementIsMarkedUpToTheCut() {
    String start = "alter table t add column " + "x".repeat(150) + " int";
    String printed = start.substring(0, 137) + "...";
    assertEquals(start.substring(0, 137), marked(start + ";", printed));
  }

  @Test
  public void threeDotsOfAShortStatementAreText() {
    assertEquals("select '...'", marked("select '...';", "select '...'"));
  }

  @Test
  public void equalStatementsAreFoundInTurn() {
    String text = "drop table a;\ndrop table a;\n";
    MigraxSqlLocator locator = new MigraxSqlLocator(text);
    assertEquals(new TextRange(0, 12), locator.find("drop table a"));
    assertEquals(new TextRange(14, 26), locator.find("drop table a"));
    assertNull(locator.find("drop table a"));
  }

  @Test
  public void aStatementIsNotFoundInsideALongerOne() {
    String text = "drop table ab;\nalter table t add c int not null default 0;\ndrop table a;";
    assertEquals(new TextRange(59, 71), new MigraxSqlLocator(text).find("drop table a"));
    String start = "alter table t add c int not null;";
    assertEquals(
        "alter table t add c int not null",
        marked(
            "alter table t add c int not null default 0;\n" + start,
            "alter table t add c int not null"));
  }

  @Test
  public void aStatementWithoutSemicolonIsFound() {
    String text = "create index i on t (a)\nGO\n";
    assertEquals("create index i on t (a)", marked(text, "create index i on t (a)"));
  }

  @Test
  public void windowsLineEndsAndCommentsAtTheEdges() {
    String text = "/* a */alter table t\r\n  add c int not null; -- last";
    assertEquals(
        "alter table t\r\n  add c int not null",
        marked(text, "alter table t add c int not null"));
    assertNull(marked("select 1; /* not closed", "not closed"));
    assertNull(marked("select 1; -- no line end", "no line end"));
  }

  @Test
  public void unknownOrEmptyStatementsAreNotFound() {
    assertNull(marked("create table t (id int);", "create table u (id int)"));
    assertNull(marked("create table t (id int);", ""));
    assertNull(marked("", "create table t (id int)"));
  }
}
