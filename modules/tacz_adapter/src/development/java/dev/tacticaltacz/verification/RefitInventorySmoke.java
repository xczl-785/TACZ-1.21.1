package dev.tacticaltacz.verification;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.*;
import dev.tacticaltacz.refit.*;
import dev.tacticaltacz.AmmoBridge;
import dev.tacticalinventory.api.*;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import dev.itemfoundation.api.inventory.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;

final class RefitInventorySmoke {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError("Refit: "+message);}
    static void verify(ServerLevel level)throws Exception {
        var gun=GunItemBuilder.create().setId(ResourceLocation.parse("tacz_fork_tarkov:m4a1")).build(level.registryAccess());
        var g=(IGun)gun.getItem();g.setCurrentAmmoCount(gun,0);g.setBulletInBarrel(gun,false);
        var all=TimelessAPI.getAllCommonAttachmentIndex().stream().map(e->AttachmentItemBuilder.create().setId(e.getKey()).build()).toList();
        var scopes=all.stream().filter(s->((IAttachment)s.getItem()).getType(s)==AttachmentType.SCOPE&&g.allowAttachment(gun,s)).toList();
        check(scopes.size()>=2,"two native compatible M4 optics");var first=scopes.get(0);var second=scopes.get(1);
        var p=player(level,gun,List.of(first));
        var view=query(p);check(view.choices().size()==1,"pocket catalog sees native component-bearing optic");
        var req=installRequest(view,view.choices().getFirst().id());
        var result=RefitBridge.handle(p,req);check(result.result().equals("installed"),"scope ordinal zero installs");
        check(ItemStack.matches(g.getAttachment(level.registryAccess(),p.getMainHandItem(),AttachmentType.SCOPE),first),"actual gun attachment installed");
        var before=p.getMainHandItem().copy();var state=p.getData(ModRegistries.PLAYER_GEAR);
        check(RefitBridge.handle(p,req).result().equals("stale"),"repeated token rejected");
        check(ItemStack.matches(before,p.getMainHandItem())&&p.getData(ModRegistries.PLAYER_GEAR)==state,"replay changes nothing");
        result=unload(p,query(p),AttachmentType.SCOPE);check(result.result().equals("unloaded")&&count(p,first)==1,"scope zero unload returns one");
        check(g.getAttachment(level.registryAccess(),p.getMainHandItem(),AttachmentType.SCOPE).isEmpty(),"removed from actual gun");
        // Moving/changing inventory after listing rejects rather than selecting a different item.
        view=query(p);check(TacticalContent.tryGrant(p,List.of(new ItemStack(Items.BEDROCK))),"change inventory");
        check(RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id())).result().equals("rejected"),"stale inventory version rejects");
        check(count(p,first)==1,"stale entry not consumed");
        var incompatible=all.stream().filter(s->!g.allowAttachment(gun,s)).findFirst().orElseThrow();
        p=player(level,gun,List.of(incompatible));view=query(p);
        check(RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id())).result().equals("rejected")&&count(p,incompatible)==1,"forged incompatible selection rejected server-side");
        // Replace in a full pocket, with the old attachment returning to the exact freed cell.
        var equipped=gun.copy();g.installAttachment(level.registryAccess(),equipped,first);
        p=player(level,equipped,List.of(second,new ItemStack(Items.BEDROCK,192)));view=query(p);
        var source=InventoryStateAdapter.combine(p.getData(ModRegistries.PLAYER_GEAR)).storage("player:pocket").orElseThrow().entries().stream().filter(e->ItemStack.isSameItemSameComponents(e.stack(),second)).findFirst().orElseThrow();
        result=RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id()));check(result.result().equals("installed"),"full-pocket swap succeeds");
        check(InventoryStateAdapter.combine(p.getData(ModRegistries.PLAYER_GEAR)).storage("player:pocket").orElseThrow().entries().stream().anyMatch(e->ItemStack.isSameItemSameComponents(e.stack(),first)&&e.position().equals(source.position())),"old optic uses freed position");
        before=p.getMainHandItem().copy();state=p.getData(ModRegistries.PLAYER_GEAR);
        check(unload(p,query(p),AttachmentType.SCOPE).result().equals("rejected"),"full pocket refuses unload");
        check(ItemStack.matches(before,p.getMainHandItem())&&state==p.getData(ModRegistries.PLAYER_GEAR),"failed unload preserves both owners");
        // Native extended-mag return is part of the same plan; chamber is retained.
        var ext=all.stream().filter(s->((IAttachment)s.getItem()).getType(s)==AttachmentType.EXTENDED_MAG&&g.allowAttachment(gun,s)).findFirst().orElseThrow();
        equipped=gun.copy();g.installAttachment(level.registryAccess(),equipped,ext);
        var ammo=net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i->i instanceof dev.tarkovcontent.ammunition.TarkovAmmunitionItem a&&a.definition().caliber().equals("556x45")).map(i->(dev.tarkovcontent.ammunition.TarkovAmmunitionItem)i).findFirst().orElseThrow();
        AmmoBridge.select(equipped,ammo);g.setCurrentAmmoCount(equipped,12);g.setBulletInBarrel(equipped,true);
        p=player(level,equipped,List.of(ext,new ItemStack(Items.BEDROCK,192)));view=query(p);before=p.getMainHandItem().copy();state=p.getData(ModRegistries.PLAYER_GEAR);
        check(RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id())).result().equals("rejected"),"ammo refund cannot fit: entire swap rejected");
        check(ItemStack.matches(before,p.getMainHandItem())&&state==p.getData(ModRegistries.PLAYER_GEAR),"ammo and attachments unchanged on failure");
        var pocket=state.pocket().orElseThrow();var filler=pocket.entries().stream().filter(e->e.stack().is(Items.BEDROCK)).findFirst().orElseThrow();
        p.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(state.schemaVersion(),state.stateRevision()+1,Optional.of(pocket.without(filler.entryId())),state.fixedSlots(),state.quickReferences(),state.mainHandLease()));
        view=query(p);check(RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id())).result().equals("installed"),"swap succeeds with refund room");
        check(g.getCurrentAmmoCount(p.getMainHandItem())==0&&g.hasBulletInBarrel(p.getMainHandItem())&&count(p,new ItemStack(ammo))==12,"12 exact rounds refunded; chamber retained");
        // Held item mutation without an inventory revision also invalidates the proposal.
        view=query(p);g.setCurrentAmmoCount(p.getMainHandItem(),1);
        check(unload(p,view,AttachmentType.EXTENDED_MAG).result().equals("rejected"),"gun state changed since listing");
        var saved=p.getMainHandItem().save(level.registryAccess());check(ItemStack.matches(p.getMainHandItem(),ItemStack.parse(level.registryAccess(),saved).orElseThrow()),"native attachment and chamber survive save");
        for(var slot:List.of(GearSlot.BACKPACK,GearSlot.CHEST_RIG,GearSlot.SECURE_CONTAINER)){
            p=player(level,gun,List.of());var carrier=dev.tarkovcontent.TarkovContent.CONTAINERS.values().stream().map(h->h.get().getDefaultInstance()).filter(s->dev.itemfoundation.api.definition.ItemProfiles.definition(s).orElseThrow().wearableSlots().contains("tactical_inventory:"+slot.name().toLowerCase(Locale.ROOT))).findFirst().orElseThrow();
            dev.itemfoundation.api.storage.ContainerComponents.ensureState(carrier);state=p.getData(ModRegistries.PLAYER_GEAR);var slots=new ArrayList<>(state.fixedSlots());slots.set(slot.ordinal(),new FixedSlotSnapshot(slot,Optional.of(new FixedSlotEntry(UUID.randomUUID(),carrier))));
            p.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(state.schemaVersion(),state.stateRevision()+1,state.pocket(),slots,state.quickReferences(),state.mainHandLease()));
            check(TacticalContent.tryGrant(p,List.of(new ItemStack(Items.BEDROCK,256),first)),"fill pocket then store optic in "+slot);
            view=query(p);check(view.choices().size()==1,"direct carried "+slot+" visible");
            check(RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id())).result().equals("installed"),"install from "+slot);
        }
        // Protocol roundtrip uses the same real-server registry and complete item components.
        p=player(level,gun,List.of(first));view=query(p);var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try {RefitProtocol.View.CODEC.encode(wire,view);var decoded=RefitProtocol.View.CODEC.decode(wire);check(ItemStack.matches(decoded.choices().getFirst().stack(),first),"wire preserves attachment identity");}finally{wire.release();}
        p=player(level,gun,List.of(first.copyWithCount(2)));view=query(p);
        check(RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id())).result().equals("installed")&&count(p,first)==1,"only one physical attachment consumed");
        p=player(level,gun,List.of(first));view=query(p);g.setAttachmentLock(p.getMainHandItem(),true);
        check(RefitBridge.handle(p,installRequest(view,view.choices().getFirst().id())).result().equals("rejected")&&count(p,first)==1,"lock rechecked at submission");
        p=player(level,gun,List.of());var stash=p.getData(ModRegistries.PERSONAL_STASH).storage();var hiddenId=UUID.randomUUID();
        p.setData(ModRegistries.PERSONAL_STASH,new dev.tacticalinventory.storage.PersonalStash(stash.with(new InventoryEntry(hiddenId,first,new GridPosition(0,0),Orientation.DEFAULT))));
        view=query(p);check(view.choices().isEmpty(),"warehouse excluded from catalog");
        check(RefitBridge.handle(p,installRequest(view,hiddenId.toString())).result().equals("rejected"),"forged warehouse reference denied");
        var actor=p;
        var context=(net.neoforged.neoforge.network.handling.IPayloadContext)java.lang.reflect.Proxy.newProxyInstance(
                RefitInventorySmoke.class.getClassLoader(),new Class[]{net.neoforged.neoforge.network.handling.IPayloadContext.class},(proxy,method,args)->{
                    if(method.getName().equals("player"))return actor;
                    if(method.getName().equals("enqueueWork")){if(args[0] instanceof Runnable action)action.run();else ((java.util.function.Supplier<?>)args[0]).get();return java.util.concurrent.CompletableFuture.completedFuture(null);}
                    throw new UnsupportedOperationException(method.getName());
                });
        before=p.getMainHandItem().copy();state=p.getData(ModRegistries.PLAYER_GEAR);
        com.tacz.guns.network.message.ClientMessageRefitGun.handle(new com.tacz.guns.network.message.ClientMessageRefitGun(Integer.MAX_VALUE,0,AttachmentType.SCOPE),context);
        com.tacz.guns.network.message.ClientMessageUnloadAttachment.handle(new com.tacz.guns.network.message.ClientMessageUnloadAttachment(0,AttachmentType.SCOPE),context);
        check(ItemStack.matches(before,p.getMainHandItem())&&state==p.getData(ModRegistries.PLAYER_GEAR),"legacy slot packets cannot bypass owner or access forged index");
        System.out.println("REFIT_INVENTORY_SMOKE PASS: scope install/unload, native compatibility, full-cell swap, rejection atomicity, replay/stale gun and inventory, 12 exact refund rounds/chamber, carried bag/rig/secure, one-item payment, lock/stash/legacy packet denial, wire and save");
    }
    private static ServerPlayer player(ServerLevel level,ItemStack gun,List<ItemStack> contents){
        var p=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RefitSmoke"));
        var slots=new ArrayList<>(PlayerGearState.emptyFixedSlots());slots.set(GearSlot.PRIMARY_WEAPON_1.ordinal(),new FixedSlotSnapshot(GearSlot.PRIMARY_WEAPON_1,Optional.of(new FixedSlotEntry(UUID.randomUUID(),gun.copy()))));
        p.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(2,0,Optional.empty(),slots,List.of(),Optional.empty()));
        check(TacticalContent.tryGrant(p,contents),"fixture content grant");check(PlayerInventoryService.activateSource(p,new FixedSlotLocation(GearSlot.PRIMARY_WEAPON_1),0,UUID.randomUUID()),"real held lease");return p;
    }
    private static RefitProtocol.View query(ServerPlayer p){return RefitBridge.handle(p,new RefitProtocol.Request(UUID.randomUUID(),RefitProtocol.EMPTY,0,"",0));}
    private static RefitProtocol.Request installRequest(RefitProtocol.View v,String id){return new RefitProtocol.Request(UUID.randomUUID(),v.token(),1,id,0);}
    private static RefitProtocol.View unload(ServerPlayer p,RefitProtocol.View v,AttachmentType type){return RefitBridge.handle(p,new RefitProtocol.Request(UUID.randomUUID(),v.token(),2,"",type.ordinal()));}
    private static int count(ServerPlayer p,ItemStack item){return InventoryStateAdapter.combine(p.getData(ModRegistries.PLAYER_GEAR)).storages().stream().flatMap(s->s.entries().stream()).filter(e->ItemStack.isSameItemSameComponents(e.stack(),item)).mapToInt(e->e.stack().getCount()).sum();}
}
