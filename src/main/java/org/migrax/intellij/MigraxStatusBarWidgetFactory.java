package org.migrax.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The "Migrax: 2 pending" text at the bottom right of the IDE. */
public final class MigraxStatusBarWidgetFactory implements StatusBarWidgetFactory {

  static final String ID = "MigraxStatusBar";

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Migrax";
  }

  /** Only projects that use Migrax get the widget; in others the plugin stays silent. */
  @Override
  public boolean isAvailable(@NotNull Project project) {
    String base = project.getBasePath();
    return base != null && MigraxProjectDetector.isMigraxProject(Path.of(base));
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new Widget(project);
  }

  /** Text widget; click refreshes the answer and shows the tool window. */
  private static final class Widget implements StatusBarWidget, StatusBarWidget.TextPresentation {
    private final Project project;
    private final MigraxHealthService service;
    private StatusBar statusBar;
    private final Runnable changed = () -> {
      if (statusBar != null) {
        statusBar.updateWidget(ID);
      }
    };

    Widget(Project project) {
      this.project = project;
      // Taken now: while the project closes the service can no longer be looked up.
      this.service = MigraxHealthService.getInstance(project);
    }

    @Override
    public @NotNull String ID() {
      return ID;
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
      return this;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      this.statusBar = statusBar;
      service.addListener(changed);
      service.refresh();
    }

    @Override
    public void dispose() {
      service.removeListener(changed);
      statusBar = null;
    }

    @Override
    public @NotNull String getText() {
      return service.label();
    }

    @Override
    public float getAlignment() {
      return 0.5f;
    }

    @Override
    public @Nullable String getTooltipText() {
      return service.tooltip();
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
      return event -> {
        service.refresh();
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Migrax");
        if (window != null) {
          window.activate(null);
        }
      };
    }
  }
}
