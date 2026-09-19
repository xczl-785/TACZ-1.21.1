package dev.tacticaltacz.assembled;

import com.google.gson.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.pojo.model.*;
import com.tacz.guns.ammunition.TarkovAmmoItem;
import dev.itemfoundation.api.assembly.*;
import dev.tacticaltacz.AmmoBridge;
import dev.firearms.profile.FirearmProfiles;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Complete retained-gun coverage over real registered ItemStacks, without creating a world. */
class NativeRemainingStateTest {
    private static final List<String> GUNS=List.of("aa12","ai_awp","m700","m870","mk14","p90","qbz_191","scar_h","sks_tactical","uzi");
    private static AssembledWeapon weapon(String name){return Objects.requireNonNull(AssembledWeapons.byId(ResourceLocation.parse("tacz_fork_tarkov:"+name)),name);}
    @BeforeAll static void boot() throws Exception {
        NativeAssemblyStateTest.boot();
        for(String name:GUNS){var w=weapon(name);if(FirearmProfiles.profile(w.preset()).isEmpty())FirearmProfiles.register(w.PROFILE,new FirearmProfiles.Profile(w.PROFILE,w.CATALOG,w.ROOT,w.DEFINITIONS,w.requiredPaths,w::definition));}
    }
    private static NativeAssemblyGunModel model(AssembledWeapon w,boolean low){
        var gson=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer()).create();
        var pojo=gson.fromJson(AssembledWeapon.resource("assets/tacz_fork_tarkov/geo_models/gun/"+(low?"lod/":"")+w.GUN.getPath()+".json"),BedrockModelPOJO.class);
        try{return new NativeAssemblyGunModel(pojo,BedrockVersion.NEW,w);}
        catch(RuntimeException failure){throw new AssertionError(w.GUN+" low="+low,failure);}
    }
    private static ItemStack remove(ItemStack gun,List<String> path){return AssemblyGunExchange.plan(gun,ItemStack.EMPTY,path).orElseThrow().held();}
    private static List<List<String>> installedPaths(dev.firearms.assembly.AssemblyNode node,List<String> path){
        var out=new ArrayList<List<String>>();node.children().forEach((slot,child)->{var next=new ArrayList<>(path);next.add(slot);out.add(List.copyOf(next));out.addAll(installedPaths(child,next));});return out;
    }
    private static Set<String> definitions(NativeAssemblyGunModel model,JsonObject batches){
        var ids=new HashSet<String>();for(String name:model.visibleBatchNames())ids.add(batches.getAsJsonObject(name).get("definition").getAsString());return ids;
    }
    @Test void everyRetainedGunHasOneNativeAssemblyAndValidPreset(){
        assertEquals(15,AssembledWeapons.all().stream().filter(w->w.nativeRig).count());
        var types=new HashSet<String>();
        for(String name:GUNS){var w=weapon(name);var gun=w.preset();assertTrue(types.add(w.modelType));AssemblyTrees.validate(gun,AssembledWeapon.identity(gun));
            assertEquals(w.caliber,dev.tacticaltacz.GunAdoption.caliber(gun));assertTrue(FirearmProfiles.firing(gun,w.PROFILE).ready(),name);assertTrue(w.hasFeedContainer(gun),name);
            assertFalse(w.nativeAttachments.keySet().stream().anyMatch(k->k.contains("ammo_mod")),name);
            var access=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var ops=access.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            assertTrue(ItemStack.matches(gun,ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,gun).getOrThrow()).getOrThrow()),name);
            for(var path:w.requiredPaths)assertFalse(FirearmProfiles.firing(remove(gun,path),w.PROFILE).ready(),name+path);
        }
    }
    @Test void allPhysicalRemovalsAndNativeCapacityVariantsAgreeAcrossHighAndLow(){
        for(String name:GUNS){var w=weapon(name);var hi=model(w,false);var low=model(w,true);var base=w.preset();
            var batches=JsonParser.parseString(AssembledWeapon.resource("data/tacz_fork_tarkov/"+name+"/batches.json")).getAsJsonObject();
            var scenes=new ArrayList<ItemStack>();scenes.add(base);for(var path:installedPaths(w.PRESET,List.of()))scenes.add(remove(base,path));
            for(var entry:w.nativeAttachments.entrySet())if(entry.getKey().contains("extended_mag")){
                var path=NativeAttachmentProjection.path(base,AttachmentType.EXTENDED_MAG,ItemStack.EMPTY);
                var variant=w.createPart(entry.getValue());var changed=AssemblyGunExchange.plan(base,variant,path).orElseThrow().held();
                assertEquals(AssembledWeapon.identity(variant),AssembledWeapon.identity(NativeAttachmentProjection.get(changed,AttachmentType.EXTENDED_MAG)));scenes.add(changed);
            }
            scenes.add(w.preset());
            for(var scene:scenes){hi.prepareGeometry(scene);low.prepareGeometry(scene);assertEquals(definitions(hi,batches),definitions(low,batches),name);}
            hi.prepareGeometry(base);var before=Set.copyOf(hi.visibleBatchNames());hi.prepareGeometry(remove(base,w.feed.containerPath()));hi.prepareGeometry(base);assertEquals(before,hi.visibleBatchNames(),name);
        }
    }
    @Test void fullTexturedIconsChangeWithActualAssemblyAndEveryCardResolves() throws Exception {
        for(String name:GUNS){var w=weapon(name);var geometry=NativeAssemblyView.geometry(w);var materials=NativeAssemblyView.materials(w,geometry);
            assertEquals(w.ITEMS.keySet(),geometry.keySet());
            var cache=new HashMap<String,NativeAssemblyIconRaster.Texture>();
            java.util.function.Function<String,NativeAssemblyIconRaster.Texture> load=id->cache.computeIfAbsent(id,key->{
                try(var in=AssembledWeapon.class.getResourceAsStream("/assets/"+key.replace(':','/'))){var image=javax.imageio.ImageIO.read(Objects.requireNonNull(in,key));return new NativeAssemblyIconRaster.Texture(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));}
                catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            });
            for(String definition:w.ITEMS.keySet()){
                var icon=w.partIcon(definition);try(var in=AssembledWeapon.class.getResourceAsStream("/assets/"+icon.getNamespace()+"/"+icon.getPath())){assertNotNull(in,name+definition);assertNotNull(javax.imageio.ImageIO.read(in));}
            }
            var pixels=NativeAssemblyIconRaster.bake(w.PRESET,geometry,materials,load,256);
            assertTrue(Arrays.stream(pixels).filter(x->(x>>>24)>0).count()>500,name);
            assertFalse(Arrays.equals(pixels,NativeAssemblyIconRaster.bake(w.projectEnabled(remove(w.preset(),w.feed.containerPath())),geometry,materials,load,256)),name);
            var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,256,256,pixels,0,256);
            javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),name+"-native-assembled-icon.png").toFile());
        }
    }
    @Test void everyNonOpticalExternalCandidateUsesAnEditedAssetAndBothModelsAcceptIt(){
        for(String name:GUNS){var w=weapon(name);var assets=new NativeAttachmentModels(w);var hi=model(w,false);var low=model(w,true);
            for(var entry:w.nativeAttachments.entrySet()){
                var id=ResourceLocation.parse(entry.getKey());
                String root=id.getNamespace().equals("tacz")?"assets/tacz/custom/tacz_default_gun/":"";
                var index=JsonParser.parseString(AssembledWeapon.resource(root+"data/"+id.getNamespace()+"/index/attachments/"+id.getPath()+".json")).getAsJsonObject();
                String type=index.get("type").getAsString();if(type.equals("scope")||type.equals("extended_mag"))continue;
                var part=w.createPart(entry.getValue());assertNotNull(assets.resolve(part),name+entry.getKey());
                var base=w.preset();var path=NativeAttachmentProjection.path(base,AttachmentType.valueOf(type.toUpperCase(Locale.ROOT)),part);
                var changed=AssemblyGunExchange.plan(base,part,path).orElseThrow().held();
                hi.prepareGeometry(changed);low.prepareGeometry(changed);
                assertEquals(AssembledWeapon.identity(part),AssembledWeapon.identity(NativeAttachmentProjection.get(changed,AttachmentType.valueOf(type.toUpperCase(Locale.ROOT)))));
            }
        }
    }
    @Test void loadedFeedChangesRefundPreciselyWithoutMutatingInputsOrChamber(){
        for(String name:GUNS){var w=weapon(name);var paths=new ArrayList<>(w.feed.capacityPaths());paths.add(w.feed.containerPath());
            for(var path:paths){var gun=w.preset();
                var ammo=BuiltInRegistries.ITEM.stream().filter(i->i instanceof TarkovAmmoItem a&&a.definition().caliber().equals(w.caliber)).map(i->(TarkovAmmoItem)i).findFirst().orElseThrow();
                AmmoBridge.select(gun,ammo);var item=(IGun)gun.getItem();item.setCurrentAmmoCount(gun,3);item.setBulletInBarrel(gun,true);
                ItemStack payment=ItemStack.EMPTY;
                if(AssemblyTrees.state(AssemblyTrees.at(gun,path.subList(0,path.size()-1))).in(path.getLast()).isEmpty()){
                    String definition=w.nativeAttachments.entrySet().stream().filter(e->e.getKey().contains("extended_mag")).findFirst().orElseThrow().getValue();payment=w.createPart(definition);
                }
                var before=gun.copy();var result=AssemblyGunExchange.plan(gun,payment,path).orElseThrow();
                assertTrue(ItemStack.matches(before,gun),name);assertEquals(0,item.getCurrentAmmoCount(result.held()));assertTrue(item.hasBulletInBarrel(result.held()));
                assertEquals(3,result.returned().stream().filter(s->s.is(ammo)).mapToInt(ItemStack::getCount).sum(),name);
                assertEquals(AssembledWeapon.identity(gun),AssembledWeapon.identity(result.held()));
            }
        }
    }
    private static com.tacz.guns.client.model.bedrock.BedrockPart findBone(com.tacz.guns.client.model.bedrock.BedrockPart bone,String name){
        if(name.equals(bone.name))return bone;for(var child:bone.children){var found=findBone(child,name);if(found!=null)return found;}return null;
    }
    @Test void independentChamberBoneDoesNotFloatWhenEitherBoltOrBarrelIsMissing() throws Exception {
        var field=com.tacz.guns.client.model.BedrockGunModel.class.getDeclaredField("currentGunItem");field.setAccessible(true);
        for(String name:List.of("ai_awp","m870"))for(boolean low:List.of(false,true)){
            var w=weapon(name);var model=model(w,low);var full=w.preset();((IGun)full.getItem()).setBulletInBarrel(full,true);
            var chamber=(com.tacz.guns.client.model.FunctionalBedrockPart)Objects.requireNonNull(findBone(model.getRootNode(),"bullet_in_barrel"));
            for(String slot:List.of("bolt","barrel")){
                var changed=remove(full,List.of(slot));model.prepareGeometry(changed);field.set(model,changed);assertNotNull(chamber.functionalRenderer.apply(chamber),name+slot);
            }
            model.prepareGeometry(full);field.set(model,full);assertNull(chamber.functionalRenderer.apply(chamber));assertTrue(chamber.visible);
        }
    }
    @Test void independentFrontAndRearSightsAreBothRequired(){
        var paths=Map.of("qbz_191",List.of("kit","front_sight"),"scar_h",List.of("upper","front_sight"),"sks_tactical",List.of("handguard","front_sight"),"aa12",List.of("upper","front_sight"),"mk14",List.of("upper","barrel"));
        for(var entry:paths.entrySet()){
            var w=weapon(entry.getKey());var base=w.preset();assertTrue(NativeAttachmentProjection.hasSight(base));
            var stripped=remove(base,entry.getValue());assertFalse(NativeAttachmentProjection.hasSight(stripped),entry.getKey()+" needs its front aiming element");
            var rear=new ArrayList<>(entry.getValue());rear.set(rear.size()-1,"rear_sight");assertFalse(NativeAttachmentProjection.hasSight(remove(base,rear)),entry.getKey());
            var scope=w.nativeAttachments.entrySet().stream().filter(e->e.getKey().equals("tacz:scope_acog_ta31")).findFirst().orElseThrow();
            var optic=w.createPart(scope.getValue());var path=NativeAttachmentProjection.path(stripped,AttachmentType.SCOPE,optic);
            assertTrue(NativeAttachmentProjection.hasSight(AssemblyGunExchange.plan(stripped,optic,path).orElseThrow().held()));
        }
    }
    @Test void internalTubeIsNotDetachableAndCannotReloadWithoutContainer(){
        var w=weapon("m870");var gun=w.preset();assertEquals(NativeAssemblyFeed.Kind.INTERNAL_TUBE,w.feed.kind());assertFalse(w.hasMagazine(gun));assertTrue(w.hasFeedContainer(gun));
        var without=remove(gun,w.feed.containerPath());assertFalse(w.hasFeedContainer(without));assertFalse(((AssemblyGunItem)without.getItem()).canReload(null,without));
        assertFalse(w.feed.affectedBy(List.of("stock")));assertTrue(w.feed.affectedBy(w.feed.capacityPaths().getFirst()));
    }
}
