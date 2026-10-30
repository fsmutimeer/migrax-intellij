# Changelog

All notable changes to this plugin are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the plugin uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
