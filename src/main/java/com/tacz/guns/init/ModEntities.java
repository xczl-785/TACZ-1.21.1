package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, GunMod.MOD_ID);

    public static DeferredHolder<EntityType<?>, EntityType<EntityKineticBullet>> BULLET = ENTITY_TYPES.register("bullet", () -> EntityKineticBullet.TYPE);
}