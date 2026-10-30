package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.intellij.openapi.actionSystem.AnAction;
import java.io.InputStream;
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
      assertEquals(5, actions.getLength());
      for (int i = 0; i < actions.getLength(); i++) {
        Class<?> action = Class.forName(((Element) actions.item(i)).getAttribute("class"));
        assertTrue(action.getName(), AnAction.class.isAssignableFrom(action));
      }
    }
  }

  private static String text(Document doc, String tag) {
    return doc.getElementsByTagName(tag).item(0).getTextContent().trim();
  }
}
