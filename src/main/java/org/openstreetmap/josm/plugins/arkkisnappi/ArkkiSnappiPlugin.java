// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.arkkisnappi;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenu;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Entry point for the arkki-snappi plugin.
 *
 * <p>Registers {@link SnappiMode} as a new map mode when a map frame is
 * initialised, adding a toolbar toggle button that activates the snap-grid
 * building drawing mode. Also adds a "Snappi settings…" menu item under
 * "More tools" for accessing preferences.</p>
 *
 * @author Lumikeiju
 */
public class ArkkiSnappiPlugin extends Plugin {

    /**
     * Constructs the plugin from JOSM-provided metadata.
     * Registers the settings menu item.
     *
     * @param info plugin information supplied by JOSM at load time
     */
    public ArkkiSnappiPlugin(final PluginInformation info) {
        super(info);

        // Register "Snappi settings…" under More tools menu
        JMenu moreToolsMenu = MainApplication.getMenu().moreToolsMenu;
        MainMenu.add(moreToolsMenu, new SnappiSettingsAction());
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
            MainApplication.getMap().addMapMode(
                    new IconToggleButton(new SnappiMode(), false));
        }
    }

    /**
     * Menu action that opens the {@link SnappiPreferencesDialog}.
     */
    private static class SnappiSettingsAction extends JosmAction {
        SnappiSettingsAction() {
            super(tr("Snappi settings\u2026"), // ellipsis character
                    "arkkisnappi",
                    tr("Configure snap step, default tags, and behaviour for arkki-snappi"),
                    Shortcut.registerShortcut("tools:snappisettings",
                            tr("More tools: {0}", tr("Snappi settings\u2026")),
                            KeyEvent.VK_B, Shortcut.ALT_SHIFT),
                    true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            SnappiPreferencesDialog.openSettingsDialog();
        }
    }
}
