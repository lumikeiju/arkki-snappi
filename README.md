<!-- @format -->

# arkki-snappi

JOSM plugin for quick and accurate building footprint mapping with a 2-axis snap grid.

Requires JOSM 19439 or newer. The plugin adds a dedicated Snappi map mode with the `B`
shortcut and builds a distributable JAR at `build/dist/ArkkiSnappi.jar`.

## What It Does

**arkki-snappi** combines the rectangle-drawing workflow of [BuildingsTools](https://wiki.openstreetmap.org/wiki/JOSM/Plugins/BuildingsTools) with the edge-extrusion UX of [Extrude](https://josm.openstreetmap.de/wiki/Help/Action/Extrude), adding a snap grid that works in _both_ axes simultaneously.

### Workflow

1. **Click 1** — Place the anchor corner (red dot). A snap grid appears, oriented to a nearby building, selected way, or cardinal alignment.
2. **Move mouse** — Both X and Y snap independently to step multiples (e.g. 1 ft). A live rectangle preview follows your cursor.
3. **Click 2** — Commit the rectangle. Nodes, way, and default tags are created.
4. **Drag edge handles** — Extrude any edge outward/inward with the same step snapping.
5. **Click an edge** — Insert a new node at the nearest grid point, splitting the edge for partial extrusions.
6. **Enter** — Finish the current building and return to idle.
7. **Esc** — Cancel and return to idle.

Corners automatically snap to nearby existing nodes for shared-wall accuracy. After each extrusion and when a shape is finished, collinear nodes are simplified and any self-intersections from overlapping extrusions are resolved automatically (shrinkwrap). Hold `Shift` while releasing an extrusion or finishing to keep collinear nodes for that operation.

### Keyboard Shortcuts

| Key     | Effect                                 |
|---------|----------------------------------------|
| `B`     | Activate Snappi mode                   |
| `A`     | Toggle cardinal grid alignment         |
| `C`     | Halve the current snap step size       |
| `V`     | Double the current snap step size      |
| `Shift` | Lock snap to the dominant axis         |
| `Shift` | Bypass simplification for an operation |
| `Ctrl`  | Disable snapping (free position)       |
| `Alt`   | Cycle to the next step preset          |
| `Enter` | Finish the current building            |
| `Esc`   | Cancel current operation               |

## Settings

Available in **JOSM Preferences → Snappi** tab, or via **More tools → Snappi settings…**:

- **Step size** — Base snap increment (default: 1 ft / 0.3048 m), X and Y independently or linked
- **Display unit** — `ft` or `m`
- **Node snap radius** — Screen-space pixel radius for snapping corners to existing nodes (default: 15 px), so the real-world snap distance changes with zoom level
- **Max grid lines** — Per-axis cap to keep the overlay responsive on large zoom levels
- **Drag threshold** — Minimum cursor movement before a press becomes an extrude drag
- **Default tags** — Key/value pairs applied to new buildings (default: `building=yes`)
- **Tag presets** — One-click buttons for common tag sets
- **Auto-select** — Select newly created way (default: on)
- **Auto-simplify** — Remove collinear nodes after extrusions and when finishing (default: on; hold `Shift` to bypass once)
- **Auto-shrinkwrap** — Resolve self-intersecting polygons after extrusions and when finishing (default: on)
- **Winding order** — Counter-clockwise or clockwise node ordering
- **Color themes** — Blueprint (default), Satellite, Neon, Ink — or pick individual colors

## Building

Requires **Java 11+** (Java 21 recommended). No other prerequisites — the Gradle wrapper downloads everything else automatically.

```sh
# Run the test suite
.\gradlew.bat test          # Windows
./gradlew test              # macOS / Linux

# Build the plugin JAR
.\gradlew.bat build          # Windows
./gradlew build              # macOS / Linux
```

The build runs the test suite and produces the release JAR at `build/dist/ArkkiSnappi.jar`.

### Installing into JOSM

**Option A — Copy the JAR manually:**

```sh
copy build\dist\ArkkiSnappi.jar %APPDATA%\JOSM\plugins\ArkkiSnappi.jar
```

Restart JOSM, then enable the plugin in **Edit → Preferences → Plugins**.

**Option B — Local update site (hot-reload during development):**

The build also generates a local plugin list at `build/localDist/list`. In JOSM:

1. **Edit → Preferences → Plugins** (enable expert mode first)
2. Add this update site URL: `file:/<path-to-repo>/build/localDist/list`
3. Click **Update plugins**, find ArkkiSnappi-dev, enable it
4. After rebuilding, just restart JOSM — it picks up the latest JAR automatically

### Running JOSM with the plugin (one command)

```sh
.\gradlew.bat runJosm
```

This launches a JOSM instance with the plugin pre-loaded.

## Release Notes

- Package name: `org.openstreetmap.josm.plugins.arkkisnappi`
- Minimum JOSM version: `19439`
- Main class: `org.openstreetmap.josm.plugins.arkkisnappi.ArkkiSnappiPlugin`
- License: AGPL v3

## Credits

[arkki-snappi](https://github.com/Lumikeiju/arkki-snappi) by [Amy Bordenave](https://github.com/Lumikeiju).

## License

AGPL v3. See [LICENSE](LICENSE) for details.
