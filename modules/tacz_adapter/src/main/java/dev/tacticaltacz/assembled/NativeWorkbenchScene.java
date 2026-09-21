package dev.tacticaltacz.assembled;

import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import dev.firearms.assembly.AssemblyNode;
import dev.firearms.client.workbench.NativeWorkbenchTransform;
import dev.firearms.client.workbench.AssemblyTextureQuality;
import dev.firearms.client.workbench.WorkbenchModelBackend;
import dev.firearms.client.workbench.WorkbenchViewportFrame;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Isolated native scene for the workbench; never borrows the held renderer's mutable model. */
public final class NativeWorkbenchScene implements WorkbenchModelBackend {
    private final AssembledWeapon weapon;
    private final BedrockGunModel model;
    private final ResourceLocation texture;
    private final boolean translucent;
    private final Supplier<ItemStack> quoted;
    private final Supplier<AssemblyNode> tree;
    private final Supplier<Map<UUID,ItemStack>> payloads;
    private ItemStack cachedBaseSource=ItemStack.EMPTY,cachedRender=ItemStack.EMPTY;
    private AssemblyNode cachedTree;
    private Map<UUID,ItemStack> cachedPayloads=Map.of();

    public NativeWorkbenchScene(AssembledWeapon weapon,BedrockGunModel model,ResourceLocation texture,boolean translucent,
                                Supplier<ItemStack> quoted,Supplier<AssemblyNode> tree,Supplier<Map<UUID,ItemStack>> payloads) {
        this.weapon=Objects.requireNonNull(weapon);this.model=Objects.requireNonNull(model);this.texture=Objects.requireNonNull(texture);
        this.translucent=translucent;this.quoted=Objects.requireNonNull(quoted);this.tree=Objects.requireNonNull(tree);
        this.payloads=Objects.requireNonNull(payloads);
    }

    private ItemStack stack() {
        var base=quoted.get();var target=tree.get();var currentPayloads=payloads.get();
        if(target==cachedTree&&base==cachedBaseSource&&currentPayloads==cachedPayloads)return cachedRender;
        cachedBaseSource=base;cachedTree=target;cachedPayloads=currentPayloads;
        cachedRender=NativeWorkbenchStack.materialize(weapon,base,target,currentPayloads);
        prepareTextures(cachedRender);return cachedRender;
    }

    private void prepareTextures(ItemStack stack) {
        AssemblyTextureQuality.prepare(texture);
        for(var type:AttachmentType.values()) {
            if(type==AttachmentType.NONE)continue;
            var attachment=NativeAttachmentProjection.get(stack,type);
            var item=IAttachment.getIAttachmentOrNull(attachment);
            if(item==null)continue;
            TimelessAPI.getClientAttachmentIndex(item.getAttachmentId(attachment)).ifPresent(index->{
                var main=index.getModelTexture();if(main!=null)AssemblyTextureQuality.prepare(main);
                var lod=index.getLodModel();if(lod!=null&&lod.getRight()!=null)AssemblyTextureQuality.prepare(lod.getRight());
            });
        }
    }

    @Override public void render(com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext context,WorkbenchViewportFrame frame) {
        var stack=stack();if(stack.isEmpty())return;
        var poses=context.graphics.pose();poses.pushPose();
        try {
            poses.translate(frame.screenCenterX(),frame.screenCenterY(),200);
            float planar=(float)(frame.fitScale()*frame.renderScale());
            poses.scale(planar,-planar,(float)(frame.fitScale()*.2));
            poses.mulPose(Axis.XP.rotation((float)frame.pitch()));poses.mulPose(Axis.YP.rotation((float)frame.yaw()));
            poses.translate(-frame.modelCenter().x(),-frame.modelCenter().y(),-frame.modelCenter().z());
            NativeWorkbenchTransform.apply(poses);
            model.setRenderHand(false);
            var type=translucent?RenderType.entityTranslucent(texture):RenderType.entityCutoutNoCull(texture);
            model.render(poses,stack,ItemDisplayContext.NONE,type,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        } finally {
            model.setRenderHand(false);model.cleanAnimationTransform();poses.popPose();
        }
    }
}
