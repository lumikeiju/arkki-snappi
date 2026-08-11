# ArkkiSnappi — JOSM plugin

Quick building-footprint mapping with a 2-axis snap grid. Package `org.openstreetmap.josm.plugins.arkkisnappi` · map-mode shortcut `B` · min JOSM 19439 · License: `GPL-2.0-or-later`.

## Build & test

```bash
./gradlew build        # → build/dist/ArkkiSnappi.jar (runs tests first)
./gradlew test         # unit tests — pure EastNorth math, no JOSM needed
./gradlew runJosm      # launch JOSM with the plugin pre-loaded
```

Windows: use `.\gradlew.bat` instead of `./gradlew`. Java 11+ (Java 21 recommended — matches CI).

Gradle version is pinned to 8.x. The JOSM Gradle plugin 0.8.2 uses Provider.forUseAtConfigurationTime(), which was removed in Gradle 9. Do not upgrade the Gradle wrapper past 8.x until the JOSM Gradle plugin is updated. The Dependabot gradle ecosystem entry has been intentionally omitted for this reason.

## Non-negotiable rules

1. **Coordinates** — geometry/snapping in `EastNorth` only; never mix `EastNorth`, `LatLon`, screen `Point`. Mercator inflates EN vs real metres by `sec(lat)` (~2× at 60° lat): use `SnappiGrid.realWorldDistance`/`projectionScale` for measurements, never raw EN arithmetic.
2. **OSM writes** — always via JOSM `Command`s (`AddCommand`/`ChangeCommand`/`DeleteCommand`/`SequenceCommand`); never mutate a `DataSet` directly.
3. **`wayCorners[]`** — keep in sync with the committed way after every geometry change; after extrude run `shrinkwrapWay()` then `simplifyWay()`.
4. **Stateless utilities** — `SnappiGrid`, `SnappiShrinkwrap`, `SnappiPreferences`, `ReferenceOrientationDetector` are all-static; only `SnappiMode` holds state.

## Agent resources

- **Full project reference:** `.agents/instructions/main.instructions.md`
- **Contributing guide:** `CONTRIBUTING.md`
- **Release history:** `CHANGELOG.md`
- **Skills** (auto-load when triggered / relevant files edited):
  - `build-test-run` — build, test, run-JOSM commands & gotchas
  - `coordinate-spaces` — the three coordinate spaces + projection correction
  - `undo-safe-edits` — JOSM Command pattern and node merging
  - `plugin-conventions` — formatting, preferences, testing rules
