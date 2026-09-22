package dev.tacticaltacz;

import com.tacz.guns.api.extension.AssemblyEntryExtension;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Routes the assembly press for TaCZ's assembled guns to the public workbench.
 *
 * <p>When both keys sit on the same binding the public key already handles the press, so TaCZ stays
 * out of the way and one press still has exactly one owner. When an owner kept a different refit
 * binding, that press is forwarded to the public entry instead of being dropped, so an existing
 * habit is never silently lost. Native TaCZ guns are never claimed.
 */
public final class TacticalAssemblyEntryExtension implements AssemblyEntryExtension {
    @Override public Route route(Player player, ItemStack held) {
        if (dev.firearms.workbench.WorkbenchProviders.forItem(held).isEmpty()) return Route.NATIVE;
        return sameBinding() ? Route.IGNORE : Route.FORWARD;
    }

    @Override public void openExternalEntry(Player player) {
        dev.firearms.client.workbench.WorkbenchController.open();
    }

    @Override public String assemblyKeyName(ItemStack held) {
        return dev.firearms.client.workbench.WorkbenchControls.ASSEMBLE.getName();
    }

    private static boolean sameBinding() {
        return com.tacz.guns.client.input.RefitKey.REFIT_KEY.getKey()
                .equals(dev.firearms.client.workbench.WorkbenchControls.ASSEMBLE.getKey());
    }
}
