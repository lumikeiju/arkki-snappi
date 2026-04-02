// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import java.awt.Color;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.spi.preferences.Config;

/**
 * Typed accessor layer over JOSM's {@link Config} preference store.
 *
 * <p>All keys are prefixed with {@code arkki_snappi.} and documented in
 * {@code docs/planning.md §4}. Values are stored in metres internally;
 * display-unit conversion is handled at the UI layer only.</p>
 *
 * @author Lumikeiju
 */
public final class SnappiPreferences {

    // ------------------------------------------------------------------
    // Preference key constants
    // ------------------------------------------------------------------

    /** Base snap increment in metres along U (X) axis. Default 0.3048 m (1 ft). */
    public static final String KEY_STEP_X_METRES = "arkki_snappi.step_x_metres";

    /** Base snap increment in metres along V (Y) axis. Default 0.3048 m (1 ft). */
    public static final String KEY_STEP_Y_METRES = "arkki_snappi.step_y_metres";

    /** Whether X and Y step sizes are linked (changing one changes both). */
    public static final String KEY_LINKED_STEPS = "arkki_snappi.linked_steps";

    /** Display unit: "ft" or "m". */
    public static final String KEY_STEP_UNIT = "arkki_snappi.step_unit";

    /** Semicolon-separated list of step preset values in metres. */
    public static final String KEY_STEP_PRESETS = "arkki_snappi.step_presets";

    /** JSON-encoded list of default tags, e.g. {@code [["building","yes"]]}. */
    public static final String KEY_TAGS = "arkki_snappi.tags";

    /** Whether to auto-select the created way. */
    public static final String KEY_AUTO_SELECT = "arkki_snappi.auto_select";

    /** Per-axis cap on grid lines rendered. */
    public static final String KEY_MAX_GRID_LINES = "arkki_snappi.max_grid_lines";

    /** Pixel radius for edge-handle hover detection. */
    public static final String KEY_HANDLE_RADIUS = "arkki_snappi.handle_radius";

    /** Pixel radius for snapping corners to existing OSM nodes. */
    public static final String KEY_NODE_SNAP_RADIUS = "arkki_snappi.node_snap_radius";

    /** Whether closed ways are wound counter-clockwise (true) or clockwise (false). */
    public static final String KEY_CCW_WINDING = "arkki_snappi.ccw_winding";

    /** Whether to auto-simplify (remove collinear nodes) when finishing a shape. */
    public static final String KEY_AUTO_SIMPLIFY = "arkki_snappi.auto_simplify";

    /** Whether to auto-shrinkwrap (resolve self-intersections) when finishing a shape. */
    public static final String KEY_AUTO_SHRINKWRAP = "arkki_snappi.auto_shrinkwrap";

    // ------------------------------------------------------------------
    // Color keys
    // ------------------------------------------------------------------
    public static final String KEY_COLOR_GRID = "arkki_snappi.color.grid";
    public static final String KEY_COLOR_RECT = "arkki_snappi.color.rect";
    public static final String KEY_COLOR_ANCHOR = "arkki_snappi.color.anchor";
    public static final String KEY_COLOR_TARGET = "arkki_snappi.color.target";
    public static final String KEY_COLOR_HANDLE = "arkki_snappi.color.handle";
    public static final String KEY_COLOR_EXTRUDE = "arkki_snappi.color.extrude";

    // ------------------------------------------------------------------
    // Defaults
    // ------------------------------------------------------------------

    /** 1 foot in metres, to 10 decimal places (no rounding loss). */
    public static final double DEFAULT_STEP_X_METRES = 0.3048;
    public static final double DEFAULT_STEP_Y_METRES = 0.3048;
    public static final boolean DEFAULT_LINKED_STEPS = true;
    public static final String DEFAULT_STEP_UNIT = "ft";
    public static final String DEFAULT_STEP_PRESETS =
            "0.0762;0.1524;0.3048;0.6096;1.0;0.5;0.25";
    public static final boolean DEFAULT_AUTO_SELECT = true;
    public static final int DEFAULT_MAX_GRID_LINES = 200;
    public static final int DEFAULT_HANDLE_RADIUS = 10;
    public static final int DEFAULT_NODE_SNAP_RADIUS = 15;
    public static final boolean DEFAULT_CCW_WINDING = true;
    public static final boolean DEFAULT_AUTO_SIMPLIFY = true;
    public static final boolean DEFAULT_AUTO_SHRINKWRAP = true;

    private SnappiPreferences() {
        // utility class — no instances
    }

    // ------------------------------------------------------------------
    // Snap settings
    // ------------------------------------------------------------------

    /** Returns the X (U-axis) snap step size in metres. */
    public static double getStepXMetres() {
        return Config.getPref().getDouble(KEY_STEP_X_METRES, DEFAULT_STEP_X_METRES);
    }

    /** Persists the X (U-axis) snap step size in metres. */
    public static void setStepXMetres(double metres) {
        Config.getPref().putDouble(KEY_STEP_X_METRES, metres);
    }

