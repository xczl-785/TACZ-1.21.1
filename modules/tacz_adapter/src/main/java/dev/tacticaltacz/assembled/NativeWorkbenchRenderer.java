package dev.tacticaltacz.assembled;

import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockGunModel;
import dev.weaponassembly.api.AssemblyNode;
import dev.weaponassemblyui.client.WorkbenchModelBackend;
import dev.weaponassemblyui.client.WorkbenchViewportFrame;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Workbench adapter over the same Bedrock model, textures and attachment renderers used in hand. */
public final class NativeWorkbenchRenderer implements WorkbenchModelBackend {
    private final AssembledWeapon weapon;
    private final BedrockGunModel model;
    private final ResourceLocation texture;
    private final boolean translucent;
    private final Supplier<ItemStack> quoted;
    private final Supplier<AssemblyNode> tree;
    private ItemStack cachedBaseSource=ItemStack.EMPTY,cachedRender=ItemStack.EMPTY;
    private AssemblyNode cachedTree;

    public NativeWorkbenchRenderer(AssembledWeapon weapon,BedrockGunModel model,ResourceLocation texture,boolean translucent,
                                   Supplier<ItemStack> quoted,Supplier<AssemblyNode> tree) {
        this.weapon=Objects.requireNonNull(weapon);this.model=Objects.requireNonNull(model);this.texture=Objects.requireNonNull(texture);
        this.translucent=translucent;this.quoted=Objects.requireNonNull(quoted);this.tree=Objects.requireNonNull(tree);
    }

    private ItemStack stack() {
        var base=quoted.get();var target=tree.get();
        if(target==cachedTree&&base==cachedBaseSource)return cachedRender;
        cachedBaseSource=base;cachedTree=target;cachedRender=NativeWorkbenchStack.materialize(weapon,base,target);return cachedRender;
    }

    @Override public void render(com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext context,WorkbenchViewportFrame frame) {
        var stack=stack();if(stack.isEmpty())return;
        var poses=context.graphics.pose();poses.pushPose();boolean hand=model.getRenderHand();
        try {
            poses.translate(frame.screenCenterX(),frame.screenCenterY(),200);
            float planar=(float)(frame.fitScale()*frame.renderScale());
            poses.scale(planar,-planar,(float)(frame.fitScale()*.2));
            poses.mulPose(Axis.XP.rotation((float)frame.pitch()));poses.mulPose(Axis.YP.rotation((float)frame.yaw()));
            poses.translate(-frame.modelCenter().x(),-frame.modelCenter().y(),-frame.modelCenter().z());
            poses.scale(-16,16,16);poses.translate(0,1.5,0);poses.mulPose(Axis.ZP.rotationDegrees(180));
            model.setRenderHand(false);
            var type=translucent?RenderType.entityTranslucent(texture):RenderType.entityCutout(texture);
            model.render(poses,stack,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,type,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        } finally {
            model.setRenderHand(hand);model.cleanAnimationTransform();poses.popPose();
        }
    }
}
