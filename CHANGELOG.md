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
