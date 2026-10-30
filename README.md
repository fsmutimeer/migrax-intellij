# Migrax for IntelliJ IDEA

Plugin for [Migrax](https://migrax.org), which generates SQL migrations from your JPA/Hibernate
entities. The plugin shows what Migrax found and runs the Migrax command line for you, so you
do not have to leave the editor.

**Status: early development.** Nothing is published to the JetBrains Marketplace yet.

## What works today

- A **Migrax** tool window with the tabs Status (migrations and their state), Plan (the SQL of
  the next migration), Lint (findings, double-click opens the file) and Environments (every
  configured database).
- A status bar entry ("Migrax: 2 pending", "Migrax: in sync"), shown only in projects that use
  Migrax.
- An icon in the editor's margin next to a Java or Kotlin entity class with changes that are
  not in a migration yet. Its tooltip lists the changes; click it to see their SQL in the Plan
  tab. You can turn it off in Settings > Editor > General > Gutter Icons. It needs a Migrax
  command that names the entity class in `check --json` (the next Migrax release), and
  entities read with Hibernate 5 or newer or by annotation scanning.
- Lint findings inside migration files: a risky statement is underlined (red for errors) and
  its tooltip gives the rule, the message and the fix. The text in the editor is checked, also
  before you save it, with `migrax lint`.

- A **Tools > Migrax** menu:
  - *Generate Migration* writes the next migration and opens it with its rollback script.
    When a change looks like a rename it asks "Did you rename ...?", so the data is kept, and
    it asks before a migration that drops tables or columns.
  - *Show Plan* opens the Plan tab.
  - *Migrate...* and *Roll Back...* first show the database and the migrations and ask. A
    database that is not on this computer gets a red warning. Roll Back undoes the newest
    applied migration. Once started, they cannot be cancelled: stopping halfway could leave a
    migration half applied.
  - *Doctor* checks the settings, the project and the database.

## Planned

- A quick action on the margin icon to generate the migration.
- Squash and drift in the menu, and a run configuration for Migrate.

## Known limitations

- With several Migrax projects below one IDE project, the first one found is used.
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
- The plugin finds the command through the path in Settings > Tools > Migrax, or on `PATH`.

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
supported yet. The plugin needs the Migrax command
line, version 0.17 or newer.

## License

Apache License 2.0, see [LICENSE](LICENSE).
