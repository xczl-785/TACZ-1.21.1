package dev.tacticaltacz.verification;
import dev.tacticaltacz.assembled.*;
import dev.tacticaltacz.*;
import dev.itemfoundation.api.assembly.*;
import dev.itemfoundation.api.storage.ContainerComponents;
import dev.tacticalinventory.api.*;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.entity.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;

/** Actual server inventory, persistence, protocol and TaCZ projectile regression. */
final class AdarSmoke {
    private static final AssembledWeapon WEAPON=AssembledWeapons.byId(net.minecraft.resources.ResourceLocation.parse("newmod_adar:adar"));
    static void check(boolean value,String message){if(!value)throw new AssertionError("ADAR: "+message);}
    static void verify(ServerLevel level)throws Exception{
        var index=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(WEAPON.GUN).orElseThrow();
        check(index.getGunData().getAmmoAmount()==10,"PMAG capacity");
        var gun=WEAPON.preset();var nativeGun=(IGun)gun.getItem();
        check(WEAPON.ENGINE.validate(WEAPON.project(gun)).complete(),"full tree valid");
        check(AssemblyTrees.flatten(gun).size()==10,"ten real child items");AssemblyTrees.validate(gun);
        check(!AssembledWeapon.identity(gun).equals(AssembledWeapon.identity(WEAPON.preset())),"new guns have distinct identities");
        var ammo=com.tacz.guns.ammunition.AmmunitionRegistry.AMMUNITION.values().stream().map(v->v.get()).filter(a->a.definition().caliber().equals("556x45")).findFirst().orElseThrow();
        AmmoBridge.select(gun,ammo);nativeGun.setCurrentAmmoCount(gun,10);nativeGun.setBulletInBarrel(gun,true);
        var p=player(level,gun,true);
        var original=WEAPON.project(p.getMainHandItem());
        if (Boolean.getBoolean("newmod.adarAssemblyAudit")) verifyAssemblyInventory(level, gun);
        var saved=p.getMainHandItem().save(level.registryAccess());var restored=ItemStack.parse(level.registryAccess(),saved).orElseThrow();
        check(ItemStack.matches(restored,p.getMainHandItem()),"save restores gun, chamber, variant and all parts");
        var quote=query(p);var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try{AssemblyGunProtocol.View.CODEC.encode(wire,quote);var decoded=AssemblyGunProtocol.View.CODEC.decode(wire);check(ItemStack.matches(decoded.held(),quote.held()),"network snapshot preserves all components");}finally{wire.release();}
        var missing=AssemblyGunExchange.plan(restored,ItemStack.EMPTY,List.of("mod_reciever","mod_barrel")).orElseThrow().held();
        var shooter=new net.minecraft.world.entity.monster.Zombie(level);shooter.setPos(0,250,0);shooter.setNoAi(true);shooter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,missing);
        IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
        var api=new com.tacz.guns.item.ModernKineticGunScriptAPI();api.setShooter(shooter);api.setItemStack(missing);api.setDataHolder(new com.tacz.guns.entity.shooter.ShooterDataHolder());api.setPitchSupplier(()->0f);api.setYawSupplier(()->0f);
        int count=AdapterVerification.firedCount();var before=missing.copy();api.shootOnce(true);
        check(count==AdapterVerification.firedCount()&&ItemStack.matches(before,missing),"missing barrel actually blocks projectile and preserves ammo");
        nativeGun.setCurrentAmmoCount(restored,3);nativeGun.setBulletInBarrel(restored,true);shooter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,restored);IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);api.setItemStack(restored);api.shootOnce(true);
        check(AdapterVerification.firedCount()==count+1,"actual ADAR projectile spawned");
        check(nativeGun.getCurrentAmmoCount(restored)+(nativeGun.hasBulletInBarrel(restored)?1:0)==3,"one round consumed");
        java.nio.file.Files.writeString(java.nio.file.Path.of("adar-server.pass"),"PASS: actual ADAR projectile; missing-barrel fire gate; ten physical parts; ItemStack save and network roundtrip. Assembly inventory audit is separate and pending.\n");
        System.out.println("ADAR_SERVER_SMOKE PASS");
    }
    private static void verifyAssemblyInventory(ServerLevel level, ItemStack gun) {
        var nativeGun=(IGun)gun.getItem();
        var ammo=com.tacz.guns.ammunition.AmmunitionRegistry.AMMUNITION.values().stream().map(v->v.get()).filter(a->a.definition().caliber().equals("556x45")).findFirst().orElseThrow();
        var p=player(level,gun,true);
        var original=WEAPON.project(p.getMainHandItem());
        for(var child:AssemblyTrees.flatten(gun)){
            var quote=query(p);var request=new AssemblyGunProtocol.Request(UUID.randomUUID(),quote.token(),2,"",child.path());
            var removed=AssemblyGunWorkbench.handle(p,request);check(removed.result().equals("committed"),"remove "+child.path()+" result="+removed.result()+" ready="+AssemblyGunExchange.ready(p)+" proposal="+AssemblyGunExchange.plan(p.getMainHandItem(),ItemStack.EMPTY,child.path()).isPresent());
            var snapshot=p.getMainHandItem().copy();var inventory=p.getData(ModRegistries.PLAYER_GEAR);
            check(AssemblyGunWorkbench.handle(p,request).result().equals("rejected"),"replay denied");
            check(ItemStack.matches(snapshot,p.getMainHandItem())&&inventory==p.getData(ModRegistries.PLAYER_GEAR),"replay is atomic");
            if(child.path().equals(List.of("mod_reciever","mod_barrel")))check(AssemblyFireGate.blocked(p.getMainHandItem()),"barrel gate");
            if(child.path().equals(List.of("mod_magazine"))){
                check(nativeGun.getCurrentAmmoCount(p.getMainHandItem())==0&&nativeGun.hasBulletInBarrel(p.getMainHandItem()),"detach preserves chamber, clears mag count");
                check(TacticalAmmunition.count(p,s->s.is(ammo))==10,"ten exact rounds refunded");
                check(!((com.tacz.guns.api.item.gun.AbstractGunItem)gun.getItem()).canReload(p,p.getMainHandItem()),"cannot reload without magazine");
            }
            var fresh=query(p);var part=fresh.choices().stream().filter(c->AssembledWeapon.identity(c.stack()).equals(child.instanceId())).findFirst().orElseThrow();
            check(AssemblyGunWorkbench.handle(p,new AssemblyGunProtocol.Request(UUID.randomUUID(),fresh.token(),1,part.id(),child.path())).result().equals("committed"),"reinstall "+child.path());
            check(original.equals(WEAPON.project(p.getMainHandItem())),"all instance identities and nested topology restored");
        }
        // A change after the quote is rejected even if the displayed source still exists.
        var quote=query(p);nativeGun.setCurrentAmmoCount(p.getMainHandItem(),1);var before=p.getMainHandItem().copy();
        check(AssemblyGunWorkbench.handle(p,new AssemblyGunProtocol.Request(UUID.randomUUID(),quote.token(),2,"",List.of("mod_charge"))).result().equals("rejected"),"stale held state rejected");
        check(ItemStack.matches(before,p.getMainHandItem()),"stale state unchanged");nativeGun.setCurrentAmmoCount(p.getMainHandItem(),0);
        var full=player(level,gun,false);check(TacticalContent.tryGrant(full,List.of(new ItemStack(Items.BEDROCK,256))),"fill pocket");
        quote=query(full);before=full.getMainHandItem().copy();var inventory=full.getData(ModRegistries.PLAYER_GEAR);
        check(AssemblyGunWorkbench.handle(full,new AssemblyGunProtocol.Request(UUID.randomUUID(),quote.token(),2,"",List.of("mod_reciever","mod_barrel"))).result().equals("rejected"),"full inventory denies refund");
        check(ItemStack.matches(before,full.getMainHandItem())&&inventory==full.getData(ModRegistries.PLAYER_GEAR),"full failure is atomic");
    }
    private static AssemblyGunProtocol.View query(ServerPlayer p){return AssemblyGunWorkbench.handle(p,new AssemblyGunProtocol.Request(UUID.randomUUID(),AssemblyGunProtocol.EMPTY,0,"",List.of()));}
    private static ServerPlayer player(ServerLevel level,ItemStack gun,boolean bag){
        var p=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AdarSmoke"));
        var slots=new ArrayList<>(PlayerGearState.emptyFixedSlots());slots.set(GearSlot.PRIMARY_WEAPON_1.ordinal(),new FixedSlotSnapshot(GearSlot.PRIMARY_WEAPON_1,Optional.of(new FixedSlotEntry(UUID.randomUUID(),gun.copy()))));
        if(bag){var item=dev.tarkovcontent.TarkovContent.CONTAINERS.values().stream().map(h->h.get().getDefaultInstance()).filter(s->dev.itemfoundation.api.definition.ItemProfiles.definition(s).orElseThrow().wearableSlots().contains("tactical_inventory:backpack")).max(java.util.Comparator.comparingInt(s->dev.itemfoundation.api.definition.ItemProfiles.definition(s).orElseThrow().containerAreas().stream().mapToInt(a->a.size().width()*a.size().height()).sum())).orElseThrow();ContainerComponents.ensureState(item);slots.set(GearSlot.BACKPACK.ordinal(),new FixedSlotSnapshot(GearSlot.BACKPACK,Optional.of(new FixedSlotEntry(UUID.randomUUID(),item))));}
        p.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(2,0,Optional.empty(),slots,List.of(),Optional.empty()));
        check(TacticalContent.tryGrant(p,List.of()),"initialize storage");check(PlayerInventoryService.activateSource(p,new FixedSlotLocation(GearSlot.PRIMARY_WEAPON_1),0,UUID.randomUUID()),"lease custom gun");return p;
    }
}
