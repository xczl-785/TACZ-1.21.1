package dev.tacticaltacz.verification;

import java.util.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAttachment;
import dev.tacticalinventory.verification.*;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class DevelopmentTaczCatalogSmoke {
    static void verify(ServerLevel level){
        var catalog=DevelopmentItemCatalog.entries();
        for(var old:List.of("tarkov_content:test_flesh_9x19","tarkov_content:test_ap_9x19")) {
            var id=ResourceLocation.parse(old);
            require(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id),"retired test round unregistered");
            require(!catalog.containsKey(id),"retired test round absent from catalog");
        }
        try {java.nio.file.Files.writeString(java.nio.file.Path.of("development-gun-cache.json"),new com.google.gson.Gson().toJson(com.tacz.guns.resource.CommonAssetsManager.getInstance().getNetworkCache()));
            java.nio.file.Files.writeString(java.nio.file.Path.of("development-catalog.json"),DevelopmentItemCatalog.Entry.CODEC.listOf().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,List.copyOf(catalog.values())).getOrThrow().toString());}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try {
            DevelopmentItems.Reply.CODEC.encode(wire,new DevelopmentItems.Reply(true,"目录",List.copyOf(catalog.values())));
            require(wire.readableBytes()<1048576,"catalog within clientbound payload limit");
            var decoded=DevelopmentItems.Reply.CODEC.decode(wire);
            require(decoded.entries().size()==catalog.size(),"network catalog complete");
            for(var entry:decoded.entries())require(ItemStack.isSameItemSameComponents(entry.stack(),catalog.get(entry.key()).stack()),"network preset preserved");
        } finally {wire.release();}
        var entries=catalog.values().stream().filter(e->e.source().equals("tacz")).toList();
        require(entries.size()==189,"15 guns + 85 attachments + 86 rounds + 3 retained props");
        require(entries.stream().filter(e->e.key().getPath().startsWith("gun/")).count()==15,"all guns");
        require(entries.stream().filter(e->e.key().getPath().startsWith("attachment/")).count()==85,"all attachments");
        require(entries.stream().filter(e->e.key().getNamespace().equals("tarkov_content")).count()==86,"legacy ammo IDs have current source");
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CatalogSmoke")){
            @Override public boolean hasPermissions(int level){return true;}
        };
        verifyPickupRules(level,player,catalog);
        for(var entry:entries){
            var bag=dev.tarkovcontent.TarkovContent.CONTAINERS.get("5df8a4d786f77412672a1e3b").get().getDefaultInstance();
            dev.itemfoundation.api.storage.ContainerComponents.ensureState(bag);
            var slots=new ArrayList<>(PlayerGearState.emptyFixedSlots());
            var slot=dev.tacticalinventory.core.GearSlot.BACKPACK;
            slots.set(slot.ordinal(),new dev.tacticalinventory.core.FixedSlotSnapshot(slot,Optional.of(new dev.tacticalinventory.core.FixedSlotEntry(UUID.randomUUID(),bag))));
            player.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(PlayerGearState.CURRENT_SCHEMA_VERSION,0,Optional.empty(),slots,List.of(),Optional.empty()));
            var result=DevelopmentItems.grant(player,new DevelopmentItems.Request(entry.key(),1));
            require(result.startsWith("已领取"),"actual grant "+entry.key()+": "+result);
            var snapshot=InventoryStateAdapter.combine(player.getData(ModRegistries.PLAYER_GEAR));
            var stored=new ArrayList<ItemStack>();
            snapshot.storages().stream().flatMap(area->area.entries().stream()).forEach(e->stored.add(e.stack()));
            snapshot.fixedSlots().stream().filter(slotEntry->slotEntry.slot()!=dev.tacticalinventory.core.GearSlot.BACKPACK)
                    .flatMap(slotEntry->slotEntry.entry().stream())
                    .forEach(e->stored.add(e.stack()));
            require(stored.size()==1&&ItemStack.isSameItemSameComponents(stored.getFirst(),entry.stack()),"complete preset survives grant "+entry.key());
            var stack=stored.getFirst();
            if(stack.getItem() instanceof IGun gun){
                require(gun.getGunId(stack).getPath().equals(entry.key().getPath().substring(4)),"exact gun identity");
                require(gun.getCurrentAmmoCount(stack)==0&&!gun.hasBulletInBarrel(stack),"guns granted empty, no native ammo");
            }
            if(stack.getItem() instanceof IAttachment attachment)require(attachment.getAttachmentId(stack).getPath().equals(entry.key().getPath().substring(11)),"exact attachment identity");
            var restored=ItemStack.parseOptional(level.registryAccess(),(net.minecraft.nbt.CompoundTag)stack.save(level.registryAccess()));
            require(ItemStack.isSameItemSameComponents(stack,restored),"saved preset retains identity");
        }
        for(var bad:List.of("tacz:ammo","tacz:modern_kinetic_gun","tacz:attachment","tacz:gun/ak47","tacz:gun/missing","tarkov_content:test_flesh_9x19","tarkov_content:test_ap_9x19")){
            var before=player.getData(ModRegistries.PLAYER_GEAR);
            require(!DevelopmentItems.grant(player,new DevelopmentItems.Request(ResourceLocation.parse(bad),1)).startsWith("已领取"),"reject raw/retired/forged key");
            require(before==player.getData(ModRegistries.PLAYER_GEAR),"denial preserves inventory");
        }
        System.out.println("DEVELOPMENT_TACZ_CATALOG PASS: all 189 entries real grant and save, 15 exact empty guns, 85 exact attachments, 86 migrated rounds, raw/retired/forged denial");
    }
    private static void verifyPickupRules(ServerLevel level,net.minecraft.server.level.ServerPlayer player,
            Map<ResourceLocation,DevelopmentItemCatalog.Entry> catalog) {
        var key=ResourceLocation.parse("tacz:gun/glock_17");
        var gun=catalog.get(key).stack();var original=gun.copy();
        player.setData(ModRegistries.PLAYER_GEAR,emptyGear());
        var before=player.getData(ModRegistries.PLAYER_GEAR);
        require(!dev.tacticalinventory.api.TacticalContent.tryGrant(player,List.of(gun)),"normal storage grant still never auto-equips");
        require(before==player.getData(ModRegistries.PLAYER_GEAR),"normal grant failure unchanged");
        require(DevelopmentItems.grant(player,new DevelopmentItems.Request(key,1)).startsWith("已领取"),"bare player can claim Glock through pickup");
        var equipped=player.getData(ModRegistries.PLAYER_GEAR).fixedSlot(dev.tacticalinventory.core.GearSlot.SIDEARM).entry().orElseThrow().stack();
        require(ItemStack.isSameItemSameComponents(gun,equipped),"Glock preset auto-equipped exactly");
        before=player.getData(ModRegistries.PLAYER_GEAR);
        require(DevelopmentItems.grant(player,new DevelopmentItems.Request(key,1)).startsWith("未领取"),"occupied sidearm and one-cell pockets reject second gun");
        require(before==player.getData(ModRegistries.PLAYER_GEAR),"failed pickup leaves all inventory unchanged");
        var bag=dev.tarkovcontent.TarkovContent.CONTAINERS.get("5df8a4d786f77412672a1e3b").get();
        var bagKey=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bag);
        require(DevelopmentItems.grant(player,new DevelopmentItems.Request(bagKey,1)).startsWith("已领取"),"bare backpack slot can receive backpack without storage capacity");
        require(player.getData(ModRegistries.PLAYER_GEAR).fixedSlot(dev.tacticalinventory.core.GearSlot.BACKPACK).entry().orElseThrow().stack().is(bag),"backpack equipped by pickup");
        require(DevelopmentItems.grant(player,new DevelopmentItems.Request(key,1)).startsWith("已领取"),"occupied weapon slot falls back to equipped backpack");
        require(InventoryStateAdapter.combine(player.getData(ModRegistries.PLAYER_GEAR)).storages().stream()
                .flatMap(area->area.entries().stream()).anyMatch(e->ItemStack.isSameItemSameComponents(gun,e.stack())),"second gun stored with preset intact");
        require(ItemStack.isSameItemSameComponents(gun,original),"pickup does not mutate caller input");
        player.setData(ModRegistries.PLAYER_GEAR,emptyGear());
        var pos=player.getEyePosition().add(player.getLookAngle().scale(.8));
        var entity=new net.minecraft.world.entity.item.ItemEntity(level,pos.x,pos.y,pos.z,gun.copy());
        entity.setNoPickUpDelay();level.addFreshEntity(entity);
        try {
            require(WorldPickupTargeting.canTarget(player,entity),"actual world source passes target checks");
            WorldPickupHandler.pickup(player,entity.getUUID());
            require(entity.isRemoved(),"successful world pickup consumes actual entity");
            require(ItemStack.isSameItemSameComponents(gun,player.getData(ModRegistries.PLAYER_GEAR)
                    .fixedSlot(dev.tacticalinventory.core.GearSlot.SIDEARM).entry().orElseThrow().stack()),"world and dev pickup equip same preset");
        } finally {entity.discard();}
        var blocked=new net.minecraft.world.entity.item.ItemEntity(level,pos.x,pos.y,pos.z,gun.copy());
        blocked.setNoPickUpDelay();level.addFreshEntity(blocked);
        try {
            before=player.getData(ModRegistries.PLAYER_GEAR);
            WorldPickupHandler.pickup(player,blocked.getUUID());
            require(!blocked.isRemoved()&&ItemStack.isSameItemSameComponents(gun,blocked.getItem()),"failed world pickup preserves ground source");
            require(before==player.getData(ModRegistries.PLAYER_GEAR),"failed world pickup preserves inventory");
        } finally {blocked.discard();}
        System.out.println("SHARED_PICKUP_SMOKE PASS: bare Glock, occupied slot rejection, backpack auto-equip, storage fallback, ordinary grant unchanged, world entity consumed only on success");
    }
    private static PlayerGearState emptyGear(){
        return new PlayerGearState(PlayerGearState.CURRENT_SCHEMA_VERSION,0,Optional.empty(),PlayerGearState.emptyFixedSlots(),List.of(),Optional.empty());
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
