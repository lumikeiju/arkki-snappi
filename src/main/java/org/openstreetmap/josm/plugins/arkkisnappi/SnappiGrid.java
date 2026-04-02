// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.MapView;

/**
 * Stateless geometry utility and overlay renderer for the snap grid.
 *
 * <p>This class owns two responsibilities:</p>
 * <ol>
 *   <li><strong>Snapping</strong> — projecting a world-coordinate mouse position
 *       onto the local (u, v) grid frame and rounding each component to the
 *       nearest step multiple.</li>
 *   <li><strong>Painting</strong> — drawing the grid lines, rectangle preview,
 *       anchor/target dots, edge handles, length labels, and extrude fill
 *       onto the map view.</li>
 * </ol>
 *
 * <p>All geometric arithmetic is performed in JOSM's <em>EastNorth</em>
 * (projected-metre) coordinate system so that the step value has direct
 * physical meaning. Screen-pixel conversions happen only at draw time via
 * {@link MapView#getPoint(EastNorth)}.</p>
 *
 * @author Lumikeiju
 * @see SnappiMode
 * @see SnappiPreferences
 */
public final class SnappiGrid {

    /** Radius in pixels for the anchor and target dots. */
    private static final int DOT_RADIUS = 6;

    /** Half-size in pixels for edge handle squares. */
    private static final int HANDLE_HALF = 4;

    /** Stroke width in pixels for grid lines. */
    private static final float GRID_STROKE_WIDTH = 1.0f;

    /** Stroke width in pixels for the rectangle preview. */
    private static final float RECT_STROKE_WIDTH = 2.5f;

    /** Font for length labels. */
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    /** Offset in pixels for length labels from their edge. */
    private static final int LABEL_OFFSET = 12;

    private SnappiGrid() {
        // utility class — no instances
    }

    // ==================================================================
    // Snapping
    // ==================================================================

