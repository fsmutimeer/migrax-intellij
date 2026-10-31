package org.migrax.intellij;

import com.google.gson.JsonObject;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.Decompressor;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Downloads the Migrax command for users who have not installed it, so installing the plugin
 * is enough, and offers newer releases for that copy. An installed Migrax, on PATH or in the
 * settings, is always used first and never touched.
 *
 * <p>The newest release is read from the Scoop package of Migrax, which the release pipeline
 * writes with the zip's SHA-256. Only a stable version, the exact GitHub release URL of that
 * version and a SHA-256 are taken from it, and the zip must match that SHA-256. Without an answer
 * (offline, a proxy) the release built in here is used, with its own SHA-256.
 */
final class MigraxDownload {

  private static final Logger LOG = Logger.getInstance(MigraxDownload.class);

  /** A release of the Migrax command: version, zip and the zip's SHA-256. */
  record Release(String version, String url, String sha256) {}

  /** The release this plugin version was tested with; used when the newest is not known. */
  static final Release BUILT_IN =
      new Release(
          "0.17.1",
          url("0.17.1"),
          "b185b85697ae29c2e85a3b75a5fd2209e5d10b8e3f43880ab5cdd8c9f83a4560");
  /** The oldest Migrax the plugin works with: it reads that version's output. */
  static final String OLDEST_SUPPORTED = "0.17.0";

  private static final String MANIFEST =
      "https://raw.githubusercontent.com/fsmutimeer/scoop-migrax/main/bucket/migrax.json";
  private static final Pattern STABLE = Pattern.compile("\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}");
  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

  // One offer at a time: every command that finds no Migrax would show its own.
  private static final AtomicBoolean OFFERED = new AtomicBoolean();
  private static final AtomicBoolean DOWNLOADING = new AtomicBoolean();
  // Newer releases are looked for once per IDE session.
  private static final AtomicBoolean UPDATE_CHECKED = new AtomicBoolean();

  private MigraxDownload() {}

  /** The only URL a release is downloaded from. */
  static String url(String version) {
    return "https://github.com/fsmutimeer/migrax/releases/download/v"
        + version
        + "/migrax-"
        + version
        + ".zip";
  }

  /** Where downloaded releases live: the IDE's own folder, one sub-folder per version. */
  static Path root() {
    return Path.of(PathManager.getSystemPath(), "migrax", "cli");
  }

  /** The launcher folder of the newest downloaded release, or null. Reads the disk. */
  static @Nullable Path launcherFolder() {
    String version = installedVersion(root());
    return version == null ? null : root().resolve(version).resolve("bin");
  }

