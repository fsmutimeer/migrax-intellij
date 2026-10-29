package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MigraxEntityChangesTest {

  private static MigraxEntityChanges check(String operations) {
    return MigraxEntityChanges.from(
        MigraxResult.parseJsonValue(
            "{\"ok\":false,\"changes\":[],\"operations\":[" + operations + "]}"));
  }

  @Test
  public void groupsTheChangesByEntityClass() {
    MigraxEntityChanges changes =
        check(
            "{\"kind\":\"add_column\",\"summary\":\"add column categories.name\","
                + "\"table\":\"categories\",\"column\":\"name\","
                + "\"entity\":\"com.example.Category\"},"
                + "{\"kind\":\"add_unique\",\"summary\":\"add unique constraint on "
                + "categories.name\",\"table\":\"categories\",\"column\":null,"
                + "\"entity\":\"com.example.Category\"},"
                + "{\"kind\":\"drop_table\",\"summary\":\"drop table old\","
                + "\"table\":\"old\",\"column\":null,\"entity\":null}");
    assertEquals(
        List.of("add column categories.name", "add unique constraint on categories.name"),
        changes.of("com.example.Category"));
    assertTrue(changes.of("com.example.Other").isEmpty());
    assertTrue(changes.of(null).isEmpty());
  }

  @Test
  public void nestedClassesUseTheEditorsName() {
    MigraxEntityChanges changes =
        check("{\"summary\":\"create table line\",\"entity\":\"com.example.Order$Line\"}");
    assertEquals(List.of("create table line"), changes.of("com.example.Order.Line"));
  }

  @Test
  public void anOlderCommandWithoutOperationsHasNoChanges() {
    assertSame(
        MigraxEntityChanges.NONE,
        MigraxEntityChanges.from(
            MigraxResult.parseJsonValue("{\"ok\":false,\"changes\":[\"add column a.b\"]}")));
    assertSame(MigraxEntityChanges.NONE, MigraxEntityChanges.from(null));
    assertSame(MigraxEntityChanges.NONE, check("{\"summary\":\"drop table x\"}"));
    assertSame(MigraxEntityChanges.NONE, check("{\"summary\":\"drop table x\",\"entity\":7}"));
  }

  @Test
  public void theTooltipListsTheChangesAndEscapesThem() {
    String tooltip = MigraxEntityLineMarkerProvider.tooltip(List.of("add column a.<b>"));
    assertTrue(tooltip, tooltip.contains("1 change not in a migration yet"));
    assertTrue(tooltip, tooltip.contains("add column a.&lt;b&gt;"));
  }
}
