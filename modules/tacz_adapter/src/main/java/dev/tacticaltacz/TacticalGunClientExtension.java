package dev.tacticaltacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.extension.GunClientExtension;
import com.tacz.guns.client.model.BedrockGunModel;
import dev.tacticaltacz.assembled.AssemblyGunModel;
import dev.tacticaltacz.assembled.AssemblyPresentationClient;
import dev.tacticaltacz.assembled.AssembledWeapons;
import dev.tacticaltacz.assembled.NativeAssemblyIcons;
import dev.tacticaltacz.assembled.NativeAttachmentProjection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class TacticalGunClientExtension implements GunClientExtension {
    @Override public boolean blockAim(LocalPlayer player, ItemStack gun) {
        return NativeAttachmentProjection.blocksAim(gun) || GunAdoption.contains(gun)
                && dev.tacticalinventory.api.ClientCharacterDisplay.resources().map(resource -> !resource.canAim()).orElse(false);
    }
    @Override public float zoom(ItemStack gun, float fallback) { return AssemblyPresentationClient.zoom(gun, fallback); }
    @Override public RecoilFactors recoilFactors() {
        var factors = AssemblyPresentationClient.factors();
        return new RecoilFactors(factors.pitch(), factors.yaw());
    }
    @Override public float modelFov(ItemStack gun, float fallback) {
        return AssemblyPresentationClient.model(gun).flatMap(model -> model.presentation.aim(gun))
                .map(aim -> (float) aim.modelFov()).orElse(fallback);
    }
    @Override public ResourceLocation slotTexture(ItemStack gun, ResourceLocation fallback) { return NativeAssemblyIcons.texture(gun, fallback); }
    @Override public boolean prepareFirstPerson(LocalPlayer player, ItemStack gun, PoseStack poseStack, BedrockGunModel model, float aimingProgress) {
        if (!(model instanceof AssemblyGunModel assembled)) return false;
        assembled.preparePresentation(gun, aimingProgress);
        return true;
    }
    @Override public boolean applyFirstPersonShot(LocalPlayer player, ItemStack gun, BedrockGunModel model, float aimingProgress) {
        if (!(model instanceof AssemblyGunModel assembled)) return false;
        assembled.presentation.applyShot(assembled.getRootNode(), aimingProgress);
        return true;
    }
    @Override public boolean ownsFirstPersonSway(BedrockGunModel model) { return model instanceof AssemblyGunModel; }
}
