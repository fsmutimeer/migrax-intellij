package org.migrax.intellij;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/**
 * Migrax runs nothing in a project the IDE does not trust. The command reads the project's own
 * settings files (migrax.properties, application.properties), which can fill a database URL
 * with environment variables ({@code ${GITHUB_TOKEN}}) and point it at someone else's server:
 * a cloned repository must not make Migrax connect anywhere just by being opened.
 */
final class MigraxTrust {

  static final String UNTRUSTED =
      "This project is not trusted, so Migrax runs nothing in it. Trust it (Safe Mode banner),"
          + " then click Refresh in the Migrax tool window.";

  private static final Logger LOG = Logger.getInstance(MigraxTrust.class);
  private static volatile boolean missingLogged;

  private MigraxTrust() {}

  /**
   * Whether the IDE trusts the project. Any thread. The IDE marks its trust API experimental:
   * should a later version drop it, the project counts as untrusted rather than safe.
   */
  static boolean isTrusted(Project project) {
    try {
      return TrustedProjects.isTrusted(project);
    } catch (LinkageError e) {
      if (!missingLogged) {
        missingLogged = true;
        LOG.warn("The IDE's trusted-projects check is missing; Migrax runs nothing", e);
      }
      return false;
    }
  }
}
