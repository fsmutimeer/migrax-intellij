package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.intellij.openapi.actionSystem.AnAction;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PluginDescriptorTest {

  @Test
  public void descriptorDeclaresPluginIdAndToolWindowFactory() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
      assertNotNull(in);
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
      assertEquals("org.migrax.ide", text(doc, "id"));
      Element toolWindow = (Element) doc.getElementsByTagName("toolWindow").item(0);
      assertNotNull(toolWindow);
      assertEquals("Migrax", toolWindow.getAttribute("id"));
      Class<?> factory = Class.forName(toolWindow.getAttribute("factoryClass"));
      assertEquals(MigraxToolWindowFactory.class, factory);
    }
  }

  @Test
  public void everyActionClassExists() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
      NodeList actions = doc.getElementsByTagName("action");
      assertEquals(15, actions.getLength());
      Set<String> ids = new HashSet<>();
      for (int i = 0; i < actions.getLength(); i++) {
        Element element = (Element) actions.item(i);
        ids.add(element.getAttribute("id"));
        Class<?> action = Class.forName(element.getAttribute("class"));
        assertTrue(action.getName(), AnAction.class.isAssignableFrom(action));
      }
      // The tool window builds its toolbar from this group; the toolbar shows the menu's
      // actions by reference.
      NodeList groups = doc.getElementsByTagName("group");
      Set<String> groupIds = new HashSet<>();
      for (int i = 0; i < groups.getLength(); i++) {
        groupIds.add(((Element) groups.item(i)).getAttribute("id"));
      }
      assertTrue(groupIds.contains("Migrax.ToolWindowToolbar"));
      assertTrue(groupIds.contains(MigraxEntityLineMarkerProvider.ACTIONS));
      // The Migrax menu: Tools and the two right-click menus.
      Set<String> menuParents = new HashSet<>();
      for (int i = 0; i < groups.getLength(); i++) {
        Element group = (Element) groups.item(i);
        if ("Migrax.Menu".equals(group.getAttribute("id"))) {
          NodeList addTo = group.getElementsByTagName("add-to-group");
          for (int j = 0; j < addTo.getLength(); j++) {
            menuParents.add(((Element) addTo.item(j)).getAttribute("group-id"));
          }
        }
      }
      assertEquals(
          Set.of("ToolsMenu", "ProjectViewPopupMenu", "EditorPopupMenu"), menuParents);
      // The margin icon's popup: an empty group would open nothing.
      for (int i = 0; i < groups.getLength(); i++) {
        Element group = (Element) groups.item(i);
        if (MigraxEntityLineMarkerProvider.ACTIONS.equals(group.getAttribute("id"))) {
          NodeList refs = group.getElementsByTagName("reference");
          assertEquals(2, refs.getLength());
          assertEquals("Migrax.Generate", ((Element) refs.item(0)).getAttribute("ref"));
          assertEquals("Migrax.Plan", ((Element) refs.item(1)).getAttribute("ref"));
        }
      }
      NodeList references = doc.getElementsByTagName("reference");
      assertEquals(16, references.getLength());
      for (int i = 0; i < references.getLength(); i++) {
        String ref = ((Element) references.item(i)).getAttribute("ref");
        assertTrue(ref, ids.contains(ref));
      }
    }
  }

  @Test
  public void onlyThePlatformIsRequiredSoOtherIdesInstallIt() throws Exception {
    Document doc = parse("/META-INF/plugin.xml");
    NodeList depends = doc.getElementsByTagName("depends");
    Map<String, String> optional = new HashMap<>();
    for (int i = 0; i < depends.getLength(); i++) {
      Element element = (Element) depends.item(i);
      String id = element.getTextContent().trim();
      if ("true".equals(element.getAttribute("optional"))) {
        optional.put(id, element.getAttribute("config-file"));
      } else {
        // PyCharm, WebStorm, GoLand and Rider have no Java: a required Java stops the install.
        assertEquals("com.intellij.modules.platform", id);
      }
    }
    assertEquals(
        Map.of(
            "com.intellij.modules.java", "migrax-java.xml",
            "org.jetbrains.kotlin", "migrax-kotlin.xml"),
        optional);
    // Each optional part registers the margin icon for its language, and the class exists.
    for (String file : optional.values()) {
      Element marker =
          (Element) parse("/META-INF/" + file)
              .getElementsByTagName("codeInsight.lineMarkerProvider")
              .item(0);
      assertNotNull(file, marker);
      assertEquals(
          MigraxEntityLineMarkerProvider.class,
          Class.forName(marker.getAttribute("implementationClass")));
    }
    // The main file names no class that needs Java.
    assertEquals(0, doc.getElementsByTagName("codeInsight.lineMarkerProvider").getLength());
  }

  @Test
  public void onlyTheMarginIconUsesJavaClasses() throws Exception {
    // Java's code model lives in the Java plugin: any other class using it would fail to load
    // in PyCharm, WebStorm, GoLand or Rider.
    List<String> javaOnly =
        List.of(
            "org/jetbrains/uast/",
            "com/intellij/psi/PsiClass",
            "com/intellij/psi/JavaPsiFacade",
            "com/intellij/lang/java/",
            "com/intellij/ide/highlighter/JavaFileType");
    // The IDE's class loader gives no code source: find the folder or jar through the class file.
    URL self = MigraxHealth.class.getResource("MigraxHealth.class");
    assertNotNull(self);
    Path classes =
        self.getProtocol().equals("jar")
            ? Path.of(URI.create(self.toString().substring(4, self.toString().indexOf("!/"))))
            : Path.of(self.toURI()).resolve("../../../..").normalize();
    Set<String> users = new TreeSet<>();
    Set<String> checked = new HashSet<>();
    forEachClass(classes, (name, bytes) -> {
      checked.add(name);
      String text = new String(bytes, StandardCharsets.ISO_8859_1);
      if (javaOnly.stream().anyMatch(text::contains)) {
        users.add(name.replaceAll("(\\$.*)?\\.class$", ""));
      }
    });
    assertTrue(checked.contains("org/migrax/intellij/MigraxHealth.class"));
    assertEquals(Set.of("org/migrax/intellij/MigraxEntityLineMarkerProvider"), users);
  }

  private static void forEachClass(Path location, BiConsumer<String, byte[]> action)
      throws IOException {
    if (Files.isDirectory(location)) {
      try (Stream<Path> files = Files.walk(location)) {
        for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
          String name = location.relativize(file).toString().replace('\\', '/');
          action.accept(name, Files.readAllBytes(file));
        }
      }
      return;
    }
    try (JarFile jar = new JarFile(location.toFile())) {
      for (JarEntry entry : Collections.list(jar.entries())) {
        if (entry.getName().endsWith(".class")) {
          try (InputStream in = jar.getInputStream(entry)) {
            action.accept(entry.getName(), in.readAllBytes());
          }
        }
      }
    }
  }

  private Document parse(String resource) throws Exception {
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      assertNotNull(resource, in);
      return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }
  }

  private static String text(Document doc, String tag) {
    return doc.getElementsByTagName(tag).item(0).getTextContent().trim();
  }
}