  /** The newest complete release in the folder (see {@link #complete}), or null. */
  static @Nullable String installedVersion(Path root) {
    if (!Files.isDirectory(root)) {
      return null;
    }
    try (Stream<Path> folders = Files.list(root)) {
      return folders
          .filter(MigraxDownload::complete)
          .map(folder -> folder.getFileName().toString())
          .filter(name -> STABLE.matcher(name).matches())
          .max(MigraxDownload::compare)
          .orElse(null);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** Compares versions such as 0.17.1 and 0.18.0 by their numbers. */
  static int compare(String a, String b) {
    String[] left = a.split("\\.");
    String[] right = b.split("\\.");
    for (int i = 0; i < Math.max(left.length, right.length); i++) {
      int x = i < left.length ? Integer.parseInt(left[i]) : 0;
      int y = i < right.length ? Integer.parseInt(right[i]) : 0;
      if (x != y) {
        return Integer.compare(x, y);
      }
    }
    return 0;
  }

  /**
   * The release in the Scoop package file, or empty when it is not one the plugin takes: a
   * stable version not older than {@link #OLDEST_SUPPORTED}, exactly that version's GitHub URL
   * and a SHA-256.
   */
  static Optional<Release> fromManifest(@Nullable String json) {
    JsonObject manifest = json == null ? null : MigraxResult.parseJson(json);
    if (manifest == null) {
      return Optional.empty();
    }
    String version = MigraxResult.string(manifest, "version");
    String url = MigraxResult.string(manifest, "url");
    String hash = MigraxResult.string(manifest, "hash");
    if (version == null
        || !STABLE.matcher(version).matches()
        || compare(version, OLDEST_SUPPORTED) < 0
        || !url(version).equals(url)
        || hash == null
        || !SHA256.matcher(hash).matches()) {
      return Optional.empty();
    }
    return Optional.of(new Release(version, url, hash));
  }

  /**
   * The newest release, never older than the built-in one, which is used when the newest is not
   * known. Background thread: asks GitHub.
   */
  private static Release newest(@Nullable ProgressIndicator indicator) {
    try {
      String json =
          HttpRequests.request(MANIFEST)
              .productNameAsUserAgent()
              .connectTimeout(10_000)
              .readTimeout(10_000)
              .readString(indicator);
      Optional<Release> release = fromManifest(json);
      if (release.isPresent()) {
        return compare(release.get().version(), BUILT_IN.version()) >= 0
            ? release.get()
            : BUILT_IN;
      }
      LOG.warn("The Migrax package file is not one the plugin takes; using " + BUILT_IN);
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      LOG.info("Newest Migrax release unknown, using " + BUILT_IN.version() + ": " + e);
    }
    return BUILT_IN;
  }

  /** Offers the download in a notification, once until it is answered. Any thread. */
  static void offer(Project project) {
    if (!OFFERED.compareAndSet(false, true)) {
      return;
    }
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Migrax")
                    .createNotification(
                        "Migrax",
                        "The plugin runs the Migrax command, which was not found on this"
                            + " computer. Download it for the plugin (about 1 MB, needs Java 17"
                            + " or newer), or set the path of an installed one in the settings.",
                        NotificationType.WARNING)
                    .addAction(
                        NotificationAction.createSimpleExpiring(
                            "Download Migrax", () -> download(project, null)))
                    .addAction(
                        NotificationAction.createSimpleExpiring(
                            "Settings",
                            () -> {
                              OFFERED.set(false);
                              ShowSettingsUtil.getInstance()
                                  .showSettingsDialog(project, MigraxConfigurable.class);
                            }))
                    // Closed without an answer: offer again at the next command.
                    .whenExpired(
                        () -> {
                          if (!DOWNLOADING.get()) {
                            OFFERED.set(false);
                          }
                        })
                    .notify(project),
            project.getDisposed());
  }

  /**
   * Once per IDE session, when the plugin runs its own downloaded copy: looks for a newer
   * release and offers it, on a thread of its own so the command does not wait for GitHub.
   */
  static void checkForUpdate(Project project, Path launcher) {
    if (launcher.startsWith(root()) && UPDATE_CHECKED.compareAndSet(false, true)) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> offerUpdate(project));
    }
  }

  private static void offerUpdate(Project project) {
    String installed = installedVersion(root());
    Release newest = newest(null);
    if (installed == null || compare(newest.version(), installed) <= 0) {
      return;
    }
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Migrax")
                    .createNotification(
                        "Migrax",
                        "Migrax "
                            + newest.version()
                            + " is out; the plugin uses its download of "
                            + installed
                            + ".",
                        NotificationType.INFORMATION)
                    .addAction(
                        NotificationAction.createSimpleExpiring(
                            "Update to " + newest.version(), () -> download(project, newest)))
                    .notify(project),
            project.getDisposed());
  }

  /**
   * Downloads, checks and unpacks a release in the background, the newest when {@code release}
   * is null. Any thread.
   */
  static void download(Project project, @Nullable Release release) {
    DOWNLOADING.set(true);
    new Task.Backgroundable(project, "Downloading Migrax", true) {
      private Release installed;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        installed = release != null ? release : newest(indicator);
        indicator.setText("Downloading Migrax " + installed.version());
        try {
          install(installed, root(), indicator);
        } catch (IOException e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
      }

      @Override
      public void onSuccess() {
        finished();
        MigraxNotifications.info(
            project,
            "Migrax " + installed.version() + " is ready. The plugin uses it from now on.");
        MigraxToolWindowFactory.refreshStatus(project);
      }

      @Override
      public void onCancel() {
        finished();
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        finished();
        String reason = error.getMessage() != null ? error.getMessage() : error.toString();
        MigraxNotifications.error(project, "Migrax could not be downloaded: " + reason);
      }

      private void finished() {
        DOWNLOADING.set(false);
        OFFERED.set(false);
      }
    }.queue();
  }

  /**
   * Downloads into a folder next to the final one and moves the release in place at the end,
   * so a cancelled or failed download never leaves half a release that looks installed. Older
   * downloads are removed afterwards when they are not in use.
   */
  private static void install(Release release, Path root, ProgressIndicator indicator)
      throws IOException {
    if (!complete(root.resolve(release.version()))) {
      Files.createDirectories(root);
      Path work = Files.createTempDirectory(root, "download-");
      try {
        Path zip = work.resolve("migrax.zip");
        HttpRequests.request(release.url())
            .productNameAsUserAgent()
            .connectTimeout(10_000)
            .saveToFile(zip, indicator);
        indicator.checkCanceled();
        unpack(release, zip, root, work);
      } finally {
        delete(work);
      }
    }
    removeOlder(root, release.version());
  }

  /**
   * Checks the zip against the release's SHA-256, unpacks it in {@code work} and moves it to
   * {@code root/<version>}. Nothing is put in place unless the file is the release and holds a
   * complete Migrax.
   */
  static void unpack(Release release, Path zip, Path root, Path work) throws IOException {
    String actual = sha256(zip);
    if (!release.sha256().equals(actual)) {
      throw new IOException(
          "the file from GitHub is not the Migrax " + release.version() + " release (SHA-256 "
              + actual + "). Nothing was installed.");
    }
    Path unpacked = work.resolve("migrax");
    new Decompressor.Zip(zip).removePrefixPath("migrax-" + release.version()).extract(unpacked);
    // Zip files do not keep the executable flag the shell script needs.
    Path script = unpacked.resolve("bin").resolve("migrax");
    if (Files.isRegularFile(script) && !script.toFile().setExecutable(true)) {
      throw new IOException("bin/migrax could not be made executable.");
    }
    if (!complete(unpacked)) {
      throw new IOException(
          "the Migrax " + release.version() + " release is laid out differently than this"
              + " plugin expects. Nothing was installed; update the plugin.");
    }
    Path home = root.resolve(release.version());
    try {
      Files.move(unpacked, home, StandardCopyOption.ATOMIC_MOVE);
    } catch (FileSystemException e) {
      // Another window put the same release in place meanwhile: that copy is just as good.
      if (!complete(home)) {
        throw e;
      }
    }
  }

  /** A release folder the plugin can run: the jar and a launcher. */
  static boolean complete(Path folder) {
    Path bin = folder.resolve("bin");
    return Files.isRegularFile(folder.resolve("lib").resolve("migrax.jar"))
        && (Files.isRegularFile(bin.resolve("migrax.cmd"))
            || Files.isRegularFile(bin.resolve("migrax")));
  }

  /**
   * Removes downloads older than {@code keep}, never a newer one or a download in progress. A
   * folder is renamed before it is deleted: Windows refuses to rename one whose Migrax still
   * runs, so that one stays whole and goes next time, and a delete that stops halfway leaves
   * only an old-* folder, which is never used and is removed next time.
   */
  static void removeOlder(Path root, String keep) {
    List<Path> folders;
    try (Stream<Path> listed = Files.list(root)) {
      folders = listed.toList();
    } catch (IOException | RuntimeException e) {
      return;
    }
    for (Path folder : folders) {
      String name = folder.getFileName().toString();
      if (name.startsWith("old-")) {
        delete(folder);
      } else if (STABLE.matcher(name).matches() && compare(name, keep) < 0) {
        Path old = root.resolve("old-" + name);
        try {
          Files.move(folder, old, StandardCopyOption.ATOMIC_MOVE);
          delete(old);
        } catch (IOException e) {
          // In use: left for the next download.
        }
      }
    }
  }

  /**
   * Deletes a folder and everything in it, never throwing: a file that cannot go (one still in
   * use on Windows) is left and the rest is still removed; a folder that cannot be read is left
   * whole. Plain Java, as the IDE's own helper for this is being removed from newer IDE versions.
   */
  static void delete(Path folder) {
    List<Path> paths;
    try (Stream<Path> walked = Files.walk(folder)) {
      // Deepest first, so each folder is empty when its turn comes.
      paths = walked.sorted(Comparator.reverseOrder()).toList();
    } catch (IOException | RuntimeException e) {
      return;
    }
    for (Path path : paths) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException e) {
        // In use: left for the next time.
      }
    }
  }

  /** The SHA-256 of a file, as lowercase hex. */
  static String sha256(Path file) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
    try (InputStream in = Files.newInputStream(file)) {
      byte[] buffer = new byte[64 * 1024];
      for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
