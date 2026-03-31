<!-- @format -->

# arkki-snappi

JOSM plugin for quick and accurate building footprint mapping with a 2-axis snap grid.

## What It Does

**arkki-snappi** combines the rectangle-drawing workflow of [BuildingsTools](https://wiki.openstreetmap.org/wiki/JOSM/Plugins/BuildingsTools) with the edge-extrusion UX of [Extrude](https://josm.openstreetmap.de/wiki/Help/Action/Extrude), adding a snap grid that works in _both_ axes simultaneously.

### Workflow

1. **Click 1** — Place the anchor corner (red dot). A snap grid appears.
2. **Move mouse** — Both X and Y snap independently to step multiples (e.g. 1 ft). A live rectangle preview follows your cursor.
3. **Click 2** — Commit the rectangle. Nodes, way, and default tags are created.
4. **Drag edge handles** — Extrude any edge outward/inward with the same step snapping.
5. **Esc** — Return to idle.

### Keyboard Modifiers

| Key     | Effect                                   |
|---------|------------------------------------------|
| `A`     | Toggle cardinal (N/S/E/W) grid alignment |
| `Shift` | Lock snap to the dominant axis only      |
| `Ctrl`  | Disable snapping (free position)         |
| `Alt`   | Cycle to the next step preset            |
| `Esc`   | Cancel current operation                 |

## Settings

Open via **More tools → Snappi settings…** or `Alt+Shift+B`:

- **Step size** — Base snap increment (default: 1 ft / 0.3048 m)
- **Display unit** — `ft` or `m`
- **Default tags** — Key/value pairs applied to new buildings (default: `building=yes`)
- **Tag presets** — One-click buttons for common tag sets
- **Auto-select** — Select newly created way (default: on)
- **Winding order** — Counter-clockwise or clockwise node ordering

## Building

Requires **Java 11+** (Java 21 recommended). No other prerequisites — the Gradle wrapper downloads everything else automatically.

```sh
# Build the plugin JAR
.\gradlew.bat build          # Windows
./gradlew build              # macOS / Linux
```

The JAR is produced at `build/dist/ArkkiSnappi.jar`.

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

## Credits

[arkki-snappi](https://github.com/Lumikeiju/arkki-snappi) by [Amy Bordenave](https://github.com/Lumikeiju).

## License

GPL v3+. See [LICENSE](LICENSE) for details.