    /** Returns the Y (V-axis) snap step size in metres. */
    public static double getStepYMetres() {
        return Config.getPref().getDouble(KEY_STEP_Y_METRES, DEFAULT_STEP_Y_METRES);
    }

    /** Persists the Y (V-axis) snap step size in metres. */
    public static void setStepYMetres(double metres) {
        Config.getPref().putDouble(KEY_STEP_Y_METRES, metres);
    }

    /** Whether X and Y step sizes are linked. */
    public static boolean isLinkedSteps() {
        return Config.getPref().getBoolean(KEY_LINKED_STEPS, DEFAULT_LINKED_STEPS);
    }

    /** Persists the linked-steps flag. */
    public static void setLinkedSteps(boolean linked) {
        Config.getPref().putBoolean(KEY_LINKED_STEPS, linked);
    }

    /** Returns the display unit string ({@code "ft"} or {@code "m"}). */
    public static String getStepUnit() {
        return Config.getPref().get(KEY_STEP_UNIT, DEFAULT_STEP_UNIT);
    }

    /** Persists the display unit string. */
    public static void setStepUnit(String unit) {
        Config.getPref().put(KEY_STEP_UNIT, unit);
    }

    /**
     * Returns the X step size expressed in the user's chosen display unit.
     * This is purely for UI labels — all arithmetic uses metres.
     */
    public static double getStepXInDisplayUnit() {
        double m = getStepXMetres();
        return "ft".equals(getStepUnit()) ? metresToFeet(m) : m;
    }

    /**
     * Returns the Y step size expressed in the user's chosen display unit.
     */
    public static double getStepYInDisplayUnit() {
        double m = getStepYMetres();
        return "ft".equals(getStepUnit()) ? metresToFeet(m) : m;
    }

