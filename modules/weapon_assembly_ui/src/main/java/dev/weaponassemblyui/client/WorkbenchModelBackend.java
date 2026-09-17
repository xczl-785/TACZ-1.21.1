package dev.weaponassemblyui.client;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

/** Optional native model backend. The viewport remains the owner of camera, clipping and overlays. */
@FunctionalInterface
public interface WorkbenchModelBackend {
    void render(GUIContext context,WorkbenchViewportFrame frame);
}
