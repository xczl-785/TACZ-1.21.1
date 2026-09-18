package dev.tacticaltacz.assembled;

import com.google.gson.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.*;
import dev.itemfoundation.api.assembly.*;
import dev.weaponruntime.WeaponCapabilities;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Registry-backed checks for three distinct native rigs. No world, server or GL rendering. */
class NativeGunBatchStateTest {
    private static final List<String> GUNS=List.of("m16a1","scar_l","ump45");
    private static AssembledWeapon weapon(String gun){return Objects.requireNonNull(AssembledWeapons.byId(ResourceLocation.parse("tacz_fork_tarkov:"+gun)),gun);}
    @BeforeAll static void boot() throws Exception {
        NativeAssemblyStateTest.boot();
        for(String gun:GUNS){
            var w=weapon(gun);
            if(WeaponCapabilities.profile(w.preset()).isEmpty())WeaponCapabilities.register(w.PROFILE,new WeaponCapabilities.Profile(w.PROFILE,w.CATALOG,w.ROOT,w.DEFINITIONS,w.requiredPaths,w::definition));
        }
    }
    private static ItemStack remove(ItemStack gun,List<String> path){return AssemblyGunExchange.plan(gun,ItemStack.EMPTY,path).orElseThrow().held();}
    private static ItemStack install(AssembledWeapon w,ItemStack gun,String definition,List<String> path){return AssemblyGunExchange.plan(gun,w.createPart(definition),path).orElseThrow().held();}
    private static JsonObject json(String path){return JsonParser.parseString(AssembledWeapon.resource(path)).getAsJsonObject();}
    private static NativeAssemblyGunModel model(AssembledWeapon w,boolean low){
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer()).create();
        var pojo=gson.fromJson(AssembledWeapon.resource("assets/tacz_fork_tarkov/geo_models/gun/"+(low?"lod/":"")+w.GUN.getPath()+".json"),BedrockModelPOJO.class);
        return new NativeAssemblyGunModel(pojo,BedrockVersion.NEW,w);
    }
    private static Set<String> visibleDefinitions(NativeAssemblyGunModel model,JsonObject batches){
        var out=new HashSet<String>();for(String name:model.visibleBatchNames())out.add(batches.getAsJsonObject(name).get("definition").getAsString());return out;
    }
    @Test void fullIconsUseTexturedAssemblyAndChangeWhenUpperIsRemoved() throws Exception {
        for(String name:GUNS){
            var w=weapon(name);var models=NativeAssemblyView.geometry(w);var materials=NativeAssemblyView.materials(w,models);
            var textures=new HashMap<String,NativeAssemblyIconRaster.Texture>();
            java.util.function.Function<String,NativeAssemblyIconRaster.Texture> load=id->textures.computeIfAbsent(id,key->{
                try(var input=AssembledWeapon.class.getResourceAsStream("/assets/"+key.replace(':','/'))){
                    var image=javax.imageio.ImageIO.read(Objects.requireNonNull(input,key));
                    return new NativeAssemblyIconRaster.Texture(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));
                }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            });
            var pixels=NativeAssemblyIconRaster.bake(w.PRESET,models,materials,load,256);
            assertTrue(Arrays.stream(pixels).filter(p->(p>>>24)>0).count()>1000,name);
            assertFalse(Arrays.equals(pixels,NativeAssemblyIconRaster.bake(w.projectEnabled(remove(w.preset(),List.of("upper"))),models,materials,load,256)),name);
            var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0,0,256,256,pixels,0,256);
            javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),name+"-native-assembled-icon.png").toFile());
        }
    }
    @Test void nativeCalibersAndEquipmentAgreeWithActualAmmoBridge(){
        var counts=Map.of("m16a1",13,"scar_l",11,"ump45",8);
        for(String name:GUNS){
            var w=weapon(name);var gun=w.preset();AssemblyTrees.validate(gun,AssembledWeapon.identity(gun));
            assertEquals(counts.get(name)-1,AssemblyTrees.flatten(gun).size());
            assertEquals(name.equals("ump45")?"45acp":"556x45",dev.tacticaltacz.GunAdoption.caliber(gun));
            assertEquals(Set.of("tactical_inventory:primary_weapon_1","tactical_inventory:primary_weapon_2"),w.wearableSlots);
            assertTrue(w.nativeAttachments.keySet().stream().noneMatch(id->id.contains("ammo_mod_")));
            assertFalse(dev.tacticaltacz.AssemblyFireGate.blocked(gun));
        }
    }
    @Test void criticalPartsBlockFiringWhileMagazineRemovalKeepsChamber(){
        for(String name:GUNS){
            var w=weapon(name);var gun=w.preset();var before=gun.copy();
            for(var path:w.requiredPaths)assertTrue(dev.tacticaltacz.AssemblyFireGate.blocked(remove(gun,path)),name+" "+path);
            assertTrue(ItemStack.matches(before,gun));
            ((AssemblyGunItem)gun.getItem()).setBulletInBarrel(gun,true);
            var noMag=remove(gun,w.magazinePath);assertFalse(w.hasMagazine(noMag));
            assertTrue(((AssemblyGunItem)gun.getItem()).hasBulletInBarrel(noMag));
            assertFalse(dev.tacticaltacz.AssemblyFireGate.blocked(noMag),name+" chamber remains usable");
        }
    }
    @Test void everyGunUsesItsOwnThreeMagazineVariantsAndPreservesPhysicalIdentity(){
        for(String name:GUNS){
            var w=weapon(name);var candidates=w.nativeAttachments.entrySet().stream().filter(e->e.getKey().contains("extended_mag_")).toList();assertEquals(3,candidates.size());
            for(var candidate:candidates){
                assertTrue(candidate.getValue().contains(name),"Per-gun magazine geometry: "+candidate);
                var part=w.createPart(candidate.getValue());var id=AssembledWeapon.identity(part);
                var result=AssemblyGunExchange.plan(w.preset(),part,w.magazinePath).orElseThrow();
                assertEquals(1,result.returned().size());assertTrue(w.hasMagazine(result.held()));
                assertEquals(id,AssembledWeapon.identity(NativeAttachmentProjection.get(result.held(),AttachmentType.EXTENDED_MAG)));
                assertEquals(candidate.getKey(),((AssemblyGunItem)result.held().getItem()).getAttachmentId(result.held(),AttachmentType.EXTENDED_MAG).toString());
            }
        }
    }
    @Test void nativeSightRulesFollowEachPhysicalTopology(){
        for(String name:GUNS){
            var w=weapon(name);var gun=w.preset();assertTrue(NativeAttachmentProjection.hasSight(gun));
            assertTrue(NativeAttachmentProjection.blocksAim(remove(gun,List.of("upper"))));
            if(name.equals("ump45")){
                assertTrue(NativeAttachmentProjection.hasSight(remove(gun,List.of("upper","rails"))),"UMP sights belong to upper, not accessory rails");
            }else{
                var front=name.equals("m16a1")?List.of("upper","barrel_ring","barrel","front_sight"):List.of("upper","front_sight");
                var noFront=remove(gun,front);assertTrue(NativeAttachmentProjection.blocksAim(noFront));
                assertTrue(NativeAttachmentProjection.blocksAim(remove(gun,List.of("upper","rear_sight"))));
                var scope=w.nativeAttachments.entrySet().stream().filter(e->e.getKey().contains(":scope_")||e.getKey().contains(":sight_")).findFirst().orElseThrow();
                var path=w.nativeProfile.path("SCOPE",scope.getKey(),p->false);
                assertFalse(NativeAttachmentProjection.blocksAim(install(w,noFront,scope.getValue(),path)));
            }
        }
    }
    @Test void highAndLowRenderTheSameEntitiesAcrossRemovalReplacementAndDifferentInstances(){
        for(String name:GUNS){
            var w=weapon(name);var hi=model(w,false);var lo=model(w,true);
            var batches=json("data/tacz_fork_tarkov/"+name+"/batches.json");
            var original=w.preset();var cases=new ArrayList<ItemStack>();cases.add(original);
            for(var child:w.PRESET.children().keySet())cases.add(remove(original,List.of(child)));
            for(var candidate:w.nativeAttachments.entrySet())if(candidate.getKey().contains("extended_mag_"))cases.add(install(w,original,candidate.getValue(),w.magazinePath));
            cases.add(w.preset());cases.add(original);
            Set<String> first=null;
            for(var gun:cases){
                hi.prepareGeometry(gun);lo.prepareGeometry(gun);
                var visible=visibleDefinitions(hi,batches);assertFalse(visible.isEmpty());assertEquals(visible,visibleDefinitions(lo,batches),name);
                if(first==null)first=hi.visibleBatchNames();
                var absent=new HashSet<String>();collect(w.PRESET,absent);var installed=new HashSet<String>();collect(w.projectEnabled(gun),installed);absent.removeAll(installed);
                assertTrue(Collections.disjoint(visible,absent),name+" removed physical entities remain hidden");
            }
            assertEquals(first,hi.visibleBatchNames(),name+" model state must not leak between stacks");
            assertTrue(lo.batchCubeCounts().values().stream().mapToInt(Integer::intValue).sum()<hi.batchCubeCounts().values().stream().mapToInt(Integer::intValue).sum(),name+" real reduction");
        }
    }
    private static void collect(dev.weaponassembly.api.AssemblyNode node,Set<String> out){out.add(node.definitionId());node.children().values().forEach(c->collect(c,out));}
    private static BedrockPart find(BedrockPart part,String name){if(name.equals(part.name))return part;for(var child:part.children){var found=find(child,name);if(found!=null)return found;}return null;}
    @Test void chamberRoundRetainsNativeAmmoGateAndRequiresBothBarrelAndBolt() throws Exception {
        var current=BedrockGunModel.class.getDeclaredField("currentGunItem");current.setAccessible(true);
        for(String name:GUNS)for(boolean low:List.of(false,true)){
            var w=weapon(name);var model=model(w,low);var gun=w.preset();
            ((AssemblyGunItem)gun.getItem()).setBulletInBarrel(gun,true);
            var bolt=(FunctionalBedrockPart)Objects.requireNonNull(find(model.getRootNode(),name.equals("ump45")?"ump45_bolt":"bolt"));
            var round=(FunctionalBedrockPart)Objects.requireNonNull(find(model.getRootNode(),"bullet_in_barrel"));
            current.set(model,gun);model.prepareGeometry(gun);assertNull(bolt.functionalRenderer.apply(bolt));assertNull(round.functionalRenderer.apply(round));assertTrue(round.visible);
            var noBolt=remove(gun,List.of("upper","bolt"));current.set(model,noBolt);model.prepareGeometry(noBolt);assertNotNull(bolt.functionalRenderer.apply(bolt));
            var barrelPath=name.equals("m16a1")?List.of("upper","barrel_ring","barrel"):List.of("upper","barrel");
            var noBarrel=remove(gun,barrelPath);current.set(model,noBarrel);model.prepareGeometry(noBarrel);assertNotNull(round.functionalRenderer.apply(round));
            current.set(model,gun);model.prepareGeometry(gun);assertNull(bolt.functionalRenderer.apply(bolt));assertNull(round.functionalRenderer.apply(round));assertTrue(round.visible);
            ((AssemblyGunItem)gun.getItem()).setBulletInBarrel(gun,false);assertNull(round.functionalRenderer.apply(round));assertFalse(round.visible);
        }
    }
}
