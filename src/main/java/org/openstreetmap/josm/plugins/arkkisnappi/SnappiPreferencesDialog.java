// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;

/**
 * Settings dialog for the arkki-snappi plugin.
 *
 * <p>Provides controls for:</p>
 * <ul>
 *   <li>Snap step size and display unit (ft / m)</li>
 *   <li>Default tag table with preset buttons</li>
 *   <li>Behaviour toggles (auto-select, address dialog, winding order)</li>
 *   <li>Grid rendering limits</li>
 * </ul>
 *
 * <p>Accessible via {@code More tools → Snappi settings…} or {@code Alt+Shift+B}.</p>
 *
 * @author Lumikeiju
 */
public class SnappiPreferencesDialog extends ExtendedDialog {

    // ------------------------------------------------------------------
    // UI components
    // ------------------------------------------------------------------

    private final JSpinner stepXSpinner;
    private final JSpinner stepYSpinner;
    private final JCheckBox linkedCheck;

    /** Re-entrance guard for linked spinner synchronisation. */
    private boolean updatingLinked;
    private final JComboBox<String> unitCombo;
    private final DefaultTableModel tagTableModel;
    private final JCheckBox autoSelectCheck;
    private final JCheckBox showAddressCheck;
    private final JCheckBox ccwWindingCheck;
    private final JSpinner maxGridLinesSpinner;

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
     * Creates and lays out the preferences dialog.
     */
    public SnappiPreferencesDialog() {
        super(MainApplication.getMainFrame(),
                tr("Snappi Settings"),
                new String[]{tr("OK"), tr("Cancel")});

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // ---- Step X ----
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel(tr("Step X:")), gbc);

        double currentStepX = SnappiPreferences.getStepXInDisplayUnit();
        stepXSpinner = new JSpinner(
                new SpinnerNumberModel(currentStepX, 0.0001, 10000.0, 0.1));
        JSpinner.NumberEditor editorX = new JSpinner.NumberEditor(stepXSpinner, "0.######");
        stepXSpinner.setEditor(editorX);
        gbc.gridx = 1;
        panel.add(stepXSpinner, gbc);

        unitCombo = new JComboBox<>(new String[]{"ft", "m"});
        unitCombo.setSelectedItem(SnappiPreferences.getStepUnit());
        gbc.gridx = 2;
        panel.add(unitCombo, gbc);

        // ---- Step Y ----
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(tr("Step Y:")), gbc);

        double currentStepY = SnappiPreferences.getStepYInDisplayUnit();
        stepYSpinner = new JSpinner(
                new SpinnerNumberModel(currentStepY, 0.0001, 10000.0, 0.1));
        JSpinner.NumberEditor editorY = new JSpinner.NumberEditor(stepYSpinner, "0.######");
        stepYSpinner.setEditor(editorY);
        gbc.gridx = 1;
        panel.add(stepYSpinner, gbc);

        linkedCheck = new JCheckBox(tr("Linked"), SnappiPreferences.isLinkedSteps());
        gbc.gridx = 2;
        panel.add(linkedCheck, gbc);

        // Wire linked behaviour: changing one spinner updates the other
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

        // ---- Default tags ----
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        panel.add(new JLabel(tr("Default tags:")), gbc);

        row++;
        tagTableModel = new DefaultTableModel(
                new String[]{tr("Key"), tr("Value")}, 0);
        for (Map.Entry<String, String> tag : SnappiPreferences.getDefaultTags()) {
            tagTableModel.addRow(new Object[]{tag.getKey(), tag.getValue()});
        }
        JTable tagTable = new JTable(tagTableModel);
        JScrollPane tagScroll = new JScrollPane(tagTable);
        tagScroll.setPreferredSize(new java.awt.Dimension(340, 100));
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panel.add(tagScroll, gbc);

        // Tag add/remove buttons
        row++;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        JPanel tagButtons = new JPanel();
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
        panel.add(tagButtons, gbc);

        // Tag presets
        row++;
        gbc.gridy = row;
        JPanel presetPanel = new JPanel();
        presetPanel.add(new JLabel(tr("Presets:")));
        for (String[] preset : TAG_PRESETS) {
            String label = preset[0] + "=" + preset[1];
            JButton btn = new JButton(label);
            btn.addActionListener(e -> {
                // Replace all rows with this single preset
                tagTableModel.setRowCount(0);
                tagTableModel.addRow(new Object[]{preset[0], preset[1]});
            });
            presetPanel.add(btn);
        }
        panel.add(presetPanel, gbc);

        // ---- Behaviour ----
        row++;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        autoSelectCheck = new JCheckBox(
                tr("Auto-select created way"), SnappiPreferences.isAutoSelect());
        panel.add(autoSelectCheck, gbc);

        row++;
        gbc.gridy = row;
        showAddressCheck = new JCheckBox(
                tr("Show address dialog after creation"), SnappiPreferences.isShowAddress());
        panel.add(showAddressCheck, gbc);

        row++;
        gbc.gridy = row;
        ccwWindingCheck = new JCheckBox(
                tr("Counter-clockwise winding order"), SnappiPreferences.isCcwWinding());
        panel.add(ccwWindingCheck, gbc);

        // ---- Max grid lines ----
        row++;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(new JLabel(tr("Max grid lines per axis:")), gbc);

        maxGridLinesSpinner = new JSpinner(
                new SpinnerNumberModel(SnappiPreferences.getMaxGridLines(), 10, 10000, 10));
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(maxGridLinesSpinner, gbc);

        setContent(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    // ------------------------------------------------------------------
    // OK handler
    // ------------------------------------------------------------------

    /**
     * Persists all preference values when the user clicks OK.
     */
    public void savePreferences() {
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
        SnappiPreferences.setShowAddress(showAddressCheck.isSelected());
        SnappiPreferences.setCcwWinding(ccwWindingCheck.isSelected());
        SnappiPreferences.setMaxGridLines(((Number) maxGridLinesSpinner.getValue()).intValue());
    }

    // ------------------------------------------------------------------
    // Static convenience
    // ------------------------------------------------------------------

    /**
     * Opens the settings dialog and persists changes if the user clicks OK.
     */
    public static void openSettingsDialog() {
        SnappiPreferencesDialog dlg = new SnappiPreferencesDialog();
        dlg.showDialog();
        if (dlg.getValue() == 1) { // OK
            dlg.savePreferences();
        }
    }
}
