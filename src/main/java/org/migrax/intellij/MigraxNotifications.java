package org.migrax.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/** Short messages in the IDE's notification balloon, for the results of the actions. */
final class MigraxNotifications {

  /** The notification group in plugin.xml. */
  private static final String GROUP = "Migrax";

  private MigraxNotifications() {}

  static void info(Project project, @Nullable String message) {
    show(project, message, NotificationType.INFORMATION);
  }

  static void error(Project project, @Nullable String message) {
    show(project, message, NotificationType.ERROR);
  }

  private static void show(Project project, @Nullable String message, NotificationType type) {
    if (project.isDisposed()) {
      return;
    }
    NotificationGroupManager.getInstance()
        .getNotificationGroup(GROUP)
        .createNotification("Migrax", html(message), type)
        .notify(project);
  }

  /**
   * The balloon shows HTML: the command's texts, such as "--package <name>", must show as they
   * are, with their line breaks.
   */
  static String html(@Nullable String message) {
    String text = message == null || message.isBlank() ? "The Migrax command failed." : message;
    return StringUtil.escapeXmlEntities(text.strip()).replace("\n", "<br>");
  }
}
