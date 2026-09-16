package dev.tacticaltacz.assembled;

import com.google.gson.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real assembly projections and native functional callbacks, without GL, client or world startup. */
class NativeScarDisplayTest {
    @BeforeAll static void boot() throws Exception {NativeAssemblyStateTest.boot();}
    private static AssembledWeapon weapon(){return AssembledWeapons.byId(ResourceLocation.parse("tacz_assembly:scar_l"));}
    private static ItemStack remove(ItemStack gun,String... path){return AssemblyGunExchange.plan(gun,ItemStack.EMPTY,List.of(path)).orElseThrow().held();}
    private static ItemStack install(ItemStack gun,String definition,String... path){return AssemblyGunExchange.plan(gun,weapon().createPart(definition),List.of(path)).orElseThrow().held();}
    private static NativeAssemblyGunModel model(boolean low){
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer()).create();
        var pojo=gson.fromJson(AssembledWeapon.resource("assets/tacz_assembly/geo_models/gun/"+(low?"lod/":"")+"scar_l.json"),BedrockModelPOJO.class);
        return new NativeAssemblyGunModel(pojo,BedrockVersion.NEW,weapon());
    }
    private static JsonObject batches(){return JsonParser.parseString(AssembledWeapon.resource("data/tacz_assembly/scar_l/batches.json")).getAsJsonObject();}
    private static BedrockPart find(BedrockPart root,String name){
        if(name.equals(root.name))return root;
        for(var child:root.children){var result=find(child,name);if(result!=null)return result;}
        return null;
    }
    private static FunctionalBedrockPart bone(NativeAssemblyGunModel model,String name){return (FunctionalBedrockPart)Objects.requireNonNull(find(model.getRootNode(),name),name);}
    private static void prepare(NativeAssemblyGunModel model,ItemStack gun){
        model.prepareGeometry(gun);
        // BedrockGunModel.render normally populates this map before running its callbacks.
        for(var type:AttachmentType.values())if(type!=AttachmentType.NONE)
            model.getCurrentAttachmentItem().put(type,NativeAttachmentProjection.get(gun,type));
    }
    private static Set<String> variants(NativeAssemblyGunModel model,String definition){
        var result=new HashSet<String>();var rows=batches();var counts=model.batchCubeCounts();
        for(var name:model.visibleBatchNames()){
            var row=rows.getAsJsonObject(name);
            if(row.get("definition").getAsString().equals(definition)&&counts.get(name)>0)result.add(row.get("variant").getAsString());
        }
        return result;
    }
    @Test void highAndLowSightsSwitchExclusivelyAndRemovedPartsNeverReturn(){
        var plain=weapon().preset();var scoped=install(plain,"tacz_scope_acog_ta31","upper","scope");
        for(boolean low:List.of(false,true)){
            var model=model(low);
            for(var gun:List.of(plain,scoped,remove(scoped,"upper","scope"),scoped)){
                boolean optic=!NativeAttachmentProjection.get(gun,AttachmentType.SCOPE).isEmpty();
                prepare(model,gun);
                for(String definition:List.of("scar_l_front_sight","scar_l_rear_sight"))
                    assertEquals(Set.of(optic?"folded":"upright"),variants(model,definition));
                for(String slot:List.of("front_sight","rear_sight")){
                    var stripped=remove(gun,"upper",slot);prepare(model,stripped);
                    assertTrue(variants(model,"scar_l_"+slot).isEmpty(),"Neither pose of a removed sight may return");
                    String other=slot.equals("front_sight")?"rear_sight":"front_sight";
                    assertEquals(Set.of(optic?"folded":"upright"),variants(model,"scar_l_"+other));
                }
                prepare(model,remove(remove(gun,"upper","front_sight"),"upper","rear_sight"));
                assertTrue(variants(model,"scar_l_front_sight").isEmpty());assertTrue(variants(model,"scar_l_rear_sight").isEmpty());
            }
            prepare(model,plain);assertEquals(Set.of("upright"),variants(model,"scar_l_front_sight"));
        }
    }
    @Test void factoryAndArStockUseDifferentExclusiveGeometryRoutes(){
        var original=weapon().preset();var external=install(original,"tacz_stock_moe","stock");var absent=remove(external,"stock");
        var replacements=new NativeAttachmentModels(weapon());
        assertNull(replacements.resolve(NativeAttachmentProjection.get(original,AttachmentType.STOCK)));
        assertNotNull(replacements.resolve(NativeAttachmentProjection.get(external,AttachmentType.STOCK)),"AR stock resolves to a separate attachment model");
        assertNull(replacements.resolve(NativeAttachmentProjection.get(absent,AttachmentType.STOCK)));
        for(boolean low:List.of(false,true)){
            var model=model(low);var rows=batches();
            assertTrue(rows.entrySet().stream().noneMatch(e->e.getValue().getAsJsonObject().get("definition").getAsString().equals("tacz_stock_moe")),"External stock must not also be baked into gun leaves");
            for(var gun:List.of(original,external,absent,original)){
                prepare(model,gun);boolean factory=gun==original;
                assertEquals(factory,!variants(model,"scar_l_stock").isEmpty());
                var attachment=NativeAttachmentProjection.get(gun,AttachmentType.STOCK);
                assertFalse(model.usesInlineAttachment(AttachmentType.STOCK,attachment));
                var anchor=bone(model,"stock_pos");assertNotNull(anchor.functionalRenderer.apply(anchor));assertFalse(anchor.visible);
                if(attachment.isEmpty())anchor.functionalRenderer.apply(anchor).render(null,null,null,0,0); // Empty external route exits before GL.
            }
        }
        // ar_stock_adapter selection uses TimelessAPI ClientAttachmentIndex loading. No fixture is installed here;
        // its index-driven visibility is covered by source review, not claimed as an executed render check.
    }
    @Test void handguardCoverRetainsNativeGripOrLaserGateAndRestoresBetweenInstances(){
        var bare=weapon().preset();var grip=install(bare,"tacz_grip_rk0","upper","grip");
        var laser=install(bare,"tacz_laser_peq15","upper","laser");var both=install(grip,"tacz_laser_peq15","upper","laser");
        for(boolean low:List.of(false,true)){
            var model=model(low);var cover=bone(model,"handguard_default");assertNotNull(cover.functionalRenderer);
            for(var gun:List.of(bare,grip,laser,both,remove(both,"upper","laser"),remove(laser,"upper","laser"),bare)){
                prepare(model,gun);
                boolean attachment=!NativeAttachmentProjection.get(gun,AttachmentType.GRIP).isEmpty()||!NativeAttachmentProjection.get(gun,AttachmentType.LASER).isEmpty();
                assertNull(cover.functionalRenderer.apply(cover));assertEquals(!attachment,cover.visible);
                assertTrue(cover.children.stream().anyMatch(child->child.name.startsWith("assembly_")),"Edited cover remains under native visibility gate");
            }
        }
    }
}
