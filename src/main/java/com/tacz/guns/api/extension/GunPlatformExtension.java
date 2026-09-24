package com.tacz.guns.api.extension;

import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import org.apache.commons.lang3.tuple.Pair;

/** Optional platform integration owned by TaCZ. Defaults preserve standalone TaCZ behavior. */
public interface GunPlatformExtension {
    record TraceResult(Optional<Vec3> point) {
        public TraceResult { point = point == null ? Optional.empty() : point; }
    }

    default void register(IEventBus modBus) {}
    default ItemStack createGunStack(Item item, int count) { return new ItemStack(item, count); }
    default boolean keepPresetFireMode(ItemStack stack) { return false; }
    default ItemStack creativeStack(ResourceLocation gunId, ItemStack fallback) { return fallback; }
    /** Scope of gun content exposed by this platform; standalone TaCZ keeps all loaded packs. */
    default boolean allowsGun(ResourceLocation gunId) { return true; }

    default boolean managesAmmunition(ItemStack gun) { return false; }
    default Optional<Boolean> canReload(LivingEntity shooter, ItemStack gun) { return Optional.empty(); }
    default boolean dropAllAmmo(Player player, ItemStack gun) { return false; }
    default boolean matchesAmmunition(ItemStack gun, ItemStack ammunition) { return false; }
    default <T> T modifyAmmunitionProperty(ItemStack gun, GunProperty<?> property, Class<T> type, T value) { return value; }
    default <T> T modifyAmmunitionProperty(ItemStack gun, String property, Class<T> type, T value) { return value; }
    default boolean hasSelectedAmmunition(ItemStack gun) { return true; }
    default int consumeAmmunition(ServerPlayer player, ItemStack gun, int amount) { return 0; }
    default boolean hasAmmunition(Player player, ItemStack gun) { return false; }
    default int reserveAmmunition(Player player, ItemStack gun) { return 0; }

    default boolean blockFire(ItemStack gun) { return false; }
    default boolean blockAim(LivingEntity shooter, ItemStack gun) { return false; }

    default void initializeProjectile(EntityKineticBullet bullet, ItemStack gun) {}
    default boolean continued(EntityKineticBullet bullet) { return false; }
    default Optional<TraceResult> trace(EntityKineticBullet bullet, Entity target, Vec3 start, Vec3 end) { return Optional.empty(); }
    default boolean quoteImpact(EntityKineticBullet bullet, TacHitResult hit, Vec3 start, Vec3 end) { return false; }
    default void applyImpact(EntityKineticBullet bullet, EntityKineticBullet.MaybeMultipartEntity parts,
                             float nativeDamage, Pair<DamageSource, DamageSource> sources, Runnable nativeAttack) {
        nativeAttack.run();
    }
    default float scaleDamage(EntityKineticBullet bullet, float nativeDamage) { return nativeDamage; }
    default boolean hasContinuation(EntityKineticBullet bullet) { return false; }
    default void initializeContinuation(EntityKineticBullet bullet, Object payload) {}
    default void prepareFeedback(EntityHurtByGunEvent.Pre event) {}
    /** True only when a platform has emitted the ordinary block-hit display. */
    default boolean ordinaryBlockImpact(EntityKineticBullet bullet, BlockHitResult hit) { return false; }
}
