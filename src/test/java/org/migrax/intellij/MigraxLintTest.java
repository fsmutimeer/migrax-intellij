package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigraxLintTest {

  private static MigraxLint parse(String json) {
    return MigraxLint.from(MigraxResult.parseJsonValue(json));
  }

  @Test
  public void readsTheRealEmptyOutput() {
    MigraxLint lint = parse("{\"files\":0,\"findings\":[]}");
    assertTrue(lint.findings().isEmpty());
    assertEquals("0 files checked, no findings.", lint.summary());
  }

  @Test
  public void readsFindings() {
    MigraxLint lint =
        parse(
            "{\"files\":1,\"findings\":[{\"code\":\"MX001\",\"severity\":\"error\","
                + "\"file\":\"db/migration/V2__x.sql\",\"statement\":1,\"sql\":\"s\","
                + "\"message\":\"blocks the table\",\"fix\":\"use an index concurrently\"},"
                + "\"junk\"]}");
    assertEquals(1, lint.findings().size());
    MigraxFinding f = lint.findings().get(0);
    assertEquals("db/migration/V2__x.sql", f.file());
    assertEquals("s", f.sql());
    assertEquals("MX001 error: blocks the table\n  Fix: use an index concurrently", f.describe());
    assertEquals("1 file checked, 1 finding.", lint.summary());
  }

  @Test
  public void outputWithoutFindingsIsNotALintResult() {
    assertNull(parse("{\"ok\":false}"));
    assertNull(parse("[]"));
  }

  @Test
  public void pluralSummaryAndMissingFileCount() {
    MigraxLint lint =
        parse(
            "{\"findings\":[{\"code\":\"A\",\"severity\":\"warning\"},"
                + "{\"code\":\"B\",\"severity\":\"error\",\"message\":\"m\"}]}");
    assertEquals("0 files checked, 2 findings.", lint.summary());
    assertEquals("A warning: ", lint.findings().get(0).describe());
    assertEquals("B error: m", lint.findings().get(1).describe());
    assertTrue(MigraxFinding.listFrom(null).isEmpty());
  }
}
