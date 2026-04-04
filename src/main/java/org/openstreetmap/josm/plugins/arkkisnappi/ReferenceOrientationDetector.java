// License: AGPL v3 or later. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import java.awt.Point;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

/**
 * Detects a reference orientation for the snap grid axes at anchor placement.
 *
 * <p>Priority (highest to lowest):</p>
 * <ol>
 *   <li>Cardinal angle-snap (when {@code angleSnap} is {@code true}).</li>
 *   <li>The first edge of a currently selected way in the data set.</li>
 *   <li>The first edge of the nearest closed way (building) near the click point.</li>
 * </ol>
 *
 * <p>When none of the above applies the result reports
 * {@link Result#hasReferenceOrientation} as {@code false} and the axes are
 * {@code null}, indicating that the caller should use 3-click (freehand) mode.</p>
 *
 * <p>Extracting this logic from {@link SnappiMode} keeps the state machine
 * focused on event handling and makes orientation detection independently
 * unit-testable.</p>
 *
 * @author Lumikeiju
 * @see SnappiMode
 */
public final class ReferenceOrientationDetector {

    private ReferenceOrientationDetector() {
        // utility class — no instances
    }

    // ==================================================================
    // Result type
    // ==================================================================

    /**
     * Immutable result returned by {@link #detect}.
     */
    public static final class Result {

        /** True if a reference orientation was found and the axes are valid. */
        public final boolean hasReferenceOrientation;

        /** U-axis unit vector, or {@code null} if no orientation was found. */
        public final EastNorth uAxis;

        /** V-axis unit vector, or {@code null} if no orientation was found. */
        public final EastNorth vAxis;

        /**
         * The way whose first edge was used as the reference, or {@code null}
         * if orientation came from angle-snap or no reference was found.
         */
        public final Way referenceWay;

        /**
         * Cached EastNorth corners of {@link #referenceWay} for grid extent,
         * or {@code null} if there is no reference way.
         */
        public final EastNorth[] referenceCorners;

        /** Sentinel result indicating no reference orientation was found. */
        static final Result NONE = new Result(false, null, null, null, null);

        Result(boolean hasRef, EastNorth u, EastNorth v,
               Way way, EastNorth[] corners) {
            this.hasReferenceOrientation = hasRef;
            this.uAxis = u;
            this.vAxis = v;
            this.referenceWay = way;
            this.referenceCorners = corners;
        }
    }

    // ==================================================================
    // Public API
    // ==================================================================

    /**
     * Detects the reference orientation to use for the snap grid.
     *
     * @param clickEN   anchor point in EastNorth (the first building corner)
     * @param angleSnap true if cardinal angle-snap is currently active
     * @param ds        the current edit data set (may be {@code null})
     * @param mv        the map view used for screen-space proximity tests
     *                  (may be {@code null}; nearby-building detection is skipped when null)
     * @return a {@link Result} describing the detected orientation
     */
    public static Result detect(EastNorth clickEN, boolean angleSnap,
                                DataSet ds, MapView mv) {
        // 1. Cardinal angle-snap always wins
        if (angleSnap) {
            return new Result(true, new EastNorth(1, 0), new EastNorth(0, 1), null, null);
        }

        // 2. First selected way that has at least two nodes
        if (ds != null) {
            for (Way w : ds.getSelectedWays()) {
                if (!w.isDeleted() && w.getNodesCount() >= 2) {
                    return fromWay(w);
                }
            }
        }

        // 3. Nearest closed way (existing building) near the click point
        if (ds != null && mv != null) {
            Way nearby = findNearbyClosedWay(clickEN, ds, mv);
            if (nearby != null) {
                return fromWay(nearby);
            }
        }

        return Result.NONE;
    }

    // ==================================================================
    // Package-visible helpers (for unit tests)
    // ==================================================================

    /**
     * Builds a {@link Result} from a way's first edge direction.
     *
     * @param w a way with at least two nodes
     * @return a result whose axes are derived from the first edge, and whose
     *         {@link Result#referenceCorners} cache the way's EastNorth corners
     */
    static Result fromWay(Way w) {
        if (w.getNodesCount() < 2) return Result.NONE;
        EastNorth n0 = w.getNode(0).getEastNorth();
        EastNorth n1 = w.getNode(1).getEastNorth();
        EastNorth[] axes = SnappiGrid.computeAxes(n0, n1);

        List<Node> nodes = w.getNodes();
        int count = w.isClosed() ? nodes.size() - 1 : nodes.size();
        EastNorth[] corners = new EastNorth[count];
        for (int i = 0; i < count; i++) {
            corners[i] = nodes.get(i).getEastNorth();
        }

        return new Result(true, axes[0], axes[1], w, corners);
    }

    /**
     * Finds the nearest closed way whose boundary falls within screen-hit
     * distance of the click point.
     *
     * @param clickEN anchor point
     * @param ds      edit data set (must not be null)
     * @param mv      map view for screen-space conversion (must not be null)
     * @return the nearest qualifying closed way, or {@code null} if none found
     */
    static Way findNearbyClosedWay(EastNorth clickEN, DataSet ds, MapView mv) {
        Point clickScreen = mv.getPoint(clickEN);
        double radius = SnappiPreferences.getHandleRadius() * 4.0;
        double radiusSq = radius * radius;

        Way best = null;
        double bestDist = radiusSq;

        for (Way w : ds.getWays()) {
            if (w.isDeleted() || !w.isClosed() || w.getNodesCount() < 3) continue;
            for (int i = 0; i < w.getNodesCount() - 1; i++) {
                Point pa = mv.getPoint(w.getNode(i).getEastNorth());
                Point pb = mv.getPoint(w.getNode(i + 1).getEastNorth());
                double dSq = SnappiGrid.distToSegmentSq(clickScreen, pa, pb);
                if (dSq < bestDist) {
                    bestDist = dSq;
                    best = w;
                }
            }
        }
        return best;
    }
}
