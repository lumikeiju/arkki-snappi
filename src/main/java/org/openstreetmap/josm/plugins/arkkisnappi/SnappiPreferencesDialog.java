// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;

/**
 * Preferences panel for the arkki-snappi plugin, shown as a tab in
 * JOSM's main Preferences window (Edit → Preferences → Snappi).
 *
 * <p>Provides controls for:</p>
 * <ul>
 *   <li>Snap step size and display unit (ft / m)</li>
 *   <li>Default tag table with preset buttons</li>
 *   <li>Behaviour toggles (auto-select, address dialog, winding order)</li>
 *   <li>Node snap radius and grid rendering limits</li>
 *   <li>Color customisation with theme presets</li>
 * </ul>
 *
 * @author Lumikeiju
 */
public class SnappiPreferencesDialog extends DefaultTabPreferenceSetting {

    // ------------------------------------------------------------------
    // UI components (created in addGui)
    // ------------------------------------------------------------------

    private JSpinner stepXSpinner;
    private JSpinner stepYSpinner;
    private JCheckBox linkedCheck;
    private boolean updatingLinked;
    private JComboBox<String> unitCombo;
    private DefaultTableModel tagTableModel;
    private JCheckBox autoSelectCheck;

    private JCheckBox ccwWindingCheck;
    private JCheckBox autoSimplifyCheck;
    private JCheckBox autoShrinkwrapCheck;
    private JSpinner maxGridLinesSpinner;
    private JSpinner nodeSnapRadiusSpinner;

    // --- Color swatches ---
    private JButton gridColorBtn;
    private JButton rectColorBtn;
    private JButton anchorColorBtn;
    private JButton targetColorBtn;
    private JButton handleColorBtn;
    private JButton extrudeColorBtn;

    private Color gridColor;
    private Color rectColor;
    private Color anchorColor;
    private Color targetColor;
    private Color handleColor;
    private Color extrudeColor;

    // ------------------------------------------------------------------
    // Tag presets
    // ------------------------------------------------------------------

    private static final String[][] TAG_PRESETS = {
            {"building", "yes"},
            {"building", "house"},
            {"building", "garage"},
            {"building", "shed"},
            {"leisure", "pitch"},
            {"amenity", "parking"},
    };

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Creates the preference setting descriptor.
     */
    public SnappiPreferencesDialog() {
        super("preferences/arkkisnappi",
                tr("Snappi"),
                tr("Settings for the arkki-snappi building drawing plugin"));
    }

    // ------------------------------------------------------------------
    // Build UI — called by JOSM to populate the tab
    // ------------------------------------------------------------------

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        JPanel outerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints outerGbc = new GridBagConstraints();
        outerGbc.gridx = 0;
        outerGbc.fill = GridBagConstraints.HORIZONTAL;
        outerGbc.weightx = 1.0;
        outerGbc.insets = new Insets(2, 0, 2, 0);

