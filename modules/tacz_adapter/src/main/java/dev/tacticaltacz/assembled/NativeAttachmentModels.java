package dev.tacticaltacz.assembled;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Per-weapon attachment assets; native attachment effects still own their render behavior. */
final class NativeAttachmentModels {
    record Asset(BedrockAttachmentModel model, ResourceLocation texture, BedrockAttachmentModel lodModel, ResourceLocation lodTexture) {}
    private final Map<String,Asset> assets;
    NativeAttachmentModels(AssembledWeapon weapon) {
        String path="data/"+weapon.GUN.getNamespace()+"/"+weapon.resourceDirectory+"/native_attachment_overrides.json";
        var entries=JsonParser.parseString(AssembledWeapon.resource(path)).getAsJsonObject();
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer()).create();
        var result=new HashMap<String,Asset>();
        for(var entry:entries.entrySet()) {
            var value=entry.getValue().getAsJsonObject();
            var id=ResourceLocation.parse(value.get("model").getAsString());
            var pojo=gson.fromJson(AssembledWeapon.resource("assets/"+id.getNamespace()+"/geo_models/"+id.getPath()+".json"),BedrockModelPOJO.class);
            var model=Objects.requireNonNull(ClientAttachmentIndex.getAttachmentModel(pojo),"Invalid attachment model "+id);
            var texture=ResourceLocation.parse(value.get("texture").getAsString());
            BedrockAttachmentModel low=null;ResourceLocation lowTexture=null;
            if(value.has("lodModel")){
                var lowId=ResourceLocation.parse(value.get("lodModel").getAsString());
                var lowPojo=gson.fromJson(AssembledWeapon.resource("assets/"+lowId.getNamespace()+"/geo_models/"+lowId.getPath()+".json"),BedrockModelPOJO.class);
                low=Objects.requireNonNull(ClientAttachmentIndex.getAttachmentModel(lowPojo),"Invalid attachment LOD "+lowId);
                var tid=ResourceLocation.parse(value.get("lodTexture").getAsString());
                lowTexture=ResourceLocation.fromNamespaceAndPath(tid.getNamespace(),"textures/"+tid.getPath()+".png");
            }
            result.put(entry.getKey(),new Asset(model,ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),"textures/"+texture.getPath()+".png"),low,lowTexture));
        }
        assets=Map.copyOf(result);
    }
    Asset resolve(ItemStack stack) {
        if(stack==null||stack.isEmpty())return null;
        var attachment=IAttachment.getIAttachmentOrNull(stack);
        return attachment==null?null:assets.get(attachment.getAttachmentId(stack).toString());
    }
    int size(){return assets.size();}
    IFunctionalRenderer renderer(BedrockGunModel gun,AttachmentType type) {
        return (poses,buffer,context,light,overlay)->{
            var stack=gun.getCurrentAttachmentItem().get(type);
            if(stack==null||stack.isEmpty())return;
            var asset=resolve(stack);
            if(asset==null){new AttachmentRender(gun,type).render(poses,buffer,context,light,overlay);return;}
            var normal=new Matrix3f(poses.last().normal());var pose=new Matrix4f(poses.last().pose());
            gun.delegateRender((ignored,ignoredBuffer,ignoredContext,ignoredLight,ignoredOverlay)->{
                var restored=new PoseStack();restored.last().normal().mul(normal);restored.last().pose().mul(pose);
                restored.translate(0,-1.5,0);
                boolean low=asset.lodModel!=null&&!context.firstPerson()&&!com.tacz.guns.util.RenderDistance.inRenderHighPolyModelDistance(restored);
                var texture=low?asset.lodTexture:asset.texture;
                if(context==ItemDisplayContext.NONE)dev.weaponassemblyui.client.AssemblyTextureQuality.prepare(texture);
                var renderType=context==ItemDisplayContext.NONE?RenderType.entityCutoutNoCull(texture):RenderType.entityCutout(texture);
                (low?asset.lodModel:asset.model).render(stack,gun.getCurrentGunItem(),restored,context,renderType,light,overlay);
            });
        };
    }
}
