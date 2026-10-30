package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Runs a fake launcher script, so no real Migrax and no database are needed. */
public class MigraxCliTest {

  private static final boolean WINDOWS = File.separatorChar == '\\';

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path workDir;

  @Before
  public void createWorkDir() throws IOException {
    workDir = tmp.newFolder("project").toPath();
  }

  /** A launcher that runs the given shell (or batch) lines. */
  private Path launcher(String... lines) throws IOException {
    Path file = tmp.getRoot().toPath().resolve(WINDOWS ? "migrax.cmd" : "migrax");
    String body = String.join(WINDOWS ? "\r\n" : "\n", lines) + (WINDOWS ? "\r\n" : "\n");
    Files.writeString(file, (WINDOWS ? "@echo off\r\n" : "#!/bin/sh\n") + body);
    assertTrue(file.toFile().setExecutable(true));
    return file;
  }

  @Test
  public void parsesTheJsonAndKeepsTheExitCode() throws IOException {
    Path migrax =
        launcher(
            WINDOWS
                ? "echo {\"ok\":false,\"changes\":[\"create table t\"],\"extra\":1}"
                : "echo '{\"ok\":false,\"changes\":[\"create table t\"],\"extra\":1}'",
            "exit 2");
    MigraxResult result = new MigraxCli(migrax, workDir).run(List.of("check", "--json"), null);
    assertEquals(MigraxResult.EXIT_CHANGES, result.exitCode());
    assertTrue(result.succeeded());
    assertNotNull(result.json());
    assertEquals(1, result.json().getAsJsonArray("changes").size());
    assertNull(result.errorMessage());
  }

  @Test
  public void passesTheArgumentsAndRunsInTheWorkFolder() throws IOException {
    Path migrax =
        launcher(WINDOWS ? "echo %1 %2 %CD%" : "echo \"$1 $2 $(pwd)\"");
    MigraxResult result = new MigraxCli(migrax, workDir).run(List.of("status", "--json"), null);
    assertTrue(result.stdout(), result.stdout().startsWith("status --json"));
    assertTrue(result.stdout(), result.stdout().contains(workDir.getFileName().toString()));
  }

  @Test
  public void anErrorObjectBecomesAMessage() throws IOException {
    String json =
        "{\"ok\":false,\"exitCode\":1,\"error\":{\"code\":\"MXE116\","
            + "\"message\":\"No environments.\",\"hint\":\"Add one.\"}}";
    Path migrax = launcher(WINDOWS ? "echo " + json : "echo '" + json + "'", "exit 1");
    MigraxResult result = new MigraxCli(migrax, workDir).run(List.of("environments"), null);
    assertFalse(result.succeeded());
    assertEquals("MXE116: No environments. Add one.", result.errorMessage());
  }

  @Test
  public void anUnknownEntityPackageIsExplainedForTheIde() {
    String hint =
        "Pass --package <name> or set MIGRAX_PACKAGE (for Maven projects the groupId is used).";
    String json =
        "{\"ok\":false,\"exitCode\":1,\"error\":{\"code\":\"MXE001\","
            + "\"message\":\"Entity package is unknown.\",\"hint\":\"" + hint + "\"}}";
    assertEquals(
        MigraxResult.NO_PACKAGE_IN_IDE, new MigraxResult(1, json, "", false, false).errorMessage());
    // generate has no JSON: the same error on stderr.
    String stderr = "error[MXE001]: Entity package is unknown. " + hint + "\n";
    assertEquals(
        MigraxResult.NO_PACKAGE_IN_IDE,
        new MigraxResult(1, "", stderr, false, false).errorMessage());
    // A further database names its own setting, which already works in the IDE.
    String further =
        "error[MXE001]: Entity package is unknown. Set migrax.databases.audit.package or pass"
            + " --package <name>.";
    assertEquals(further, new MigraxResult(1, "", further, false, false).errorMessage());
  }

  @Test
  public void aFailureWithoutJsonShowsTheErrorOutput() throws IOException {
    Path migrax = launcher(WINDOWS ? "echo boom 1>&2" : "echo boom >&2", "exit 1");
    MigraxResult result = new MigraxCli(migrax, workDir).run(List.of("status"), null);
    assertNull(result.json());
    assertEquals("boom", result.errorMessage());
  }

