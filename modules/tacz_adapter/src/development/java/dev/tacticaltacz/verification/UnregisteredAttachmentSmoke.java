package dev.tacticaltacz.verification;

import dev.itemfoundation.api.definition.*;
import dev.itemfoundation.api.inventory.*;
import dev.itemfoundation.api.storage.*;
import dev.tacticalinventory.api.*;
import dev.tacticalinventory.application.*;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.definition.InventoryDefinitions;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;

final class UnregisteredAttachmentSmoke {
    static void verify(ServerLevel level){
        var id=com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex().iterator().next().getKey();
        var item=com.tacz.guns.api.item.builder.AttachmentItemBuilder.create().setId(id).build();
        require(UnregisteredItems.isUnregistered(item),"TaCZ attachment intentionally has no project adoption");
        require(InventoryDefinitions.CURRENT.footprint(item).orElseThrow().equals(new ItemFootprint(1,1)),"unknown attachment one-cell footprint");
        require(UnregisteredItems.display(item,ItemDisplayData.EMPTY).unitWeightKg().isEmpty(),"unknown attachment has no project weight");
        int compartments=0;
        for(var holder:dev.tarkovcontent.TarkovContent.CONTAINERS.values()){
            var carrier=holder.get().getDefaultInstance();
            for(var area:ItemProfiles.definition(carrier).orElseThrow().containerAreas()){
                require(ContainerAdmissions.canStore(carrier,area.areaId(),item),"shared boundary admits attachment in "+carrier+"/"+area.areaId());
                require(((ContainerAdmissionProvider)carrier.getItem()).canStore(carrier,area.areaId(),item),"direct content provider agrees");
                compartments++;
            }
        }
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"UnknownPickup"));
        player.setData(ModRegistries.PLAYER_GEAR,emptyGear(Optional.empty(),PlayerGearState.emptyFixedSlots()));
        require(TacticalPickup.tryPickup(player,item).accepted(),"unknown attachment picked into bare player pocket");
        var pocket=player.getData(ModRegistries.PLAYER_GEAR).pocket().orElseThrow();
        require(pocket.entries().size()==1,"unknown pickup uses pocket");
        require(player.getData(ModRegistries.PLAYER_GEAR).fixedSlots().stream().allMatch(s->s.entry().isEmpty()),"unknown pickup does not auto-occupy special slots");
        var snapshot=InventoryStateAdapter.combine(player.getData(ModRegistries.PLAYER_GEAR));
        for(var slot:List.of(GearSlot.SPECIAL_1,GearSlot.SPECIAL_2,GearSlot.SPECIAL_3)){
            var coordinator=new InventoryCoordinator(new AuthoritativeInventory(snapshot),InventoryDefinitions.CURRENT,8);
            var result=coordinator.execute(new InventoryOperationRequest(UUID.randomUUID(),snapshot.stateRevision(),InventoryActionId.EQUIP,
                    new ItemLocation(pocket.storageId(),pocket.entries().getFirst().entryId()),new FixedSlotLocation(slot)));
            require(!result.succeeded(),"unregistered item has no special-slot qualification: "+slot);
        }
        for(var slot:List.of(GearSlot.BACKPACK,GearSlot.CHEST_RIG,GearSlot.SECURE_CONTAINER)){
            var carrier=dev.tarkovcontent.TarkovContent.CONTAINERS.values().stream().map(h->h.get().getDefaultInstance())
                    .filter(s->ItemProfiles.definition(s).orElseThrow().wearableSlots().contains("tactical_inventory:"+slot.name().toLowerCase(java.util.Locale.ROOT)))
                    .findFirst().orElseThrow();
            ContainerComponents.ensureState(carrier);
            var slots=new ArrayList<>(PlayerGearState.emptyFixedSlots());
            slots.set(slot.ordinal(),new FixedSlotSnapshot(slot,Optional.of(new FixedSlotEntry(UUID.randomUUID(),carrier))));
            var occupied=new ArrayList<InventoryEntry>();
            for(int x=0;x<4;x++)occupied.add(new InventoryEntry(UUID.randomUUID(),new ItemStack(Items.BEDROCK,64),new GridPosition(x,0),Orientation.DEFAULT));
            player.setData(ModRegistries.PLAYER_GEAR,emptyGear(Optional.of(new GridStorageSnapshot("player:pocket",new GridSize(4,1),occupied)),slots));
            require(TacticalPickup.tryPickup(player,item).accepted(),"full pockets fall back into "+slot);
            var full=InventoryStateAdapter.combine(player.getData(ModRegistries.PLAYER_GEAR));
            require(full.storages().stream().filter(s->!s.storageId().equals("player:pocket")).flatMap(s->s.entries().stream())
                    .anyMatch(e->ItemStack.isSameItemSameComponents(e.stack(),item)),"actual component-bearing attachment stored in "+slot);
        }
        var stash=player.getData(ModRegistries.PERSONAL_STASH).storage();
        var full=InventoryStateAdapter.combine(player.getData(ModRegistries.PLAYER_GEAR),dev.tacticalinventory.storage.ExternalStorageAccess.read(player),List.of(stash));
        require(TacticalGrantPlanner.plan(full,InventoryDefinitions.CURRENT,List.of(stash.storageId()),List.of(item)).isPresent(),"personal stash intake accepts unknown item");
        var restored=ItemStack.parseOptional(level.registryAccess(),(net.minecraft.nbt.CompoundTag)item.save(level.registryAccess()));
        require(ItemStack.isSameItemSameComponents(item,restored),"native attachment identity survives serialization");
        System.out.println("UNREGISTERED_ATTACHMENT_SMOKE PASS: "+compartments+" compartments, pocket/backpack/rig/secure/stash, special slots rejected, one-cell unknown weight, native component preservation");
    }
    private static PlayerGearState emptyGear(Optional<GridStorageSnapshot> pocket,List<FixedSlotSnapshot> slots){return new PlayerGearState(PlayerGearState.CURRENT_SCHEMA_VERSION,0,pocket,slots,List.of(),Optional.empty());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
