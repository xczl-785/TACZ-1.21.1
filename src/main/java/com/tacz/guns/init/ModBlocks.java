package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Shared ballistic tag; no TaCZ blocks are registered. */
public final class ModBlocks {
    public static final TagKey<Block> BULLET_IGNORE_BLOCKS = BlockTags.create(ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "bullet_ignore"));
    private ModBlocks() {}
}
