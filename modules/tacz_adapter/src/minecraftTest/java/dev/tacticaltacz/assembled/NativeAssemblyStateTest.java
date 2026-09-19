package dev.tacticaltacz.assembled;

import com.google.gson.JsonParser;
import com.tacz.guns.api.item.attachment.AttachmentType;
import dev.itemfoundation.api.assembly.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real ItemStacks and registered components in a unit-test JVM. No server/world is created. */
class NativeAssemblyStateTest {
    @BeforeAll static void boot() throws Exception {
        Bootstrap.bootStrap();
        var definitions=new ArrayList<AssemblyDefinition>();
        for(var weapon:AssembledWeapons.all())if(weapon.nativeRig){
        var json=JsonParser.parseString(AssembledWeapon.resource("data/"+weapon.GUN.getNamespace()+"/assembly/"+weapon.GUN.getPath()+".json")).getAsJsonObject();
        for(var entry:json.getAsJsonArray("items")){
            var o=entry.getAsJsonObject();var slots=new ArrayList<AssemblyDefinition.Slot>();
            for(var s:o.getAsJsonArray("slots")){var slot=s.getAsJsonObject();var ids=new HashSet<String>();for(var id:slot.getAsJsonArray("compatibleItems"))ids.add(id.getAsString());slots.add(new AssemblyDefinition.Slot(slot.get("id").getAsString(),ids,Set.of(),Set.of(),false));}
            definitions.add(new AssemblyDefinition(o.get("itemId").getAsString(),slots));
        }
        }
        AssemblyDefinitions.replace(definitions);
    }
    private static AssembledWeapon weapon(){return AssembledWeapons.byId(net.minecraft.resources.ResourceLocation.parse("tacz_fork_tarkov:m4a1"));}
    @Test void standardWorkbenchLoadsTexturedPartsAndLocalMounts(){
        var models=NativeAssemblyView.geometry(weapon());
        var materials=NativeAssemblyView.materials(weapon(),models);
        assertEquals(weapon().ITEMS.keySet(),models.keySet());
        for(var entry:models.entrySet()){
            assertEquals(new dev.weaponmodels.ModelGeometry.Point(0,0,0),entry.getValue().attachmentOrigin());
            for(var mesh:entry.getValue().meshes())for(var triangle:mesh.triangles())
                assertFalse(materials.resolve(entry.getKey(),triangle.region()).texture().isEmpty());
        }
        var muzzle=dev.weaponmodels.ModelGeometry.origin(weapon().PRESET,models,List.of("upper","barrel_mount","barrel","muzzle")).orElseThrow();
        var buffer=dev.weaponmodels.ModelGeometry.origin(weapon().PRESET,models,List.of("buffer")).orElseThrow();
        assertTrue(muzzle.z()>buffer.z(),"Radian workbench art frame points toward +Z");
    }
    @Test void itemEntryAndIconGeometryCoverTheCurrentAssembly(){
        var entry=JsonParser.parseString(AssembledWeapon.resource("assets/tacz_fork_tarkov/models/item/m4a1.json")).getAsJsonObject();
        assertEquals("builtin/entity",entry.get("parent").getAsString());
        var a=weapon().preset();var b=weapon().preset();
        assertEquals(NativeAssemblyIconRaster.appearanceKey(weapon().projectEnabled(a)),NativeAssemblyIconRaster.appearanceKey(weapon().projectEnabled(b)));
        ((AssemblyGunItem)a.getItem()).setCurrentAmmoCount(a,10);
        assertEquals(NativeAssemblyIconRaster.appearanceKey(weapon().projectEnabled(a)),NativeAssemblyIconRaster.appearanceKey(weapon().projectEnabled(b)));
        var noStock=AssemblyGunExchange.plan(a,ItemStack.EMPTY,List.of("buffer","stock")).orElseThrow().held();
        assertNotEquals(NativeAssemblyIconRaster.appearanceKey(weapon().projectEnabled(a)),NativeAssemblyIconRaster.appearanceKey(weapon().projectEnabled(noStock)));
        assertThrows(IllegalStateException.class,()->AssembledWeapon.resource("assets/tacz_fork_tarkov/m4a1/icon_geometry.json"));
    }
    @Test void presetIsOnePhysicalTreeAndCanDetachMagazine(){
        var gun=weapon().preset();assertEquals(14,AssemblyTrees.flatten(gun).size());AssemblyTrees.validate(gun,AssembledWeapon.identity(gun));
        var before=gun.copy();var result=AssemblyGunExchange.plan(gun,ItemStack.EMPTY,List.of("magazine")).orElseThrow();
        assertTrue(ItemStack.matches(before,gun));assertEquals(1,result.returned().size());assertFalse(weapon().hasMagazine(result.held()));
        assertEquals(13,AssemblyTrees.flatten(result.held()).size());
    }
    @Test void nativeTagsWriteIntoChildAndKeepIdentity(){
        var gun=weapon().preset();var scope=weapon().createPart("tacz_scope_acog_ta31");
        var installed=AssemblyGunExchange.plan(gun,scope,List.of("upper","scope")).orElseThrow().held();
        var item=(AssemblyGunItem)installed.getItem();var original=AssembledWeapon.identity(scope);
        var tag=item.getAttachmentTag(installed,AttachmentType.SCOPE);assertNotNull(tag);tag.putInt("ZoomNumber",2);tag.putString("AttachmentId","tacz:ammo_mod_i");
        item.setAttachmentTag(installed,AttachmentType.SCOPE,tag);
        var child=NativeAttachmentProjection.get(installed,AttachmentType.SCOPE);
        assertEquals(original,AssembledWeapon.identity(child));assertEquals("tacz:scope_acog_ta31",item.getAttachmentId(installed,AttachmentType.SCOPE).toString());assertEquals(2,item.getAttachmentTag(installed,AttachmentType.SCOPE).getInt("ZoomNumber"));
        assertFalse(installed.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getAllKeys().stream().anyMatch(NativeAssemblyStateTest::isAttachmentShadow));
        assertEquals(0,((com.tacz.guns.api.item.IAttachment)scope.getItem()).getZoomNumber(scope));
    }

