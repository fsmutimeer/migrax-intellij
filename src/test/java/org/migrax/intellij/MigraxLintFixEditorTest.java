package org.migrax.intellij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** The lint fixes in a real editor, with the preview Alt+Enter shows. */
public class MigraxLintFixEditorTest extends BasePlatformTestCase {

  public void testIgnoreForThisStatement() {
    String drop = "ALTER TABLE a DROP COLUMN id";
    String text = "CREATE TABLE a (id INT);\n" + drop + ";\n";
    myFixture.configureByText("V2__drop.sql", text);
    myFixture.checkPreviewAndLaunchAction(fix("MX007", text.indexOf("ALTER"), drop, 0));
    myFixture.checkResult(
        "CREATE TABLE a (id INT);\n-- migrax:lint-ignore MX007\n" + drop + ";\n");
  }

  public void testNoTransaction() {
    String index = "CREATE INDEX CONCURRENTLY i ON a (b)";
    String text = index + ";\n";
    myFixture.configureByText("V3__index.sql", text);
    myFixture.checkPreviewAndLaunchAction(fix("MX012", 0, index, 0));
    myFixture.checkResult("-- migrax:no-transaction\n" + text);
  }

  private static MigraxLintFix fix(String code, int start, String statement, int index) {
    return MigraxLintFix.forFinding(code, start, statement).get(index);
  }
}
