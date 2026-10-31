package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MigraxDownloadTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private static String manifest(String version, String url, String hash) {
    return "{\"version\":\"" + version + "\",\"url\":\"" + url + "\",\"hash\":\"" + hash + "\"}";
  }

  private static final String HASH =
      "b185b85697ae29c2e85a3b75a5fd2209e5d10b8e3f43880ab5cdd8c9f83a4560";

  @Test
  public void readsTheScoopPackageFile() {
    // The shape of bucket/migrax.json in the Scoop package of 0.17.1, shortened.
    String json =
        "{\"version\": \"0.18.0\", \"description\": \"Migrax\","
            + " \"url\": \"https://github.com/fsmutimeer/migrax/releases/download/v0.18.0/"
            + "migrax-0.18.0.zip\", \"hash\": \"" + HASH + "\", \"bin\": [\"bin/migrax.cmd\"]}";
    MigraxDownload.Release release = MigraxDownload.fromManifest(json).orElseThrow();
    assertEquals("0.18.0", release.version());
    assertEquals(MigraxDownload.url("0.18.0"), release.url());
    assertEquals(HASH, release.sha256());
  }

  @Test
  public void takesOnlyAStableReleaseFromTheMigraxReleasesPage() {
    String url = MigraxDownload.url("0.18.0");
    // Somewhere else, or another version's file.
    assertTrue(
        MigraxDownload.fromManifest(manifest("0.18.0", "https://example.org/migrax.zip", HASH))
            .isEmpty());
    assertTrue(
        MigraxDownload.fromManifest(manifest("0.18.0", MigraxDownload.url("0.17.1"), HASH))
            .isEmpty());
    // A release candidate, a version the plugin cannot read, a broken SHA-256.
    assertTrue(
        MigraxDownload.fromManifest(
                manifest("0.18.0-rc.1", MigraxDownload.url("0.18.0-rc.1"), HASH))
            .isEmpty());
    assertTrue(
        MigraxDownload.fromManifest(manifest("0.16.0", MigraxDownload.url("0.16.0"), HASH))
            .isEmpty());
    assertTrue(MigraxDownload.fromManifest(manifest("0.18.0", url, "abc")).isEmpty());
    assertTrue(MigraxDownload.fromManifest("not json").isEmpty());
    assertTrue(MigraxDownload.fromManifest(null).isEmpty());
  }

  @Test
  public void comparesVersionsByTheirNumbers() {
    assertTrue(MigraxDownload.compare("0.18.0", "0.17.1") > 0);
    assertTrue(MigraxDownload.compare("0.17.10", "0.17.9") > 0);
    assertEquals(0, MigraxDownload.compare("0.17.1", "0.17.1"));
  }

  @Test
  public void usesTheNewestCompleteDownload() throws Exception {
    Path root = tmp.getRoot().toPath();
    assertNull(MigraxDownload.installedVersion(root.resolve("none")));
    for (String folder : List.of("0.17.1", "0.17.10", "download-123")) {
      complete(root.resolve(folder));
    }
    // Half removed: the jar without a launcher, a launcher without the jar.
    Files.createDirectories(root.resolve("0.19.0/lib"));
    Files.createFile(root.resolve("0.19.0/lib/migrax.jar"));
    Files.createDirectories(root.resolve("0.20.0/bin"));
    Files.createFile(root.resolve("0.20.0/bin/migrax.cmd"));
    assertEquals("0.17.10", MigraxDownload.installedVersion(root));
  }

  private static void complete(Path folder) throws IOException {
    Files.createDirectories(folder.resolve("lib"));
    Files.createDirectories(folder.resolve("bin"));
    Files.createFile(folder.resolve("lib/migrax.jar"));
    Files.createFile(folder.resolve("bin/migrax.cmd"));
  }

  /** A zip shaped like a Migrax release, with these entries under migrax-<version>/. */
  private Path zip(String version, String... entries) throws Exception {
    Path zip = tmp.getRoot().toPath().resolve("release-" + version + ".zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      for (String entry : entries) {
        out.putNextEntry(new ZipEntry("migrax-" + version + "/" + entry));
        out.write(entry.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
    return zip;
  }

  private static MigraxDownload.Release release(String version, Path zip) throws Exception {
    return new MigraxDownload.Release(
        version, MigraxDownload.url(version), MigraxDownload.sha256(zip));
  }

  @Test
  public void unpacksACheckedReleaseIntoItsVersionFolder() throws Exception {
    Path root = tmp.newFolder("cli").toPath();
    Path zip = zip("0.18.0", "lib/migrax.jar", "bin/migrax", "bin/migrax.cmd", "README.md");
    MigraxDownload.unpack(release("0.18.0", zip), zip, root, tmp.newFolder("work").toPath());
    assertTrue(MigraxDownload.complete(root.resolve("0.18.0")));
    assertEquals("0.18.0", MigraxDownload.installedVersion(root));
  }

  @Test
  public void aFileThatIsNotTheReleaseInstallsNothing() throws Exception {
    Path root = tmp.newFolder("cli").toPath();
    Path zip = zip("0.18.0", "lib/migrax.jar", "bin/migrax.cmd");
    MigraxDownload.Release other =
        new MigraxDownload.Release("0.18.0", MigraxDownload.url("0.18.0"), HASH);
    IOException error =
        assertThrows(
            IOException.class,
            () -> MigraxDownload.unpack(other, zip, root, tmp.newFolder("work").toPath()));
    assertTrue(error.getMessage().contains("Nothing was installed"));
    assertNull(MigraxDownload.installedVersion(root));
  }

  @Test
  public void aReleaseLaidOutDifferentlyInstallsNothing() throws Exception {
    Path root = tmp.newFolder("cli").toPath();
    Path zip = zip("0.18.0", "migrax.jar");
    IOException error =
        assertThrows(
            IOException.class,
            () ->
                MigraxDownload.unpack(
                    release("0.18.0", zip), zip, root, tmp.newFolder("work").toPath()));
    assertTrue(error.getMessage().contains("laid out differently"));
    assertFalse(Files.exists(root.resolve("0.18.0")));
  }

  @Test
  public void theSameReleaseFromAnotherWindowIsFine() throws Exception {
    Path root = tmp.newFolder("cli").toPath();
    Path zip = zip("0.18.0", "lib/migrax.jar", "bin/migrax.cmd");
    MigraxDownload.unpack(release("0.18.0", zip), zip, root, tmp.newFolder("first").toPath());
    MigraxDownload.unpack(release("0.18.0", zip), zip, root, tmp.newFolder("second").toPath());
    assertTrue(MigraxDownload.complete(root.resolve("0.18.0")));
  }

  @Test
  public void removesOnlyOlderDownloads() throws Exception {
    Path root = tmp.newFolder("cli").toPath();
    for (String folder :
        List.of("0.17.1/lib", "0.18.0/lib", "0.19.0/lib", "download-1/lib", "old-0.16.0/lib")) {
      Files.createDirectories(root.resolve(folder));
    }
    MigraxDownload.removeOlder(root, "0.18.0");
    assertFalse(Files.exists(root.resolve("0.17.1")));
    assertFalse(Files.exists(root.resolve("old-0.16.0")));
    assertTrue(Files.exists(root.resolve("0.18.0")));
    // A newer one (a plugin downgraded since) and a download in progress stay.
    assertTrue(Files.exists(root.resolve("0.19.0")));
    assertTrue(Files.exists(root.resolve("download-1")));
  }

  @Test
  public void deletesAFolderWithEverythingInIt() throws Exception {
    Path folder = tmp.newFolder("work").toPath();
    Files.createDirectories(folder.resolve("migrax/lib"));
    Files.writeString(folder.resolve("migrax/lib/migrax.jar"), "jar");
    Files.writeString(folder.resolve("migrax.zip"), "zip");
    MigraxDownload.delete(folder);
    assertFalse(Files.exists(folder));
    // A folder that is already gone is no problem.
    MigraxDownload.delete(folder);
  }

  @Test
  public void checksumIsTheUsualHex() throws Exception {
    Path file = tmp.newFile().toPath();
    Files.writeString(file, "abc", StandardCharsets.US_ASCII);
    // The SHA-256 of "abc" from FIPS 180-2.
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        MigraxDownload.sha256(file));
  }

  @Test
  public void theBuiltInReleaseIsOneThePluginTakes() {
    MigraxDownload.Release builtIn = MigraxDownload.BUILT_IN;
    assertEquals(
        builtIn,
        MigraxDownload.fromManifest(
                manifest(builtIn.version(), builtIn.url(), builtIn.sha256()))
            .orElseThrow());
  }
}
