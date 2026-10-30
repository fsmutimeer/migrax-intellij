package org.migrax.intellij;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class MigraxReportTest {

  @Test
  public void writesThePageWhereItIsAsked() {
    Path page = Path.of("tmp", "migrax", "abc-report.html").toAbsolutePath();
    assertEquals(List.of("report", "--html", page.toString()), MigraxReport.args(page));
  }

  @Test
  public void readsWhatThePageHolds() {
    assertEquals(
        "4 table(s), 3 migration(s), 1 pending, 0 lint finding(s), no drift.",
        MigraxReport.summary(
            "Wrote C:\\Users\\me\\tmp\\migrax\\abc-report.html: 4 table(s), 3 migration(s),"
                + " 1 pending, 0 lint finding(s), no drift.\n"));
    assertNull(MigraxReport.summary("something else\n"));
  }
}