    /**
     * Computes the (u, v) unit axes for the local grid frame.
     *
     * <p>The <b>u-axis</b> points from {@code anchor} toward {@code mouse}.
     * The <b>v-axis</b> is perpendicular to u, rotated 90° counter-clockwise.
     * If the two points coincide, u defaults to (1, 0) and v to (0, 1).</p>
     *
     * @param anchor the grid origin (click-1 position)
     * @param mouse  the current mouse world position
     * @return a two-element array {@code [uAxis, vAxis]} of unit vectors
     */
    public static EastNorth[] computeAxes(EastNorth anchor, EastNorth mouse) {
        double dx = mouse.east() - anchor.east();
        double dy = mouse.north() - anchor.north();
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-12) {
            return new EastNorth[]{new EastNorth(1, 0), new EastNorth(0, 1)};
        }
        EastNorth u = new EastNorth(dx / len, dy / len);
        EastNorth v = new EastNorth(-u.north(), u.east());
        return new EastNorth[]{u, v};
    }

    /**
     * Snaps a mouse position to the nearest grid intersection,
     * with independent step sizes for the u and v axes.
     *
     * @param mouse    current mouse world position
     * @param anchor   grid origin
     * @param uAxis    unit vector along the u-axis
     * @param vAxis    unit vector along the v-axis
     * @param stepU    snap step size in metres along u-axis
     * @param stepV    snap step size in metres along v-axis
     * @param lockAxis 0 = snap both, 1 = u only (v=0), 2 = v only (u=0)
     * @param freeMode if true, skip snapping entirely (return raw mouse)
     * @return the snapped world position
     */
    public static EastNorth snap(EastNorth mouse, EastNorth anchor,
                                 EastNorth uAxis, EastNorth vAxis,
                                 double stepU, double stepV,
                                 int lockAxis, boolean freeMode) {
        if (freeMode) {
            return mouse;
        }

        double dx = mouse.east() - anchor.east();
        double dy = mouse.north() - anchor.north();

        // Project onto local axes
        double uProj = dx * uAxis.east() + dy * uAxis.north();
        double vProj = dx * vAxis.east() + dy * vAxis.north();

        // Round to nearest step (use integer step count for precision)
        long uSteps = Math.round(uProj / stepU);
        long vSteps = Math.round(vProj / stepV);
        double uSnap = uSteps * stepU;
        double vSnap = vSteps * stepV;

        // Ensure non-zero extent
        if (Math.abs(uSnap) < stepU * 0.5) {
            uSnap = Math.copySign(stepU, uProj);
        }
        if (Math.abs(vSnap) < stepV * 0.5) {
            vSnap = Math.copySign(stepV, vProj);
        }

        // Axis-lock
        if (lockAxis == 1) {
            vSnap = 0;
        } else if (lockAxis == 2) {
            uSnap = 0;
        }

        // Back to world
        return new EastNorth(
                anchor.east() + uSnap * uAxis.east() + vSnap * vAxis.east(),
                anchor.north() + uSnap * uAxis.north() + vSnap * vAxis.north());
    }

    /**
     * Snaps a scalar distance to the nearest step multiple.
     * If the result would be zero, returns ±stepM preserving sign.
     *
     * @param distance raw distance in metres
     * @param stepM    snap step in metres
     * @return snapped distance
     */
    public static double snapScalar(double distance, double stepM) {
        long steps = Math.round(distance / stepM);
        double snapped = steps * stepM;
        if (Math.abs(snapped) < stepM * 0.5) {
            snapped = Math.copySign(stepM, distance);
        }
        return snapped;
    }

    /**
     * Computes the four corners of the snapped rectangle.
     *
     * <p>Corner order (counter-clockwise when {@code ccw} is true):</p>
     * <pre>
     *   corners[0] = anchor
     *   corners[1] = anchor + uSnap * uAxis
     *   corners[2] = anchor + uSnap * uAxis + vSnap * vAxis
     *   corners[3] = anchor + vSnap * vAxis
     * </pre>
     *
     * @param anchor anchor point (grid origin)
     * @param target snapped opposite corner
     * @param uAxis  u-unit vector
     * @param vAxis  v-unit vector
     * @param ccw    counter-clockwise winding if true
     * @return four EastNorth corners in winding order
     */
    public static EastNorth[] computeCorners(EastNorth anchor, EastNorth target,
                                             EastNorth uAxis, EastNorth vAxis,
                                             boolean ccw) {
        double dx = target.east() - anchor.east();
        double dy = target.north() - anchor.north();
        double uLen = dx * uAxis.east() + dy * uAxis.north();
        double vLen = dx * vAxis.east() + dy * vAxis.north();

        EastNorth c0 = anchor;
        EastNorth c1 = new EastNorth(
                anchor.east() + uLen * uAxis.east(),
                anchor.north() + uLen * uAxis.north());
        EastNorth c2 = target;
        EastNorth c3 = new EastNorth(
                anchor.east() + vLen * vAxis.east(),
                anchor.north() + vLen * vAxis.north());

        if (ccw) {
            return new EastNorth[]{c0, c1, c2, c3};
        } else {
            return new EastNorth[]{c0, c3, c2, c1};
        }
    }

    // ==================================================================
    // Painting
    // ==================================================================

    /**
     * Paints the full snap grid overlay with independent u/v step sizes.
     * Draws grid lines, rectangle preview, and anchor/target dots.
     *
     * @param g      graphics context
     * @param mv     the map view (for coordinate conversion)
     * @param anchor grid origin
     * @param target current snapped target corner
     * @param uAxis  u-unit vector
     * @param vAxis  v-unit vector
     * @param stepU  snap step in metres along u-axis
     * @param stepV  snap step in metres along v-axis
     */
    public static void paintGrid(Graphics2D g, MapView mv,
                                 EastNorth anchor, EastNorth target,
                                 EastNorth uAxis, EastNorth vAxis,
                                 double stepU, double stepV) {
        paintGrid(g, mv, anchor, target, uAxis, vAxis, stepU, stepV, null);
    }

    /**
     * Paints the full snap grid overlay, optionally extending to cover a
     * reference polygon (e.g. an existing building).
     *
     * @param g             graphics context
     * @param mv            the map view
     * @param anchor        grid origin
     * @param target        current snapped target corner
     * @param uAxis         u-unit vector
     * @param vAxis         v-unit vector
     * @param stepU         step along u
     * @param stepV         step along v
     * @param refCorners    optional reference polygon corners to extend grid over (may be null)
     */
    public static void paintGrid(Graphics2D g, MapView mv,
                                 EastNorth anchor, EastNorth target,
                                 EastNorth uAxis, EastNorth vAxis,
                                 double stepU, double stepV,
                                 EastNorth[] refCorners) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        double dx = target.east() - anchor.east();
        double dy = target.north() - anchor.north();
        double uLen = dx * uAxis.east() + dy * uAxis.north();
        double vLen = dx * vAxis.east() + dy * vAxis.north();

        int margin = 3;
        int maxLines = SnappiPreferences.getMaxGridLines();

        int uSteps = (int) Math.round(uLen / stepU);
        int vSteps = (int) Math.round(vLen / stepV);

        int uMin = Math.min(0, uSteps) - margin;
        int uMax = Math.max(0, uSteps) + margin;
        int vMin = Math.min(0, vSteps) - margin;
        int vMax = Math.max(0, vSteps) + margin;

        // Extend grid to cover reference polygon if provided
        if (refCorners != null) {
            for (EastNorth rc : refCorners) {
                double rdx = rc.east() - anchor.east();
                double rdy = rc.north() - anchor.north();
                double ru = rdx * uAxis.east() + rdy * uAxis.north();
                double rv = rdx * vAxis.east() + rdy * vAxis.north();
                int ruStep = (int) Math.round(ru / stepU);
                int rvStep = (int) Math.round(rv / stepV);
                uMin = Math.min(uMin, ruStep - margin);
                uMax = Math.max(uMax, ruStep + margin);
                vMin = Math.min(vMin, rvStep - margin);
                vMax = Math.max(vMax, rvStep + margin);
            }
        }

        // Clamp to max lines
        uMin = Math.max(uMin, -maxLines);
        uMax = Math.min(uMax, maxLines);
        vMin = Math.max(vMin, -maxLines);
        vMax = Math.min(vMax, maxLines);

        // Draw grid lines
        Color gridColor = SnappiPreferences.getGridColor();
        g.setColor(gridColor);
        g.setStroke(new BasicStroke(GRID_STROKE_WIDTH));

        // Lines parallel to u-axis (constant v)
        for (int vi = vMin; vi <= vMax; vi++) {
            EastNorth start = gridPoint(anchor, uAxis, vAxis, uMin * stepU, vi * stepV);
            EastNorth end = gridPoint(anchor, uAxis, vAxis, uMax * stepU, vi * stepV);
            drawLine(g, mv, start, end);
        }

        // Lines parallel to v-axis (constant u)
        for (int ui = uMin; ui <= uMax; ui++) {
            EastNorth start = gridPoint(anchor, uAxis, vAxis, ui * stepU, vMin * stepV);
            EastNorth end = gridPoint(anchor, uAxis, vAxis, ui * stepU, vMax * stepV);
            drawLine(g, mv, start, end);
        }

        // Rectangle preview
        paintRectPreview(g, mv, anchor, target, uAxis, vAxis);

        // Dots
        paintDot(g, mv, anchor, SnappiPreferences.getAnchorColor());
        paintDot(g, mv, target, SnappiPreferences.getTargetColor());
    }

    /**
     * Paints only the grid lines (no rectangle preview or dots).
     * Used in PHASE_EXTRUDE where the building polygon outline is drawn
     * separately to correctly follow the actual shape after extrusions.
     */
    public static void paintGridLines(Graphics2D g, MapView mv,
                                      EastNorth anchor, EastNorth target,
                                      EastNorth uAxis, EastNorth vAxis,
                                      double stepU, double stepV,
                                      EastNorth[] refCorners) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        double dx = target.east() - anchor.east();
        double dy = target.north() - anchor.north();
        double uLen = dx * uAxis.east() + dy * uAxis.north();
        double vLen = dx * vAxis.east() + dy * vAxis.north();

        int margin = 3;
        int maxLines = SnappiPreferences.getMaxGridLines();

        int uSteps = (int) Math.round(uLen / stepU);
        int vSteps = (int) Math.round(vLen / stepV);

        int uMin = Math.min(0, uSteps) - margin;
        int uMax = Math.max(0, uSteps) + margin;
        int vMin = Math.min(0, vSteps) - margin;
        int vMax = Math.max(0, vSteps) + margin;

        if (refCorners != null) {
            for (EastNorth rc : refCorners) {
                double rdx = rc.east() - anchor.east();
                double rdy = rc.north() - anchor.north();
                double ru = rdx * uAxis.east() + rdy * uAxis.north();
                double rv = rdx * vAxis.east() + rdy * vAxis.north();
                int ruStep = (int) Math.round(ru / stepU);
                int rvStep = (int) Math.round(rv / stepV);
                uMin = Math.min(uMin, ruStep - margin);
                uMax = Math.max(uMax, ruStep + margin);
                vMin = Math.min(vMin, rvStep - margin);
                vMax = Math.max(vMax, rvStep + margin);
            }
        }

        uMin = Math.max(uMin, -maxLines);
        uMax = Math.min(uMax, maxLines);
        vMin = Math.max(vMin, -maxLines);
        vMax = Math.min(vMax, maxLines);

        Color gridColor = SnappiPreferences.getGridColor();
        g.setColor(gridColor);
        g.setStroke(new BasicStroke(GRID_STROKE_WIDTH));

        for (int vi = vMin; vi <= vMax; vi++) {
            EastNorth start = gridPoint(anchor, uAxis, vAxis, uMin * stepU, vi * stepV);
            EastNorth end = gridPoint(anchor, uAxis, vAxis, uMax * stepU, vi * stepV);
            drawLine(g, mv, start, end);
        }
        for (int ui = uMin; ui <= uMax; ui++) {
            EastNorth start = gridPoint(anchor, uAxis, vAxis, ui * stepU, vMin * stepV);
            EastNorth end = gridPoint(anchor, uAxis, vAxis, ui * stepU, vMax * stepV);
            drawLine(g, mv, start, end);
        }
    }

    /**
     * Paints the outline of an arbitrary polygon (the actual building shape).
     */
    public static void paintPolygonOutline(Graphics2D g, MapView mv,
                                           EastNorth[] corners) {
        g.setColor(SnappiPreferences.getRectColor());
        g.setStroke(new BasicStroke(RECT_STROKE_WIDTH));

        Path2D path = new Path2D.Double();
        Point first = mv.getPoint(corners[0]);
        path.moveTo(first.x, first.y);
        for (int i = 1; i < corners.length; i++) {
            Point p = mv.getPoint(corners[i]);
            path.lineTo(p.x, p.y);
        }
        path.closePath();
        g.draw(path);
    }

    /**
     * Paints a line preview for 3-click mode Phase 1 (anchor → adjacent corner).
     * Shows the line along the u-axis with snap tick marks.
     *
     * @param g      graphics context
     * @param mv     map view
     * @param anchor grid origin
     * @param target snapped adjacent corner (along u-axis only)
     * @param uAxis  u-unit vector
     * @param stepU  snap step along u
     */
    public static void paintLinePreview(Graphics2D g, MapView mv,
                                        EastNorth anchor, EastNorth target,
                                        EastNorth uAxis, double stepU) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Line from anchor to target
        g.setColor(SnappiPreferences.getRectColor());
        g.setStroke(new BasicStroke(RECT_STROKE_WIDTH));
        drawLine(g, mv, anchor, target);

        // Tick marks along the line
        double dx = target.east() - anchor.east();
        double dy = target.north() - anchor.north();
        double uLen = Math.sqrt(dx * dx + dy * dy);
        int nSteps = (int) Math.round(uLen / stepU);
        int maxTicks = Math.min(Math.abs(nSteps) + 3, SnappiPreferences.getMaxGridLines());

        // Perpendicular for tick marks
        EastNorth perp = new EastNorth(-uAxis.north(), uAxis.east());
        double tickLen = stepU * 0.3;

        g.setColor(SnappiPreferences.getGridColor());
        g.setStroke(new BasicStroke(GRID_STROKE_WIDTH));
        for (int i = 0; i <= maxTicks; i++) {
            double u = i * stepU;
            if (uLen < 0) u = -u;
            EastNorth base = new EastNorth(
                    anchor.east() + u * uAxis.east(),
                    anchor.north() + u * uAxis.north());
            EastNorth tickA = new EastNorth(
                    base.east() - tickLen * perp.east(),
                    base.north() - tickLen * perp.north());
            EastNorth tickB = new EastNorth(
                    base.east() + tickLen * perp.east(),
                    base.north() + tickLen * perp.north());
            drawLine(g, mv, tickA, tickB);
        }

        // Length label on the line
        double length = Math.abs(uLen);
        if (length > 1e-6) {
            paintEdgeLabel(g, mv, anchor, target, length);
        }

        // Dots
        paintDot(g, mv, anchor, SnappiPreferences.getAnchorColor());
        paintDot(g, mv, target, SnappiPreferences.getTargetColor());
    }

    /**
     * Paints the rectangle outline preview (medium-weight blue stroke).
     */
    public static void paintRectPreview(Graphics2D g, MapView mv,
                                        EastNorth anchor, EastNorth target,
                                        EastNorth uAxis, EastNorth vAxis) {
        EastNorth[] corners = computeCorners(anchor, target, uAxis, vAxis, true);
        g.setColor(SnappiPreferences.getRectColor());
        g.setStroke(new BasicStroke(RECT_STROKE_WIDTH));

        Path2D path = new Path2D.Double();
        Point first = mv.getPoint(corners[0]);
        path.moveTo(first.x, first.y);
        for (int i = 1; i < corners.length; i++) {
            Point p = mv.getPoint(corners[i]);
            path.lineTo(p.x, p.y);
        }
        path.closePath();
        g.draw(path);
    }

    /**
     * Paints length labels on each edge of a polygon.
     *
     * @param g       graphics context
     * @param mv      map view
     * @param corners polygon corners
     */
    public static void paintLengthLabels(Graphics2D g, MapView mv,
                                         EastNorth[] corners) {
        for (int i = 0; i < corners.length; i++) {
            EastNorth a = corners[i];
            EastNorth b = corners[(i + 1) % corners.length];
            double edgeLen = Math.sqrt(
                    (b.east() - a.east()) * (b.east() - a.east()) +
                    (b.north() - a.north()) * (b.north() - a.north()));
            if (edgeLen > 1e-6) {
                paintEdgeLabel(g, mv, a, b, edgeLen);
            }
        }
    }

    /**
     * Draws a single length label along an edge.
     */
    private static void paintEdgeLabel(Graphics2D g, MapView mv,
                                       EastNorth a, EastNorth b,
                                       double lengthMetres) {
        Point pa = mv.getPoint(a);
        Point pb = mv.getPoint(b);
        double mx = (pa.x + pb.x) / 2.0;
        double my = (pa.y + pb.y) / 2.0;

        double angle = Math.atan2(pb.y - pa.y, pb.x - pa.x);
        // Keep text readable (not upside-down)
        if (angle > Math.PI / 2) angle -= Math.PI;
        if (angle < -Math.PI / 2) angle += Math.PI;

        String text = SnappiPreferences.formatStep(lengthMetres);

        AffineTransform oldXf = g.getTransform();
        Font oldFont = g.getFont();
        g.setFont(LABEL_FONT);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);

        g.translate(mx, my);
        g.rotate(angle);

        // Compact pill-style background
        int padH = 5;
        int padV = 2;
        int textY = -LABEL_OFFSET;
        int bgX = -tw / 2 - padH;
        int bgY = textY - fm.getAscent() - padV;
        int bgW = tw + padH * 2;
        int bgH = fm.getAscent() + fm.getDescent() + padV * 2;
        g.setColor(new Color(30, 30, 30, 210));
        g.fillRoundRect(bgX, bgY, bgW, bgH, 8, 8);

        // White text
        g.setColor(Color.WHITE);
        g.drawString(text, -tw / 2, textY);

        g.setTransform(oldXf);
        g.setFont(oldFont);
    }

    /**
     * Paints the extrude grid and preview during Phase 3 dragging.
     *
     * @param g             graphics context
     * @param mv            map view
     * @param anchor        grid origin (from Phase 2)
     * @param uAxis         u-unit vector (frozen from Phase 2)
     * @param vAxis         v-unit vector (frozen from Phase 2)
     * @param stepU         snap step along u
     * @param stepV         snap step along v
     * @param wayCorners    current corners of the way
     * @param edgeIndex     index of the edge being extruded
     * @param extrudeOffset snapped extrude offset in metres (signed)
     */
    public static void paintExtrudePreview(Graphics2D g, MapView mv,
                                           EastNorth anchor,
                                           EastNorth uAxis, EastNorth vAxis,
                                           double stepU, double stepV,
                                           EastNorth[] wayCorners,
                                           int edgeIndex, double extrudeOffset) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int i0 = edgeIndex;
        int i1 = (edgeIndex + 1) % wayCorners.length;
        EastNorth edgeStart = wayCorners[i0];
        EastNorth edgeEnd = wayCorners[i1];

        double edx = edgeEnd.east() - edgeStart.east();
        double edy = edgeEnd.north() - edgeStart.north();
        double edgeLen = Math.sqrt(edx * edx + edy * edy);
        if (edgeLen < 1e-12) return;

        EastNorth normal = computeOutwardNormal(wayCorners, edgeIndex);

        // New positions of the two edge nodes
        EastNorth newStart = new EastNorth(
                edgeStart.east() + extrudeOffset * normal.east(),
                edgeStart.north() + extrudeOffset * normal.north());
        EastNorth newEnd = new EastNorth(
                edgeEnd.east() + extrudeOffset * normal.east(),
                edgeEnd.north() + extrudeOffset * normal.north());

        // Draw extrude fill
        Color extrudeColor = SnappiPreferences.getExtrudeColor();
        g.setColor(extrudeColor);
        Path2D fill = new Path2D.Double();
        Point p0 = mv.getPoint(edgeStart);
        Point p1 = mv.getPoint(edgeEnd);
        Point p2 = mv.getPoint(newEnd);
        Point p3 = mv.getPoint(newStart);
        fill.moveTo(p0.x, p0.y);
        fill.lineTo(p1.x, p1.y);
        fill.lineTo(p2.x, p2.y);
        fill.lineTo(p3.x, p3.y);
        fill.closePath();
        g.fill(fill);

        // Draw extrude outline
        g.setColor(SnappiPreferences.getRectColor());
        g.setStroke(new BasicStroke(RECT_STROKE_WIDTH));
        g.draw(fill);

        // Draw snap lines along the extrude direction
        // Determine which step to use based on edge alignment
        double uDot = Math.abs(normal.east() * uAxis.east() + normal.north() * uAxis.north());
        double step = uDot > 0.5 ? stepU : stepV;

        g.setColor(SnappiPreferences.getGridColor());
        g.setStroke(new BasicStroke(GRID_STROKE_WIDTH));

        int steps = (int) Math.round(extrudeOffset / step);
        int maxLines = SnappiPreferences.getMaxGridLines();
        int minStep = Math.min(0, steps);
        int maxStep = Math.max(0, steps);
        minStep = Math.max(minStep, -maxLines);
        maxStep = Math.min(maxStep, maxLines);

        for (int s = minStep; s <= maxStep; s++) {
            double off = s * step;
            EastNorth ls = new EastNorth(
                    edgeStart.east() + off * normal.east(),
                    edgeStart.north() + off * normal.north());
            EastNorth le = new EastNorth(
                    edgeEnd.east() + off * normal.east(),
                    edgeEnd.north() + off * normal.north());
            drawLine(g, mv, ls, le);
        }

        // Length label on the extrude direction
        double absOffset = Math.abs(extrudeOffset);
        if (absOffset > 1e-6) {
            EastNorth midOld = midpoint(edgeStart, edgeEnd);
            EastNorth midNew = new EastNorth(
                    midOld.east() + extrudeOffset * normal.east(),
                    midOld.north() + extrudeOffset * normal.north());
            paintEdgeLabel(g, mv, midOld, midNew, absOffset);
        }

        // Length label on the extruded edge
        if (edgeLen > 1e-6) {
            paintEdgeLabel(g, mv, newStart, newEnd, edgeLen);
        }
    }

    /**
     * Paints a filled circle ("dot") at the given world coordinate.
     *
     * @param g     graphics context
     * @param mv    map view
     * @param point world position
     * @param color dot fill color
     */
    public static void paintDot(Graphics2D g, MapView mv,
                                EastNorth point, Color color) {
        Point screen = mv.getPoint(point);
        g.setColor(color);
        g.fill(new Ellipse2D.Double(
                screen.x - DOT_RADIUS, screen.y - DOT_RADIUS,
                DOT_RADIUS * 2, DOT_RADIUS * 2));
    }

    /**
     * Paints small hollow square handles at the midpoints of each edge.
     * The hovered handle is drawn slightly larger and filled.
     *
     * @param g          graphics context
     * @param mv         map view
     * @param corners    way corners
     * @param hoverIndex index of the currently hovered edge (-1 if none)
     */
    public static void paintEdgeHandles(Graphics2D g, MapView mv,
                                        EastNorth[] corners, int hoverIndex) {
        Color handleColor = SnappiPreferences.getHandleColor();
        for (int i = 0; i < corners.length; i++) {
            EastNorth mid = midpoint(corners[i], corners[(i + 1) % corners.length]);
            Point screen = mv.getPoint(mid);
            if (i == hoverIndex) {
                // Hovered: slightly larger filled square
                int h = HANDLE_HALF + 2;
                g.setColor(handleColor);
                g.fillRect(screen.x - h, screen.y - h, h * 2, h * 2);
            } else {
                // Normal: small hollow square
                g.setColor(handleColor);
                g.setStroke(new BasicStroke(1.5f));
                g.drawRect(screen.x - HANDLE_HALF, screen.y - HANDLE_HALF,
                        HANDLE_HALF * 2, HANDLE_HALF * 2);
            }
        }
    }

    /**
     * Determines which edge handle (if any) the mouse is hovering over.
     *
     * @param mouseScreen mouse position in screen pixels
     * @param mv          map view
     * @param corners     way corners
     * @return edge index or -1 if none
     */
    public static int hitTestEdgeHandle(Point mouseScreen, MapView mv,
                                        EastNorth[] corners) {
        int radius = SnappiPreferences.getHandleRadius();
        double radiusSq = (double) radius * radius;
        for (int i = 0; i < corners.length; i++) {
            EastNorth mid = midpoint(corners[i], corners[(i + 1) % corners.length]);
            Point screen = mv.getPoint(mid);
            double distSq = mouseScreen.distanceSq(screen);
            if (distSq <= radiusSq) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Hit-tests whether the mouse is near any edge segment of a polygon.
     * Returns the edge index or -1.
     *
     * @param mouseScreen mouse screen position
     * @param mv          map view
     * @param corners     polygon corners
     * @param radiusPx    hit-test radius in pixels
     * @return edge index or -1
     */
    public static int hitTestEdge(Point mouseScreen, MapView mv,
                                  EastNorth[] corners, int radiusPx) {
        double radiusSq = (double) radiusPx * radiusPx;
        for (int i = 0; i < corners.length; i++) {
            Point pa = mv.getPoint(corners[i]);
            Point pb = mv.getPoint(corners[(i + 1) % corners.length]);
            if (distToSegmentSq(mouseScreen, pa, pb) <= radiusSq) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Computes the outward-pointing unit normal for edge {@code edgeIndex}
     * of a polygon defined by {@code corners}.
     *
     * @param corners   polygon vertices
     * @param edgeIndex edge index (0-based)
     * @return outward unit normal
     */
    public static EastNorth computeOutwardNormal(EastNorth[] corners, int edgeIndex) {
        int i0 = edgeIndex;
        int i1 = (edgeIndex + 1) % corners.length;
        double edx = corners[i1].east() - corners[i0].east();
        double edy = corners[i1].north() - corners[i0].north();
        double edgeLen = Math.sqrt(edx * edx + edy * edy);

        double nx = -edy / edgeLen;
        double ny = edx / edgeLen;

        // Centroid
        double cx = 0, cy = 0;
        for (EastNorth c : corners) {
            cx += c.east();
            cy += c.north();
        }
        cx /= corners.length;
        cy /= corners.length;

        // Edge midpoint
        EastNorth mid = midpoint(corners[i0], corners[i1]);

        // If the normal points toward the centroid, flip it
        double toCenter = (cx - mid.east()) * nx + (cy - mid.north()) * ny;
        if (toCenter > 0) {
            nx = -nx;
            ny = -ny;
        }

        return new EastNorth(nx, ny);
    }

    /**
     * Finds the nearest grid point on a polygon edge.
     *
     * @param clickEN    click position in EastNorth
     * @param corners    polygon corners
     * @param edgeIndex  which edge
     * @param anchor     grid origin
     * @param uAxis      u-axis unit vector
     * @param vAxis      v-axis unit vector
     * @param stepU      step along u
     * @param stepV      step along v
     * @return the nearest grid point on the edge, or null if edge too short
     */
    public static EastNorth nearestGridPointOnEdge(EastNorth clickEN,
                                                   EastNorth[] corners,
                                                   int edgeIndex,
                                                   EastNorth anchor,
                                                   EastNorth uAxis, EastNorth vAxis,
                                                   double stepU, double stepV) {
        int i0 = edgeIndex;
        int i1 = (edgeIndex + 1) % corners.length;
        EastNorth edgeA = corners[i0];
        EastNorth edgeB = corners[i1];

        double edx = edgeB.east() - edgeA.east();
        double edy = edgeB.north() - edgeA.north();
        double edgeLen = Math.sqrt(edx * edx + edy * edy);
        if (edgeLen < 1e-9) return null;

        // Determine step along this edge based on alignment
        double uDot = Math.abs(edx * uAxis.east() + edy * uAxis.north()) / edgeLen;
        double step = uDot > 0.5 ? stepU : stepV;

        // Project click onto the edge parameter [0, edgeLen]
        double cdx = clickEN.east() - edgeA.east();
        double cdy = clickEN.north() - edgeA.north();
        double t = (cdx * edx + cdy * edy) / (edgeLen * edgeLen);
        double dist = t * edgeLen;

        // Snap dist to step multiple (but not 0 or edgeLen to avoid coinciding with existing nodes)
        long nSteps = Math.round(dist / step);
        double snapped = nSteps * step;
        if (snapped <= step * 0.5 || snapped >= edgeLen - step * 0.5) {
            return null; // too close to existing node
        }

        double frac = snapped / edgeLen;
        return new EastNorth(
                edgeA.east() + frac * edx,
                edgeA.north() + frac * edy);
    }

    // ==================================================================
    // Internal helpers
    // ==================================================================

    /**
     * Returns the EastNorth point at grid coordinate (u, v) relative to the anchor.
     */
    private static EastNorth gridPoint(EastNorth anchor,
                                       EastNorth uAxis, EastNorth vAxis,
                                       double u, double v) {
        return new EastNorth(
                anchor.east() + u * uAxis.east() + v * vAxis.east(),
                anchor.north() + u * uAxis.north() + v * vAxis.north());
    }

    /** Draws a line between two world-coordinate points. */
    private static void drawLine(Graphics2D g, MapView mv,
                                 EastNorth a, EastNorth b) {
        Point pa = mv.getPoint(a);
        Point pb = mv.getPoint(b);
        g.draw(new Line2D.Double(pa.x, pa.y, pb.x, pb.y));
    }

    /** Returns the midpoint of two EastNorth points. */
    static EastNorth midpoint(EastNorth a, EastNorth b) {
        return new EastNorth(
                (a.east() + b.east()) / 2.0,
                (a.north() + b.north()) / 2.0);
    }

    /** Squared distance from point p to the line segment (a, b) in screen coords. */
    static double distToSegmentSq(Point p, Point a, Point b) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double lenSq = abx * abx + aby * aby;
        if (lenSq < 1e-6) return p.distanceSq(a);

        double t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double projX = a.x + t * abx;
        double projY = a.y + t * aby;
        double dx = p.x - projX;
        double dy = p.y - projY;
        return dx * dx + dy * dy;
    }
}
