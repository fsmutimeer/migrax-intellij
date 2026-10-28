package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MigraxCliLocatorTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path launcher(String folder, String name) throws IOException {
    Path dir = Files.createDirectories(tmp.getRoot().toPath().resolve(folder));
    Path file = Files.createFile(dir.resolve(name));
    Assume.assumeTrue(file.toFile().setExecutable(true));
    return file;
  }

  @Test
  public void findsTheLauncherOnPath() throws IOException {
    Path migrax = launcher("bin", "migrax");
    String path = "/nonexistent" + File.pathSeparator + migrax.getParent();
    assertEquals(migrax, new MigraxCliLocator(false, path).locate("").orElseThrow());
  }

  @Test
  public void aBlankSettingMeansPath() throws IOException {
    Path migrax = launcher("bin", "migrax");
    MigraxCliLocator locator = new MigraxCliLocator(false, migrax.getParent().toString());
    assertEquals(migrax, locator.locate("  ").orElseThrow());
    assertEquals(migrax, locator.locate(null).orElseThrow());
  }

  @Test
  public void theSettingWinsOverPath() throws IOException {
    Path onPath = launcher("a", "migrax");
    Path chosen = launcher("b", "migrax");
    MigraxCliLocator locator = new MigraxCliLocator(false, onPath.getParent().toString());
    assertEquals(chosen, locator.locate(chosen.toString()).orElseThrow());
  }

  @Test
  public void aFolderInTheSettingIsSearchedForTheLauncher() throws IOException {
    Path migrax = launcher("tools", "migrax");
    MigraxCliLocator locator = new MigraxCliLocator(false, "");
    assertEquals(migrax, locator.locate(migrax.getParent().toString()).orElseThrow());
  }

  @Test
  public void aWrongSettingIsNotReplacedByPath() throws IOException {
    Path onPath = launcher("a", "migrax");
    MigraxCliLocator locator = new MigraxCliLocator(false, onPath.getParent().toString());
    assertTrue(locator.locate(tmp.getRoot().toPath().resolve("missing").toString()).isEmpty());
  }

  @Test
  public void nothingFoundGivesEmpty() {
    assertTrue(new MigraxCliLocator(false, "/nonexistent").locate("").isEmpty());
    assertTrue(new MigraxCliLocator(false, null).locate("").isEmpty());
  }

  @Test
  public void onWindowsTheCmdLauncherIsUsedNotTheShellScript() throws IOException {
    launcher("bin", "migrax");
    Path cmd = launcher("bin", "migrax.cmd");
    MigraxCliLocator locator = new MigraxCliLocator(true, cmd.getParent().toString());
    assertEquals(cmd, locator.locate("").orElseThrow());
  }

  @Test
  public void onWindowsTheShellScriptAloneIsNotEnough() throws IOException {
    Path script = launcher("bin", "migrax");
    assertTrue(new MigraxCliLocator(true, script.getParent().toString()).locate("").isEmpty());
  }

  @Test
  public void aBrokenPathEntryIsSkipped() throws IOException {
    Path migrax = launcher("bin", "migrax");
    String path = "\0bad" + File.pathSeparator + migrax.getParent();
    assertEquals(migrax, new MigraxCliLocator(false, path).locate("").orElseThrow());
  }

  @Test
  public void onWindowsExeAndBatCountAndCmdWins() throws IOException {
    Path bat = launcher("one", "migrax.bat");
    Path exe = launcher("two", "migrax.exe");
    assertEquals(bat, new MigraxCliLocator(true, bat.getParent().toString()).locate("").get());
    assertEquals(exe, new MigraxCliLocator(true, exe.getParent().toString()).locate("").get());
    Path both = launcher("three", "migrax.exe");
    Path cmd = launcher("three", "migrax.cmd");
    assertEquals(cmd, new MigraxCliLocator(true, both.getParent().toString()).locate("").get());
  }

  @Test
  public void onWindowsAConfiguredFileNeedsALauncherExtension() throws IOException {
    Path script = launcher("bin", "migrax");
    Path text = launcher("bin", "notes.txt");
    Path cmd = launcher("bin", "migrax.cmd");
    MigraxCliLocator locator = new MigraxCliLocator(true, "");
    assertTrue(locator.locate(script.toString()).isEmpty());
    assertTrue(locator.locate(text.toString()).isEmpty());
    assertEquals(cmd, locator.locate(cmd.toString()).orElseThrow());
  }

  @Test
  public void aNotExecutableFileIsNotUsedOutsideWindows() throws IOException {
    Path file = Files.createFile(tmp.newFolder("plain").toPath().resolve("migrax"));
    Assume.assumeTrue(file.toFile().setExecutable(false));
    Assume.assumeFalse(Files.isExecutable(file));
    MigraxCliLocator locator = new MigraxCliLocator(false, file.getParent().toString());
    assertTrue(locator.locate("").isEmpty());
    assertTrue(locator.locate(file.toString()).isEmpty());
  }

  @Test
  public void aConfiguredFolderWithoutALauncherGivesEmpty() throws IOException {
    Path empty = tmp.newFolder("empty").toPath();
    assertTrue(new MigraxCliLocator(false, "").locate(empty.toString()).isEmpty());
  }

  @Test
  public void aQuotedPathEntryIsUnquoted() throws IOException {
    Path migrax = launcher("Program Files", "migrax");
    String path = "\"" + migrax.getParent() + "\"";
    assertEquals(migrax, new MigraxCliLocator(false, path).locate("").orElseThrow());
  }
}
