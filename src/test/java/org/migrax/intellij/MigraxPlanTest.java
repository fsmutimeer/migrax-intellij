package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigraxPlanTest {

  private static MigraxPlan parse(String json) {
    return MigraxPlan.from(MigraxResult.parseJsonValue(json));
  }

  @Test
  public void readsOperationsAndLint() {
    MigraxPlan plan =
        parse(
            "{\"dialect\":\"mysql\",\"source\":\"snapshot\",\"operations\":["
                + "{\"kind\":\"AddColumn\",\"summary\":\"add column customer.phone\","
                + "\"sql\":\"ALTER TABLE customer ADD COLUMN phone VARCHAR(30);\","
                + "\"destructive\":false,\"table\":\"customer\",\"rows\":null,\"bytes\":null},"
                + "{\"kind\":\"DropColumn\",\"summary\":\"drop column customer.fax\","
                + "\"sql\":\"ALTER TABLE customer DROP COLUMN fax;\",\"destructive\":true}],"
                + "\"lint\":[{\"code\":\"MX004\",\"severity\":\"warning\","
                + "\"file\":\"plan\",\"statement\":2,\"message\":\"drops data\","
                + "\"fix\":\"keep the column first\",\"sql\":\"x\"}]}");
    assertEquals(2, plan.operations().size());
    assertEquals("2 operations, 1 destructive, 1 lint finding", plan.summary());
    assertEquals(
        "-- add column customer.phone\n"
            + "ALTER TABLE customer ADD COLUMN phone VARCHAR(30);\n\n"
            + "-- drop column customer.fax  (destructive)\n"
            + "ALTER TABLE customer DROP COLUMN fax;",
        plan.sqlText());
    assertEquals("MX004 warning: drops data\n  Fix: keep the column first", plan.lintText());
  }

  @Test
  public void noOperationsMeansNoChanges() {
    MigraxPlan plan = parse("{\"dialect\":\"h2\",\"source\":\"s\",\"operations\":[],\"lint\":[]}");
    assertTrue(plan.operations().isEmpty());
    assertEquals("No changes: the entities match the last migration.", plan.summary());
    assertEquals("", plan.sqlText());
    assertEquals("", plan.lintText());
  }

  @Test
  public void outputWithoutAnOperationsListIsNotAPlan() {
    assertNull(parse("{\"ok\":false,\"error\":{\"code\":\"MXE1\"}}"));
    assertNull(parse("[]"));
    assertNull(parse("nothing"));
  }
}
