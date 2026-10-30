package org.migrax.intellij;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what {@code migrax generate --no-input} printed. The command has no JSON output, so
 * this reads its text: the renames it could not ask about, the files it created, and whether it
 * stopped because of drops.
 */
record MigraxGenerateOutput(
    List<Rename> renames,
    List<String> created,
    boolean noChanges,
    boolean needsDrops,
    List<String> drops) {

  /**
   * A change that may be a rename. {@code option} is --rename or --rename-table, {@code value}
   * its argument, such as {@code categories.description=color}.
   */
  record Rename(String what, String option, String value) {

    /** "Did you rename categories.description to categories.color?" */
    String question() {
      return "Did you rename " + what + "?";
    }
  }

  // Printed for each rename the command would have asked about.
  private static final Pattern RENAME =
      Pattern.compile(
          "^Possible rename: (.+)\\. To keep the data, rerun with "
              + "(--rename(?:-table)?) (\\S+?)\\.$");
  // "Created src/main/resources/db/migration/0002_x.sql:" and the online or baseline forms.
  private static final Pattern CREATED = Pattern.compile("^Created (.+?\\.sql)(?:[:. ].*)?$");
  // "  3. drop column categories.description  [DESTRUCTIVE]" in the list of changes.
  private static final Pattern DROP = Pattern.compile("^\\d+\\. (.+?)\\s+\\[DESTRUCTIVE]$");
  private static final String NO_CHANGES = "No changes detected.";
  // The error code for drops without --allow-destructive.
  private static final String DESTRUCTIVE_ERROR = "error[MXE108]";

  static MigraxGenerateOutput of(MigraxResult result) {
    List<Rename> renames = new ArrayList<>();
    List<String> created = new ArrayList<>();
    List<String> drops = new ArrayList<>();
    boolean noChanges = false;
    for (String line : result.stdout().split("\\R")) {
      String trimmed = line.strip();
      Matcher rename = RENAME.matcher(trimmed);
      Matcher file = CREATED.matcher(trimmed);
      Matcher drop = DROP.matcher(trimmed);
      if (rename.matches()) {
        renames.add(new Rename(rename.group(1), rename.group(2), rename.group(3)));
      } else if (file.matches()) {
        created.add(file.group(1));
      } else if (drop.matches()) {
        drops.add(drop.group(1));
      } else if (trimmed.equals(NO_CHANGES)) {
        noChanges = true;
      }
    }
    boolean needsDrops = result.stderr().contains(DESTRUCTIVE_ERROR);
    return new MigraxGenerateOutput(
        List.copyOf(renames), List.copyOf(created), noChanges, needsDrops, List.copyOf(drops));
  }

  /**
   * The arguments of {@code generate} with these renames: one --rename and one --rename-table
   * with comma-separated values, as the command takes them.
   */
  static List<String> args(List<Rename> renames, boolean allowDrops) {
    List<String> args = new ArrayList<>(List.of("generate", "--no-input"));
    for (String option : List.of("--rename-table", "--rename")) {
      List<String> values =
          renames.stream().filter(r -> r.option().equals(option)).map(Rename::value).toList();
      if (!values.isEmpty()) {
        args.add(option);
        args.add(String.join(",", values));
      }
    }
    if (allowDrops) {
      args.add("--allow-destructive");
    }
    return args;
  }
}
