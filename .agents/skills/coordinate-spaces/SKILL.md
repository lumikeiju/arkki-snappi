---
name: coordinate-spaces
description: Rules for the three coordinate spaces (EastNorth, LatLon, Point) and Web Mercator projection correction in arkki-snappi geometry code. Use when writing or reviewing snapping, distance, grid-spacing, or node-placement code.
triggers:
  - coordinate system
  - projection
  - EastNorth
  - LatLon
  - snapping
  - grid spacing
  - real world distance
paths:
  - src/main/java/org/openstreetmap/josm/plugins/arkkisnappi/SnappiGrid.java
  - src/main/java/org/openstreetmap/josm/plugins/arkkisnappi/SnappiMode.java
  - src/main/java/org/openstreetmap/josm/plugins/arkkisnappi/SnappiShrinkwrap.java
  - src/main/java/org/openstreetmap/josm/plugins/arkkisnappi/ReferenceOrientationDetector.java
  - src/test/java/org/openstreetmap/josm/plugins/arkkisnappi/SnappiGridTest.java
  - src/test/java/org/openstreetmap/josm/plugins/arkkisnappi/SnappiShrinkwrapTest.java
---

# Coordinate Spaces — Never Mix Them

| Space     | Use                                        |
| --------- | ------------------------------------------ |
| EastNorth | **All geometry and snapping** (projected)  |
| LatLon    | OSM storage and projection-correction only |
| Point     | Screen pixels — hit-testing and rendering only |

**Mercator trap:** Web Mercator (EPSG:3857) inflates EastNorth vs real metres by `sec(lat)` — ~2× error at 60° lat. Raw EN arithmetic produces wrong measurements and grid spacing.

## Mandatory corrections

- Real-world length → `SnappiGrid.realWorldDistance(a, b)` (great-circle).
- Snap steps in EN units → `stepMetres * projectionScale` via `enStepU()` / `enStepV()` in `SnappiMode`.
- `projectionScale` is computed once at anchor time: `SnappiGrid.projectionScale(anchorEN)`.
- **Never** use raw EN Euclidean arithmetic for measurements or grid spacing.

## Local grid frame

```
u-axis = unit vector anchor→mouse (or reference edge first segment)
v-axis = u rotated 90° CCW
world  = anchor + uSnapped·uAxis + vSnapped·vAxis
(u, v) = (dot(world-anchor, uAxis), dot(world-anchor, vAxis))
```

## Conversions

```java
mv.getEastNorth(point)                                        // screen → world
mv.getPoint(en)                                               // world → screen
ProjectionRegistry.getProjection().eastNorth2latlon(en)       // EN → LatLon (node creation)
```

Tests use pure EastNorth arithmetic — no JOSM/MapView instance required.
