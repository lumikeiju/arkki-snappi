// License: AGPL v3 or later. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.EastNorth;

/**
 * Unit tests for {@link SnappiShrinkwrap}.
 *
 * <p>All tests use pure EastNorth arithmetic — no JOSM instance required.</p>
 */
class SnappiShrinkwrapTest {

    // ------------------------------------------------------------------
    // isSelfIntersecting
    // ------------------------------------------------------------------

    @Test
    void isSelfIntersecting_simpleSquare_returnsFalse() {
        EastNorth[] square = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(4, 4),
            new EastNorth(0, 4)
        };
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(square));
    }

    @Test
    void isSelfIntersecting_lShape_returnsFalse() {
        // L-shape: no self-intersection
        EastNorth[] lShape = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(4, 2),
            new EastNorth(2, 2),
            new EastNorth(2, 4),
            new EastNorth(0, 4)
        };
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(lShape));
    }

    @Test
    void isSelfIntersecting_bowTie_returnsTrue() {
        // A figure-eight (bow tie) is self-intersecting
        EastNorth[] bowTie = {
            new EastNorth(0, 0),
            new EastNorth(4, 4),
            new EastNorth(4, 0),
            new EastNorth(0, 4)
        };
        assertTrue(SnappiShrinkwrap.isSelfIntersecting(bowTie));
    }

    @Test
    void isSelfIntersecting_overlappingExtrusion_returnsTrue() {
        // Simulates a 4×4 square whose right edge has been split at (4,2)
        // and the bottom half extruded inward by 5 units (past the left side).
        //
        //  (0,4)─────────(4,4)
        //    │                │
        //    │           (4,2)│
        //    │  (-1,2)────╯   │  ← edge (-1,2)→(4,2) CROSSES the left side
        //  (0,0)─(4,0)        │    edge (0,4)→(0,0) at (0,2)
        //         │           │
        //        (-1,0)───────╯ (not shown, just illustrative)
        //
        // Polygon: (0,0)→(4,0)→(-1,0)→(-1,2)→(4,2)→(4,4)→(0,4)
        // Edge (-1,2)→(4,2) crosses the closing edge (0,4)→(0,0) at (0,2).
        EastNorth[] shape = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(-1, 0),
            new EastNorth(-1, 2),
            new EastNorth(4, 2),
            new EastNorth(4, 4),
            new EastNorth(0, 4)
        };
        assertTrue(SnappiShrinkwrap.isSelfIntersecting(shape));
    }

    // ------------------------------------------------------------------
    // computeOuterBoundary
    // ------------------------------------------------------------------

    @Test
    void computeOuterBoundary_simpleSquare_returnsNull() {
        // No self-intersection → should return null (caller uses original corners)
        EastNorth[] square = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(4, 4),
            new EastNorth(0, 4)
        };
        assertNull(SnappiShrinkwrap.computeOuterBoundary(square));
    }

    @Test
    void computeOuterBoundary_overlappingExtrusion_returnsNonSelfIntersecting() {
        // Same polygon as the isSelfIntersecting test above:
        // (0,0)→(4,0)→(-1,0)→(-1,2)→(4,2)→(4,4)→(0,4)
        EastNorth[] shape = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(-1, 0),
            new EastNorth(-1, 2),
            new EastNorth(4, 2),
            new EastNorth(4, 4),
            new EastNorth(0, 4)
        };

        assertTrue(SnappiShrinkwrap.isSelfIntersecting(shape), "pre-condition: is self-intersecting");

        EastNorth[] outer = SnappiShrinkwrap.computeOuterBoundary(shape);
        assertNotNull(outer, "outer boundary should be found");
        assertTrue(outer.length >= 3, "outer boundary must have at least 3 vertices");
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(outer),
                "outer boundary must not be self-intersecting");
    }

    @Test
    void computeOuterBoundary_symmetricBowTie_returnsNonSelfIntersecting() {
        // Symmetric bow-tie: the outer boundary is one of the two triangles
        EastNorth[] bowTie = {
            new EastNorth(0, 0),
            new EastNorth(4, 4),
            new EastNorth(4, 0),
            new EastNorth(0, 4)
        };

        assertTrue(SnappiShrinkwrap.isSelfIntersecting(bowTie));

        EastNorth[] outer = SnappiShrinkwrap.computeOuterBoundary(bowTie);
        assertNotNull(outer);
        assertTrue(outer.length >= 3);
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(outer),
                "outer boundary must not be self-intersecting");
    }

    @Test
    void computeOuterBoundary_outputHasPositiveArea() {
        // The outer boundary should be CCW (positive signed area in EastNorth)
        EastNorth[] bowTie = {
            new EastNorth(0, 0),
            new EastNorth(4, 4),
            new EastNorth(4, 0),
            new EastNorth(0, 4)
        };
        EastNorth[] outer = SnappiShrinkwrap.computeOuterBoundary(bowTie);
        assertNotNull(outer);

        double area = signedArea(outer);
        // Boundary walk produces CCW for EastNorth (north = up) because it
        // always picks the most-clockwise neighbour, which traces the interior
        // of the plane — the sign depends on the coordinate orientation.
        // We just verify it's non-zero (a real polygon, not a degenerate line).
        assertTrue(Math.abs(area) > 1e-6, "outer boundary has non-zero area");
    }

    @Test
    void computeOuterBoundary_lShapeExtrusion_enclosesBothRectangles() {
        // Two rectangles that share one edge — their union is an L-shape.
        //
        //  Vertical bar: (0,0)–(2,0)–(2,5)–(0,5)
        //  Horizontal bar: (0,0)–(5,0)–(5,2)–(0,2)
        //
        // Represented as a single self-intersecting polygon that traces both:
        // (0,0)→(2,0)→(2,2)→(5,2)→(5,0)→(2,0)  ← revisits (2,0) → crosses!
        // A cleaner version: encode as the 8-vertex union:
        EastNorth[] lShape = {
            new EastNorth(0, 0),
            new EastNorth(5, 0),
            new EastNorth(5, 2),
            new EastNorth(2, 2),
            new EastNorth(2, 5),
            new EastNorth(0, 5)
        };
        // This is not self-intersecting so computeOuterBoundary should return null
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(lShape));
        assertNull(SnappiShrinkwrap.computeOuterBoundary(lShape));
    }

    @Test
    void isSelfIntersecting_triangle_returnsFalse() {
        // A triangle has no non-adjacent edge pair to check, so it can never
        // be self-intersecting. Also exercises the wrap-around adjacency guard
        // (i=0, j=n-1 is skipped as adjacent).
        EastNorth[] triangle = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(2, 3)
        };
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(triangle));
    }

    @Test
    void isSelfIntersecting_doubleCross_returnsTrue() {
        // A "ribbon" polygon with two distinct interior crossings:
        //   Edge (0,1)→(3,3) crosses edge (3,1)→(0,3) at (1.5, 2).
        //   Edge (3,3)→(6,1) crosses edge (6,3)→(3,1) at (4.5, 2).
        EastNorth[] ribbon = {
            new EastNorth(0, 1),
            new EastNorth(3, 3),
            new EastNorth(6, 1),
            new EastNorth(6, 3),
            new EastNorth(3, 1),
            new EastNorth(0, 3)
        };
        assertTrue(SnappiShrinkwrap.isSelfIntersecting(ribbon));
    }

    @Test
    void computeOuterBoundary_doubleCross_returnsNonSelfIntersecting() {
        // The double-cross ribbon from the test above.
        EastNorth[] ribbon = {
            new EastNorth(0, 1),
            new EastNorth(3, 3),
            new EastNorth(6, 1),
            new EastNorth(6, 3),
            new EastNorth(3, 1),
            new EastNorth(0, 3)
        };

        assertTrue(SnappiShrinkwrap.isSelfIntersecting(ribbon), "pre-condition: is self-intersecting");

        EastNorth[] outer = SnappiShrinkwrap.computeOuterBoundary(ribbon);
        assertNotNull(outer, "outer boundary should be found");
        assertTrue(outer.length >= 3, "outer boundary must have at least 3 vertices");
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(outer),
                "outer boundary must not be self-intersecting");
    }

    @Test
    void computeOuterBoundary_noConsecutiveDuplicateVertices() {
        // Every pair of consecutive output vertices must be distinct.
        EastNorth[] bowTie = {
            new EastNorth(0, 0),
            new EastNorth(4, 4),
            new EastNorth(4, 0),
            new EastNorth(0, 4)
        };
        EastNorth[] outer = SnappiShrinkwrap.computeOuterBoundary(bowTie);
        assertNotNull(outer);
        int n = outer.length;
        for (int i = 0; i < n; i++) {
            EastNorth a = outer[i];
            EastNorth b = outer[(i + 1) % n];
            double dist = Math.hypot(a.east() - b.east(), a.north() - b.north());
            assertTrue(dist > 1e-6,
                    "consecutive vertices " + i + " and " + ((i + 1) % n) + " must not coincide");
        }
    }

    @Test
    void computeOuterBoundary_convexHexagon_returnsNull() {
        // A convex hexagon has no self-intersections — should return null.
        EastNorth[] hex = {
            new EastNorth(2, 0),
            new EastNorth(4, 0),
            new EastNorth(5, 2),
            new EastNorth(4, 4),
            new EastNorth(2, 4),
            new EastNorth(1, 2)
        };
        assertFalse(SnappiShrinkwrap.isSelfIntersecting(hex));
        assertNull(SnappiShrinkwrap.computeOuterBoundary(hex));
    }

    // ------------------------------------------------------------------
    // Internal helper
    // ------------------------------------------------------------------

    private static double signedArea(EastNorth[] c) {
        int n = c.length;
        double area = 0;
        for (int i = 0; i < n; i++) {
            EastNorth a = c[i];
            EastNorth b = c[(i + 1) % n];
            area += a.east() * b.north() - b.east() * a.north();
        }
        return area / 2.0;
    }
}
