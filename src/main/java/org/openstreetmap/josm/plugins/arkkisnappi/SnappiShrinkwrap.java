// License: AGPL v3 or later. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.tools.Logging;

/**
 * Computes the outer boundary of a potentially self-intersecting polygon.
 *
 * <p>When extrusions overlap the original building or each other, the way
 * can become self-intersecting. This utility detects such crossings and
 * replaces the polygon with its outermost boundary — the "shrinkwrap".</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Find every pair of non-adjacent edges that intersect.</li>
 *   <li>Split those edges at the intersection points to produce a
 *       planar subdivision (all crossings become proper vertices).</li>
 *   <li>Walk the outer boundary by starting from the leftmost vertex
 *       and always choosing the most-clockwise turn at each junction
 *       (rightmost relative heading). This traces exactly the external
 *       outline.</li>
 * </ol>
 *
 * <p>All arithmetic is in EastNorth (projected metres).</p>
 *
 * @author Lumikeiju
 */
public final class SnappiShrinkwrap {

    /** Tolerance for coincidence tests (1 mm). */
    private static final double EPS = 1e-3;

    /** Maximum vertices in the output to prevent infinite loops. */
    private static final int MAX_WALK = 10_000;

    private SnappiShrinkwrap() {
        // utility class
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} if the closed polygon defined by {@code corners}
     * has at least one self-intersection between non-adjacent edges.
     *
     * @param corners polygon vertices (no closing duplicate)
     * @return true if self-intersecting
     */
    public static boolean isSelfIntersecting(EastNorth[] corners) {
        int n = corners.length;
        for (int i = 0; i < n; i++) {
            EastNorth a = corners[i];
            EastNorth b = corners[(i + 1) % n];
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) continue; // adjacent (wrap-around)
                EastNorth c = corners[j];
                EastNorth d = corners[(j + 1) % n];
                if (segmentsIntersect(a, b, c, d)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Computes the outer boundary of a self-intersecting polygon.
     *
     * @param corners polygon vertices (no closing duplicate)
     * @return the outer boundary vertices, or null if no self-intersection
     *         was found (caller should use the original corners)
     */
    public static EastNorth[] computeOuterBoundary(EastNorth[] corners) {
        // Step 1: find all intersection points and split edges
        List<EastNorth> split = splitAtIntersections(corners);
        if (split.size() == corners.length) {
            return null; // no intersections found
        }

        // Step 2: walk the outer boundary
        List<EastNorth> outer = walkOuterBoundary(split);
        if (outer == null || outer.size() < 3) {
            return null; // walking failed; don't modify the polygon
        }

        Logging.debug("arkki-snappi: shrinkwrap reduced {0} → {1} vertices",
                split.size(), outer.size());
        return outer.toArray(new EastNorth[0]);
    }

    // ------------------------------------------------------------------
    // Step 1: split edges at intersection points
    // ------------------------------------------------------------------

    /**
     * Returns a new vertex list with intersection points inserted at the
     * correct positions along each edge.
     */
    private static List<EastNorth> splitAtIntersections(EastNorth[] corners) {
        int n = corners.length;

        // For each edge, collect fractional parameters of intersection points
        @SuppressWarnings("unchecked")
        List<double[]>[] edgeSplits = new List[n];
        for (int i = 0; i < n; i++) {
            edgeSplits[i] = new ArrayList<>();
        }

        for (int i = 0; i < n; i++) {
            EastNorth a = corners[i];
            EastNorth b = corners[(i + 1) % n];
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) continue;
                EastNorth c = corners[j];
                EastNorth d = corners[(j + 1) % n];
                double[] params = intersectionParams(a, b, c, d);
                if (params != null) {
                    double t = params[0];
                    double u = params[1];
                    EastNorth pt = new EastNorth(
                            a.east() + t * (b.east() - a.east()),
                            a.north() + t * (b.north() - a.north()));
                    edgeSplits[i].add(new double[]{t, pt.east(), pt.north()});
                    edgeSplits[j].add(new double[]{u, pt.east(), pt.north()});
                }
            }
        }

        // Build the split vertex list
        List<EastNorth> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(corners[i]);
            List<double[]> splits = edgeSplits[i];
            if (!splits.isEmpty()) {
                // Sort by parameter t along the edge
                splits.sort((a, b) -> Double.compare(a[0], b[0]));
                for (double[] sp : splits) {
                    EastNorth pt = new EastNorth(sp[1], sp[2]);
                    // Avoid adding a point coincident with an existing vertex
                    if (nearEqual(pt, corners[i]) || nearEqual(pt, corners[(i + 1) % n])) {
                        continue;
                    }
                    // Avoid near-duplicate split points on the same edge (can occur
                    // when two different edges intersect edge i at almost the same
                    // position due to floating-point differences in T-intersections).
                    boolean duplicate = false;
                    for (int k = result.size() - 1; k >= 0; k--) {
                        if (nearEqual(result.get(k), pt)) {
                            duplicate = true;
                            break;
                        }
                        // Stop searching once we're past the current edge's splits
                        if (nearEqual(result.get(k), corners[i])) break;
                    }
                    if (!duplicate) {
                        result.add(pt);
                    }
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Step 2: walk outer boundary
    // ------------------------------------------------------------------

    /**
     * Walks the outer boundary of the planar subdivision.
     *
     * <p>Starts from the leftmost (smallest east) vertex and an initial
     * heading of "due south" (negative north). At each vertex, examines
     * all edges leaving that vertex and picks the one that turns the most
     * clockwise (smallest signed angle from the incoming direction). This
     * always traces the exterior outline.</p>
     */
    private static List<EastNorth> walkOuterBoundary(List<EastNorth> verts) {
        int n = verts.size();

        // Build adjacency: for each vertex, find all connected neighbours
        // (the two adjacent vertices in the polygon ring)
        // Using index-based adjacency
        int[][] adj = new int[n][];
        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            int next = (i + 1) % n;
            // Check for duplicate vertices (intersection points appear on
            // multiple edges)
            List<Integer> neighbours = new ArrayList<>();
            neighbours.add(prev);
            neighbours.add(next);
            // Also add any other vertex that occupies the same position
            // (these are the cross-links at intersection points)
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                if (nearEqual(verts.get(i), verts.get(j))) {
                    int jp = (j - 1 + n) % n;
                    int jn = (j + 1) % n;
                    if (!neighbours.contains(jp)) neighbours.add(jp);
                    if (!neighbours.contains(jn)) neighbours.add(jn);
                }
            }
            adj[i] = neighbours.stream().mapToInt(Integer::intValue).toArray();
        }

        // Find the starting vertex: leftmost (min east), break ties by min north
        int startIdx = 0;
        for (int i = 1; i < n; i++) {
            EastNorth si = verts.get(i);
            EastNorth sb = verts.get(startIdx);
            if (si.east() < sb.east() - EPS ||
                    (Math.abs(si.east() - sb.east()) < EPS && si.north() < sb.north())) {
                startIdx = i;
            }
        }

        // Also consider coincident vertices at the start position
        // and pick the one whose adjacency offers the best first step
        List<Integer> startCandidates = new ArrayList<>();
        startCandidates.add(startIdx);
        for (int i = 0; i < n; i++) {
            if (i != startIdx && nearEqual(verts.get(i), verts.get(startIdx))) {
                startCandidates.add(i);
            }
        }

        // Initial heading: "coming from the south" means we arrived heading north
        // i.e. incoming direction is (0, -1) → we came from below
        double inDirE = 0;
        double inDirN = -1;

        // From the start candidates, pick the first step that yields the
        // most clockwise (rightmost) turn
        int bestStart = -1;
        int bestFirstNext = -1;
        double bestAngle = Double.POSITIVE_INFINITY;

        for (int si : startCandidates) {
            for (int ni : adj[si]) {
                EastNorth from = verts.get(si);
                EastNorth to = verts.get(ni);
                double de = to.east() - from.east();
                double dn = to.north() - from.north();
                double len = Math.sqrt(de * de + dn * dn);
                if (len < EPS) continue;
                double angle = signedAngleCW(inDirE, inDirN, de / len, dn / len);
                if (angle < bestAngle) {
                    bestAngle = angle;
                    bestStart = si;
                    bestFirstNext = ni;
                }
            }
        }

        if (bestStart < 0 || bestFirstNext < 0) return null;

        // Walk the boundary
        List<EastNorth> hull = new ArrayList<>();
        int prev = bestStart;
        int curr = bestFirstNext;
        hull.add(verts.get(prev));

        for (int step = 0; step < MAX_WALK; step++) {
            hull.add(verts.get(curr));

            if (curr == bestStart && step > 0) {
                // Special: check if we've actually returned to the start
                // by also checking the incoming direction matches
                hull.remove(hull.size() - 1); // don't duplicate start
                break;
            }

            // Incoming direction: prev → curr
            EastNorth pEN = verts.get(prev);
            EastNorth cEN = verts.get(curr);
            double ide = cEN.east() - pEN.east();
            double idn = cEN.north() - pEN.north();
            double ilen = Math.sqrt(ide * ide + idn * idn);
            if (ilen < EPS) {
                // Degenerate edge; try to continue
                prev = curr;
                curr = adj[curr].length > 0 ? adj[curr][0] : bestStart;
                continue;
            }
            ide /= ilen;
            idn /= ilen;

            // Find the most-clockwise neighbour (excluding going back to prev)
            double bestTurn = Double.POSITIVE_INFINITY;
            int nextIdx = -1;
            for (int ni : adj[curr]) {
                if (ni == prev) continue;
                // Also skip if it leads to a vertex coincident with prev
                // but different index (would be an immediate backtrack)
                if (nearEqual(verts.get(ni), pEN)) continue;

                EastNorth nEN = verts.get(ni);
                double ode = nEN.east() - cEN.east();
                double odn = nEN.north() - cEN.north();
                double olen = Math.sqrt(ode * ode + odn * odn);
                if (olen < EPS) continue;

                double angle = signedAngleCW(ide, idn, ode / olen, odn / olen);
                if (angle < bestTurn) {
                    bestTurn = angle;
                    nextIdx = ni;
                }
            }

            if (nextIdx < 0) {
                // Dead end — walk failed
                return null;
            }

            prev = curr;
            curr = nextIdx;
        }

        return hull.size() >= 3 ? hull : null;
    }

    // ------------------------------------------------------------------
    // Geometry primitives
    // ------------------------------------------------------------------

    /**
     * Tests whether segments (a,b) and (c,d) have a proper interior intersection
     * (not just touching at endpoints).
     */
    private static boolean segmentsIntersect(EastNorth a, EastNorth b,
                                             EastNorth c, EastNorth d) {
        double[] params = intersectionParams(a, b, c, d);
        return params != null;
    }

    /**
     * Returns the intersection parameters [t, u] for segments (a,b) and (c,d),
     * where the intersection point is a + t*(b-a) = c + u*(d-c).
     * Returns null if the segments don't have a proper interior intersection
     * (t and u must be strictly in (eps, 1-eps) to avoid endpoint touches).
     */
    private static double[] intersectionParams(EastNorth a, EastNorth b,
                                               EastNorth c, EastNorth d) {
        double bae = b.east() - a.east();
        double ban = b.north() - a.north();
        double dce = d.east() - c.east();
        double dcn = d.north() - c.north();

        double denom = bae * dcn - ban * dce;
        if (Math.abs(denom) < 1e-12) return null; // parallel or collinear

        double cae = c.east() - a.east();
        double can = c.north() - a.north();

        double t = (cae * dcn - can * dce) / denom;
        double u = (cae * ban - can * bae) / denom;

        double margin = 1e-6;
        if (t > margin && t < 1 - margin && u > margin && u < 1 - margin) {
            return new double[]{t, u};
        }
        return null;
    }

    /**
     * Returns the signed clockwise angle from direction (ide,idn) to (ode,odn),
     * in the range (-PI, PI]. Negative = clockwise, positive = counter-clockwise.
     *
     * <p>For the outer boundary walk we want the most clockwise turn, i.e. the
     * smallest (most negative) angle.</p>
     */
    private static double signedAngleCW(double ide, double idn,
                                        double ode, double odn) {
        // Cross product: positive if ode is counter-clockwise from ide
        double cross = ide * odn - idn * ode;
        double dot = ide * ode + idn * odn;
        return Math.atan2(cross, dot);
    }

    /** Tests whether two EastNorth points are within EPS of each other. */
    private static boolean nearEqual(EastNorth a, EastNorth b) {
        return Math.abs(a.east() - b.east()) < EPS &&
                Math.abs(a.north() - b.north()) < EPS;
    }
}
