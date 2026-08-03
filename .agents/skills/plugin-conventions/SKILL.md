---
name: plugin-conventions
description: Coding conventions and patterns for the arkki-snappi JOSM plugin — formatting, license headers, preferences, code structure, and testing rules. Use when writing or reviewing Java code, adding a preference, or adding a test.
triggers:
  - conventions
  - formatting
  - preference key
  - license header
  - add preference
  - javadoc
paths:
  - src/**/*.java
  - .editorconfig
  - .prettierrc.js
---

# Plugin Conventions

## Formatting & files

- 4-space indent · UTF-8 · **CRLF** line endings (enforced by `.editorconfig`).
- GPL-2.0-or-later license header on every source file: `// License: GPL v2 or later. For details, see LICENSE file.`
- Javadoc on all public API (include `@author`); utility classes get a private no-op constructor.
- YAML/JSON/JS files carry the `@format` marker and follow Prettier (`.prettierrc.js`).

## Preferences

- Keys: `arkki_snappi.<name>`. Steps are stored/operated in **real-world metres**.
- Add a preference end-to-end: `KEY_*` constant + `DEFAULT_*` + static getter in `SnappiPreferences`, then UI in `SnappiPreferencesDialog`.
- Display via `formatStep(metres)` / `formatStepPair(xM, yM)` (5 sig-figs, strips trailing zeros); `metresToFeet()` uses exact `0.3048`.

## Code structure

- `SnappiGrid`, `SnappiShrinkwrap`, `SnappiPreferences`, `ReferenceOrientationDetector` are stateless all-static utilities — no instance state.
- `SnappiMode` owns all state: state machine + event handling only.
- CCW winding default (JOSM convention); toggle via `SnappiPreferences.isCcwWinding()`.
- No external dependencies beyond JOSM core and JUnit 5 (tests).

## Tests

- Unit tests cover pure EastNorth arithmetic only — **no JOSM instance**; keep new tests JOSM-free.
- JUnit Platform, run with `./gradlew test`.
