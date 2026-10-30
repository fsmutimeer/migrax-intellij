package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class MigraxSchemaResultsTest {

  @Test
  public void readsWhatMigrateAndRollbackDidOrWouldDo() {
    assertEquals(
        List.of("0001_a.sql", "0002_b.sql"),
        MigraxSchemaResults.migrations(
            MigraxResult.parseJson(
                "{\"ok\":true,\"exitCode\":0,\"schemas\":[{\"schema\":null,"
                    + "\"wouldApply\":[\"0001_a.sql\",\"0002_b.sql\"]}]}"),
            "wouldApply"));
    assertEquals(
        2,
        MigraxSchemaResults.applied(
            MigraxResult.parseJson(
                "{\"ok\":true,\"schemas\":[{\"schema\":null,\"applied\":2,\"total\":2}]}")));
  }

  @Test
  public void everySchemaCountsOnce() {
    String json =
        "{\"schemas\":[{\"schema\":\"a\",\"rolledBack\":[\"0002_b.sql\"],\"applied\":1},"
            + "{\"schema\":\"b\",\"rolledBack\":[\"0002_b.sql\"],\"applied\":1}]}";
    assertEquals(
        List.of("0002_b.sql"),
        MigraxSchemaResults.migrations(MigraxResult.parseJson(json), "rolledBack"));
    assertEquals(2, MigraxSchemaResults.applied(MigraxResult.parseJson(json)));
  }

  @Test
  public void noSchemasListIsUnreadable() {
    String error = "{\"ok\":false,\"error\":{\"code\":\"MXE106\",\"message\":\"No driver\"}}";
    assertNull(MigraxSchemaResults.migrations(MigraxResult.parseJson(error), "wouldApply"));
    assertEquals(-1, MigraxSchemaResults.applied(MigraxResult.parseJson(error)));
    assertNull(MigraxSchemaResults.migrations(null, "wouldApply"));
  }
}
