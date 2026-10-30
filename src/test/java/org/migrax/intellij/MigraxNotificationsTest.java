package org.migrax.intellij;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MigraxNotificationsTest {

  @Test
  public void showsTheCommandsTextAsItIs() {
    assertEquals(
        "Pass --package &lt;name&gt;.<br>Then run again.",
        MigraxNotifications.html("Pass --package <name>.\nThen run again.\n"));
  }

  @Test
  public void neverShowsAnEmptyMessage() {
    assertEquals("The Migrax command failed.", MigraxNotifications.html(null));
    assertEquals("The Migrax command failed.", MigraxNotifications.html(" "));
  }
}
