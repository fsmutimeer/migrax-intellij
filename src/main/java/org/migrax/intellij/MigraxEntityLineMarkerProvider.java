package org.migrax.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.ui.awt.RelativePoint;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

/**
 * An icon in the editor's margin next to an entity class whose changes are not in a migration
 * yet. The tooltip lists the changes; a click offers to generate the migration or to show its
 * SQL in the Plan tab. Registered for Java, and for Kotlin when the Kotlin plugin is enabled
 * (migrax-kotlin.xml).
 */
public final class MigraxEntityLineMarkerProvider extends LineMarkerProviderDescriptor {

  /** The actions of the click, in plugin.xml. */
  static final String ACTIONS = "Migrax.EntityGutter";

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
    // Called for every leaf element: the cheap tests first. Markers go on leaf elements: the
    // name of the class.
    if (element.getFirstChild() != null
        || !(element.getParent() instanceof PsiNameIdentifierOwner owner)
        || owner.getNameIdentifier() != element) {
      return null;
    }
    Project project = element.getProject();
    MigraxEntityChanges all = MigraxHealthService.getInstance(project).entityChanges();
    if (!all.hasSimpleName(owner.getName())) {
      return null;
    }
    // UAST gives a Java and a Kotlin class the same shape and the same qualified name.
    UClass type = UastContextKt.toUElement(owner, UClass.class);
    if (type == null) {
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
        (event, target) -> showActions(project, event),
        GutterIconRenderer.Alignment.LEFT,
        () -> "Migrax: entity changes without a migration");
  }

  private static void showActions(Project project, MouseEvent event) {
    if (!(ActionManager.getInstance().getAction(ACTIONS) instanceof ActionGroup group)) {
      return;
    }
    JBPopupFactory.getInstance()
        .createActionGroupPopup(
            "Migrax",
            group,
            // The actions need only the project; this one is the entity's, whatever the click hit.
            SimpleDataContext.getProjectContext(project),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true)
        .show(new RelativePoint(event));
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
    return html.append("<br><i>Click to generate the migration or see its SQL</i></html>")
        .toString();
  }
}