        // === Snap Settings Section ===
        JPanel snapPanel = new JPanel(new GridBagLayout());
        snapPanel.setBorder(BorderFactory.createTitledBorder(tr("Snap Settings")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // Step X
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        snapPanel.add(new JLabel(tr("Step X:")), gbc);

        double currentStepX = SnappiPreferences.getStepXInDisplayUnit();
        stepXSpinner = new JSpinner(
                new SpinnerNumberModel(currentStepX, 0.0001, 10000.0, 0.1));
        stepXSpinner.setEditor(new JSpinner.NumberEditor(stepXSpinner, "0.######"));
        gbc.gridx = 1;
        snapPanel.add(stepXSpinner, gbc);

        unitCombo = new JComboBox<>(new String[]{"ft", "m"});
        unitCombo.setSelectedItem(SnappiPreferences.getStepUnit());
        gbc.gridx = 2;
        snapPanel.add(unitCombo, gbc);

        // Step Y
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        snapPanel.add(new JLabel(tr("Step Y:")), gbc);

        double currentStepY = SnappiPreferences.getStepYInDisplayUnit();
        stepYSpinner = new JSpinner(
                new SpinnerNumberModel(currentStepY, 0.0001, 10000.0, 0.1));
        stepYSpinner.setEditor(new JSpinner.NumberEditor(stepYSpinner, "0.######"));
        gbc.gridx = 1;
        snapPanel.add(stepYSpinner, gbc);

        linkedCheck = new JCheckBox(tr("Linked"), SnappiPreferences.isLinkedSteps());
        gbc.gridx = 2;
        snapPanel.add(linkedCheck, gbc);

        // Wire linked behaviour
        stepXSpinner.addChangeListener(e -> {
            if (!updatingLinked && linkedCheck.isSelected()) {
                updatingLinked = true;
                stepYSpinner.setValue(stepXSpinner.getValue());
                updatingLinked = false;
            }
        });
        stepYSpinner.addChangeListener(e -> {
            if (!updatingLinked && linkedCheck.isSelected()) {
                updatingLinked = true;
                stepXSpinner.setValue(stepYSpinner.getValue());
                updatingLinked = false;
            }
        });

        // Node snap radius
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        snapPanel.add(new JLabel(tr("Node snap radius (px):")), gbc);
        nodeSnapRadiusSpinner = new JSpinner(
                new SpinnerNumberModel(SnappiPreferences.getNodeSnapRadius(), 0, 200, 1));
        gbc.gridx = 1;
        snapPanel.add(nodeSnapRadiusSpinner, gbc);
        gbc.gridx = 2;
        JLabel snapHint = new JLabel(tr("(0 = disabled)"));
        snapHint.setFont(snapHint.getFont().deriveFont(Font.ITALIC));
        snapPanel.add(snapHint, gbc);

        // Max grid lines
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        snapPanel.add(new JLabel(tr("Max grid lines per axis:")), gbc);
        maxGridLinesSpinner = new JSpinner(
                new SpinnerNumberModel(SnappiPreferences.getMaxGridLines(), 10, 10000, 10));
        gbc.gridx = 1;
        snapPanel.add(maxGridLinesSpinner, gbc);

        outerGbc.gridy = 0;
        outerPanel.add(snapPanel, outerGbc);

        // === Default Tags Section ===
        JPanel tagPanel = new JPanel(new GridBagLayout());
        tagPanel.setBorder(BorderFactory.createTitledBorder(tr("Default Tags")));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        row = 0;

        tagTableModel = new DefaultTableModel(
                new String[]{tr("Key"), tr("Value")}, 0);
        for (Map.Entry<String, String> tag : SnappiPreferences.getDefaultTags()) {
            tagTableModel.addRow(new Object[]{tag.getKey(), tag.getValue()});
        }
        JTable tagTable = new JTable(tagTableModel);
        JScrollPane tagScroll = new JScrollPane(tagTable);
        tagScroll.setPreferredSize(new Dimension(340, 80));
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        tagPanel.add(tagScroll, gbc);

        // Tag add/remove buttons
        row++;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        JPanel tagButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addTagBtn = new JButton(new AbstractAction(tr("Add row")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                tagTableModel.addRow(new Object[]{"", ""});
            }
        });
        JButton removeTagBtn = new JButton(new AbstractAction(tr("Remove selected")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                int sel = tagTable.getSelectedRow();
                if (sel >= 0) {
                    tagTableModel.removeRow(sel);
                }
            }
        });
        tagButtons.add(addTagBtn);
        tagButtons.add(removeTagBtn);
        tagPanel.add(tagButtons, gbc);

        // Tag presets
        row++;
        gbc.gridy = row;
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presetPanel.add(new JLabel(tr("Presets:")));
        for (String[] preset : TAG_PRESETS) {
            String label = preset[0] + "=" + preset[1];
            JButton btn = new JButton(label);
            btn.addActionListener(e -> {
                tagTableModel.setRowCount(0);
                tagTableModel.addRow(new Object[]{preset[0], preset[1]});
            });
            presetPanel.add(btn);
        }
        tagPanel.add(presetPanel, gbc);

        outerGbc.gridy = 1;
        outerPanel.add(tagPanel, outerGbc);

        // === Behaviour Section ===
        JPanel behaviourPanel = new JPanel(new GridBagLayout());
        behaviourPanel.setBorder(BorderFactory.createTitledBorder(tr("Behaviour")));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        row = 0;

        autoSelectCheck = new JCheckBox(
                tr("Auto-select created way"), SnappiPreferences.isAutoSelect());
        gbc.gridy = row++;
        behaviourPanel.add(autoSelectCheck, gbc);

        ccwWindingCheck = new JCheckBox(
                tr("Counter-clockwise winding order"), SnappiPreferences.isCcwWinding());
        gbc.gridy = row++;
        behaviourPanel.add(ccwWindingCheck, gbc);

