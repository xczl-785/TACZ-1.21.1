package dev.tacticaltacz;

import com.tacz.guns.api.extension.AssemblyEntryExtension;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Reports that an assembled gun belongs to the public assembly workbench, so TaCZ's own refit key
 * defers instead of opening a second entry for the same press. Native TaCZ guns are never claimed.
 */
public final class TacticalAssemblyEntryExtension implements AssemblyEntryExtension {
    @Override public boolean ownsAssemblyEntry(Player player, ItemStack held) {
        return dev.firearms.workbench.WorkbenchProviders.forItem(held).isPresent();
    }

    @Override public String assemblyKeyName(ItemStack held) {
        return dev.firearms.client.workbench.WorkbenchControls.ASSEMBLE.getName();
    }
}
