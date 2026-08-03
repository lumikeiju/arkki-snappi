---
<!-- @format -->
<!-- SPDX-License-Identifier: AGPL-2.0-or-later -->

# ArkkiSnappi - Changelog

All notable changes to ArkkiSnappi are documented here, using the [Keep a Changelog](https://keepachangelog.com/) format, [Semantic Versioning](https://semver.org/), and [Conventional Commits](https://www.conventionalcommits.org/).

<!--

## Unreleased: v0.0.0 (0000-00-00)

### Added

-

### Changed

-

### Fixed

-

-->

## v0.3.0 (2026-05-22)

### Added

- Hold `Shift` while extruding an edge or finishing a shape to keep collinear nodes for that operation, bypassing auto-simplification
- Live conversion of displayed step values when switching the display unit (`ft` ↔ `m`) in the Snappi preferences dialog

## v0.2.0 (2026-04-05)

### Added

- Automated GitHub releases on version tags (`v*`), with generated release notes and the plugin JAR attached

## v0.1.0 (2026-04-04)

### Added

- Initial release!
- Snappi map mode (`B` shortcut): quick and accurate building footprint mapping with a 2-axis snap grid
- Reference orientation detection from cardinal alignment, a selected way, or a nearby building
- Independent X/Y snapping with configurable step sizes (default 1 ft), step presets, and keyboard shortcuts
- Edge extrusion via drag handles and edge-click splitting for partial extrusions
- Auto-simplification (collinear-node removal) and shrinkwrap (self-intersection resolution) on finish
- Snappi preferences tab and dialog for steps, snapping, tags, and colors
- Unit tests for grid geometry and shrinkwrap logic
