package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigraxEnvironmentsTest {

  private static MigraxEnvironments parse(String json) {
    return MigraxEnvironments.from(MigraxResult.parseJsonValue(json));
  }

  private static final String TWO =
      "{\"migrations\":3,\"environments\":["
          + "{\"name\":\"dev\",\"url\":\"jdbc:h2:mem:x\",\"reachable\":true,\"error\":null,"
          + "\"applied\":2,\"pending\":[\"0003.sql\"],\"skipped\":[],"
          + "\"lastApplied\":{\"version\":\"0002.sql\",\"appliedAt\":\"2026-10-28\"},"
          + "\"problems\":[],\"drift\":[],\"driftNote\":null},"
          + "{\"name\":\"prod\",\"url\":\"jdbc:mysql://h/db\",\"reachable\":false,"
          + "\"error\":\"connection refused\",\"applied\":0,\"pending\":[],"
          + "\"problems\":[],\"drift\":null,\"driftNote\":\"not checked\"}]}";

  @Test
  public void readsEnvironmentsAndTheirStatus() {
    MigraxEnvironments parsed = parse(TWO);
    assertEquals(2, parsed.environments().size());
    MigraxEnvironments.Environment dev = parsed.environments().get(0);
    assertEquals("pending", dev.status());
    assertEquals("0002.sql 2026-10-28", dev.lastApplied());
    assertEquals(2, dev.applied());
    MigraxEnvironments.Environment prod = parsed.environments().get(1);
    assertEquals("unreachable", prod.status());
    assertEquals("2 environments, 1 needs attention.", parsed.summary());
  }

  @Test
  public void detailsListPendingProblemsAndDrift() {
    MigraxEnvironments parsed =
        parse(
            "{\"environments\":[{\"name\":\"qa\",\"url\":\"u\",\"reachable\":true,"
                + "\"applied\":1,\"pending\":[\"a.sql\",\"b.sql\"],"
                + "\"problems\":[{\"version\":\"c.sql\",\"state\":\"changed\","
                + "\"detail\":\"checksum differs\"}],"
                + "\"drift\":[{\"kind\":\"extra column\",\"object\":\"t.c\",\"detail\":\"\"}]}]}");
    MigraxEnvironments.Environment qa = parsed.environments().get(0);
    assertEquals("problems", qa.status());
    assertEquals(
        "qa  u\nPending (2):\n  a.sql\n  b.sql\nProblems (1):\n  c.sql changed: checksum differs"
            + "\nDrift (1):\n  extra column t.c",
        qa.details());
  }

  @Test
  public void driftShowsWhenThereAreNoProblems() {
    MigraxEnvironments parsed =
        parse(
            "{\"environments\":[{\"name\":\"qa\",\"reachable\":true,\"pending\":[],"
                + "\"problems\":[],\"drift\":[{\"kind\":\"k\",\"object\":\"o\"}]}]}");
    assertEquals("drift", parsed.environments().get(0).status());
    assertEquals("1 environment, 1 needs attention.", parsed.summary());
  }

  @Test
  public void aSingleHealthyEnvironmentNeedsNoAttention() {
    MigraxEnvironments parsed =
        parse("{\"environments\":[{\"name\":\"d\",\"reachable\":true,\"pending\":[]}]}");
    assertEquals("in sync", parsed.environments().get(0).status());
    assertEquals("1 environment, none needs attention.", parsed.summary());
  }

  @Test
  public void emptyListAndErrorOutput() {
    assertTrue(parse("{\"environments\":[]}").environments().isEmpty());
    assertEquals("No environments configured.", parse("{\"environments\":[]}").summary());
    assertNull(parse("{\"ok\":false,\"error\":{\"code\":\"MXE116\"}}"));
  }
}
