---
<!-- @format -->
<!-- SPDX-License-Identifier: GPL-2.0-or-later -->

# Contributing to ArkkiSnappi

Thanks for contributing! This guide covers how to build, test, and submit changes to ArkkiSnappi, the JOSM building-footprint mapping plugin.

## Getting started

Java 11+ is required (Java 21 recommended — matches CI). The Gradle wrapper is the only build tool.

```bash
./gradlew build        # → build/dist/ArkkiSnappi.jar (runs tests first)
./gradlew test         # unit tests — pure EastNorth math, no JOSM instance needed
./gradlew runJosm      # launch JOSM with the plugin pre-loaded
```

Windows: use `.\gradlew.bat` instead of `./gradlew`.

## Development workflow

1. Create a feature branch from `main` (e.g. `fix/edge-case`, `feat/new-option`).
2. Make focused commits using [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` new user-facing functionality
   - `fix:` bug fixes
   - `refactor:` internal changes with no behaviour change
   - `docs:` documentation (e.g. this file, README, comments)
   - `chore:` maintenance (dependencies, tooling)
3. Run `./gradlew test` (and `./gradlew build`) before pushing.
4. Open a pull request against `main` and describe the change.
5. Update `CHANGELOG.md` under the relevant `Unreleased`/release section.

## Code conventions

- 4-space indent · UTF-8 · **CRLF** line endings (`.editorconfig` enforces).
- GPL-2.0-or-later license header on every source file: `// License: GPL v2 or later. For details, see LICENSE file.`
- Javadoc on all public API.
- All OSM writes go through JOSM `Command`s — never mutate a `DataSet` directly.
- Geometry/snapping in `EastNorth` only; never mix `EastNorth`, `LatLon`, and screen `Point`; always apply the Mercator projection correction.
- `SnappiGrid`, `SnappiShrinkwrap`, `SnappiPreferences`, and `ReferenceOrientationDetector` are stateless all-static utilities — no instance state.
- Preference keys: `arkki_snappi.<name>`; step values stored in real-world metres.
- No external dependencies beyond JOSM core and JUnit 5 (tests).
- Unit tests cover pure EastNorth arithmetic only — keep them JOSM-free.

For full detail see `.agents/instructions/main.instructions.md` and the auto-loading skills in `.agents/skills/`.

## Licensing

By contributing, you agree that your contributions are licensed under **GPL-2.0-or-later**, matching easy-pan's licensing. See `LICENSE` for details.
