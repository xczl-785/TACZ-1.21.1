package dev.tacticaltacz.verification;
import com.tacz.guns.api.entity.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.*;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import dev.itemfoundation.api.assembly.*;
import dev.tacticalinventory.api.*;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import dev.weaponruntime.WeaponRuntime;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;

/** Real TaCZ projectile execution and physical held exchange, in the existing isolated server. */
final class BarrelFireSmoke {
    private static void check(boolean b,String message){if(!b)throw new AssertionError("Barrel gate: "+message);}
    static void verify(ServerLevel level) {
        var nativeGun=AdapterVerification.gun(dev.tacticaltacz.development.VerificationRounds.flesh(),level);
        check(!dev.tacticaltacz.AssemblyFireGate.blocked(nativeGun),"unmanaged native gun unaffected");
        var barrel=BarrelFireFixture.barrel();var managed=BarrelFireFixture.attach(nativeGun,barrel);
        var identity=AssemblyTrees.state(managed).in("mod_barrel").orElseThrow().instanceId();
        var g=IGun.getIGunOrNull(managed);g.setCurrentAmmoCount(managed,5);g.setBulletInBarrel(managed,false);
        var disabled=managed.copy();
        var installed=AssemblyTrees.state(disabled).in("mod_barrel").orElseThrow();
        disabled.set(AssemblyComponents.STATE.get(),AssemblyTrees.state(disabled).updated(List.of(new AssemblyState.Installed("mod_barrel",installed.instanceId(),installed.stack(),false))));
        check(dev.tacticaltacz.AssemblyFireGate.blocked(disabled),"disabled critical barrel fails closed");
        var full=player(level,managed);
        check(TacticalContent.tryGrant(full,List.of(new ItemStack(net.minecraft.world.item.Items.BEDROCK,256))),"fill four pocket cells");
        var fullState=full.getData(ModRegistries.PLAYER_GEAR);var fullGun=full.getMainHandItem().copy();
        check(!BarrelFireFixture.exchange(full,false),"no refund room rejects detach");
        check(fullState==full.getData(ModRegistries.PLAYER_GEAR)&&ItemStack.matches(fullGun,full.getMainHandItem()),"failed detach changes neither gun nor inventory");
        var p=player(level,managed);
        check(BarrelFireFixture.exchange(p,false),"actual held exchange removes barrel");
        var empty=p.getMainHandItem();check(dev.tacticaltacz.AssemblyFireGate.blocked(empty),"missing barrel blocks firing");
        var encoded=empty.save(level.registryAccess());var persisted=ItemStack.parseOptional(level.registryAccess(),(net.minecraft.nbt.CompoundTag)encoded);
        check(dev.tacticaltacz.AssemblyFireGate.blocked(persisted),"missing barrel remains blocked after ItemStack save");
        var bad=persisted.copy();bad.remove(dev.firearms.profile.FirearmComponents.PROFILE.get());bad.set(WeaponRuntime.PROFILE.get(),"missing:profile");check(dev.tacticaltacz.AssemblyFireGate.blocked(bad),"unknown managed profile fails closed");
        var wrong=managed.copy();((IGun)wrong.getItem()).setGunId(wrong,net.minecraft.resources.ResourceLocation.parse("tacz:m4a1"));check(dev.tacticaltacz.AssemblyFireGate.blocked(wrong),"profile cannot transfer to another gun");
        var shooter=new Zombie(level);shooter.setPos(0,250,0);shooter.setNoAi(true);shooter.setItemSlot(EquipmentSlot.MAINHAND,empty);
        IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
        var data=new ShooterDataHolder();data.currentGunItem=shooter::getMainHandItem;
        var shoot=new LivingEntityShoot(shooter,data,new LivingEntityDrawGun(shooter,data));
        var before=empty.copy();
        check(shoot.shoot(()->0f,()->0f,System.currentTimeMillis())==ShootResult.FORGE_EVENT_CANCEL,"three-argument trigger denied before chamber");
        check(shoot.shoot(()->0f,()->0f,System.currentTimeMillis(),0)==ShootResult.FORGE_EVENT_CANCEL,"charged trigger denied before chamber");
        check(ItemStack.matches(before,empty),"rejected trigger changes no ammunition or component");
        var api=new ModernKineticGunScriptAPI();api.setShooter(shooter);api.setItemStack(empty);api.setDataHolder(new ShooterDataHolder());api.setPitchSupplier(()->0f);api.setYawSupplier(()->0f);
        int count=AdapterVerification.firedCount();api.shootOnce(true);
        check(AdapterVerification.firedCount()==count&&ItemStack.matches(before,empty),"direct/per-cycle fire also denied without ammo consumption");
        check(BarrelFireFixture.exchange(p,true),"same barrel consumed from actual inventory");
        var restored=p.getMainHandItem();check(!dev.tacticaltacz.AssemblyFireGate.blocked(restored),"reinstall restores structural fire permission");
        check(AssemblyTrees.state(restored).in("mod_barrel").orElseThrow().instanceId().equals(identity),"physical identity preserved");
        shooter.setItemSlot(EquipmentSlot.MAINHAND,restored);IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
        g.setBulletInBarrel(restored,true);api.setItemStack(restored);
        int totalBefore=g.getCurrentAmmoCount(restored)+(g.hasBulletInBarrel(restored)?1:0);
        count=AdapterVerification.firedCount();api.shootOnce(true);
        check(AdapterVerification.firedCount()==count+1,"restored gun actually spawns a projectile");
        check(g.getCurrentAmmoCount(restored)+(g.hasBulletInBarrel(restored)?1:0)==totalBefore-1,"actual shot consumes exactly one round");
        System.out.println("BARREL_FIRE_SMOKE PASS: actual inventory remove/restore same part; unmanaged fallback; unknown/wrong profile fail closed; saved missing state; both trigger overloads and per-fire cancellation preserve ammo; restored actual projectile");
    }
    private static ServerPlayer player(ServerLevel level,ItemStack gun){
        var p=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BarrelSmoke"));
        var slots=new ArrayList<>(PlayerGearState.emptyFixedSlots());slots.set(GearSlot.SIDEARM.ordinal(),new FixedSlotSnapshot(GearSlot.SIDEARM,Optional.of(new FixedSlotEntry(UUID.randomUUID(),gun.copy()))));
        p.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(2,0,Optional.empty(),slots,List.of(),Optional.empty()));
        check(TacticalContent.tryGrant(p,List.of()),"initialize carried storage");
        check(PlayerInventoryService.activateSource(p,new FixedSlotLocation(GearSlot.SIDEARM),0,UUID.randomUUID()),"real held lease");return p;
    }
}
