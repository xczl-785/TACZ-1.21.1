package com.tacz.guns.api.extension;

import net.minecraft.world.item.ItemStack;

/** Client tooltip bridge to the public workbench; the public module owns key handling. */
public interface AssemblyEntryExtension {
    /** Public assembly key name for this item, or empty when no provider owns it. */
    default String assemblyKeyName(ItemStack held) { return ""; }
}
