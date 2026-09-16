package dev.tacticaltacz.assembled;

import com.google.gson.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import dev.itemfoundation.api.assembly.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Second native topology: actual registered stacks, no client/server or world startup. */
class NativeGlockStateTest {
    @BeforeAll static void boot() throws Exception { NativeAssemblyStateTest.boot(); }
    private static AssembledWeapon weapon(){return AssembledWeapons.byId(ResourceLocation.parse("tacz_assembly:glock_17"));}
    private static ItemStack remove(ItemStack gun,String... path){return AssemblyGunExchange.plan(gun,ItemStack.EMPTY,List.of(path)).orElseThrow().held();}
    private static ItemStack install(ItemStack gun,String definition,String... path){return AssemblyGunExchange.plan(gun,weapon().createPart(definition),List.of(path)).orElseThrow().held();}

    @Test void actualWorkbenchCardResolverFindsEveryNativeGunIcon() throws Exception {
        for(var w:AssembledWeapons.all())if(w.nativeRig)for(String definition:w.ITEMS.keySet()){
            var icon=w.partIcon(definition);
            try(var input=AssembledWeapon.class.getResourceAsStream("/assets/"+icon.getNamespace()+"/"+icon.getPath())){
                assertNotNull(input,w.PROFILE+" / "+definition+" -> "+icon);
                assertNotNull(javax.imageio.ImageIO.read(input),"Invalid image: "+icon);
            }
        }
        assertEquals("textures/item/glock_17/tacz_laser_compact.png",weapon().partIcon("tacz_laser_compact").getPath());
        assertEquals("textures/item/tacz_laser_compact.png",AssembledWeapons.byId(ResourceLocation.parse("tacz_assembly:m4a1")).partIcon("tacz_laser_compact").getPath());
        assertThrows(IllegalArgumentException.class,()->weapon().partIcon("unknown"));
    }

    @Test void pistolConfigurationAndPhysicalTreeAreIndependentOfM4(){
        var w=weapon();var gun=w.preset();
        assertEquals("9x19",dev.tacticaltacz.GunAdoption.caliber(gun));
        assertEquals(Set.of("tactical_inventory:sidearm"),w.wearableSlots);
        assertEquals(Set.of("tactical_inventory:primary_weapon_1","tactical_inventory:primary_weapon_2"),AssembledWeapons.byId(ResourceLocation.parse("tacz_assembly:m4a1")).wearableSlots);
        assertEquals(5,AssemblyTrees.flatten(gun).size());
        AssemblyTrees.validate(gun,AssembledWeapon.identity(gun));
        var before=gun.copy();var removed=remove(gun,"slide");
        assertTrue(ItemStack.matches(before,gun));
        assertEquals(2,AssemblyTrees.flatten(removed).size());
        assertFalse(w.requiredPaths.contains(List.of("magazine")),"Chambered round may fire without a magazine");
        assertTrue(w.requiredPaths.contains(List.of("slide")));
        assertTrue(w.requiredPaths.contains(List.of("barrel")));
        assertNotEquals(AssembledWeapon.identity(gun),AssembledWeapon.identity(w.preset()));
    }

    @Test void sightsAndNativeAttachmentsFollowPistolPaths(){
        var gun=weapon().preset();assertTrue(NativeAttachmentProjection.hasSight(gun));
        var frontRemoved=remove(gun,"slide","front_sight");
        var rearRemoved=remove(gun,"slide","rear_sight");
        assertTrue(NativeAttachmentProjection.blocksAim(frontRemoved));
        assertTrue(NativeAttachmentProjection.blocksAim(rearRemoved));
        var scope=install(frontRemoved,"tacz_sight_rmr_dot","slide","scope");
        assertFalse(NativeAttachmentProjection.blocksAim(scope));
        assertEquals(List.of("slide","scope"),NativeAttachmentProjection.path(scope,AttachmentType.SCOPE,ItemStack.EMPTY));
        assertTrue(NativeAttachmentProjection.blocksAim(remove(scope,"slide")));
        var muzzle=install(gun,"tacz_muzzle_silencer_mirage","barrel","muzzle");
        assertEquals("tacz:muzzle_silencer_mirage",((AssemblyGunItem)muzzle.getItem()).getAttachmentId(muzzle,AttachmentType.MUZZLE).toString());
        assertTrue(NativeAttachmentProjection.get(remove(muzzle,"barrel"),AttachmentType.MUZZLE).isEmpty());
    }

    @Test void magazineExchangeUsesPistolVariantAndRetainsChamberAndIdentity(){
        var gun=weapon().preset();var item=(AssemblyGunItem)gun.getItem();item.setBulletInBarrel(gun,true);
        for(int level=1;level<=3;level++){
            var part=weapon().createPart("tacz_glock_17_light_extended_mag_"+level);var id=AssembledWeapon.identity(part);
            var exchanged=AssemblyGunExchange.plan(gun,part,List.of("magazine")).orElseThrow();
            var result=exchanged.held();assertEquals(1,exchanged.returned().size());
            assertEquals(id,AssembledWeapon.identity(NativeAttachmentProjection.get(result,AttachmentType.EXTENDED_MAG)));
            assertTrue(weapon().hasMagazine(result));assertTrue(item.hasBulletInBarrel(result));
            var without=remove(result,"magazine");assertFalse(weapon().hasMagazine(without));assertTrue(item.hasBulletInBarrel(without));
        }
        assertFalse(weapon().nativeAttachments.containsKey("tacz:extended_mag_1"),"Heavy rifle magazine is not a pistol candidate");
    }

