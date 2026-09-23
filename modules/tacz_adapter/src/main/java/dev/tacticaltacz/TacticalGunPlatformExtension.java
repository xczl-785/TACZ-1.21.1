package dev.tacticaltacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.extension.GunPlatformExtension;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.TacHitResult;
import dev.tacticalcombat.api.BallisticProfile;
import dev.tacticalcombat.api.BulletImpact;
import dev.tacticalcombat.api.BulletImpactEvent;
import dev.tacticalcombat.api.BulletTraceEvent;
import dev.tacticalcombat.api.FirearmBallistics;
import dev.tacticalcombat.api.ProjectileContinuation;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.commons.lang3.tuple.Pair;

/** NewMod's implementation of the stable TaCZ extension surface. */
public final class TacticalGunPlatformExtension implements GunPlatformExtension {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static TacticalGunPlatformExtension installed;
    private final Map<EntityKineticBullet, ImpactState> impacts = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class ImpactState {
        BallisticProfile ammunition;
        BulletImpactEvent impact;
        boolean continued;
        double nativeScale = 1;
        int ricochetCount;
        final Set<UUID> resolved = new java.util.HashSet<>();
    }
    private record ContinuationPayload(ProjectileContinuation continuation, double nativeScale) {}

    public TacticalGunPlatformExtension() { installed = this; }
    public static TacticalGunPlatformExtension installed() {
        com.tacz.guns.api.extension.GunPlatformExtensions.current();
        if (installed == null) throw new IllegalStateException("TaCZ tactical platform extension is not installed");
        return installed;
    }
    private ImpactState state(EntityKineticBullet bullet) { return impacts.computeIfAbsent(bullet, ignored -> new ImpactState()); }
    public BallisticProfile ammunition(EntityKineticBullet bullet) { return state(bullet).ammunition; }
    public void setAmmunition(EntityKineticBullet bullet, BallisticProfile ammunition) { state(bullet).ammunition = ammunition; }
    public int ricochetCount(EntityKineticBullet bullet) { return state(bullet).ricochetCount; }
    public void setRicochetCount(EntityKineticBullet bullet, int count) { state(bullet).ricochetCount = count; }

    @Override public void register(IEventBus modBus) {
        dev.weaponruntime.WeaponRuntime.register(modBus);
        TacticalTaczAdapter.registerInfrastructure(modBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::prepareFeedback);
    }

    @Override public ItemStack createGunStack(Item item, int count) {
        return item instanceof dev.tacticaltacz.assembled.AssemblyGunItem assembled && assembled.weapon().nativeRig
                ? assembled.weapon().preset() : new ItemStack(item, count);
    }
    @Override public boolean keepPresetFireMode(ItemStack stack) {
        return stack.getItem() instanceof dev.tacticaltacz.assembled.AssemblyGunItem assembled && assembled.weapon().nativeRig;
    }
    @Override public ItemStack creativeStack(ResourceLocation gunId, ItemStack fallback) {
        var assembled = dev.tacticaltacz.assembled.AssembledWeapons.byId(gunId);
        return assembled != null && assembled.nativeRig ? assembled.preset() : fallback;
    }
    @Override public boolean allowsGun(ResourceLocation gunId) {
        return gunId != null && dev.tacticaltacz.assembled.AssembledWeapons.byId(gunId) != null;
    }

    @Override public boolean managesAmmunition(ItemStack gun) { return AmmoBridge.managed(gun); }
    @Override public Optional<Boolean> canReload(LivingEntity shooter, ItemStack gun) {
        if (AssemblyFireGate.outOfScope(gun)) return Optional.of(false);
        if (!AmmoBridge.managed(gun)) return Optional.empty();
        var item = (IGun) gun.getItem();
        var assembled = dev.tacticaltacz.assembled.AssembledWeapons.from(gun);
        if (assembled != null && !assembled.hasFeedContainer(gun)) return Optional.of(false);
        boolean room = TimelessAPI.getCommonGunIndex(item.getGunId(gun)).map(index ->
                item.getCurrentAmmoCount(gun) < AttachmentDataUtils.getAmmoCountWithAttachment(gun, index.getGunData())).orElse(false);
        return Optional.of(room && !item.useInventoryAmmo(gun) && !item.useDummyAmmo(gun)
                && shooter instanceof Player player && AmmoBridge.hasAmmo(player, gun));
    }
    @Override public boolean dropAllAmmo(Player player, ItemStack gun) {
        if (!AmmoBridge.managed(gun)) return false;
        if (player.level().isClientSide) return true;
        var ammunition = AmmoBridge.ammunition(gun);
        if (ammunition == null) return true;
        var item = (IGun) gun.getItem();
        int count = item.getCurrentAmmoCount(gun);
        item.setCurrentAmmoCount(gun, 0);
        while (count > 0) {
            int amount = Math.min(ammunition.getDefaultMaxStackSize(), count);
            var returned = new ItemStack(ammunition, amount);
            if (!(player instanceof ServerPlayer server) || !AmmoBridge.refund(server, java.util.List.of(returned)))
                player.drop(returned, false);
            count -= amount;
        }
        return true;
    }
    @Override public boolean matchesAmmunition(ItemStack gun, ItemStack ammunition) { return AmmoBridge.matches(gun, ammunition); }
    @Override @SuppressWarnings("unchecked") public <T> T modifyAmmunitionProperty(ItemStack gun, GunProperty<?> property, Class<T> type, T value) {
        var entry = AmmoBridge.definition(gun);
        return entry != null && property == GunProperties.AMMO_SPEED ? (T) (Object) entry.initialSpeed() : value;
    }
    @Override @SuppressWarnings("unchecked") public <T> T modifyAmmunitionProperty(ItemStack gun, String property, Class<T> type, T value) {
        var entry = AmmoBridge.definition(gun);
        return entry != null && property.equals(GunProperties.RuntimeOnly.BULLET_AMOUNT) ? (T) (Object) entry.projectileCount() : value;
    }
    @Override public boolean hasSelectedAmmunition(ItemStack gun) { return AmmoBridge.ammunition(gun) != null; }
    @Override public int consumeAmmunition(ServerPlayer player, ItemStack gun, int amount) { return AmmoBridge.consume(player, gun, amount); }
    @Override public boolean hasAmmunition(Player player, ItemStack gun) { return AmmoBridge.hasAmmo(player, gun); }
    @Override public int reserveAmmunition(Player player, ItemStack gun) { return AmmoBridge.reserveCount(player, gun); }

