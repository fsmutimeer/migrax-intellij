package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigraxStatusTest {

  private static MigraxStatus parse(String json) {
    return MigraxStatus.from(MigraxResult.parseJsonValue(json));
  }

  @Test
  public void readsTheRowsOfTheRealOutput() {
    MigraxStatus status =
        parse(
            "{\"schema\":null,\"migrations\":[{\"version\":\"0001_initial.sql\","
                + "\"state\":\"applied\",\"detail\":\"applied 2026-10-24 14:09:46\"}]}");
    assertEquals(1, status.rows().size());
    assertEquals("0001_initial.sql", status.rows().get(0).version());
    assertEquals("applied", status.rows().get(0).state());
    assertEquals("1 applied", status.summary());
    assertFalse(status.rows().get(0).isProblem());
  }

  @Test
  public void countsStatesAndProblems() {
    MigraxStatus status =
        parse(
            "{\"migrations\":[{\"version\":\"a\",\"state\":\"applied\"},"
                + "{\"version\":\"b\",\"state\":\"pending\"},"
                + "{\"version\":\"c\",\"state\":\"pending\"},"
                + "{\"version\":\"d\",\"state\":\"failed\",\"detail\":\"x\"},"
                + "{\"version\":\"e\",\"state\":\"changed\"},\"junk\"],\"new\":1}");
    assertEquals(5, status.rows().size());
    assertEquals(2, status.rows().stream().filter(MigraxStatus.Row::isProblem).count());
    assertEquals("1 applied, 2 pending, 1 failed, 1 changed", status.summary());
    assertEquals("", status.rows().get(0).detail());
  }

  @Test
  public void noMigrationsListGivesAnEmptyStatus() {
    assertNull(parse("{\"ok\":true}"));
    assertNull(parse("no json"));
  }

  @Test
  public void anEmptyListIsAStatusWithNoRows() {
    MigraxStatus status = parse("{\"migrations\":[]}");
    assertTrue(status.rows().isEmpty());
    assertEquals("No migrations found.", status.summary());
  }

  @Test
  public void severalSchemasComeAsAnArray() {
    MigraxStatus status =
        parse(
            "[{\"schema\":\"a\",\"migrations\":[{\"version\":\"1.sql\",\"state\":\"APPLIED\"}]},"
                + "{\"schema\":\"b\",\"migrations\":"
                + "[{\"version\":\"1.sql\",\"state\":\"pending\"}]}]");
    assertEquals(2, status.rows().size());
    assertEquals("a: 1.sql", status.rows().get(0).label());
    assertEquals("applied", status.rows().get(0).state());
    assertEquals("1 applied, 1 pending", status.summary());
  }

  @Test
  public void aFailedRunStillPrintsTheListWithExitCodeOne() {
    MigraxResult result =
        new MigraxResult(
            1, "{\"migrations\":[{\"version\":\"x.sql\",\"state\":\"failed\"}]}", "", false, false);
    MigraxStatus status = MigraxStatus.from(MigraxResult.parseJsonValue(result.stdout()));
    assertTrue(status.rows().get(0).isProblem());
  }
}