    @Test void bothNativeRenderPathsMaskTheSamePartsWithoutSharedInstanceLeakage(){
        var gson=new GsonBuilder().registerTypeAdapter(com.tacz.guns.client.resource.pojo.model.CubesItem.class,new com.tacz.guns.client.resource.pojo.model.CubesItem.Deserializer()).create();
        for(String resource:List.of("assets/tacz_assembly/geo_models/gun/glock_17.json","assets/tacz_assembly/geo_models/gun/lod/glock_17.json")){
            var pojo=gson.fromJson(AssembledWeapon.resource(resource),com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO.class);
            var model=new NativeAssemblyGunModel(pojo,com.tacz.guns.client.resource.pojo.model.BedrockVersion.NEW,weapon());
            var batches=JsonParser.parseString(AssembledWeapon.resource("data/tacz_assembly/glock_17/batches.json")).getAsJsonObject();
            var gun=weapon().preset();model.prepareGeometry(gun);var original=model.visibleBatchNames();assertFalse(original.isEmpty());
            var missingSlide=remove(gun,"slide");model.prepareGeometry(missingSlide);
            var missingDefinitions=weapon().projectEnabled(missingSlide);var installed=new HashSet<String>();collect(missingDefinitions,installed);
            assertTrue(model.visibleBatchNames().stream().allMatch(n->installed.contains(batches.getAsJsonObject(n).get("definition").getAsString())));
            assertTrue(model.visibleBatchNames().size()<original.size());
            for(int level=1;level<=3;level++){
                String target="tacz_glock_17_light_extended_mag_"+level;
                model.prepareGeometry(install(gun,target,"magazine"));
                var shown=model.visibleBatchNames().stream().map(n->batches.getAsJsonObject(n).get("definition").getAsString()).collect(java.util.stream.Collectors.toSet());
                assertTrue(shown.contains(target));assertFalse(shown.contains("glock_magazine_standard"));
                for(int other=1;other<=3;other++)if(other!=level)assertFalse(shown.contains("tacz_glock_17_light_extended_mag_"+other));
            }
            model.prepareGeometry(gun);assertEquals(original,model.visibleBatchNames());
            assertNotNull(model.getIronSightPath());assertNotNull(model.getRootNode());
        }
    }
    private static void collect(dev.weaponassembly.api.AssemblyNode node,Set<String> ids){ids.add(node.definitionId());node.children().values().forEach(child->collect(child,ids));}

    @Test void completeIconBakesWithAllInstalledParts() throws Exception {
        var models=NativeAssemblyView.geometry(weapon());var materials=NativeAssemblyView.materials(weapon(),models);
        var textures=new HashMap<String,NativeAssemblyIconRaster.Texture>();
        java.util.function.Function<String,NativeAssemblyIconRaster.Texture> load=id->textures.computeIfAbsent(id,key->{
            try(var input=AssembledWeapon.class.getResourceAsStream("/assets/"+key.replace(':','/'))){
                var image=javax.imageio.ImageIO.read(java.util.Objects.requireNonNull(input));
                return new NativeAssemblyIconRaster.Texture(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));
            }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        var pixels=NativeAssemblyIconRaster.bake(weapon().PRESET,models,materials,load,256);
        assertTrue(Arrays.stream(pixels).filter(p->(p>>>24)>0).count()>1000);
        var incomplete=weapon().projectEnabled(remove(weapon().preset(),"slide"));
        assertFalse(Arrays.equals(pixels,NativeAssemblyIconRaster.bake(incomplete,models,materials,load,256)));
        var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,256,256,pixels,0,256);
        javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"glock-native-assembled-icon.png").toFile());
    }

    @Test void workbenchAndInventoryUseCompleteTexturedGeometryAndSharedNonOptics(){
        var models=NativeAssemblyView.geometry(weapon());var materials=NativeAssemblyView.materials(weapon(),models);
        assertEquals(weapon().ITEMS.keySet(),models.keySet());
        for(var entry:models.entrySet())for(var mesh:entry.getValue().meshes())for(var triangle:mesh.triangles())assertFalse(materials.resolve(entry.getKey(),triangle.region()).texture().isEmpty());
        assertEquals(AssembledWeapon.resource("data/tacz_assembly/glock_17/preview.json"),AssembledWeapon.resource("assets/tacz_assembly/glock_17/icon_geometry.json"));
        var external=new NativeAttachmentModels(weapon());
        for(String definition:List.of("tacz_muzzle_silencer_mirage","tacz_muzzle_silencer_ptilopsis","tacz_muzzle_silencer_wraith","tacz_laser_compact","tacz_laser_nightstick"))assertNotNull(external.resolve(weapon().createPart(definition)));
        assertNull(external.resolve(weapon().createPart("tacz_sight_rmr_dot")));
    }
}
