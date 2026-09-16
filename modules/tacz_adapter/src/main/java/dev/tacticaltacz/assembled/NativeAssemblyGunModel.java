package dev.tacticaltacz.assembled;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.*;
import dev.weaponassembly.api.AssemblyNode;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Native rig and native scope pipeline; immutable leaf geometry is shared by both render paths. */
public final class NativeAssemblyGunModel extends BedrockGunModel {
    private record Batch(BedrockPart part,String definition,String variant){}
    private final AssembledWeapon weapon;
    private final List<Batch> batches;
    private final Map<AttachmentType,Set<String>> inlineAttachments = new EnumMap<>(AttachmentType.class);
    public NativeAssemblyGunModel(BedrockModelPOJO pojo,BedrockVersion version,AssembledWeapon weapon){
        super(pojo,version);this.weapon=weapon;
        var data=JsonParser.parseString(AssembledWeapon.resource("data/"+weapon.GUN.getNamespace()+"/"+weapon.resourceDirectory+"/batches.json")).getAsJsonObject();
        var list=new ArrayList<Batch>();
        data.entrySet().forEach(e->{var wrapper=modelMap.get(e.getKey());if(wrapper!=null){var v=e.getValue().getAsJsonObject();list.add(new Batch(wrapper.getModelRenderer(),v.get("definition").getAsString(),v.get("variant").getAsString()));}});
        batches=List.copyOf(list);
        var inline=JsonParser.parseString(AssembledWeapon.resource("data/"+weapon.GUN.getNamespace()+"/"+weapon.resourceDirectory+"/inline_attachments.json")).getAsJsonObject();
        inline.entrySet().forEach(e->{
            var type=AttachmentType.valueOf(e.getKey().toUpperCase(Locale.ROOT));
            inlineAttachments.put(type,Set.copyOf(e.getValue().getAsJsonObject().keySet()));
            setFunctionalRenderer(e.getKey()+"_pos",part->{
                // Edited attachment geometry is already on this rig. Other candidates
                // still use the native attachment renderer, never both at once.
                boolean embedded=usesInlineAttachment(type,getCurrentAttachmentItem().get(type));
                part.visible=embedded;
                return embedded?null:new AttachmentRender(this,type);
            });
        });
        // These are rig ancestors, not inventory entities. Gate their leaves instead.
        for(var name:List.of("sight","sight_folded","handguard_default","handguard_tactical","muzzle_default","mag_standard","mag_extended_1","mag_extended_2","extend_magazine"))
            setFunctionalRenderer(name,part->{part.visible=true;return null;});
    }
    boolean usesInlineAttachment(AttachmentType type,ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        var attachment=IAttachment.getIAttachmentOrNull(stack);
        return attachment!=null&&inlineAttachments.getOrDefault(type,Set.of()).contains(attachment.getAttachmentId(stack).toString());
    }
    @Override public void render(PoseStack poses,ItemStack stack,ItemDisplayContext context,RenderType type,int light,int overlay){
        prepareGeometry(stack);
        super.render(poses,stack,context,type,light,overlay);
    }
    void prepareGeometry(ItemStack stack){
        var installed=new HashSet<String>();
        try{collect(weapon.projectEnabled(stack),installed);}catch(IllegalArgumentException invalid){installed.clear();}
        boolean optic=!NativeAttachmentProjection.get(stack,AttachmentType.SCOPE).isEmpty();
        for(var batch:batches)batch.part.visible=installed.contains(batch.definition)
                &&(!batch.definition.equals("rear_sight")||!optic)
                &&(!batch.variant.equals("upright")||!optic)&&(!batch.variant.equals("folded")||optic);
    }
    Set<String> visibleBatchNames(){return batches.stream().filter(b->b.part.visible).map(b->b.part.name).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    Map<String,Integer> batchCubeCounts(){var counts=new HashMap<String,Integer>();for(var b:batches)counts.put(b.part.name,cubes(b.part));return Map.copyOf(counts);}
    private static int cubes(BedrockPart part){return part.cubes.size()+part.children.stream().mapToInt(NativeAssemblyGunModel::cubes).sum();}
    private static void collect(AssemblyNode node,Set<String> out){out.add(node.definitionId());node.children().values().forEach(child->collect(child,out));}
}