        autoSimplifyCheck = new JCheckBox(
                tr("Auto-simplify on finish (remove collinear nodes)"),
                SnappiPreferences.isAutoSimplify());
        gbc.gridy = row++;
        behaviourPanel.add(autoSimplifyCheck, gbc);

        autoShrinkwrapCheck = new JCheckBox(
                tr("Auto-shrinkwrap on finish (resolve self-intersections)"),
                SnappiPreferences.isAutoShrinkwrap());
        gbc.gridy = row++;
        behaviourPanel.add(autoShrinkwrapCheck, gbc);

        outerGbc.gridy = 2;
        outerPanel.add(behaviourPanel, outerGbc);

        // === Colors Section ===
        JPanel colorPanel = new JPanel(new GridBagLayout());
        colorPanel.setBorder(BorderFactory.createTitledBorder(tr("Colors")));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        row = 0;

        gridColor = SnappiPreferences.getGridColor();
        rectColor = SnappiPreferences.getRectColor();
        anchorColor = SnappiPreferences.getAnchorColor();
        targetColor = SnappiPreferences.getTargetColor();
        handleColor = SnappiPreferences.getHandleColor();
        extrudeColor = SnappiPreferences.getExtrudeColor();

        gridColorBtn = createColorButton(gridColor);
        rectColorBtn = createColorButton(rectColor);
        anchorColorBtn = createColorButton(anchorColor);
        targetColorBtn = createColorButton(targetColor);
        handleColorBtn = createColorButton(handleColor);
        extrudeColorBtn = createColorButton(extrudeColor);

        gridColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(gridColorBtn, tr("Grid color"), gridColor);
            if (c != null) { gridColor = c; updateSwatch(gridColorBtn, c); }
        });
        rectColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(rectColorBtn, tr("Rectangle color"), rectColor);
            if (c != null) { rectColor = c; updateSwatch(rectColorBtn, c); }
        });
        anchorColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(anchorColorBtn, tr("Anchor dot color"), anchorColor);
            if (c != null) { anchorColor = c; updateSwatch(anchorColorBtn, c); }
        });
        targetColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(targetColorBtn, tr("Target dot color"), targetColor);
            if (c != null) { targetColor = c; updateSwatch(targetColorBtn, c); }
        });
        handleColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(handleColorBtn, tr("Handle dot color"), handleColor);
            if (c != null) { handleColor = c; updateSwatch(handleColorBtn, c); }
        });
        extrudeColorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(extrudeColorBtn, tr("Extrude fill color"), extrudeColor);
            if (c != null) { extrudeColor = c; updateSwatch(extrudeColorBtn, c); }
        });

        row = addColorRow(colorPanel, gbc, row, tr("Grid lines:"), gridColorBtn);
        row = addColorRow(colorPanel, gbc, row, tr("Rectangle:"), rectColorBtn);
        row = addColorRow(colorPanel, gbc, row, tr("Anchor dot:"), anchorColorBtn);
        row = addColorRow(colorPanel, gbc, row, tr("Target dot:"), targetColorBtn);
        row = addColorRow(colorPanel, gbc, row, tr("Edge handle:"), handleColorBtn);
        row = addColorRow(colorPanel, gbc, row, tr("Extrude fill:"), extrudeColorBtn);

        // Theme presets row
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        themePanel.add(new JLabel(tr("Theme:")));

        JButton defaultTheme = new JButton(tr("Blueprint"));
        defaultTheme.addActionListener(e -> applyTheme(
                new Color(100, 130, 180, 120),
                new Color(30, 60, 120, 220),
                new Color(220, 50, 50),
                new Color(255, 255, 255),
                new Color(255, 255, 255),
                new Color(100, 130, 180, 50)));
        themePanel.add(defaultTheme);

        JButton satelliteTheme = new JButton(tr("Satellite"));
        satelliteTheme.addActionListener(e -> applyTheme(
                new Color(0, 220, 255, 140),
                new Color(0, 120, 255, 220),
                new Color(255, 40, 40),
                new Color(40, 255, 40),
                new Color(255, 220, 0),
                new Color(0, 120, 255, 50)));
        themePanel.add(satelliteTheme);

        JButton neonTheme = new JButton(tr("Neon"));
        neonTheme.addActionListener(e -> applyTheme(
                new Color(255, 0, 200, 140),
                new Color(0, 255, 120, 220),
                new Color(255, 40, 40),
                new Color(0, 255, 255),
                new Color(255, 255, 255),
                new Color(0, 255, 120, 50)));
        themePanel.add(neonTheme);

        JButton inkTheme = new JButton(tr("Ink"));
        inkTheme.addActionListener(e -> applyTheme(
                new Color(60, 60, 60, 100),
                new Color(20, 20, 20, 220),
                new Color(180, 40, 40),
                new Color(40, 140, 40),
                new Color(160, 140, 40),
                new Color(60, 60, 60, 40)));
        themePanel.add(inkTheme);

        colorPanel.add(themePanel, gbc);

        outerGbc.gridy = 3;
        outerPanel.add(colorPanel, outerGbc);

        // Wrap and add to the preference tabbed pane
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(outerPanel, BorderLayout.NORTH);
        GridBagConstraints tabGbc = new GridBagConstraints();
        tabGbc.fill = GridBagConstraints.BOTH;
        tabGbc.weightx = 1.0;
        tabGbc.weighty = 1.0;
        gui.createPreferenceTab(this).add(wrapper, tabGbc);
    }

    // ------------------------------------------------------------------
    // OK handler — called by JOSM when user clicks OK in Preferences
    // ------------------------------------------------------------------

    @Override
    public boolean ok() {
        // Step sizes — convert from display unit to metres
        double displayX = ((Number) stepXSpinner.getValue()).doubleValue();
        double displayY = ((Number) stepYSpinner.getValue()).doubleValue();
        String unit = (String) unitCombo.getSelectedItem();
        double metresX = "ft".equals(unit)
                ? SnappiPreferences.feetToMetres(displayX)
                : displayX;
        double metresY = "ft".equals(unit)
                ? SnappiPreferences.feetToMetres(displayY)
                : displayY;
        SnappiPreferences.setStepXMetres(metresX);
        SnappiPreferences.setStepYMetres(metresY);
        SnappiPreferences.setLinkedSteps(linkedCheck.isSelected());
        SnappiPreferences.setStepUnit(unit);

        // Tags
        List<Map.Entry<String, String>> tags = new ArrayList<>();
        for (int i = 0; i < tagTableModel.getRowCount(); i++) {
            String key = String.valueOf(tagTableModel.getValueAt(i, 0)).trim();
            String val = String.valueOf(tagTableModel.getValueAt(i, 1)).trim();
            if (!key.isEmpty()) {
                tags.add(new AbstractMap.SimpleImmutableEntry<>(key, val));
            }
        }
        SnappiPreferences.setDefaultTags(tags);

        // Behaviour
        SnappiPreferences.setAutoSelect(autoSelectCheck.isSelected());
        SnappiPreferences.setCcwWinding(ccwWindingCheck.isSelected());
        SnappiPreferences.setAutoSimplify(autoSimplifyCheck.isSelected());
        SnappiPreferences.setAutoShrinkwrap(autoShrinkwrapCheck.isSelected());
        SnappiPreferences.setMaxGridLines(((Number) maxGridLinesSpinner.getValue()).intValue());
        SnappiPreferences.setNodeSnapRadius(((Number) nodeSnapRadiusSpinner.getValue()).intValue());

        // Colors
        SnappiPreferences.setGridColor(gridColor);
        SnappiPreferences.setRectColor(rectColor);
        SnappiPreferences.setAnchorColor(anchorColor);
        SnappiPreferences.setTargetColor(targetColor);
        SnappiPreferences.setHandleColor(handleColor);
        SnappiPreferences.setExtrudeColor(extrudeColor);

        return false; // no restart required
    }

    // ------------------------------------------------------------------
    // Color helpers
    // ------------------------------------------------------------------

    private static JButton createColorButton(Color color) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(28, 28));
        updateSwatch(btn, color);
        return btn;
    }

    private static void updateSwatch(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setForeground(color);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        btn.repaint();
    }

    private static int addColorRow(JPanel panel, GridBagConstraints gbc,
                                   int row, String label, JButton swatch) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(swatch, gbc);
        return row + 1;
    }

    private void applyTheme(Color grid, Color rect, Color anchor,
                            Color target, Color handle, Color extrude) {
        gridColor = grid;
        rectColor = rect;
        anchorColor = anchor;
        targetColor = target;
        handleColor = handle;
        extrudeColor = extrude;
        updateSwatch(gridColorBtn, grid);
        updateSwatch(rectColorBtn, rect);
        updateSwatch(anchorColorBtn, anchor);
        updateSwatch(targetColorBtn, target);
        updateSwatch(handleColorBtn, handle);
        updateSwatch(extrudeColorBtn, extrude);
    }
}
