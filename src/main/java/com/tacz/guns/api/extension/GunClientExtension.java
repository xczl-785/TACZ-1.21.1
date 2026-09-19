package com.tacz.guns.api.extension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Client-only presentation extension, loaded lazily from client call sites. */
public interface GunClientExtension {
    record RecoilFactors(float pitch, float yaw) { public static final RecoilFactors IDENTITY = new RecoilFactors(1, 1); }
    default boolean blockAim(LocalPlayer player, ItemStack gun) { return false; }
    default float zoom(ItemStack gun, float fallback) { return fallback; }
    default RecoilFactors recoilFactors() { return RecoilFactors.IDENTITY; }
    default float modelFov(ItemStack gun, float fallback) { return fallback; }
    default ResourceLocation slotTexture(ItemStack gun, ResourceLocation fallback) { return fallback; }
    default boolean prepareFirstPerson(LocalPlayer player, ItemStack gun, PoseStack poseStack, BedrockGunModel model, float aimingProgress) { return false; }
    default boolean applyFirstPersonShot(LocalPlayer player, ItemStack gun, BedrockGunModel model, float aimingProgress) { return false; }
    default boolean ownsFirstPersonSway(BedrockGunModel model) { return false; }
}