    @Test void allNativePresetsUseOnePhysicalAssemblyAuthority(){
        var nativeWeapons=AssembledWeapons.all().stream().filter(w->w.nativeRig).toList();assertEquals(15,nativeWeapons.size());
        nativeWeapons.forEach(w->assertSingleAuthority(w.preset()));
    }

    @Test void authorityGateRejectsRealAttachmentShadowAndIdentityDrift(){
        var shadowed=weapon().preset();
        shadowed.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY,
                data->data.update(tag->tag.put("AttachmentSCOPE",new net.minecraft.nbt.CompoundTag())));
        assertThrows(AssertionError.class,()->assertSingleAuthority(shadowed));

        var drifted=weapon().preset();var state=AssemblyTrees.state(drifted);var children=new ArrayList<>(state.installed());var first=children.getFirst();
        children.set(0,new AssemblyState.Installed(first.slotId(),UUID.randomUUID(),first.stack(),first.enabled()));
        drifted.set(AssemblyComponents.STATE.get(),state.updated(children));
        assertThrows(AssertionError.class,()->assertSingleAuthority(drifted));
    }

    private static boolean isAttachmentShadow(String key){
        return Arrays.stream(AttachmentType.values()).filter(type->type!=AttachmentType.NONE).anyMatch(type->key.equals("Attachment"+type.name()));
    }

    private static void assertSingleAuthority(ItemStack gun){
        var root=gun.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        assertFalse(root.getAllKeys().stream().anyMatch(NativeAssemblyStateTest::isAttachmentShadow),"TaCZ attachment shadow state exists beside foundation assembly_state");
        assertNotNull(gun.get(dev.firearms.profile.FirearmComponents.PROFILE.get()),"firearms:profile remains a reference, not a physical tree");
        var identities=new HashSet<UUID>();identities.add(AssembledWeapon.identity(gun));
        for(var node:AssemblyTrees.flatten(gun)){
            assertEquals(node.instanceId(),AssembledWeapon.identity(node.stack()),"outer assembly identity must match the physical child");
            assertTrue(identities.add(node.instanceId()),"physical assembly identities must be unique");
        }
    }

    @Test void serverProposalIsSingleUseAndExpiresAcrossBothInterfaces(){
        var store=new AssemblyProposalSessions();var player=UUID.randomUUID();
        var view=new dev.tacticalinventory.api.TacticalHeldExchange.View(player,3,UUID.randomUUID(),weapon().preset(),List.of());
        var first=store.issue(player,view,10);assertSame(first,store.peek(player));
        assertSame(first,store.take(player,first.token(),11));assertNull(store.take(player,first.token(),12));
        var second=store.issue(player,view,20);assertNull(store.take(player,second.token(),1221));
        var third=store.issue(player,view,30);assertNull(store.take(player,UUID.randomUUID(),31));assertNull(store.take(player,third.token(),32));
        var old=store.issue(player,view,40);var fresh=store.issue(player,view,41);assertNotEquals(old.token(),fresh.token());
        assertNull(store.take(player,old.token(),42)); // stale UI cannot commit the new proposal
    }
    @Test void subtreeRefundAndIndependentInstancesSurviveCodecRoundtrip(){
        var first=weapon().preset();var second=weapon().preset();assertNotEquals(AssembledWeapon.identity(first),AssembledWeapon.identity(second));
        var bayonet=weapon().createPart("tacz_bayonet_m9");
        first=AssemblyGunExchange.plan(first,bayonet,List.of("upper","barrel_mount","barrel","muzzle","bayonet")).orElseThrow().held();
        var result=AssemblyGunExchange.plan(first,weapon().createPart("tacz_muzzle_silencer_knight_qd"),List.of("upper","barrel_mount","barrel","muzzle")).orElseThrow();
        assertEquals(1,result.returned().size());assertEquals(1,AssemblyTrees.flatten(result.returned().getFirst()).size());assertEquals(14,AssemblyTrees.flatten(second).size());
        var access=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var ops=access.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var encoded=ItemStack.CODEC.encodeStart(ops,first).getOrThrow();var decoded=ItemStack.CODEC.parse(ops,encoded).getOrThrow();
        assertTrue(ItemStack.matches(first,decoded));assertEquals(AssembledWeapon.identity(bayonet),AssembledWeapon.identity(NativeAttachmentProjection.get(decoded,AttachmentType.MUZZLE)));
    }
    @Test void disabledNestedBayonetDoesNotMaskItsParentRoute(){
        var gun=AssemblyGunExchange.plan(weapon().preset(),weapon().createPart("tacz_bayonet_m9"),List.of("upper","barrel_mount","barrel","muzzle","bayonet")).orElseThrow().held();
        var path=List.of("upper","barrel_mount","barrel","muzzle");
        var muzzle=AssemblyTrees.at(gun,path).copy();var bayonet=AssemblyTrees.state(muzzle).in("bayonet").orElseThrow();
        muzzle.set(AssemblyComponents.STATE.get(),AssemblyState.empty().updated(List.of(new AssemblyState.Installed("bayonet",bayonet.instanceId(),bayonet.stack(),false))));
        var disabled=AssemblyTrees.replace(gun,path,muzzle);
        assertEquals(path,NativeAttachmentProjection.path(disabled,AttachmentType.MUZZLE,ItemStack.EMPTY));
        assertTrue(NativeAttachmentProjection.get(disabled,AttachmentType.MUZZLE).isEmpty()); // default muzzle is a physical part, not a native IAttachment
        assertFalse(NativeAttachmentProjection.get(gun,AttachmentType.MUZZLE).isEmpty());
    }
    @Test void laserStateIsStoredOnReturnedPartAndChamberSurvivesMagazineRemoval(){
        var gun=weapon().preset();var item=(AssemblyGunItem)gun.getItem();item.setBulletInBarrel(gun,true);
        gun=AssemblyGunExchange.plan(gun,weapon().createPart("handguard_tactical"),List.of("upper","barrel_mount","handguard")).orElseThrow().held();
        gun=AssemblyGunExchange.plan(gun,weapon().createPart("tacz_laser_peq15"),List.of("upper","barrel_mount","handguard","laser")).orElseThrow().held();
        var tag=item.getAttachmentTag(gun,AttachmentType.LASER);tag.putInt("LaserColor",0x22AA44);item.setAttachmentTag(gun,AttachmentType.LASER,tag);
        var removed=AssemblyGunExchange.plan(gun,ItemStack.EMPTY,List.of("upper","barrel_mount","handguard","laser")).orElseThrow();
        assertEquals(0x22AA44,((com.tacz.guns.api.item.IAttachment)removed.returned().getFirst().getItem()).getLaserColor(removed.returned().getFirst()));
        var withoutMag=AssemblyGunExchange.plan(removed.held(),ItemStack.EMPTY,List.of("magazine")).orElseThrow().held();assertTrue(item.hasBulletInBarrel(withoutMag));assertFalse(weapon().hasMagazine(withoutMag));
    }

    @Test void editedAttachmentModelsAreScopedAndKeepNativeFallback() {
        var models=new NativeAttachmentModels(weapon());assertEquals(25,models.size());
        var entries=JsonParser.parseString(AssembledWeapon.resource("data/tacz_fork_tarkov/m4a1/native_attachment_overrides.json")).getAsJsonObject();
        for(var entry:entries.entrySet()){
            var stack=weapon().createPart(entry.getKey().replace(':','_'));
            var asset=models.resolve(stack);assertNotNull(asset,entry.getKey());
            assertNotNull(asset.model());assertTrue(asset.texture().getPath().endsWith(".png"));
        }
        assertNotNull(models.resolve(weapon().createPart("tacz_laser_compact")).lodModel());
        assertNotNull(models.resolve(weapon().createPart("tacz_laser_lopro")).lodModel());
        assertNotNull(models.resolve(weapon().createPart("tacz_laser_nightstick")).lodModel());
        assertNull(models.resolve(weapon().createPart("tacz_grip_cobra")).lodModel());
        assertNull(models.resolve(ItemStack.EMPTY));
        assertNull(models.resolve(weapon().createPart("tacz_scope_acog_ta31")));
        assertNull(models.resolve(weapon().createPart("tacz_stock_moe")));
    }

    @Test void temporaryPresetEditsHaveNoPhysicalInventoryAuthority(){
        var actual=weapon().preset();var before=actual.copy();
        var draft=dev.weaponassemblyui.session.AssemblySession.preset(weapon().CATALOG,weapon().project(actual),new dev.firearms.assembly.WeaponStats.Context(0,0),Optional.of("native stats"));
        assertTrue(draft.temporaryPreset());assertEquals(Optional.of("native stats"),draft.statsExplanation());
        draft.select(List.of("upper","barrel_mount","handguard"));
        var rail=draft.candidates().stream().filter(n->n.definitionId().equals("handguard_tactical")).findFirst().orElseThrow();
        assertTrue(draft.install(rail.instanceId()).success());
        draft.select(List.of("upper","barrel_mount","handguard","grip"));
        var grip=draft.candidates().stream().filter(n->n.definitionId().equals("tacz_grip_rk1_b25u")).findFirst().orElseThrow();
        assertTrue(draft.install(grip.instanceId()).success());assertTrue(draft.remove().success());
        assertTrue(draft.install(grip.instanceId()).success());
        assertTrue(draft.detached().isEmpty());assertTrue(ItemStack.matches(before,actual));
        var reopened=dev.weaponassemblyui.session.AssemblySession.preset(weapon().CATALOG,weapon().project(actual),new dev.firearms.assembly.WeaponStats.Context(0,0),Optional.empty());
        assertEquals("handguard_default",reopened.nodeAt(List.of("upper","barrel_mount","handguard")).orElseThrow().definitionId());
        assertTrue(ItemStack.matches(before,actual));
    }

    @Test void nativeHighAndLowSelectImmutableLeavesWithoutInstanceLeakage(){
        var gson=new com.google.gson.GsonBuilder().registerTypeAdapter(com.tacz.guns.client.resource.pojo.model.CubesItem.class,new com.tacz.guns.client.resource.pojo.model.CubesItem.Deserializer()).create();
        for(var resource:List.of("assets/tacz_fork_tarkov/geo_models/gun/m4a1.json","assets/tacz_fork_tarkov/geo_models/gun/lod/m4a1.json")){
            var pojo=gson.fromJson(AssembledWeapon.resource(resource),com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO.class);
            var model=new NativeAssemblyGunModel(pojo,com.tacz.guns.client.resource.pojo.model.BedrockVersion.NEW,weapon());
            var cubes=model.batchCubeCounts();var first=weapon().preset();model.prepareGeometry(first);var original=model.visibleBatchNames();assertFalse(original.isEmpty());
            assertTrue(model.usesInlineAttachment(AttachmentType.STOCK,weapon().createPart("tacz_stock_tactical_ar")));
            assertTrue(model.usesInlineAttachment(AttachmentType.STOCK,weapon().createPart("tacz_stock_moe")));
            assertFalse(model.usesInlineAttachment(AttachmentType.STOCK,ItemStack.EMPTY));
            assertTrue(original.stream().anyMatch(n->n.startsWith("assembly_editable_tacz_stock_tactical_ar_")));
            var inline=JsonParser.parseString(AssembledWeapon.resource("data/tacz_fork_tarkov/m4a1/inline_attachments.json")).getAsJsonObject().getAsJsonObject("stock");
            for(var candidate:inline.entrySet()){
                String definition=candidate.getValue().getAsString();
                var exchanged=AssemblyGunExchange.plan(weapon().preset(),weapon().createPart(definition),List.of("buffer","stock")).orElseThrow().held();
                assertTrue(model.usesInlineAttachment(AttachmentType.STOCK,weapon().createPart(definition)));
                model.prepareGeometry(exchanged);
                assertTrue(model.visibleBatchNames().stream().anyMatch(n->n.startsWith("assembly_editable_"+definition+"_")),definition);
                for(var other:inline.entrySet())if(!other.getValue().getAsString().equals(definition))
                    assertFalse(model.visibleBatchNames().stream().anyMatch(n->n.startsWith("assembly_editable_"+other.getValue().getAsString()+"_")),other.getKey());
            }
            for(var magazine:List.of("tacz_extended_mag_1","tacz_extended_mag_2","tacz_extended_mag_3")){
                var changed=AssemblyGunExchange.plan(weapon().preset(),weapon().createPart(magazine),List.of("magazine")).orElseThrow().held();
                model.prepareGeometry(changed);
                assertTrue(model.visibleBatchNames().stream().anyMatch(n->n.startsWith("assembly_editable_"+magazine+"_")),magazine);
                assertFalse(model.visibleBatchNames().stream().anyMatch(n->n.startsWith("assembly_editable_magazine_standard_")));
                for(var other:List.of("tacz_extended_mag_1","tacz_extended_mag_2","tacz_extended_mag_3"))if(!other.equals(magazine))
                    assertFalse(model.visibleBatchNames().stream().anyMatch(n->n.startsWith("assembly_editable_"+other+"_")),other);
            }
            var noStock=AssemblyGunExchange.plan(weapon().preset(),ItemStack.EMPTY,List.of("buffer","stock")).orElseThrow().held();
            model.prepareGeometry(noStock);
            assertFalse(model.visibleBatchNames().stream().anyMatch(n->n.startsWith("assembly_editable_tacz_stock_tactical_ar_")));
            model.prepareGeometry(first);
            var second=AssemblyGunExchange.plan(weapon().preset(),ItemStack.EMPTY,List.of("upper")).orElseThrow().held();
            model.prepareGeometry(second);assertTrue(model.visibleBatchNames().size()<original.size());
            var bare=weapon().createPart("lower_receiver");model.prepareGeometry(bare);
            assertFalse(model.visibleBatchNames().isEmpty());assertTrue(model.visibleBatchNames().stream().allMatch(n->n.startsWith("assembly_editable_lower_receiver_")));
            model.prepareGeometry(first);assertEquals(original,model.visibleBatchNames());assertEquals(cubes,model.batchCubeCounts());
            var scoped=AssemblyGunExchange.plan(first,weapon().createPart("tacz_scope_acog_ta31"),List.of("upper","scope")).orElseThrow().held();
            model.prepareGeometry(scoped);assertNotEquals(original,model.visibleBatchNames());model.prepareGeometry(first);assertEquals(original,model.visibleBatchNames());
            assertNotNull(model.getIronSightPath());assertNotNull(model.getRootNode());
        }
    }
}
