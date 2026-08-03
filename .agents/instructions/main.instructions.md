---
applyTo: "**"
---

<!-- @format -->

# arkki-snappi — AI Instructions

## Identity & Build

JOSM plugin · Package `org.openstreetmap.josm.plugins.arkkisnappi` · Map-mode shortcut `B` · Min JOSM 19439 · GPL-2.0-or-later (license header required on every source file)

Version lives in `gradle.properties` (`version=0.3.0`). The Gradle wrapper is the only build tool; Java 11+ is required (Java 21 recommended — matches CI).

```bash
./gradlew build        # → build/dist/ArkkiSnappi.jar (runs tests first)
./gradlew test         # unit tests; no JOSM instance needed
./gradlew runJosm      # launch JOSM with the plugin pre-loaded
./gradlew clean        # remove build/
# Windows: use .\gradlew.bat instead of ./gradlew
```

CI (`.github/workflows/build-and-release.yml`) runs `./gradlew build` on JDK 21 (Temurin) for pushes to `main`, PRs targeting `main`, tags `v*`, and manual dispatch. Tags `v*` additionally create a GitHub Release with the JAR attached.

## File Map

```
src/main/java/…/arkkisnappi/
├── ArkkiSnappiPlugin.java             # entry point; registers mode + settings menu
├── SnappiMode.java                    # MapMode state machine + all event handling
├── SnappiGrid.java                    # geometry + rendering (stateless, all-static)
├── SnappiShrinkwrap.java              # self-intersection → outer boundary (stateless)
├── ReferenceOrientationDetector.java  # axis detection at anchor placement
├── SnappiPreferences.java             # Config.getPref wrappers (all-static)
└── SnappiPreferencesDialog.java       # JOSM Preferences tab + standalone dialog

src/test/java/…/arkkisnappi/
├── SnappiGridTest.java                # pure EastNorth arithmetic
└── SnappiShrinkwrapTest.java          # shrinkwrap invariants
```

There is no authoritative spec document — the code and tests are the source of truth. All classes except `SnappiMode` are stateless utilities; never introduce instance state into them.

## State Machine (SnappiMode)

Phases: `IDLE → PHASE_ANCHOR → PHASE_DEPTH → PHASE_EXTRUDE`

```
IDLE →(click 1)→ PHASE_ANCHOR
  hasReferenceOrientation: →(click 2)→ PHASE_EXTRUDE
  no reference:            →(click 2)→ PHASE_DEPTH →(click 3)→ PHASE_EXTRUDE

PHASE_EXTRUDE:
  drag handle   → commitExtrude() → wayCorners updated → shrinkwrapWay() → simplifyWay()
  click edge    → handleEdgeClickExtrude() (splits edge, inserts node)
  click away    → finish + start new building preserving axes
  Enter         → finishShape() → shrinkwrapWay() → simplifyWay() → IDLE
  Esc           → IDLE (resetState())
```

Reference orientation priority (see `ReferenceOrientationDetector.detect`): `A` key (cardinal) > first selected way with ≥ 2 nodes > nearby closed way > none (3-click mode).

## ⚠ Coordinate System — Common Error Source

Three spaces; **never mix** them:

| Space     | Type             | Use                                        |
| --------- | ---------------- | ------------------------------------------ |
| EastNorth | projected coords | **All geometry and snapping**              |
| LatLon    | WGS-84           | OSM storage and projection-correction only |
| Point     | screen pixels    | Hit-testing and rendering only             |

**Problem:** In Web Mercator (EPSG:3857), EastNorth ≠ real-world metres — inflated by `sec(lat)`. At 60° lat: ~2× error if uncorrected.

**Correction (mandatory):**

- Display lengths → `SnappiGrid.realWorldDistance(a, b)` (calls `greatCircleDistance()`)
- EN snap steps → `activeStepMetres * projectionScale` via `enStepU()` / `enStepV()`
- `projectionScale` computed once at anchor time: `SnappiGrid.projectionScale(anchorEN)`
- **Never** use raw EN Euclidean arithmetic for measurements or grid spacing

**Local grid frame:**

```
u-axis = unit vector anchor→mouse (or reference edge first segment)
v-axis = u rotated 90° CCW
world  = anchor + uSnapped·uAxis + vSnapped·vAxis
(u, v) = (dot(world-anchor, uAxis), dot(world-anchor, vAxis))
```

## Architecture Rules

