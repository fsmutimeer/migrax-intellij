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

## Planned

- A marker next to an entity class that changed without a migration, with a quick action to
  generate it.
- Menu actions for generate, plan, migrate and rollback.

## Known limitations

- The command runs in the project's base folder, so a Migrax project inside a sub-module is
  not found yet.
- The status bar entry refreshes when the project opens and when you click it.
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
