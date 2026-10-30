package org.migrax.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * The database a command that changes it would work on, for the confirm dialog: the URL from
 * the "Database URL" check of {@code migrax doctor --json}, without its password.
 */
record MigraxTarget(String url) {

  private static final String URL_CHECK = "Database URL: ";
  // 127.0.0.1 and the rest of 127.0.0.0/8, but not a name such as 127.example.com.
  private static final Pattern LOOPBACK = Pattern.compile("127(\\.\\d{1,3}){3}");

  /** Reads the output of {@code migrax doctor --json}; null when it names no database URL. */
  static @Nullable MigraxTarget fromDoctor(@Nullable JsonObject doctor) {
    if (doctor == null || !(doctor.get("checks") instanceof JsonArray checks)) {
      return null;
    }
    for (JsonElement element : checks) {
      if (element instanceof JsonObject check
          && check.get("message") instanceof JsonPrimitive message
          && message.isString()
          && message.getAsString().startsWith(URL_CHECK)) {
        String url = message.getAsString().substring(URL_CHECK.length()).trim();
        return url.isEmpty() ? null : new MigraxTarget(url);
      }
    }
    return null;
  }

  /**
   * True when the database runs on this computer: an embedded or in-memory database, or a
   * server on localhost. Anything this cannot read counts as another computer, so the dialog
   * warns rather than stays quiet.
   */
  boolean isOnThisComputer() {
    String lower = url.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("jdbc:")) {
      return false;
    }
    // Testcontainers starts the database in Docker on this computer.
    if (lower.startsWith("jdbc:tc:")) {
      return true;
    }
    // SQL Server: a serverName property names the host instead of the part after //.
    if (lower.contains("servername=")) {
      return false;
    }
    if (lower.startsWith("jdbc:oracle:")) {
      // jdbc:oracle:thin:user/***@host:port:sid, @//host:port/service or @(DESCRIPTION=...).
      int at = lower.lastIndexOf('@');
      if (at < 0) {
        return false;
      }
      String rest = lower.substring(at + 1);
      if (rest.startsWith("//")) {
        return hostsAreLocal(authority(rest.substring(2)));
      }
      return !rest.startsWith("(") && isLocalHost(rest.split("[:/]", 2)[0]);
    }
    int slashes = lower.indexOf("://");
    if (slashes >= 0) {
      return hostsAreLocal(authority(lower.substring(slashes + 3)));
    }
    return hasNoHost(lower);
  }

  // Databases in a file or in memory, and PostgreSQL's jdbc:postgresql:db (localhost).
  private static boolean hasNoHost(String lower) {
    return lower.startsWith("jdbc:h2:")
        || lower.startsWith("jdbc:hsqldb:mem:")
        || lower.startsWith("jdbc:hsqldb:file:")
        || lower.startsWith("jdbc:hsqldb:res:")
        || lower.startsWith("jdbc:derby:")
        || lower.startsWith("jdbc:sqlite:")
        || lower.startsWith("jdbc:postgresql:");
  }

  // host:port,host2:port up to the path, the parameters or the properties.
  private static String authority(String afterSlashes) {
    int end = afterSlashes.length();
    for (char stop : new char[] {'/', '?', ';'}) {
      int at = afterSlashes.indexOf(stop);
      if (at >= 0 && at < end) {
        end = at;
      }
    }
    return afterSlashes.substring(0, end);
  }

  private static boolean hostsAreLocal(String authority) {
    if (authority.isEmpty()) {
      return false;
    }
    for (String hostAndPort : authority.split(",")) {
      String host = hostAndPort.substring(hostAndPort.lastIndexOf('@') + 1);
      if (host.startsWith("[")) {
        int close = host.indexOf(']');
        host = close < 0 ? host : host.substring(1, close);
      } else {
        host = host.split(":", 2)[0];
      }
      if (!isLocalHost(host)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isLocalHost(String host) {
    return host.equals("localhost") || LOOPBACK.matcher(host).matches() || host.equals("::1");
  }
}
