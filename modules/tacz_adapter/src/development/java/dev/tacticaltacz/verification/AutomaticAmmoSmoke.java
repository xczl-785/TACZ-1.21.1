package dev.tacticaltacz.verification;

import dev.tarkovcontent.ammunition.*;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import dev.tacticalinventory.api.TacticalAmmunition;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.definition.InventoryDefinitions;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import dev.itemfoundation.api.storage.ContainerComponents;
import dev.tacticaltacz.*;
import dev.tarkovcontent.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/** Actual persisted containers and validated payments, separate from reload animation smoke. */
final class AutomaticAmmoSmoke {
    static void run(ServerLevel level) {
        require(com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex().stream().filter(e->e.getKey().getNamespace().equals("tacz")).count()==15,"self-built runtime contains 15 retained guns");
        for(var id:List.of("kar98","lonetrail","m320","minigun","rpg7","springfield1873","taurus500","taurus943"))
            require(com.tacz.guns.api.TimelessAPI.getCommonGunIndex(ResourceLocation.parse("tacz:"+id)).isEmpty(),"excluded gun index: "+id);
        require(com.tacz.guns.api.TimelessAPI.getAllCommonAmmoIndex().size()==24,"retain 24 caliber rendering definitions");
        for(var name:List.of("gun_smith_table","workbench_a","workbench_b","workbench_c","ammo_box")) {
            var id=ResourceLocation.parse("tacz:"+name);
            require(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id),"retired item absent: "+id);
            require(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id),"retired block absent: "+id);
        }
        require(!net.minecraft.core.registries.BuiltInRegistries.MENU.containsKey(ResourceLocation.parse("tacz:gun_smith_table_menu")),"retired workstation menu absent");
        require(!net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(ResourceLocation.parse("tacz:gun_smith_table")),"retired workstation block entity absent");
        require(!level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT).containsKey(ResourceLocation.parse("tacz:blood_strike_1")),"retired painting absent");
        for(var name:List.of("target","statue","target_minecart"))require(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("tacz:"+name)),"preserved item: "+name);
        for(var name:List.of("target","statue"))require(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(ResourceLocation.parse("tacz:"+name)),"preserved block entity: "+name);
        require(com.tacz.guns.api.TimelessAPI.getAllCommonBlockIndex().isEmpty(),"no exported workstations reintroduced");
        System.out.println("EXTRA_CONTENT_RUNTIME PASS: workstations/ammo_box/painting/menu absent; target/statue/target_minecart retained");
        System.out.println("SELF_BUILT_TACZ_SMOKE PASS: 15 retained guns, retired indexes, 24 caliber definitions");
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.fromString("31477654-56bb-4943-a210-124490ffe312"),"AmmoPolicy"));
        var rounds=dev.tarkovcontent.TarkovContent.AMMUNITION.values().stream().map(v->v.get()).filter(a->a.definition().caliber().equals("9x19")).toList();
        var a=rounds.get(0);var b=rounds.get(1);
        var rig=ArmorVerification.armor();
        var backpackEntry=TarkovContent.CATALOG.stream().filter(e->e.category().equals("backpack")).findFirst().orElseThrow();
        var backpack=new ItemStack(TarkovContent.CONTAINERS.get(backpackEntry.tarkovId()).get());ContainerComponents.ensureState(backpack);
        var slots=new ArrayList<>(PlayerGearState.emptyFixedSlots());
        slots.set(GearSlot.CHEST_RIG.ordinal(),new FixedSlotSnapshot(GearSlot.CHEST_RIG,Optional.of(new FixedSlotEntry(UUID.randomUUID(),rig))));
        slots.set(GearSlot.BACKPACK.ordinal(),new FixedSlotSnapshot(GearSlot.BACKPACK,Optional.of(new FixedSlotEntry(UUID.randomUUID(),backpack))));
        player.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(PlayerGearState.CURRENT_SCHEMA_VERSION,0,Optional.empty(),slots,List.of(),Optional.empty()));
        require(TacticalGrantService.tryGrant(player,List.of()),"initialize player pocket");
        String bag=backpack.get(ContainerComponents.STATE.get()).areas().getFirst().storage().storageId();
        String chest=rig.get(ContainerComponents.STATE.get()).areas().getFirst().storage().storageId();
        grant(player,bag,new ItemStack(a,7));
        var gun=GunItemBuilder.create().setId(AmmoBridge.GUN).setFireMode(FireMode.SEMI).build(level.registryAccess());var g=(IGun)gun.getItem();
        var original=player.getData(ModRegistries.PLAYER_GEAR);
        require(!AmmoBridge.hasAmmo(player,gun)&&AmmoBridge.consume(player,gun,5)==0,"backpack cannot supply ammo");
        require(player.getData(ModRegistries.PLAYER_GEAR).equals(original),"failed payment does not mutate inventory");
        for(var id:PistolAdoption.IDS)grant(player,bag,GunItemBuilder.create().setId(id).setFireMode(FireMode.SEMI).build(level.registryAccess()));
        grant(player,"player:pocket",new ItemStack(b,5));
        require(AmmoBridge.candidate(player,gun)==b,"pocket fallback");
        grant(player,chest,new ItemStack(a,3));
        require(AmmoBridge.candidate(player,gun)==a,"rig precedes pocket");
        require(AmmoBridge.consume(player,gun,10)==3,"partial reload only consumes first type");g.setCurrentAmmoCount(gun,3);
        require(!AmmoBridge.hasAmmo(player,gun)&&AmmoBridge.consume(player,gun,2)==0,"loaded gun cannot mix second type");
        g.setCurrentAmmoCount(gun,0);g.setBulletInBarrel(gun,true);
        require(!AmmoBridge.hasAmmo(player,gun),"chamber alone locks type");
        g.setBulletInBarrel(gun,false);
        require(AmmoBridge.candidate(player,gun)==b&&AmmoBridge.consume(player,gun,2)==2,"fully empty gun automatically changes type");
        require(AmmoBridge.ammunition(gun)==b&&TacticalAmmunition.count(player,s->s.is(b))==3,"new identity and exact remaining quantity");
        for(var id:List.of("p90","uzi")) {
            var smg=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:"+id)).setFireMode(FireMode.SEMI).build(level.registryAccess());
            require(!smg.isEmpty(),"known SMG fixture");
            require(!InventoryDefinitions.CURRENT.compatibleGearSlots(smg).contains(GearSlot.SIDEARM),"SMG not a sidearm");
        }
        System.out.println("AUTOMATIC_AMMO_SMOKE PASS: backpack rejection and pistol intake, rig priority, pocket fallback, partial payment, chamber lock, empty switch, SMG sidearm exclusion");
    }
    private static void grant(ServerPlayer p,String storage,ItemStack stack) {
        var before=p.getData(ModRegistries.PLAYER_GEAR);
        var planned=TacticalGrantPlanner.plan(InventoryStateAdapter.combine(before),InventoryDefinitions.CURRENT,List.of(storage),List.of(stack)).orElseThrow(()->new IllegalStateException("intake "+storage+" "+stack));
        p.setData(ModRegistries.PLAYER_GEAR,InventoryStateAdapter.split(before,planned));
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
