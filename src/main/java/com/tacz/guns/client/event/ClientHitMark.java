package com.tacz.guns.client.event;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.client.sound.SoundPlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(value = Dist.CLIENT)
public class ClientHitMark {
    @SubscribeEvent
    public static void onEntityHurt(EntityHurtByGunEvent.Post event) {
        LogicalSide logicalSide = event.getLogicalSide();
        if (logicalSide != LogicalSide.CLIENT) {
            return;
        }
        LivingEntity attacker = event.getAttacker();
        LocalPlayer player = Minecraft.getInstance().player;
        Entity hurtEntity = event.getHurtEntity();
        if (player != null && player.equals(attacker) && hurtEntity!=null) {
            ResourceLocation gunId = event.getGunId();
            ResourceLocation gunDisplayId = event.getGunDisplayId();
            RenderCrosshairEvent.markHitTimestamp();
            if (event.isHeadShot()) {
                RenderCrosshairEvent.markHeadShotTimestamp();
                TimelessAPI.getGunDisplay(gunDisplayId, gunId).ifPresent(index -> SoundPlayManager.playHeadHitSound(player, index));
            } else {
                TimelessAPI.getGunDisplay(gunDisplayId, gunId).ifPresent(index -> SoundPlayManager.playFleshHitSound(player, index));
            }

        }
    }

    @SubscribeEvent
    public static void onEntityKill(EntityKillByGunEvent event) {
        LogicalSide logicalSide = event.getLogicalSide();
        if (logicalSide != LogicalSide.CLIENT) {
            return;
        }
        LivingEntity attacker = event.getAttacker();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.equals(attacker)) {
            RenderCrosshairEvent.markKillTimestamp();
            if (event.isHeadShot()) {
                RenderCrosshairEvent.markHeadShotTimestamp();
            }
        }
    }
}
