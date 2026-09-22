package com.tacz.guns.api.extension;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-scoped assembly entry ownership. An external assembly entry that owns the assembly key for
 * an item reports it here, so TaCZ's own refit key and tooltip defer instead of competing for one
 * press. Loaded lazily from client call sites only; standalone TaCZ keeps the no-op default.
 */
public interface AssemblyEntryExtension {
    /** True when another entry owns the assembly key for this held item. */
    default boolean ownsAssemblyEntry(Player player, ItemStack held) { return false; }

    /** Key name to advertise for this item, or empty to keep TaCZ's own refit key. */
    default String assemblyKeyName(ItemStack held) { return ""; }
}
