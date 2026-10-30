package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigraxTargetTest {

  private static boolean local(String url) {
    return new MigraxTarget(url).isOnThisComputer();
  }

  @Test
  public void readsTheUrlFromDoctor() {
    MigraxTarget target =
        MigraxTarget.fromDoctor(
            MigraxResult.parseJson(
                "{\"ok\":false,\"exitCode\":1,\"checks\":["
                    + "{\"status\":\"ok\",\"message\":\"Java 21.0.5\",\"fix\":null},"
                    + "{\"status\":\"ok\",\"message\":\"Database URL: jdbc:sqlite:demo.db\","
                    + "\"fix\":null},"
                    + "{\"status\":\"fail\",\"message\":\"Database connection: no driver\","
                    + "\"fix\":\"Check the URL\"}]}"));
    assertEquals(new MigraxTarget("jdbc:sqlite:demo.db"), target);
  }

  @Test
  public void noUrlWhenDoctorNamesNone() {
    assertNull(MigraxTarget.fromDoctor(null));
    assertNull(MigraxTarget.fromDoctor(MigraxResult.parseJson("{\"checks\":[]}")));
    assertNull(
        MigraxTarget.fromDoctor(
            MigraxResult.parseJson(
                "{\"checks\":[{\"status\":\"fail\","
                    + "\"message\":\"Database URL not configured\"}]}")));
  }

  @Test
  public void databasesOnThisComputer() {
    assertTrue(local("jdbc:mysql://localhost:3306/shop?allowPublicKeyRetrieval=true"));
    assertTrue(local("jdbc:postgresql://127.0.0.1/app"));
    assertTrue(local("jdbc:postgresql://[::1]:5432/app"));
    assertTrue(local("jdbc:sqlserver://localhost;databaseName=app"));
    assertTrue(local("jdbc:mysql://root@localhost/app"));
    assertTrue(local("jdbc:oracle:thin:@localhost:1521:xe"));
    assertTrue(local("jdbc:oracle:thin:@//localhost:1521/xepdb1"));
    assertTrue(local("jdbc:h2:mem:test"));
    assertTrue(local("jdbc:h2:./data/app"));
    assertTrue(local("jdbc:sqlite:demo.db"));
    assertTrue(local("jdbc:derby:memory:app;create=true"));
    assertTrue(local("jdbc:tc:postgresql:16:///app"));
    assertTrue(local("jdbc:oracle:thin:scott/***@localhost:1521:xe"));
    assertTrue(local("jdbc:postgresql:app"));
    assertTrue(local("jdbc:hsqldb:mem:app"));
  }

  @Test
  public void databasesOnAnotherComputer() {
    assertFalse(local("jdbc:postgresql://db.example.com:5432/app"));
    assertFalse(local("jdbc:mysql://localhost,replica.example.com/app"));
    assertFalse(local("jdbc:h2:tcp://build-server/~/app"));
    assertFalse(local("jdbc:oracle:thin:@prod-db:1521:orcl"));
    assertFalse(local("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=localhost)))"));
    assertFalse(local("jdbc:postgresql://localhost.example.com/app"));
    assertFalse(local("postgres://localhost/app"));
    // The command shows the password as ***; the host comes after the last @.
    assertFalse(local("jdbc:oracle:thin:scott/***@prod:1521:orcl"));
    assertFalse(local("jdbc:oracle:thin:@prod.example.com/orcl"));
    assertFalse(local("jdbc:postgresql://127.example.com/app"));
    assertFalse(local("jdbc:sqlserver://localhost;serverName=prod;databaseName=app"));
    assertFalse(local("jdbc:hsqldb:hsql://db.example.com/app"));
    assertFalse(local("jdbc:unknown:somewhere"));
  }
}
