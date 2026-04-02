// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.KeyEvent;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/**
 * Entry point for the arkki-snappi plugin.
 *
 * <p>Registers {@link SnappiMode} as a new map mode when a map frame is
 * initialised, adding a toolbar toggle button that activates the snap-grid
 * building drawing mode. Plugin settings are available in JOSM's main
 * Preferences window under a dedicated "Snappi" tab.</p>
 *
 * @author Lumikeiju
 */
public class ArkkiSnappiPlugin extends Plugin {

    private SnappiMode snappiMode;

    /**
     * Constructs the plugin from JOSM-provided metadata.
     *
     * @param info plugin information supplied by JOSM at load time
     */
    public ArkkiSnappiPlugin(final PluginInformation info) {
        super(info);
    }

    /**
     * Called by JOSM when the map frame changes (opened, closed, or replaced).
     * Registers the Snappi mode button on the left-hand toolbar.
     *
     * @param oldFrame the previous map frame (null on first open)
     * @param newFrame the new map frame (null when closing)
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            snappiMode = new SnappiMode();
            MainApplication.getMap().addMapMode(
                    new IconToggleButton(snappiMode, false));
        }
    }

    /**
     * Returns the preference setting that JOSM displays as a tab in the
     * main Preferences dialog.
     */
    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new SnappiPreferencesDialog();
    }
}