  @Test
  public void aMissingLauncherIsAnErrorNotACrash() {
    MigraxResult result =
        new MigraxCli(tmp.getRoot().toPath().resolve("nothing"), workDir)
            .run(List.of("status"), null);
    assertFalse(result.succeeded());
    assertTrue(result.errorMessage(), result.errorMessage().startsWith("Could not start"));
  }

  @Test
  public void aSlowCommandIsStoppedAtTheTimeout() throws IOException {
    Path migrax = launcher(WINDOWS ? "ping -n 30 127.0.0.1 >nul" : "sleep 30");
    long start = System.nanoTime();
    MigraxResult result = new MigraxCli(migrax, workDir, 500).run(List.of("status"), null);
    assertTrue(result.timedOut());
    assertFalse(result.succeeded());
    assertTrue(System.nanoTime() - start < 15_000_000_000L);
  }

  @Test
  public void cancellingStopsTheProcess() throws Exception {
    Path migrax = launcher(WINDOWS ? "ping -n 30 127.0.0.1 >nul" : "sleep 30");
    AbstractProgressIndicatorBase indicator = new AbstractProgressIndicatorBase();
    Thread canceller =
        new Thread(
            () -> {
              try {
                Thread.sleep(700);
              } catch (InterruptedException e) {
                return;
              }
              indicator.cancel();
            });
    canceller.setDaemon(true);
    canceller.start();
    long start = System.nanoTime();
    MigraxResult result = new MigraxCli(migrax, workDir).run(List.of("status"), indicator);
    assertTrue(result.cancelled());
    assertEquals("Cancelled.", result.errorMessage());
    assertTrue(System.nanoTime() - start < 15_000_000_000L);
  }

  @Test
  public void aSecondCallWaitsAndCanBeCancelledWhileWaiting() throws Exception {
    Path migrax = launcher(WINDOWS ? "ping -n 4 127.0.0.1 >nul" : "sleep 3");
    MigraxCli cli = new MigraxCli(migrax, workDir);
    Thread first = new Thread(() -> cli.run(List.of("status"), null));
    first.setDaemon(true);
    first.start();
    Thread.sleep(500);
    AbstractProgressIndicatorBase waiting = new AbstractProgressIndicatorBase();
    waiting.cancel();
    MigraxResult second = cli.run(List.of("status"), waiting);
    assertTrue(second.cancelled());
    first.join();
  }

  @Test
  public void cancellingAlsoStopsTheChildOfTheLauncher() throws Exception {
    Path marker = tmp.getRoot().toPath().resolve("child-survived");
    Path migrax =
        launcher(
            WINDOWS
                ? "cmd /c \"ping -n 4 127.0.0.1 >nul & echo x > " + marker + "\""
                : "sh -c \"sleep 3; echo x > '" + marker + "'\"");
    AbstractProgressIndicatorBase indicator = new AbstractProgressIndicatorBase();
    Thread canceller = new Thread(() -> {
      try {
        Thread.sleep(700);
      } catch (InterruptedException e) {
        return;
      }
      indicator.cancel();
    });
    canceller.setDaemon(true);
    canceller.start();
    new MigraxCli(migrax, workDir).run(List.of("status"), indicator);
    Thread.sleep(5000);
    assertFalse("a child of the launcher kept running", Files.exists(marker));
  }

  @Test(expected = IllegalArgumentException.class)
  public void aTimeoutBeyondIntRangeIsRefused() {
    new MigraxCli(tmp.getRoot().toPath(), workDir, 3_000_000_000L);
  }

  @Test
  public void jsonAfterAStartLineIsStillFound() {
    assertNotNull(MigraxResult.parseJson("note: starting\n{\"ok\":true}\n"));
    assertNull(MigraxResult.parseJson("no json here"));
    assertNull(MigraxResult.parseJson("{broken"));
    assertNotNull(MigraxResult.parseJson("path C:\\{x}\n{\"ok\":true}\nhint: later"));
    assertNotNull(MigraxResult.parseJson("{\"ok\":true}\nhint: later"));
  }
}
