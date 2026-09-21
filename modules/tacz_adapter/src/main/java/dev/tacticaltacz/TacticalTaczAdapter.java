package dev.tacticaltacz;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.damagesource.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

public final class TacticalTaczAdapter {
    public static final String MOD_ID = "tactical_tacz_adapter";
    public static final ResourceKey<DamageType> RESOLVED = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "resolved_bullet"));
    private TacticalTaczAdapter() {}
    public static void registerInfrastructure(net.neoforged.bus.api.IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AssemblyFireGate::fire);
        bus.addListener(dev.tacticaltacz.refit.RefitProtocol::register);
        bus.addListener(dev.tacticaltacz.assembled.AssemblyGunProtocol::register);
        dev.tacticaltacz.refit.RefitBridge.register();
        NeoForge.EVENT_BUS.addListener(TacticalTaczAdapter::playerPose);
        NeoForge.EVENT_BUS.addListener((dev.tacticalcharacter.resource.ResourceActionEvent event) -> {
            if (com.tacz.guns.api.item.IGun.getIGunOrNull(event.player.getMainHandItem()) != null
                && GunAdoption.contains(event.player.getMainHandItem()))
                event.aiming = dev.tacticalcharacter.resource.PlayerResources.canAim(event.player) && com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(event.player).getSynAimingProgress() > 0;
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p && GunAdoption.contains(p.getMainHandItem())&&(!dev.tacticalcharacter.resource.PlayerResources.canAim(p)||dev.tacticaltacz.assembled.NativeAttachmentProjection.blocksAim(p.getMainHandItem())))com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(p).aim(false);});
    }
    private static void playerPose(dev.tacticalcombat.api.PlayerGunPoseEvent event) {
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(event.player.getMainHandItem());
        if(gun==null)return;
        event.unsupported=!GunAdoption.contains(event.player.getMainHandItem());
        event.holding=true;event.aiming=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(event.player).getSynAimingProgress();
    }
    public static DamageSource resolvedSource(EntityKineticBullet bullet) {
        return new DamageSource(bullet.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(RESOLVED), bullet, bullet.getOwner());
    }
}
