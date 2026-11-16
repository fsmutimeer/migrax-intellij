package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import org.junit.Test;

public class MigraxCommandsTest {

  @Test
  public void theStatusTextSaysWhatTheNotificationOffers() {
    assertEquals(
        "The Migrax command set in Settings > Tools > Migrax does not exist: C:\\old\\migrax.cmd."
            + " Use the installed one from the notification, or clear the path in the settings.",
        MigraxCommands.gone("C:\\old\\migrax.cmd", true));
    assertTrue(MigraxCommands.gone("C:\\old\\migrax.cmd", false)
        .endsWith("and the plugin offers to download Migrax."));
  }

  @Test
  public void theNotificationNamesTheInstalledMigraxAndEscapesPaths() {
    String text = MigraxCommands.goneNotification("C:\\R&D\\<old>\\migrax.cmd",
        Path.of("bin", "migrax.cmd"));
    assertTrue(text, text.contains("C:\\R&amp;D\\&lt;old&gt;\\migrax.cmd"));
    assertTrue(text, text.contains("Migrax is installed at " + Path.of("bin", "migrax.cmd")));
    assertTrue(MigraxCommands.goneNotification("x", null)
        .contains("the plugin offers to download one"));
  }

  @Test
  public void aClickClearsOnlyThePathItWasAbout() {
    assertTrue(MigraxCommands.stillSetTo("C:\\old\\migrax.cmd", "C:\\old\\migrax.cmd"));
    // Spaces edited into migrax.xml by hand.
    assertTrue(MigraxCommands.stillSetTo(" C:\\old\\migrax.cmd ", "C:\\old\\migrax.cmd"));
    // Changed in the settings while the notification was open: keep the new one.
    assertFalse(MigraxCommands.stillSetTo("D:\\new\\migrax.cmd", "C:\\old\\migrax.cmd"));
  }
}
