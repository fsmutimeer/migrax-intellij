package org.migrax.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An icon in the editor's margin next to an entity class whose changes are not in a migration
 * yet. The tooltip lists the changes; a click opens the Plan tab with their SQL.
 */
public final class MigraxEntityLineMarkerProvider extends LineMarkerProviderDescriptor {

  static final Icon ICON =
      IconLoader.getIcon("/icons/migraxGutter.svg", MigraxEntityLineMarkerProvider.class);

  @Override
  public @NotNull String getName() {
    return "Migrax: entity changes without a migration";
  }

  @Override
  public @NotNull Icon getIcon() {
    return ICON;
  }

  @Override
  public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    // Called for every leaf element: the cheap test first.
    if (!(element instanceof PsiIdentifier)) {
      return null;
    }
    Project project = element.getProject();
    MigraxEntityChanges all = MigraxHealthService.getInstance(project).entityChanges();
    // Markers go on leaf elements: the name of the class.
    if (all.isEmpty()
        || !(element.getParent() instanceof PsiClass type)
        || type.getNameIdentifier() != element) {
      return null;
    }
    List<String> changes = all.of(type.getQualifiedName());
    if (changes.isEmpty()) {
      return null;
    }
    String tooltip = tooltip(changes);
    return new LineMarkerInfo<>(
        element,
        element.getTextRange(),
        ICON,
        e -> tooltip,
        (event, target) -> MigraxToolWindowFactory.showPlan(project),
        GutterIconRenderer.Alignment.LEFT,
        () -> "Migrax: entity changes without a migration");
  }

  /** "2 changes not in a migration yet" and one line per change, as HTML. */
  static String tooltip(List<String> changes) {
    StringBuilder html = new StringBuilder("<html><b>Migrax:</b> ");
    html.append(changes.size())
        .append(changes.size() == 1 ? " change" : " changes")
        .append(" not in a migration yet");
    for (String change : changes) {
      html.append("<br>&nbsp;&nbsp;").append(StringUtil.escapeXmlEntities(change));
    }
    return html.append("<br><i>Click to see the SQL in the Plan tab</i></html>").toString();
  }
}
