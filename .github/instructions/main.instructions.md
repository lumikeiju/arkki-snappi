---
applyTo: "**"
---

<!-- @format -->

# arkki-snappi — AI Instructions

## Identity & Build

JOSM plugin · Package `org.openstreetmap.josm.plugins.arkkisnappi` · Shortcut `B` · Min JOSM 19439 · AGPL v3

```bash
.\gradlew.bat build    # → build/dist/ArkkiSnappi.jar
.\gradlew.bat test     # run unit tests (no JOSM instance needed)
.\gradlew.bat runJosm  # launch JOSM with plugin loaded
```

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

local-storage/planning.md  # working design notes
docs/                      # intentionally sparse (no authoritative spec file)
```

## State Machine (SnappiMode)

```
IDLE →(click 1)→ PHASE_ANCHOR
  hasReferenceOrientation: →(click 2)→ PHASE_EXTRUDE
  no reference:            →(click 2)→ PHASE_DEPTH →(click 3)→ PHASE_EXTRUDE

PHASE_EXTRUDE:
  drag handle   → commitExtrude() → wayCorners updated → shrinkwrapWay() → simplifyWay()
  click edge    → handleEdgeClickExtrude() (splits edge, inserts node)
  click away    → finish + start new building preserving axes
  Enter         → finishShape() → shrinkwrapWay() → simplifyWay() → IDLE
  Esc           → IDLE
```

Reference orientation priority: `A` key (cardinal) > selected way > nearby building > none (3-click mode).

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

All step values stored and operated on in **real-world metres**.

```
step_x_metres / step_y_metres   double   0.3048   (1 ft)
linked_steps                    bool     true
step_unit                       string   "ft"
step_presets                    string   "0.0762;0.1524;0.3048;0.6096;1.0;0.5;0.25"
auto_simplify / auto_shrinkwrap bool     true
ccw_winding                     bool     true
handle_radius / node_snap_radius int     10 / 15
drag_threshold_px               int      5
max_grid_lines                  int      200
```

Display: `SnappiPreferences.formatStep(metres)` / `formatStepPair(xM, yM)` — rounds to 5 sig-figs, strips trailing zeros.

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

## Coding Conventions

- 4-space indent · UTF-8 · CRLF · AGPL v3 header on every source file · Javadoc on public API
- CCW winding default (JOSM convention); `SnappiPreferences.isCcwWinding()` toggleable
- Preference keys: `arkki_snappi.<name>` — no external deps beyond JOSM core
- Unit tests: pure EastNorth arithmetic only — no JOSM instance required (`SnappiGridTest`, `SnappiShrinkwrapTest`)