    @Override public boolean blockFire(ItemStack gun) { return AssemblyFireGate.blocked(gun); }
    @Override public boolean blockAim(LivingEntity shooter, ItemStack gun) {
        if (AssemblyFireGate.outOfScope(gun)) return true;
        if (dev.tacticaltacz.assembled.NativeAttachmentProjection.blocksAim(gun)) return true;
        return shooter instanceof ServerPlayer player && GunAdoption.contains(gun)
                && !dev.tacticalcharacter.resource.PlayerResources.canAim(player);
    }

    @Override public void initializeProjectile(EntityKineticBullet bullet, ItemStack gun) {
        var snapshot = AmmoBridge.snapshot(gun);
        if (snapshot != null) state(bullet).ammunition = FirearmBallistics.profile(snapshot);
    }
    @Override public boolean continued(EntityKineticBullet bullet) { return state(bullet).continued; }
    @Override public Optional<TraceResult> trace(EntityKineticBullet bullet, Entity target, Vec3 start, Vec3 end) {
        if (bullet.level().isClientSide || state(bullet).ammunition == null) return Optional.empty();
        var event = NeoForge.EVENT_BUS.post(new BulletTraceEvent(bullet, target, start, end));
        return event.claimed() ? Optional.of(new TraceResult(event.result())) : Optional.empty();
    }
    @Override public boolean quoteImpact(EntityKineticBullet bullet, TacHitResult hit, Vec3 start, Vec3 end) {
        var state = state(bullet);
        state.impact = null;
        if (state.ammunition == null || bullet.level().isClientSide || !(hit.getEntity() instanceof LivingEntity)
                || hit.getEntity() instanceof com.tacz.guns.api.entity.ITargetEntity) return false;
        if (state.resolved.contains(hit.getEntity().getUUID())) return true;
        state.impact = NeoForge.EVENT_BUS.post(new BulletImpactEvent(new BulletImpact(state.ammunition, bullet,
                hit.getEntity(), hit.getLocation(), start, end, state.ricochetCount)));
        return false;
    }
    @Override public void applyImpact(EntityKineticBullet bullet, EntityKineticBullet.MaybeMultipartEntity parts,
                                      float nativeDamage, Pair<DamageSource, DamageSource> sources, Runnable nativeAttack) {
        var state = state(bullet);
        if (state.impact == null || !state.impact.claimed() || state.impact.impact().target() != parts.hitPart()) {
            nativeAttack.run();
            return;
        }
        if (!state.resolved.add(parts.hitPart().getUUID())) return;
        parts.core().invulnerableTime = 0;
        if (state.impact.apply(TacticalTaczAdapter.resolvedSource(bullet)))
            state.impact.takeContinuation().ifPresent(continuation -> continueProjectile(bullet, state, continuation));
    }
    private void continueProjectile(EntityKineticBullet bullet, ImpactState state, ProjectileContinuation continuation) {
        float oldDamage = state.ammunition.fleshDamage();
        if (!(oldDamage > 0)) {
            LOGGER.error("Rejected zero-damage ricochet continuation projectile={}", bullet.getUUID());
            return;
        }
        state.continued = true;
        double scale = state.nativeScale * continuation.ammunition().fleshDamage() / oldDamage;
        bullet.spawnExtensionContinuation(continuation.position(), continuation.velocity(), scale,
                new ContinuationPayload(continuation, scale));
    }
    @Override public float scaleDamage(EntityKineticBullet bullet, float nativeDamage) {
        double scale = state(bullet).nativeScale;
        return scale == 1 ? nativeDamage : (float) (nativeDamage * scale);
    }
    @Override public boolean hasContinuation(EntityKineticBullet bullet) { return state(bullet).ricochetCount > 0; }
    @Override public void initializeContinuation(EntityKineticBullet bullet, Object payload) {
        var continuation = (ContinuationPayload) payload;
        var state = state(bullet);
        state.ammunition = continuation.continuation().ammunition();
        state.ricochetCount = continuation.continuation().ricochetCount();
        state.nativeScale = continuation.nativeScale();
    }
    @Override public void prepareFeedback(EntityHurtByGunEvent.Pre event) {
        if (!(event.getBullet() instanceof EntityKineticBullet bullet)) return;
        var quote = state(bullet).impact;
        if (quote != null && quote.claimed() && quote.impact().target() == event.getHurtEntity()) {
            event.setBaseAmount(quote.damage());
            event.setHeadshotMultiplier(1);
        }
    }
}