1. **Separation of concerns** — snapping math → `SnappiGrid`; self-intersection → `SnappiShrinkwrap`; `SnappiMode` = state + events only.
2. **Undo safety** — every OSM write uses the Command pattern; no direct `DataSet` mutation ever.
3. **`wayCorners[]` cache** — must reflect the current way geometry at all times; updated in `commitExtrude()`, `simplifyWay()`, `shrinkwrapWay()`, and rebuilt by `syncWayCorners()`.
4. **Cleanup order** — after `commitExtrude()` updates `wayCorners`: `shrinkwrapWay()` then `simplifyWay()`. Same pair on `finishShape()`.
5. **Simplify bypass** — `shouldAutoSimplify()` returns `false` when `shiftDown`; this is per-operation, not a global toggle.
6. **Projection-aware geometry** — never store or measure in screen `Point` space; convert to `EastNorth` first.

## Keyboard & Modifiers

| Key       | During drawing                 | During extrude / finish                   |
| --------- | ------------------------------ | ----------------------------------------- |
| `Shift`   | axis-lock (dominant axis only) | bypass `simplifyWay()` for this operation |
| `Ctrl`    | free mode (no snapping)        | —                                         |
| `Alt`     | cycle step preset              | —                                         |
| `A`       | toggle cardinal grid           | —                                         |
| `C` / `V` | halve / double step            | —                                         |
| `Enter`   | finish shape                   | —                                         |
| `Esc`     | cancel → IDLE                  | —                                         |

## Preferences (`arkki_snappi.*`)

All step values are stored and operated on in **real-world metres**. Defaults are declared as `DEFAULT_*` constants in `SnappiPreferences`.

```
step_x_metres / step_y_metres   double   0.3048                (1 ft)
linked_steps                    bool     true
step_unit                       string   "ft"                  ("ft" | "m")
step_presets                    string   "0.0762;0.1524;0.3048;0.6096;1.0;0.5;0.25"
tags                            list     [["building","yes"]]  default tags for new buildings
auto_select                     bool     true                  auto-select created way
auto_simplify / auto_shrinkwrap bool     true
ccw_winding                     bool     true
handle_radius / node_snap_radius int     10 / 15
drag_threshold_px               int      5
max_grid_lines                  int      200
color.grid / color.rect / color.anchor / color.target / color.handle / color.extrude  # ARGB ints; blueprint theme by default
```

Display: `SnappiPreferences.formatStep(metres)` / `formatStepPair(xM, yM)` — rounds to 5 sig-figs, strips trailing zeros. `metresToFeet()` / `feetToMetres()` use exact `0.3048` (no rounding loss).

## Key JOSM API Patterns

```java
// Coordinate conversion
mv.getEastNorth(point)                                        // screen → world
mv.getPoint(en)                                               // world → screen
ProjectionRegistry.getProjection().eastNorth2latlon(en)       // EN → LatLon (node creation)

// Data mutation — always wrap in Commands
DataSet ds = getLayerManager().getEditDataSet();
List<Command> cmds = new ArrayList<>();
cmds.add(new AddCommand(ds, newNode));
Way updated = new Way(existingWay);                           // clone, then mutate
updated.setNodes(newNodeList);
cmds.add(new ChangeCommand(existingWay, updated));
cmds.add(new DeleteCommand(ds, Collections.singleton(orphan)));
UndoRedoHandler.getInstance().add(new SequenceCommand(tr("label"), cmds));

// Mode lifecycle (enterMode / exitMode)
map.mapView.addTemporaryLayer(this);          // MapViewPaintable overlay
map.keyDetector.addModifierExListener(this);  // ModifierExListener
map.keyDetector.addKeyListener(this);         // KeyPressReleaseListener
```

## Testing

- Unit tests cover **pure EastNorth arithmetic only** — no JOSM instance required (`SnappiGridTest`, `SnappiShrinkwrapTest`). Keep it that way for new tests.
- Run with `./gradlew test`; CI runs the same suite as part of `./gradlew build`.
- When changing snapping/geometry/shrinkwrap behaviour, add or extend a unit test before/with the change.

## Coding Conventions

- 4-space indent · UTF-8 · CRLF (enforced by `.editorconfig`) · GPL-2.0-or-later header on every source file · Javadoc on public API
- CCW winding default (JOSM convention); `SnappiPreferences.isCcwWinding()` toggleable
- Preference keys: `arkki_snappi.<name>` — no external deps beyond JOSM core and JUnit (tests)
- YAML/JSON/JS files are `@format`-tagged (Prettier with the repo's `.prettierrc.js`); keep existing formatting on edit
- Keep changes minimal and focused; this is a small, dependency-free plugin
