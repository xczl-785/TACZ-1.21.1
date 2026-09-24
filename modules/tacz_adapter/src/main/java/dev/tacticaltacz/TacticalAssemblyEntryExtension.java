package dev.tacticaltacz;

import com.tacz.guns.api.extension.AssemblyEntryExtension;
import net.minecraft.world.item.ItemStack;

/** Advertises the public workbench entry without installing a second input handler. */
public final class TacticalAssemblyEntryExtension implements AssemblyEntryExtension {
    @Override public String assemblyKeyName(ItemStack held) {
        return dev.firearms.workbench.WorkbenchProviders.forItem(held).isPresent()
                ? dev.firearms.client.workbench.WorkbenchControls.ASSEMBLE.getName() : "";
    }
}
