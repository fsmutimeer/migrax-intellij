# Migrax for IntelliJ IDEA

Plugin for [Migrax](https://migrax.org), which generates SQL migrations from your JPA/Hibernate
entities. The plugin shows what Migrax found and runs the Migrax command line for you, so you
do not have to leave the editor.

**Status: early development.** Nothing is published to the JetBrains Marketplace yet.

## What works today

- A **Migrax** tool window with the tabs Status (migrations and their state; double-click
  opens the migration, right-click also opens its rollback script), Plan (the SQL of the next
  migration), Lint (findings, double-click opens the file) and Environments (every configured
  database).
- A status bar entry ("Migrax: 2 pending", "Migrax: in sync"), shown only in projects that use
  Migrax.
- An icon in the editor's margin next to a Java or Kotlin entity class with changes that are
  not in a migration yet. Its tooltip lists the changes; click it to generate the migration
  (for all entity changes, not only this class) or to see the SQL in the Plan tab.
  You can turn it off in Settings > Editor > General > Gutter Icons. It needs a Migrax
  command that names the entity class in `check --json` (the next Migrax release), and
  entities read with Hibernate 5 or newer or by annotation scanning.
- Lint findings inside migration files: a risky statement is underlined (red for errors) and
  its tooltip gives the rule, the message and the fix. The text in the editor is checked, also
  before you save it, with `migrax lint`.

- Buttons down the left side of the Migrax tool window, and the same actions in a
  **Tools > Migrax** menu. An action with nothing to do (no pending migrations, nothing
  applied, no entity changes) is greyed out and its tooltip says why:
  - *Set Up Migrax...* (only in a project without Migrax yet, also as a link in the Status
    tab) asks for the service's folder, creates the `.migrax` and migration folders there and
    shows what Migrax detected: build tool, entity package, database and naming.
  - *Generate Migration* writes the next migration and opens it with its rollback script.
    When a change looks like a rename it asks "Did you rename ...?", so the data is kept, and
    it asks before a migration that drops tables or columns. By default it keeps a backup of
    the dropped data in `migrax_backup_*` tables, which Roll Back restores.
  - *Generate for Zero Downtime* (under More in the tool window) splits a change so the old
    and new version of the application can run at the same time: an expand migration now,
    and a contract migration with the drops that waits for the next release. *Generate
    Contract* adds it once every running instance has the expand migration.
  - *New Migration...* creates an empty migration and its rollback script for hand-written
    SQL, such as a data change, and opens both.
  - *Show Plan* opens the Plan tab.
  - *Migrate...* and *Roll Back...* first show the database and the migrations and ask. A
    database that is not on this computer gets a red warning. Roll Back undoes the newest
    applied migration. Once started, they cannot be cancelled: stopping halfway could leave a
    migration half applied.
  - *Repair...* fixes the history: a migration whose file was deleted is removed from it (the
    database keeps its changes), and for a failed one you say whether it took effect or you
    restored the database. It shows the database and asks first, like Migrate.
  - *Backups...* (under More) lists the backup tables of dropped data and drops them, all or
    one migration's, once you will not roll those migrations back.
  - *Drift* compares the database with what the migrations build and lists changes someone
    made by hand, such as a column added outside a migration. It only reads the database.
  - *Verify* (under More) applies every migration to a throwaway database (in memory, or in
    Docker for most databases), checks the result against the entities and tests each rollback
    script. The database in the settings is not touched.
  - *Report* (under More) writes an HTML page with the schema, the migrations, the lint
    findings and the drift, and opens it in the browser.
  - *Doctor* checks the settings, the project and the database.
  - *Refresh* (tool window only) loads the open tab again.

## Planned

- Squash in the menu, and a run configuration for Migrate.

## Known limitations

- With several Migrax projects below one IDE project, the first one found is used.
  Set Up Migrax is offered only while there is none, so set up a further service in the
  terminal (`migrax init`) or open it as its own project.
- The status bar entry and the margin icons refresh when the project opens, about 5 seconds
  after an entity, migration or settings file changes (saved, or changed on disk), when you
  click the status bar entry and when you refresh the Status tab. That check may build the
  project; turn it off in Settings > Tools > Migrax. The Plan tab still refreshes only on
  request.
- In a single-table inheritance hierarchy the icon is on the root class.
- Lint marks show about a second after you stop typing (each check starts the command). The
  plugin does not read where migrations are configured: it checks `.sql` files in the Migrax
  project folder, skipping `rollback` folders and build output.
- Migrate and Roll Back work on the database in the project's settings. Other environments
  are shown in the Environments tab but cannot be migrated from the IDE.
- The plugin finds the command through the path in Settings > Tools > Migrax, then on `PATH`,
  then the copy it downloaded itself.

The plugin does not contain migration logic. It runs the `migrax` command line and shows its
JSON output, so it behaves exactly like the terminal.

## Build

Needs JDK 17 or newer.

```bash
./gradlew build        # compile and test
./gradlew runIde       # start a test IDE with the plugin
./gradlew verifyPlugin # check compatibility with recent IDE versions
```

On Windows in `cmd` or PowerShell, use `gradlew.bat` instead of `./gradlew`.

## Compatibility

IntelliJ IDEA 2024.2 and newer (Community and Ultimate). Other JetBrains IDEs are not
supported yet.

The plugin runs the Migrax command line, version 0.17 or newer, which needs Java 17 or newer.
An installed Migrax (on `PATH`, or its path in Settings > Tools > Migrax) is used first. Without
one, the plugin offers to download the newest Migrax release for itself (about 1 MB) into the
IDE's own folder. It takes the version and its SHA-256 from the Migrax Scoop package, downloads
only from the Migrax releases on GitHub and checks the file before it is unpacked; offline it
uses the release it was built with. When a newer release is out, it offers the update once per
session. An installed Migrax is never changed.

## License

Apache License 2.0, see [LICENSE](LICENSE).
