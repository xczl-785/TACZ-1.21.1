package com.tacz.guns.ammunition;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * TEMPORARY refund interface: one fixed Tarkov round per caliber, not the loaded variant.
 * Replace this call with exact-variant refund planning once gun identity is integrated.
 * Empty means unsupported: the caller MUST leave the gun's rounds intact.
 * Planning does not change the gun, player inventory, or chamber.
 */
public final class TemporaryAmmoRefund {
    private TemporaryAmmoRefund() {}

    public static Optional<List<ItemStack>> plan(ResourceLocation caliber, int count) {
        return TemporaryAmmoRefundPolicy.sourceId(caliber.toString()).flatMap(sourceId -> {
            var holder = AmmunitionRegistry.AMMUNITION.get(sourceId);
            if (holder == null) return Optional.empty();
            var item = holder.get();
            return Optional.of(TemporaryAmmoRefundPolicy.split(count, item.getDefaultMaxStackSize())
                    .stream().map(amount -> new ItemStack(item, amount)).toList());
        });
    }
}
