<!-- @format -->

# Copilot Instructions for arkki-snappi

## About This Project & Your Role as an AI Assistant

This is a **JOSM plugin** for quick and accurate building footprint mapping. It combines the rectangle-drawing UX of BuildingsTools with edge-extrusion from the Extrude tool, using a snap-to-grid interaction in both axes simultaneously.

**Key principle:** Produce clean, well-documented Java code following JOSM plugin conventions.

## Project Structure

```
arkki-snappi/
├── build.gradle                  # Gradle build (org.openstreetmap.josm plugin)
├── settings.gradle               # Root project name = ArkkiSnappi
├── gradle/wrapper/               # Gradle wrapper (no pre-install needed)
├── local-storage/planning.md     # Full design spec — READ THIS FIRST
├── src/main/java/org/openstreetmap/josm/plugins/arkkisnappi/
│   ├── ArkkiSnappiPlugin.java    # Entry point: registers mode on map frame init
│   ├── SnappiMode.java           # MapMode state machine: IDLE → ANCHOR → EXTRUDE
│   ├── SnappiGrid.java           # Stateless geometry + grid rendering utility
│   ├── SnappiShrinkwrap.java     # Self-intersection detection + outer boundary
│   ├── SnappiPreferences.java    # Typed preference accessors (Config.getPref wrappers)
│   └── SnappiPreferencesDialog.java  # JOSM Preferences tab + standalone dialog
└── images/mapmode/arkkisnappi.png    # Toolbar icon
```

## Build

Uses **Gradle** with the [`org.openstreetmap.josm`](https://plugins.gradle.org/plugin/org.openstreetmap.josm) plugin (v0.8.2).
Reference plugins: [multipoly-gone](https://github.com/watmildon/multipoly-gone), [TIGER-ROAR](https://github.com/watmildon/TIGER-ROAR).

```bash
.\gradlew.bat build          # compile + package → build/dist/ArkkiSnappi.jar
.\gradlew.bat runJosm        # launch JOSM with plugin loaded
```

## Architecture Quick Reference

- **State machine** in `SnappiMode`: `IDLE` → click → `PHASE_ANCHOR` → click → `PHASE_DEPTH` (3-click) or `PHASE_EXTRUDE` (2-click with reference) → click → `PHASE_EXTRUDE` → Enter/Esc → `IDLE`
- **Reference orientation**: angle-snap (A key), selected way, or nearby building sets grid axes at anchor time; without reference, uses 3-click mode
- **Snap grid** in `SnappiGrid`: local (u,v) coordinate frame from anchor point; u and v axes snap independently to separate step multiples in EastNorth space
- **Auto-simplify**: removes collinear nodes on finish (toggleable via `arkki_snappi.auto_simplify`)
- **Auto-shrinkwrap** in `SnappiShrinkwrap`: resolves self-intersecting polygons from overlapping extrusions by computing the outer boundary
- **All data mutations** use JOSM Command pattern (`AddCommand`, `ChangeCommand`, `DeleteCommand`, `SequenceCommand`) for undo safety
- **Preferences** stored via `Config.getPref()` under `arkki_snappi.*` keys; settings available in JOSM Preferences tab

## Coordinate Systems

- **EastNorth**: projected coordinates — used for ALL geometric calculations and snapping
- **LatLon**: WGS-84 degrees — only for OSM data storage and projection-correction calculations
- **Point2D / MapView pixels**: screen space — only for hit-testing and rendering
- Step size is stored in metres internally; displayed in user's chosen unit (ft/m)

**Projection distortion (critical):** In JOSM's default Web Mercator projection (EPSG:3857), EastNorth units are NOT equal to real-world metres — they are inflated by sec(latitude). This is corrected in two ways:
1. **Display labels**: always use `SnappiGrid.realWorldDistance(a, b)` (→ `ILatLon.greatCircleDistance()`) for correct ground distance
2. **Snap grid spacing**: `SnappiMode` calls `SnappiGrid.projectionScale(anchorEN)` at anchor placement to get the local EastNorth-units-per-metre; `enStepU()` / `enStepV()` helpers apply this scale before every snap or paint call

**Never** compute measurement labels or snap steps with raw EastNorth Euclidean arithmetic.

## Coding Conventions

- Java 11+ language level
- 4-space indentation, UTF-8, CRLF line endings
- GPLv2+ license header on every source file
- Javadoc on all public classes and methods
- Package: `org.openstreetmap.josm.plugins.arkkisnappi`
- Preference keys: `arkki_snappi.step_metres`, `arkki_snappi.tags`, etc.
- No external dependencies beyond JOSM core APIs

## Key JOSM APIs

| Class                                                           | Purpose                                                    |
| --------------------------------------------------------------- | ---------------------------------------------------------- |
| `MapMode`                                                       | Base class for toolbar modes                               |
| `MapViewPaintable`                                              | Override `paint(Graphics2D, MapView, Bounds)` for overlays |
| `MapView.getEastNorth(Point)`                                   | Screen → world conversion                                  |
| `MapView.getPoint(EastNorth)`                                   | World → screen conversion                                  |
| `AddCommand`, `ChangeCommand`, `DeleteCommand`, `SequenceCommand` | Undo-safe data mutations                                   |
| `Config.getPref()`                                              | Preference access                                          |
| `ExtendedDialog`                                                | Dialog base class                                          |
| `IconToggleButton`                                              | Toolbar toggle button                                      |
| `MainApplication.getMap()`                                      | Access MapFrame                                            |

## When Editing This Plugin

1. Always read `local-storage/planning.md` for the full interaction design spec
2. All snapping math lives in `SnappiGrid` — keep `SnappiMode` focused on state + events
3. Self-intersection logic lives in `SnappiShrinkwrap` — keep it decoupled from mode state
4. Never write OSM data without wrapping in a JOSM Command
5. Test modifier keys: Shift (axis-lock), Ctrl (free mode), Alt (step cycle)
6. Test action keys: Enter (finish), C (halve step), V (double step), A (angle snap)
7. Keep grid rendering performant — clip to viewport, cap line count
