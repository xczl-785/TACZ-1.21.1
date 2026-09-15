package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import dev.tacticalcombat.api.*;
import dev.tacticalcombat.core.*;
import dev.itemfoundation.api.storage.*;
import dev.itemfoundation.api.inventory.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

final class ArmorFlowSmoke {
    private static void require(boolean ok,String message) { if(!ok)throw new AssertionError("Armor: "+message); }
    private static void near(float actual,float expected,String message) {require(Math.abs(actual-expected)<.002,message+" actual="+actual+" expected="+expected);}
    static void run(ServerLevel level,Vec3 origin,Supplier<EntityKineticBullet> fire,Runnable cancel) throws Exception {
        var method=EntityKineticBullet.class.getDeclaredMethod("onHitEntity",TacHitResult.class,Vec3.class,Vec3.class);method.setAccessible(true);
        var t=ArmorVerification.target(level,origin.add(0,0,4),true);level.addFreshEntity(t);
        try {
            var shoulder=ArmorTargetGeometry.armPoint(new Vec3(1,0,0),true,0);
            var hand=ArmorTargetGeometry.armPoint(new Vec3(1,8,0),true,0);
            require(ArmorTargetGeometry.trace(shoulder.add(2,0,0),shoulder.add(-2,0,0)).orElseThrow().segment().equals("Shoulder_l"),"raised shoulder coverage");
            require(ArmorTargetGeometry.trace(hand.add(2,0,0),hand.add(-2,0,0)).orElseThrow().segment()==null,"raised forearm uncovered");
            require(ArmorTargetGeometry.trace(new Vec3(.7,1.9,2),new Vec3(.7,1.9,-2)).isEmpty(),"broad-box empty space misses anatomy");
            require(ArmorTargetGeometry.trace(new Vec3(2,1.1,0),new Vec3(-2,1.1,0)).orElseThrow().part()==BodyPart.CHEST,"side torso no phantom hanging arm");
            var turned=ArmorTargetGeometry.local(new Vec3(-2,1.2,0),Vec3.ZERO,90,1.8f);
            require(turned.z>1.99&&Math.abs(turned.x)<.0001,"yaw defines front in target coordinates");
            var narrow=com.tacz.guns.util.EntityUtil.class.getDeclaredMethod("getHitResult",net.minecraft.world.entity.projectile.Projectile.class,Entity.class,Vec3.class,Vec3.class);
            narrow.setAccessible(true);
            for(boolean left:new boolean[]{true,false})for(float yaw:new float[]{0,90,180,270}) {
                t.setYRot(yaw);
                var localHand=ArmorTargetGeometry.armPoint(new Vec3(left?1:-1,8,0),left,t.tickCount);
                var hs=ArmorTargetGeometry.world(localHand.add(left?2:-2,0,0),t.position(),yaw);
                var he=ArmorTargetGeometry.world(localHand.add(left?-2:2,0,0),t.position(),yaw);
                require(t.getBoundingBox().clip(hs,he).isEmpty(),"raised hand ray is outside vanilla body box");
                var bullet=fire.get();
                var hit=(EntityKineticBullet.EntityResult)narrow.invoke(null,bullet,t,hs,he);
                require(hit!=null,"TaCZ narrow phase accepts raised arm at yaw "+yaw);
                var beforeArm=ArmorVerification.body(t).health(left?BodyPart.LEFT_ARM:BodyPart.RIGHT_ARM);
                bullet.setPos(hs);bullet.setDeltaMovement(he.subtract(hs));bullet.tick();
                near(ArmorVerification.body(t).health(left?BodyPart.LEFT_ARM:BodyPart.RIGHT_ARM),beforeArm-30,"real bullet tick outside native body box damages matching raised arm");
                t.getPersistentData().put(ArmorVerification.BODY,ArmorVerification.encode(BodyHealth.full(),0));t.setHealth(440);
            }
            t.setYRot(0);
            // Keep an actual item in the rig so damage/serialization cannot silently erase its contents.
            var carrier=t.getItemBySlot(EquipmentSlot.CHEST);var state=carrier.get(ContainerComponents.STATE.get());
            var areas=new ArrayList<>(state.areas());var a=areas.getFirst();
            areas.set(0,new ContainerArea(a.areaId(),a.layoutPosition(),new GridStorageSnapshot(a.storage().storageId(),a.storage().size(),
                    List.of(new InventoryEntry(UUID.randomUUID(),new ItemStack(Items.DIAMOND,2),new GridPosition(0,0),Orientation.values()[0])))));
            var packed=new ContainerState(state.schemaVersion(),state.stateRevision()+1,state.containerId(),areas);carrier.set(ContainerComponents.STATE.get(),packed);
            t.getPersistentData().putFloat("armor_test_roll",.99f);
            var start=t.position().add(0,t.getBbHeight()*1.2/1.8,3);var end=start.add(0,0,-6);
            var point=t.position().add(0,t.getBbHeight()*1.2/1.8,.13);
            var first=fire.get();int commits=ArmorVerification.committed;
            method.invoke(first,new TacHitResult(new EntityKineticBullet.EntityResult(t,point,false)),start,end);
            near(ArmorVerification.body(t).health(BodyPart.CHEST),74.2f,"blocked AP blunt damage");
            near(t.getItemBySlot(EquipmentSlot.CHEST).get(CombatComponents.ARMOR.get()).segments().get("Soft_armor_front"),31.875f,"only front worn");
            near(t.getItemBySlot(EquipmentSlot.CHEST).get(CombatComponents.ARMOR.get()).segments().get("Soft_armor_back"),36,"back unchanged");
            require(t.getItemBySlot(EquipmentSlot.CHEST).get(ContainerComponents.STATE.get()).equals(packed),"container contents preserved");
            method.invoke(first,new TacHitResult(new EntityKineticBullet.EntityResult(t,point,false)),start,end);
            require(ArmorVerification.committed==commits+1,"duplicate cannot commit twice");
            var before=t.getPersistentData().getCompound(ArmorVerification.BODY).copy();var armorBefore=t.getItemBySlot(EquipmentSlot.CHEST).copy();
            cancel.run();method.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(t,point,false)),start,end);
            require(before.equals(t.getPersistentData().getCompound(ArmorVerification.BODY))&&ItemStack.matches(armorBefore,t.getItemBySlot(EquipmentSlot.CHEST)),"cancelled Pre changes neither body nor armor");
            // A changed item between quote and commit rejects the entire quote.
            t.getPersistentData().putBoolean("armor_swap_before_commit",true);
            method.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(t,point,false)),start,end);
            require(before.equals(t.getPersistentData().getCompound(ArmorVerification.BODY)),"stale equipment rejects body mutation");
            t.setItemSlot(EquipmentSlot.CHEST,armorBefore);
            t.getPersistentData().putFloat("armor_test_roll",.5f);
            method.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(t,point,false)),end,start);
            near(t.getItemBySlot(EquipmentSlot.CHEST).get(CombatComponents.ARMOR.get()).segments().get("Soft_armor_back"),32.799245f,"fresh back uses penetrated AP wear");
            var saved=t.getItemBySlot(EquipmentSlot.CHEST).save(level.registryAccess());
            var restored=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)saved);
            require(ItemStack.matches(restored,t.getItemBySlot(EquipmentSlot.CHEST)),"durability and container survive item serialization");
            t.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);t.setItemSlot(EquipmentSlot.CHEST,restored);
            var entityTag=t.saveWithoutId(new CompoundTag());var reloaded=ArmorVerification.target(level,t.position(),false);reloaded.load(entityTag);
            require(ArmorVerification.body(t).equals(ArmorVerification.body(reloaded)),"body survives entity save/load");
            require(ItemStack.matches(restored,reloaded.getItemBySlot(EquipmentSlot.CHEST)),"worn rig survives entity save/load");reloaded.discard();
            var zeros=new HashMap<>(restored.get(CombatComponents.ARMOR.get()).segments());zeros.put("Soft_armor_front",0f);
            restored.set(CombatComponents.ARMOR.get(),new ArmorDurability(zeros));float chest=ArmorVerification.body(t).health(BodyPart.CHEST);
            method.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(t,point,false)),start,end);
            near(ArmorVerification.body(t).health(BodyPart.CHEST),chest-30,"zero durability gives full flesh damage");
            t.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
            var headStart=t.position().add(0,t.getBbHeight()*1.7/1.8,3);var headEnd=headStart.add(0,0,-6);
            method.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(t,headStart,false)),headStart,headEnd);
            near(ArmorVerification.body(t).health(BodyPart.HEAD),5,"bare head uses seven-part health without native multiplier");
            int kills=AdapterVerification.killCount();
            method.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(t,headStart,false)),headStart,headEnd);
            require(AdapterVerification.killCount()==kills+1,"critical death retains TaCZ kill feedback");
            require(t.isDeadOrDying()&&ArmorVerification.body(t).dead(),"critical head death despite remaining other-part health");
        }finally {t.discard();}
        var live=ArmorVerification.target(level,origin.add(0,0,4),true);live.getPersistentData().putFloat("armor_test_roll",.5f);level.addFreshEntity(live);
        try {
            int count=ArmorVerification.committed;var flying=fire.get();flying.tick();
            require(ArmorVerification.committed==count+1&&live.getHealth()<440,"real bullet tick enters armor flow");
        }finally{live.discard();}
        System.out.println("ARMOR_FLOW_SMOKE PASS: source formulas, local wear, seven parts, cancellation, stale gear, serialization, preserved contents, critical death, real bullet tick");
    }
}
