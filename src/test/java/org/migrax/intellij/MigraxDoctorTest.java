package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MigraxDoctorTest {

  @Test
  public void oneLinePerCheckWithItsFix() {
    assertEquals(
        "[ok] Java 21.0.5\n[fail] Database connection: no driver\n      Check the URL",
        MigraxDoctor.text(
            MigraxResult.parseJson(
                "{\"ok\":false,\"checks\":["
                    + "{\"status\":\"ok\",\"message\":\"Java 21.0.5\",\"fix\":null},"
                    + "{\"status\":\"fail\",\"message\":\"Database connection: no driver\","
                    + "\"fix\":\"Check the URL\"}]}")));
  }

  @Test
  public void noChecksIsUnreadable() {
    assertNull(MigraxDoctor.text(null));
    assertNull(MigraxDoctor.text(MigraxResult.parseJson("{\"ok\":false,\"error\":{}}")));
  }
}