    /**
     * Returns the ordered list of step presets in metres.
     * Cycled by holding the Alt key during drawing.
     */
    public static List<Double> getStepPresets() {
        String raw = Config.getPref().get(KEY_STEP_PRESETS, DEFAULT_STEP_PRESETS);
        List<Double> result = new ArrayList<>();
        for (String token : raw.split(";")) {
            try {
                result.add(Double.parseDouble(token.trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed entries
            }
        }
        return result.isEmpty()
                ? Collections.singletonList(DEFAULT_STEP_X_METRES)
                : Collections.unmodifiableList(result);
    }

    /** Persists the step presets list. */
    public static void setStepPresets(List<Double> presets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < presets.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(presets.get(i));
        }
        Config.getPref().put(KEY_STEP_PRESETS, sb.toString());
    }

    // ------------------------------------------------------------------
    // Default tags
    // ------------------------------------------------------------------

    /**
     * Returns the default tag list as key/value pairs.
     * Falls back to {@code [building=yes]} when no preference is stored.
     */
    public static List<Map.Entry<String, String>> getDefaultTags() {
        List<List<String>> raw = Config.getPref().getListOfLists(KEY_TAGS, null);
        if (raw == null || raw.isEmpty()) {
            return Collections.singletonList(
                    new AbstractMap.SimpleImmutableEntry<>("building", "yes"));
        }
        List<Map.Entry<String, String>> tags = new ArrayList<>();
        for (List<String> pair : raw) {
            if (pair.size() >= 2) {
                tags.add(new AbstractMap.SimpleImmutableEntry<>(pair.get(0), pair.get(1)));
            }
        }
        return Collections.unmodifiableList(tags);
    }

    /** Persists the default tag list. */
    public static void setDefaultTags(List<Map.Entry<String, String>> tags) {
        List<List<String>> raw = new ArrayList<>();
        for (Map.Entry<String, String> e : tags) {
            raw.add(Arrays.asList(e.getKey(), e.getValue()));
        }
        Config.getPref().putListOfLists(KEY_TAGS, raw);
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /** Whether to auto-select created ways. */
    public static boolean isAutoSelect() {
        return Config.getPref().getBoolean(KEY_AUTO_SELECT, DEFAULT_AUTO_SELECT);
    }

    public static void setAutoSelect(boolean v) {
        Config.getPref().putBoolean(KEY_AUTO_SELECT, v);
    }

    /** Maximum grid lines per axis. */
    public static int getMaxGridLines() {
        return Config.getPref().getInt(KEY_MAX_GRID_LINES, DEFAULT_MAX_GRID_LINES);
    }

    public static void setMaxGridLines(int n) {
        Config.getPref().putInt(KEY_MAX_GRID_LINES, n);
    }

    /** Pixel radius for edge handle hover detection. */
    public static int getHandleRadius() {
        return Config.getPref().getInt(KEY_HANDLE_RADIUS, DEFAULT_HANDLE_RADIUS);
    }

    /** Pixel radius for snapping corners to existing OSM nodes. */
    public static int getNodeSnapRadius() {
        return Config.getPref().getInt(KEY_NODE_SNAP_RADIUS, DEFAULT_NODE_SNAP_RADIUS);
    }

    /** Persists the node snap radius. */
    public static void setNodeSnapRadius(int px) {
        Config.getPref().putInt(KEY_NODE_SNAP_RADIUS, px);
    }

    /** Whether closed ways use counter-clockwise winding (true) or clockwise (false). */
    public static boolean isCcwWinding() {
        return Config.getPref().getBoolean(KEY_CCW_WINDING, DEFAULT_CCW_WINDING);
    }

    public static void setCcwWinding(boolean ccw) {
        Config.getPref().putBoolean(KEY_CCW_WINDING, ccw);
    }

    /** Whether to auto-simplify (remove collinear nodes) when finishing. */
    public static boolean isAutoSimplify() {
        return Config.getPref().getBoolean(KEY_AUTO_SIMPLIFY, DEFAULT_AUTO_SIMPLIFY);
    }

    public static void setAutoSimplify(boolean v) {
        Config.getPref().putBoolean(KEY_AUTO_SIMPLIFY, v);
    }

    /** Whether to auto-shrinkwrap (resolve self-intersections) when finishing. */
    public static boolean isAutoShrinkwrap() {
        return Config.getPref().getBoolean(KEY_AUTO_SHRINKWRAP, DEFAULT_AUTO_SHRINKWRAP);
    }

    public static void setAutoShrinkwrap(boolean v) {
        Config.getPref().putBoolean(KEY_AUTO_SHRINKWRAP, v);
    }

    // ------------------------------------------------------------------
    // Colors (stored as ARGB int)
    // ------------------------------------------------------------------

    /** Grid line color. Default: steel blue, drafting style. */
    public static Color getGridColor() {
        return getColor(KEY_COLOR_GRID, new Color(100, 130, 180, 120));
    }

    /** Rectangle preview stroke color. Default: dark navy. */
    public static Color getRectColor() {
        return getColor(KEY_COLOR_RECT, new Color(30, 60, 120, 220));
    }

    /** Anchor dot color. Default: red. */
    public static Color getAnchorColor() {
        return getColor(KEY_COLOR_ANCHOR, new Color(220, 50, 50));
    }

    /** Target / snapped corner dot color. Default: white. */
    public static Color getTargetColor() {
        return getColor(KEY_COLOR_TARGET, new Color(255, 255, 255));
    }

    /** Edge handle dot color. Default: white. */
    public static Color getHandleColor() {
        return getColor(KEY_COLOR_HANDLE, new Color(255, 255, 255));
    }

    /** Extrude preview fill color. Default: light steel blue. */
    public static Color getExtrudeColor() {
        return getColor(KEY_COLOR_EXTRUDE, new Color(100, 130, 180, 50));
    }

    // ------------------------------------------------------------------
    // Unit conversion
    // ------------------------------------------------------------------

    /** Converts metres to feet (exact factor). */
    public static double metresToFeet(double metres) {
        return metres / 0.3048;
    }

    /** Converts feet to metres (exact factor). */
    public static double feetToMetres(double feet) {
        return feet * 0.3048;
    }

    /**
     * Formats a metre value in the user's chosen display unit,
     * rounding to a reasonable number of decimal places.
     */
    public static String formatStep(double metres) {
        if ("ft".equals(getStepUnit())) {
            double ft = metresToFeet(metres);
            return formatNumber(ft) + " ft";
        }
        return formatNumber(metres) + " m";
    }

    /**
     * Formats a step pair as "X × Y unit" or "X unit" if equal.
     */
    public static String formatStepPair(double xMetres, double yMetres) {
        if (Math.abs(xMetres - yMetres) < 1e-9) {
            return formatStep(xMetres);
        }
        String unit = getStepUnit();
        double xDisp = "ft".equals(unit) ? metresToFeet(xMetres) : xMetres;
        double yDisp = "ft".equals(unit) ? metresToFeet(yMetres) : yMetres;
        return formatNumber(xDisp) + " \u00d7 " + formatNumber(yDisp) + " " + unit;
    }

    /**
     * Formats a number with up to 6 decimal places, trimming trailing zeros.
     * Produces clean output like "1 ft" instead of "1.000000 ft".
     */
    private static String formatNumber(double value) {
        // Use enough precision to avoid loss, then strip trailing zeros.
        // Locale.ROOT ensures a dot decimal separator everywhere.
        String s = String.format(java.util.Locale.ROOT, "%.6f", value);
        s = s.replaceAll("0+$", "");
        s = s.replaceAll("\\.$", "");
        return s;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Persists a color for the given key. */
    public static void setGridColor(Color c) { setColor(KEY_COLOR_GRID, c); }
    public static void setRectColor(Color c) { setColor(KEY_COLOR_RECT, c); }
    public static void setAnchorColor(Color c) { setColor(KEY_COLOR_ANCHOR, c); }
    public static void setTargetColor(Color c) { setColor(KEY_COLOR_TARGET, c); }
    public static void setHandleColor(Color c) { setColor(KEY_COLOR_HANDLE, c); }
    public static void setExtrudeColor(Color c) { setColor(KEY_COLOR_EXTRUDE, c); }

    private static Color getColor(String key, Color defaultColor) {
        int argb = Config.getPref().getInt(key, defaultColor.getRGB());
        return new Color(argb, true);
    }

    private static void setColor(String key, Color color) {
        Config.getPref().putInt(key, color.getRGB());
    }
}
