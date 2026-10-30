package org.migrax.intellij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

/** The margin icon in a real editor, for a Java and a Kotlin entity class. */
public class MigraxEntityLineMarkerTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MigraxHealthService.getInstance(getProject())
        .showEntityChanges(
            MigraxEntityChanges.from(
                MigraxResult.parseJsonValue(
                    "{\"ok\":false,\"changes\":[],\"operations\":["
                        + "{\"summary\":\"add column customer.phone\","
                        + "\"entity\":\"com.example.Customer\"},"
                        + "{\"summary\":\"create table order_line\","
                        + "\"entity\":\"com.example.Order$Line\"}]}")));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // The light project is shared with later tests.
      MigraxHealthService.getInstance(getProject()).showEntityChanges(MigraxEntityChanges.NONE);
    } finally {
      super.tearDown();
    }
  }

  public void testJavaEntityClassGetsTheIcon() {
    myFixture.configureByText(
        "Customer.java", "package com.example;\n\npublic class Customer {\n  String phone;\n}\n");
    assertEquals(tooltips("add column customer.phone"), tooltipsOfIcons());
  }

  public void testKotlinEntityClassGetsTheIcon() {
    myFixture.configureByText(
        "Customer.kt", "package com.example\n\nclass Customer {\n  var phone: String? = null\n}\n");
    assertEquals(tooltips("add column customer.phone"), tooltipsOfIcons());
  }

  public void testNestedKotlinClassGetsTheIcon() {
    myFixture.configureByText(
        "Order.kt", "package com.example\n\nclass Order {\n  class Line\n}\n");
    assertEquals(tooltips("create table order_line"), tooltipsOfIcons());
  }

  public void testSameNameInAnotherPackageGetsNoIcon() {
    myFixture.configureByText("Customer.kt", "package com.other\n\nclass Customer\n");
    assertEquals(List.of(), tooltipsOfIcons());
  }

  private static List<String> tooltips(String change) {
    return List.of(MigraxEntityLineMarkerProvider.tooltip(List.of(change)));
  }

  private List<String> tooltipsOfIcons() {
    myFixture.doHighlighting();
    return myFixture.findAllGutters().stream()
        .filter(mark -> mark.getIcon() == MigraxEntityLineMarkerProvider.ICON)
        .map(mark -> mark.getTooltipText())
        .toList();
  }
}
