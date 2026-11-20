# Changelog

All notable changes to this plugin are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the plugin uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Roll Back's confirmation now lists the tables and columns that the rollback brings back
  without their data, such as a column dropped by a migration made without "Keep a backup of
  the dropped data". Needs Migrax 0.19.0 or later; with older versions the dialog is as
  before.
- Alt+Enter on a lint mark in a migration file: "Ignore MX007 for this statement" writes
  `-- migrax:lint-ignore MX007` above it, "Ignore MX007 in this file" writes (or adds to)
  `-- migrax:lint-ignore-file`, and on MX012 "Add '-- migrax:no-transaction' to this migration"
  comes first. The marks update after the edit. Not offered on a migration the database has
  already run, because editing it changes its checksum.

## [0.2.0] - 2026-11-17

### Added
- In projects that use Migrax, the Migrax menu is now also in the right-click menu of the
  Project view and of the editor (for SQL, Java and Kotlin files). The plugin page now says
  where to find the actions.
- When the Migrax command set in Settings > Tools > Migrax does not exist (a moved or
  deleted folder), a notification offers to use the installed Migrax with one click, or to
  clear the setting, after which it offers to download Migrax. Before, the setting had to be
  cleared by hand.

### Changed
- A migration file whose text did not change is not checked again for lint marks (for ten
  minutes, or until a settings file such as `migrax.properties` or `application-dev.yml`, or
  `.migrax/snapshot.json`, changes), so the editor no longer starts Migrax every time the
  analysis runs again.
- The Status, Plan, Lint and Environments tabs now also follow a changed entity, migration or
  settings file (saved, pulled or generated elsewhere), as the status bar entry already did,
  when "Check again after an entity, migration or settings file changes" is on. The tab on
  screen loads again, the others when you open them. Saving an entity reloads only the Plan
  tab.

### Fixed
- Lint marks in MySQL, MariaDB and TiDB migration files: after a string with an escaped quote
  such as `'it\'s'`, the findings of later statements could be shown above the file instead of
  under the statement.
- Changing a profile settings file (`application-dev.yml`, `microprofile-config-prod.properties`)
  now updates the status bar entry and the margin icons too.

## [0.1.1] - 2026-10-31

### Fixed
- Stopped using IDE functions that newer IntelliJ versions remove (deleting the downloaded
  Migrax folders, opening the HTML report and opening generated files), so these keep working
  on IntelliJ IDEA 2025.2 and later, including 2026.3.

## [0.1.0] - 2026-10-30

### Added
- First project skeleton: a Migrax tool window at the bottom of the IDE.
- Settings page (Settings > Tools > Migrax) with the path to the Migrax command. Without a
  path the plugin uses the `migrax` command found on PATH.
- Runs the Migrax command in the background: one call at a time, cancellable, stopped after
  two minutes, and its JSON output is read for the tool window tabs that follow.
- Status tab in the Migrax tool window: every migration with its state (applied, pending,
  failed, changed, missing), a one-line summary and a Refresh button.
- Plan tab: the SQL of the next migration with its lint findings, loaded when the tab is
  first opened.
- Lint tab: findings about the pending migrations (or all of them), with severity colors.
  Double-click a finding to open its migration file.
- Environments tab: for every configured database, its status (in sync, pending, drift,
  problems, unreachable), applied and pending migrations, with details for the selected one.
- Status bar widget: "Migrax: 2 pending" or "Migrax: in sync", with failed migrations and entity
  changes not yet in a migration. Click it to refresh and open the tool window.
- Continuous integration: build and tests on Linux and Windows, and the Plugin Verifier against
  every recommended IDE version.
- A Migrax project in a sub-folder of the IDE project (a multi-module repository) is found, and
  the status bar entry appears as soon as one is found, without restarting the IDE.
- Finding the Migrax command and the project no longer touches the disk on the editor thread.
- Refreshing the Status tab also refreshes the status bar entry.
- An icon in the editor's margin next to a Java entity class whose changes are not in a
  migration yet, with the changes in its tooltip; a click opens the Plan tab. Needs a Migrax
  command that names the entity class in `check --json`.
- Lint findings marked inside migration files: the statement is underlined and its tooltip
  gives the rule, the message and the fix. The text in the editor is checked, also before it is
  saved. Rollback scripts and files outside the Migrax project folder are skipped.
- The status bar entry and the margin icons check again about 5 seconds after an entity,
  migration or settings file changes, in the editor or on disk. Build output is ignored, so the
  build the check starts does not trigger another one. Can be turned off in Settings > Tools >
  Migrax.
- The margin icon also shows next to Kotlin entity classes (with the Kotlin plugin, in K1 and
  K2 mode).
- Tools > Migrax menu: Generate Migration (asks about renames and drops, then opens the new
  migration and its rollback script), Show Plan, Migrate, Roll Back and Doctor. Migrate and
  Roll Back show the database and the migrations first and ask, with a red warning for a
  database that is not on this computer.
- Buttons down the left side of the Migrax tool window with the same actions and Refresh,
  which replaces the Refresh button of each tab. Migrate, Roll Back and Generate are greyed
  out when the latest status says there is nothing to do.
- New Migration: an empty migration and rollback script for hand-written SQL, opened in the
  editor.
- Repair: removes migrations whose file was deleted from the history, and records a failed
  migration as applied or clears it to run again, after a dialog that shows the database.
- Generate keeps a backup of dropped tables and columns by default (a checkbox in the drop
  dialog), so Roll Back can restore the data.
- Generate for Zero Downtime and Generate Contract, and Backups to list or drop the backup
  tables, in a More menu of the tool window and in Tools > Migrax.
- Drift, in the toolbar and the menu: lists changes made to the database outside the
  migrations.
- Verify and Report under More: apply every migration to a throwaway database and check it, or
  open an HTML report of the schema, migrations, lint findings and drift.
- A click on the margin icon next to an entity offers Generate Migration and Show Plan.
- Status tab: double-click a migration to open it; the right-click menu also opens its rollback
  script and copies its file name.
- Set Up Migrax: in a project without Migrax, creates its folders in the chosen service folder
  and shows what Migrax detected.
- Without an installed Migrax the plugin offers to download it: the newest release, checked
  against its SHA-256, into the IDE's own folder, with an update offer when a newer one is out.
- New icons from the Migrax logo.
- The buttons and tabs update as soon as an answer is in; a deleted .migrax folder is noticed.
