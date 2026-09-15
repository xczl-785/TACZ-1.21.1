package dev.tacticaltacz.verification;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import dev.itemfoundation.api.storage.ContainerComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.core.component.DataComponents;
import java.util.*;
final class WaterIntakeSmoke {
 static void verify(ServerPlayer p,ItemStack water,ItemStack emptyBottle){
 var previous=p.getData(ModRegistries.PLAYER_GEAR);
 try{
  for(String id:List.of("5df8a4d786f77412672a1e3b","5df8a42886f77412640e2e75")){
   var carrier=new ItemStack(dev.tarkovcontent.TarkovContent.CONTAINERS.get(id).get());ContainerComponents.ensureState(carrier);
   var slot=id.equals("5df8a4d786f77412672a1e3b")?GearSlot.BACKPACK:GearSlot.CHEST_RIG;
   var slots=new ArrayList<>(PlayerGearState.emptyFixedSlots());slots.set(slot.ordinal(),new FixedSlotSnapshot(slot,Optional.of(new FixedSlotEntry(UUID.randomUUID(),carrier))));
   var state=new PlayerGearState(PlayerGearState.CURRENT_SCHEMA_VERSION,0,Optional.empty(),slots,List.of(),Optional.empty());
   var snapshot=InventoryStateAdapter.combine(state);
   for(var area:carrier.get(ContainerComponents.STATE.get()).areas()){
    var dest=List.of(area.storage().storageId());
    require(TacticalGrantPlanner.plan(snapshot,dev.tacticalinventory.definition.InventoryDefinitions.CURRENT,dest,List.of(water)).isPresent(),"water enters "+id+" / "+area.areaId());
    require(TacticalGrantPlanner.plan(snapshot,dev.tacticalinventory.definition.InventoryDefinitions.CURRENT,dest,List.of(emptyBottle)).isPresent(),"empty bottle enters "+id+" / "+area.areaId());
   }
   p.setData(ModRegistries.PLAYER_GEAR,state);
   int granted=p.server.getCommands().getDispatcher().execute("give @s minecraft:potion[minecraft:potion_contents=\"minecraft:water\"] 1",p.createCommandSourceStack().withPermission(2));
   require(granted==1,"actual give command succeeds");
   var result=InventoryStateAdapter.combine(p.getData(ModRegistries.PLAYER_GEAR));
   require(result.storages().stream().flatMap(s->s.entries().stream()).filter(e->ItemStack.isSameItemSameComponents(e.stack(),water)).mapToInt(e->e.stack().getCount()).sum()==1,"give persisted one water bottle");
  }
  var potion=new ItemStack(Items.POTION);potion.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.HEALING));
  require(dev.itemfoundation.api.identity.ItemIdentities.server().resolve(potion).identities().orElseThrow().directTags().isEmpty(),"other potions are not adopted as drinking water");
  System.out.println("WATER_INTAKE_SMOKE PASS: real give / 6Sh118 / MPPV every compartment / empty bottle / other potion isolation");
 }catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new AssertionError("water command syntax",e);}
 finally{p.setData(ModRegistries.PLAYER_GEAR,previous);}
 }
 private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
