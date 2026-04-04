// License: AGPL v3 or later. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.EastNorth;

/**
 * Unit tests for the pure-geometry methods in {@link SnappiGrid}.
 *
 * <p>These tests require no JOSM instance — all tested methods operate
 * entirely in EastNorth (projected-metre) space with no MapView dependency.</p>
 */
class SnappiGridTest {

    private static final double EPS = 1e-9;

    // ------------------------------------------------------------------
    // computeAxes
    // ------------------------------------------------------------------

    @Test
    void computeAxes_eastward_givesUEastVNorth() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(10, 0);
        EastNorth[] axes = SnappiGrid.computeAxes(anchor, mouse);

        assertEquals(1.0,  axes[0].east(),  EPS, "u.east");
        assertEquals(0.0,  axes[0].north(), EPS, "u.north");
        assertEquals(0.0,  axes[1].east(),  EPS, "v.east");
        assertEquals(1.0,  axes[1].north(), EPS, "v.north");
    }

    @Test
    void computeAxes_northward_givesUNorthVWest() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(0, 5);
        EastNorth[] axes = SnappiGrid.computeAxes(anchor, mouse);

        assertEquals(0.0,  axes[0].east(),  EPS, "u.east");
        assertEquals(1.0,  axes[0].north(), EPS, "u.north");
        assertEquals(-1.0, axes[1].east(),  EPS, "v.east");
        assertEquals(0.0,  axes[1].north(), EPS, "v.north");
    }

    @Test
    void computeAxes_coincidentPoints_returnsDefault() {
        EastNorth anchor = new EastNorth(5, 5);
        EastNorth[] axes = SnappiGrid.computeAxes(anchor, anchor);

        assertEquals(1.0, axes[0].east(),  EPS);
        assertEquals(0.0, axes[0].north(), EPS);
        assertEquals(0.0, axes[1].east(),  EPS);
        assertEquals(1.0, axes[1].north(), EPS);
    }

    @Test
    void computeAxes_axesAreUnitVectors() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(3, 4); // len = 5
        EastNorth[] axes = SnappiGrid.computeAxes(anchor, mouse);

        double uLen = Math.hypot(axes[0].east(), axes[0].north());
        double vLen = Math.hypot(axes[1].east(), axes[1].north());
        assertEquals(1.0, uLen, EPS, "u is unit length");
        assertEquals(1.0, vLen, EPS, "v is unit length");
    }

    @Test
    void computeAxes_vAxisIsPerpendicularToU() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(3, 4);
        EastNorth[] axes = SnappiGrid.computeAxes(anchor, mouse);

        double dot = axes[0].east() * axes[1].east() + axes[0].north() * axes[1].north();
        assertEquals(0.0, dot, EPS, "u·v = 0 (perpendicular)");
    }

    // ------------------------------------------------------------------
    // snapScalar
    // ------------------------------------------------------------------

    @Test
    void snapScalar_exactMultiple_returnsUnchanged() {
        assertEquals(3.0, SnappiGrid.snapScalar(3.0, 1.0), EPS);
        assertEquals(6.0, SnappiGrid.snapScalar(6.0, 2.0), EPS);
    }

    @Test
    void snapScalar_roundsToNearest() {
        assertEquals(2.0, SnappiGrid.snapScalar(2.3, 1.0), EPS);
        assertEquals(3.0, SnappiGrid.snapScalar(2.7, 1.0), EPS);
    }

    @Test
    void snapScalar_nearZeroReturnsOneStep() {
        // A result of 0 is not allowed — should return ±1 step
        assertEquals(1.0,  SnappiGrid.snapScalar(0.1, 1.0), EPS);
        assertEquals(-1.0, SnappiGrid.snapScalar(-0.1, 1.0), EPS);
    }

    @Test
    void snapScalar_negativeDistance_snapsNegative() {
        assertEquals(-2.0, SnappiGrid.snapScalar(-2.3, 1.0), EPS);
        assertEquals(-3.0, SnappiGrid.snapScalar(-2.7, 1.0), EPS);
    }

    // ------------------------------------------------------------------
    // snap
    // ------------------------------------------------------------------

    @Test
    void snap_freeMode_returnsMouse() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(3.7, 2.2);
        EastNorth u = new EastNorth(1, 0);
        EastNorth v = new EastNorth(0, 1);
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, 1.0, 1.0, 0, true);
        assertEquals(3.7, result.east(),  EPS);
        assertEquals(2.2, result.north(), EPS);
    }

    @Test
    void snap_cardinalAxes_snapsToGrid() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(3.7, 2.2);
        EastNorth u = new EastNorth(1, 0);
        EastNorth v = new EastNorth(0, 1);
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, 1.0, 1.0, 0, false);
        assertEquals(4.0, result.east(),  EPS, "snaps east to 4");
        assertEquals(2.0, result.north(), EPS, "snaps north to 2");
    }

    @Test
    void snap_axisLockU_vBecomesZero() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(3.7, 2.2);
        EastNorth u = new EastNorth(1, 0);
        EastNorth v = new EastNorth(0, 1);
        // lockAxis=1 means snap u only, v=0
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, 1.0, 1.0, 1, false);
        assertEquals(4.0, result.east(),  EPS);
        assertEquals(0.0, result.north(), EPS, "v locked to 0");
    }

    @Test
    void snap_diagonalAxes_snapsCorrectly() {
        // u-axis at 45°, step 1 unit along u
        double inv = 1.0 / Math.sqrt(2);
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth u = new EastNorth(inv, inv);
        EastNorth v = new EastNorth(-inv, inv);
        // Mouse exactly 2 steps along u, 1 step along v
        double uStep = 1.0, vStep = 1.0;
        EastNorth mouse = new EastNorth(
                2 * uStep * u.east() + 1 * vStep * v.east(),
                2 * uStep * u.north() + 1 * vStep * v.north());
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, uStep, vStep, 0, false);
        assertEquals(mouse.east(),  result.east(),  1e-9);
        assertEquals(mouse.north(), result.north(), 1e-9);
    }

    // ------------------------------------------------------------------
    // computeCorners — winding order in all four drag quadrants
    // ------------------------------------------------------------------

    /** Signed area via the shoelace formula; positive = CCW in EastNorth. */
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

    private static EastNorth anchor() { return new EastNorth(0, 0); }
    private static EastNorth uAxis()  { return new EastNorth(1, 0); }
    private static EastNorth vAxis()  { return new EastNorth(0, 1); }

    @Test
    void computeCorners_quadrant1_ccwRequested_isCcw() {
        // uLen > 0, vLen > 0
        EastNorth target = new EastNorth(3, 2);
        EastNorth[] corners = SnappiGrid.computeCorners(anchor(), target, uAxis(), vAxis(), true);
        assertTrue(signedArea(corners) > 0, "CCW → positive signed area");
    }

    @Test
    void computeCorners_quadrant2_ccwRequested_isCcw() {
        // uLen < 0, vLen > 0  (mixed-sign: this was the winding bug)
        EastNorth target = new EastNorth(-3, 2);
        EastNorth[] corners = SnappiGrid.computeCorners(anchor(), target, uAxis(), vAxis(), true);
        assertTrue(signedArea(corners) > 0, "CCW → positive signed area");
    }

    @Test
    void computeCorners_quadrant3_ccwRequested_isCcw() {
        // uLen < 0, vLen < 0
        EastNorth target = new EastNorth(-3, -2);
        EastNorth[] corners = SnappiGrid.computeCorners(anchor(), target, uAxis(), vAxis(), true);
        assertTrue(signedArea(corners) > 0, "CCW → positive signed area");
    }

    @Test
    void computeCorners_quadrant4_ccwRequested_isCcw() {
        // uLen > 0, vLen < 0 (mixed-sign: this was the winding bug)
        EastNorth target = new EastNorth(3, -2);
        EastNorth[] corners = SnappiGrid.computeCorners(anchor(), target, uAxis(), vAxis(), true);
        assertTrue(signedArea(corners) > 0, "CCW → positive signed area");
    }

    @Test
    void computeCorners_quadrant1_cwRequested_isCw() {
        EastNorth target = new EastNorth(3, 2);
        EastNorth[] corners = SnappiGrid.computeCorners(anchor(), target, uAxis(), vAxis(), false);
        assertTrue(signedArea(corners) < 0, "CW → negative signed area");
    }

    @Test
    void computeCorners_quadrant4_cwRequested_isCw() {
        // Mixed-sign — previously could produce wrong winding
        EastNorth target = new EastNorth(3, -2);
        EastNorth[] corners = SnappiGrid.computeCorners(anchor(), target, uAxis(), vAxis(), false);
        assertTrue(signedArea(corners) < 0, "CW → negative signed area");
    }

    @Test
    void computeCorners_returnsExactlyFourCorners() {
        EastNorth target = new EastNorth(5, 3);
        EastNorth[] corners = SnappiGrid.computeCorners(anchor(), target, uAxis(), vAxis(), true);
        assertEquals(4, corners.length);
    }

    @Test
    void computeCorners_corner0IsAnchor() {
        EastNorth a = new EastNorth(7, 3);
        EastNorth target = new EastNorth(12, 8);
        EastNorth[] corners = SnappiGrid.computeCorners(a, target, uAxis(), vAxis(), true);
        assertEquals(a.east(),  corners[0].east(),  EPS);
        assertEquals(a.north(), corners[0].north(), EPS);
    }

    @Test
    void computeCorners_corner2IsTarget() {
        EastNorth a = new EastNorth(0, 0);
        EastNorth target = new EastNorth(4, 6);
        EastNorth[] corners = SnappiGrid.computeCorners(a, target, uAxis(), vAxis(), true);
        assertEquals(target.east(),  corners[2].east(),  EPS);
        assertEquals(target.north(), corners[2].north(), EPS);
    }

    // ------------------------------------------------------------------
    // computeOutwardNormal
    // ------------------------------------------------------------------

    @Test
    void computeOutwardNormal_simpleSquare_bottomEdgePointsSouth() {
        // Square: (0,0) → (1,0) → (1,1) → (0,1) CCW
        EastNorth[] sq = {
            new EastNorth(0, 0),
            new EastNorth(1, 0),
            new EastNorth(1, 1),
            new EastNorth(0, 1)
        };
        // Edge 0: (0,0)→(1,0) — outward normal should point south (0,-1)
        EastNorth n0 = SnappiGrid.computeOutwardNormal(sq, 0);
        assertEquals(0.0,  n0.east(),  EPS);
        assertEquals(-1.0, n0.north(), EPS);
    }

    @Test
    void computeOutwardNormal_isUnitVector() {
        EastNorth[] sq = {
            new EastNorth(0, 0),
            new EastNorth(2, 0),
            new EastNorth(2, 3),
            new EastNorth(0, 3)
        };
        for (int i = 0; i < sq.length; i++) {
            EastNorth n = SnappiGrid.computeOutwardNormal(sq, i);
            double len = Math.hypot(n.east(), n.north());
            assertEquals(1.0, len, EPS, "edge " + i + " normal is unit length");
        }
    }

    @Test
    void computeOutwardNormal_pointsAwayFromCentroid() {
        // Centroid of unit square is (0.5, 0.5)
        EastNorth[] sq = {
            new EastNorth(0, 0),
            new EastNorth(1, 0),
            new EastNorth(1, 1),
            new EastNorth(0, 1)
        };
        for (int i = 0; i < sq.length; i++) {
            EastNorth n = SnappiGrid.computeOutwardNormal(sq, i);
            EastNorth a = sq[i];
            EastNorth b = sq[(i + 1) % sq.length];
            double midE = (a.east() + b.east()) / 2.0;
            double midN = (a.north() + b.north()) / 2.0;
            // Normal should point away from centroid (0.5, 0.5)
            double toCentE = 0.5 - midE;
            double toCentN = 0.5 - midN;
            double dot = n.east() * toCentE + n.north() * toCentN;
            assertTrue(dot < 0, "edge " + i + " normal points away from centroid");
        }
    }

    // ------------------------------------------------------------------
    // nearestGridPointOnEdge
    // ------------------------------------------------------------------

    @Test
    void nearestGridPointOnEdge_midpoint_returnsGridPoint() {
        EastNorth[] corners = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(4, 4),
            new EastNorth(0, 4)
        };
        EastNorth click = new EastNorth(2.1, 0); // near midpoint of bottom edge
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth uAxis  = new EastNorth(1, 0);
        EastNorth vAxis  = new EastNorth(0, 1);
        // step=1 → nearest grid point at dist=2 from corner (0,0)
        EastNorth pt = SnappiGrid.nearestGridPointOnEdge(
                click, corners, 0, anchor, uAxis, vAxis, 1.0, 1.0);
        assertNotNull(pt);
        assertEquals(2.0, pt.east(),  EPS);
        assertEquals(0.0, pt.north(), EPS);
    }

    @Test
    void nearestGridPointOnEdge_tooCloseToEndpoint_returnsNull() {
        EastNorth[] corners = {
            new EastNorth(0, 0),
            new EastNorth(1, 0), // edge length = 1, step = 1
            new EastNorth(1, 1),
            new EastNorth(0, 1)
        };
        // With step=1 and edge length=1, the only candidate (dist=1) is at the
        // end vertex — should be rejected as too close to an existing node.
        EastNorth click = new EastNorth(0.5, 0);
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth uAxis  = new EastNorth(1, 0);
        EastNorth vAxis  = new EastNorth(0, 1);
        EastNorth pt = SnappiGrid.nearestGridPointOnEdge(
                click, corners, 0, anchor, uAxis, vAxis, 1.0, 1.0);
        // Only solution (n=1, dist=1.0) == edgeLen → rejected
        assertTrue(pt == null, "should return null when only candidate is at endpoint");
    }

    @Test
    void nearestGridPointOnEdge_vAlignedEdge_usesVStep() {
        // Right edge (1): (4,0)→(4,6) is aligned with the v-axis.
        // uDot ≈ 0, so the method should use stepV for snapping.
        EastNorth[] corners = {
            new EastNorth(0, 0),
            new EastNorth(4, 0),
            new EastNorth(4, 6),
            new EastNorth(0, 6)
        };
        EastNorth click  = new EastNorth(4, 3.2); // near v=3.2 on right edge
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth uAxis  = new EastNorth(1, 0);
        EastNorth vAxis  = new EastNorth(0, 1);
        // stepU=1, stepV=2 → snaps 3.2 to nearest multiple of 2 → 4.0
        // dist=4, edgeLen=6 → 4 is not at an endpoint → returns (4, 4)
        EastNorth pt = SnappiGrid.nearestGridPointOnEdge(
                click, corners, 1, anchor, uAxis, vAxis, 1.0, 2.0);
        assertNotNull(pt);
        assertEquals(4.0, pt.east(),  EPS);
        assertEquals(4.0, pt.north(), EPS);
    }

    @Test
    void nearestGridPointOnEdge_multipleGridPoints_picksNearest() {
        // Bottom edge (0): (0,0)→(6,0), step=1. Click at 3.8 → nearest grid point is 4.
        EastNorth[] corners = {
            new EastNorth(0, 0),
            new EastNorth(6, 0),
            new EastNorth(6, 6),
            new EastNorth(0, 6)
        };
        EastNorth click  = new EastNorth(3.8, 0);
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth uAxis  = new EastNorth(1, 0);
        EastNorth vAxis  = new EastNorth(0, 1);
        EastNorth pt = SnappiGrid.nearestGridPointOnEdge(
                click, corners, 0, anchor, uAxis, vAxis, 1.0, 1.0);
        assertNotNull(pt);
        assertEquals(4.0, pt.east(),  EPS);
        assertEquals(0.0, pt.north(), EPS);
    }

    @Test
    void nearestGridPointOnEdge_zeroLengthEdge_returnsNull() {
        // Degenerate edge (both vertices coincide) should return null.
        EastNorth[] corners = {
            new EastNorth(0, 0),
            new EastNorth(0, 0), // degenerate
            new EastNorth(4, 0),
            new EastNorth(4, 4)
        };
        EastNorth click  = new EastNorth(0, 0);
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth uAxis  = new EastNorth(1, 0);
        EastNorth vAxis  = new EastNorth(0, 1);
        EastNorth pt = SnappiGrid.nearestGridPointOnEdge(
                click, corners, 0, anchor, uAxis, vAxis, 1.0, 1.0);
        assertNull(pt, "zero-length edge should return null");
    }

    // ------------------------------------------------------------------
    // snap — additional axis-lock and boundary cases
    // ------------------------------------------------------------------

    @Test
    void snap_axisLockV_uBecomesZero() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(3.7, 2.2);
        EastNorth u = new EastNorth(1, 0);
        EastNorth v = new EastNorth(0, 1);
        // lockAxis=2 means snap v only; u component is forced to 0
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, 1.0, 1.0, 2, false);
        assertEquals(0.0, result.east(),  EPS, "u locked to 0");
        assertEquals(2.0, result.north(), EPS, "v snaps to 2");
    }

    @Test
    void snap_verySmallInput_returnsSingleStep() {
        // Both u and v projections are near zero → must return ±1 step
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(0.01, 0.01);
        EastNorth u = new EastNorth(1, 0);
        EastNorth v = new EastNorth(0, 1);
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, 1.0, 1.0, 0, false);
        assertEquals(1.0, result.east(),  EPS, "u forced to +1 step");
        assertEquals(1.0, result.north(), EPS, "v forced to +1 step");
    }

    @Test
    void snap_negativeSmallInput_returnsNegativeSingleStep() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(-0.01, -0.01);
        EastNorth u = new EastNorth(1, 0);
        EastNorth v = new EastNorth(0, 1);
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, 1.0, 1.0, 0, false);
        assertEquals(-1.0, result.east(),  EPS, "u forced to -1 step");
        assertEquals(-1.0, result.north(), EPS, "v forced to -1 step");
    }

    // ------------------------------------------------------------------
    // computeOutwardNormal — additional edges
    // ------------------------------------------------------------------

    @Test
    void computeOutwardNormal_topEdgePointsNorth() {
        // CCW square: (0,0) → (1,0) → (1,1) → (0,1)
        // Edge 2: (1,1)→(0,1) — outward normal should point north (0,+1)
        EastNorth[] sq = {
            new EastNorth(0, 0),
            new EastNorth(1, 0),
            new EastNorth(1, 1),
            new EastNorth(0, 1)
        };
        EastNorth n2 = SnappiGrid.computeOutwardNormal(sq, 2);
        assertEquals(0.0, n2.east(),  EPS, "edge 2 normal.east");
        assertEquals(1.0, n2.north(), EPS, "edge 2 normal.north");
    }

    @Test
    void computeOutwardNormal_allFourEdges_oppositeEdgesAreAntiparallel() {
        // For a rectangle, edge 0 (bottom) and edge 2 (top) normals must be
        // exact opposites, and the same for edges 1 (right) and 3 (left).
        EastNorth[] rect = {
            new EastNorth(0, 0),
            new EastNorth(3, 0),
            new EastNorth(3, 2),
            new EastNorth(0, 2)
        };
        EastNorth n0 = SnappiGrid.computeOutwardNormal(rect, 0);
        EastNorth n2 = SnappiGrid.computeOutwardNormal(rect, 2);
        EastNorth n1 = SnappiGrid.computeOutwardNormal(rect, 1);
        EastNorth n3 = SnappiGrid.computeOutwardNormal(rect, 3);

        assertEquals(-n0.east(),  n2.east(),  EPS, "edge 0 and 2 normals are opposite");
        assertEquals(-n0.north(), n2.north(), EPS);
        assertEquals(-n1.east(),  n3.east(),  EPS, "edge 1 and 3 normals are opposite");
        assertEquals(-n1.north(), n3.north(), EPS);
    }

    // ------------------------------------------------------------------
    // midpoint
    // ------------------------------------------------------------------

    @Test
    void midpoint_returnsCenter() {
        EastNorth a = new EastNorth(2, 4);
        EastNorth b = new EastNorth(6, 10);
        EastNorth mid = SnappiGrid.midpoint(a, b);
        assertEquals(4.0, mid.east(),  EPS);
        assertEquals(7.0, mid.north(), EPS);
    }

    @Test
    void midpoint_coincidentPoints_returnsSamePoint() {
        EastNorth a = new EastNorth(5, 7);
        EastNorth mid = SnappiGrid.midpoint(a, a);
        assertEquals(5.0, mid.east(),  EPS);
        assertEquals(7.0, mid.north(), EPS);
    }

    // ------------------------------------------------------------------
    // distToSegmentSq
    // ------------------------------------------------------------------

    @Test
    void distToSegmentSq_pointOnSegment_returnsZero() {
        Point a = new Point(0, 0);
        Point b = new Point(10, 0);
        Point p = new Point(5, 0);
        assertEquals(0.0, SnappiGrid.distToSegmentSq(p, a, b), EPS);
    }

    @Test
    void distToSegmentSq_pointAboveSegment_returnsSquaredDistance() {
        Point a = new Point(0, 0);
        Point b = new Point(10, 0);
        Point p = new Point(5, 3);
        assertEquals(9.0, SnappiGrid.distToSegmentSq(p, a, b), EPS);
    }

    @Test
    void distToSegmentSq_pointBeyondEndA_returnsDistToA() {
        Point a = new Point(0, 0);
        Point b = new Point(10, 0);
        Point p = new Point(-3, 4); // beyond A
        // Closest point is A at (0,0); distance = sqrt(9+16)=5, sq=25
        assertEquals(25.0, SnappiGrid.distToSegmentSq(p, a, b), EPS);
    }

    @Test
    void distToSegmentSq_pointBeyondEndB_returnsDistToB() {
        Point a = new Point(0, 0);
        Point b = new Point(10, 0);
        Point p = new Point(13, 4); // beyond B
        // Closest point is B at (10,0); distance = sqrt(9+16)=5, sq=25
        assertEquals(25.0, SnappiGrid.distToSegmentSq(p, a, b), EPS);
    }

    @Test
    void distToSegmentSq_degenerateSegment_returnsDistToPoint() {
        // Both endpoints coincide
        Point a = new Point(5, 5);
        Point b = new Point(5, 5);
        Point p = new Point(8, 9);
        // Distance to (5,5): sqrt(9+16)=5, sq=25
        assertEquals(25.0, SnappiGrid.distToSegmentSq(p, a, b), EPS);
    }

    @Test
    void distToSegmentSq_diagonalSegment() {
        Point a = new Point(0, 0);
        Point b = new Point(10, 10);
        Point p = new Point(0, 10);
        // Perpendicular distance from (0,10) to the line y=x is
        // |0 - 10| / sqrt(2) = 10/sqrt(2), sq = 50
        assertEquals(50.0, SnappiGrid.distToSegmentSq(p, a, b), EPS);
    }

    // ------------------------------------------------------------------
    // snap — independent u/v step sizes
    // ------------------------------------------------------------------

    @Test
    void snap_independentSteps_snapsEachAxisIndependently() {
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth mouse  = new EastNorth(2.3, 3.7);
        EastNorth u = new EastNorth(1, 0);
        EastNorth v = new EastNorth(0, 1);
        // stepU=1, stepV=2 → u snaps to 2, v snaps to 4
        EastNorth result = SnappiGrid.snap(mouse, anchor, u, v, 1.0, 2.0, 0, false);
        assertEquals(2.0, result.east(),  EPS, "u snaps to 2 (step 1)");
        assertEquals(4.0, result.north(), EPS, "v snaps to 4 (step 2)");
    }

    // ------------------------------------------------------------------
    // computeCorners — diagonal axes
    // ------------------------------------------------------------------

    @Test
    void computeCorners_diagonalAxes_preservesWindingOrder() {
        double inv = 1.0 / Math.sqrt(2);
        EastNorth u = new EastNorth(inv, inv);
        EastNorth v = new EastNorth(-inv, inv);
        EastNorth anchor = new EastNorth(0, 0);
        // Target 3 steps along u, 2 steps along v
        EastNorth target = new EastNorth(
                3 * u.east() + 2 * v.east(),
                3 * u.north() + 2 * v.north());
        EastNorth[] corners = SnappiGrid.computeCorners(anchor, target, u, v, true);
        assertTrue(signedArea(corners) > 0, "CCW winding with diagonal axes");
    }

    @Test
    void computeCorners_diagonalAxes_cwRequested() {
        double inv = 1.0 / Math.sqrt(2);
        EastNorth u = new EastNorth(inv, inv);
        EastNorth v = new EastNorth(-inv, inv);
        EastNorth anchor = new EastNorth(0, 0);
        EastNorth target = new EastNorth(
                3 * u.east() + 2 * v.east(),
                3 * u.north() + 2 * v.north());
        EastNorth[] corners = SnappiGrid.computeCorners(anchor, target, u, v, false);
        assertTrue(signedArea(corners) < 0, "CW winding with diagonal axes");
    }

    // ------------------------------------------------------------------
    // snapScalar — additional edge cases
    // ------------------------------------------------------------------

    @Test
    void snapScalar_fractionalStep_snapsCorrectly() {
        // Step = 0.3048 (1 ft in metres)
        double step = 0.3048;
        // 0.9144 = 3 ft exactly
        assertEquals(0.9144, SnappiGrid.snapScalar(0.9, step), 1e-6);
    }

    @Test
    void snapScalar_exactlyHalfStep_snapsUp() {
        // 0.5 is exactly halfway between 0 and 1; Math.round rounds up
        assertEquals(1.0, SnappiGrid.snapScalar(0.5, 1.0), EPS);
    }
}
