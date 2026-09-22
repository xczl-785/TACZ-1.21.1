package com.tacz.guns.api.extension;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-scoped assembly entry routing. An external assembly entry that owns the assembly key for an
 * item answers here, so TaCZ's own refit key never builds a second screen for the same press and an
 * owner who kept a different refit binding still reaches that entry. Loaded lazily from client call
 * sites only; standalone TaCZ keeps the no-op default.
 */
public interface AssemblyEntryExtension {
    enum Route {
        /** Keep TaCZ's own behaviour: build the refit screen when the gun allows it. */
        NATIVE,
        /** Another entry handles this press; do nothing here. */
        IGNORE,
        /** Another entry owns the item but this press came from a different binding: forward it. */
        FORWARD
    }

    /** What TaCZ's refit key should do for this held item. */
    default Route route(Player player, ItemStack held) { return Route.NATIVE; }

    /** Forward one press to the external assembly entry that owns the item. */
    default void openExternalEntry(Player player) {}

    /** Key name to advertise for this item, or empty to keep TaCZ's own refit key. */
    default String assemblyKeyName(ItemStack held) { return ""; }
}
