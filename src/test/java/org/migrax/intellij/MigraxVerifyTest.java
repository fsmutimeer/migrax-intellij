package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MigraxVerifyTest {

  @Test
  public void readsAPassingRun() {
    assertEquals(
        "Applied 3 migration(s) to a fresh mysql database.\n"
            + "The schema matches the entities (Hibernate 6.4.4 schema validation).\n"
            + "Rollback scripts: ok (3 rolled back and re-applied).\n"
            + "\nVerified.",
        MigraxVerify.text(
            MigraxResult.parseJson(
                "{\"dialect\":\"mysql\",\"applied\":3,"
                    + "\"validation\":\"Hibernate 6.4.4 schema validation\","
                    + "\"problems\":[],\"rollbacks\":\"ok (3 rolled back and re-applied)\","
                    + "\"ok\":true}")));
  }

  @Test
  public void readsAFailingRun() {
    assertEquals(
        "Applied 2 migration(s) to a fresh h2 database.\n"
            + "The migrated schema does not match the entities:\n"
            + "  - missing column orders.note\n"
            + "\nVerification failed.",
        MigraxVerify.text(
            MigraxResult.parseJson(
                "{\"dialect\":\"h2\",\"applied\":2,\"validation\":\"structural comparison\","
                    + "\"problems\":[\"missing column orders.note\"],\"ok\":false}")));
  }

  @Test
  public void anErrorHasNoText() {
    // An error also has "ok": false, but nothing was applied.
    assertNull(
        MigraxVerify.text(
            MigraxResult.parseJson(
                "{\"ok\":false,\"exitCode\":1,\"error\":{\"code\":\"MXE001\"}}")));
    assertNull(MigraxVerify.text(null));
  }
}
