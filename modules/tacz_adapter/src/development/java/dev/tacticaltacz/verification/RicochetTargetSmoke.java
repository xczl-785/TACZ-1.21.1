package dev.tacticaltacz.verification;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;

/** Exercises the player-facing command and actual projectile ticks in the isolated smoke world. */
final class RicochetTargetSmoke {
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError("Ricochet target: "+message);}
    private static void verifyLifetime(ServerPlayer player,ServerLevel level) throws Exception {
        var difficulty=level.getDifficulty();var created=new ArrayList<Husk>();
        try {
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
            for(String command:List.of("tacztest target","armortest target","armortest ricochet")) {
                var before=new HashSet<UUID>();level.getEntitiesOfClass(Husk.class,player.getBoundingBox().inflate(12)).forEach(t->before.add(t.getUUID()));
                level.getServer().getCommands().getDispatcher().execute(command,player.createCommandSourceStack().withPermission(2));
                var spawned=level.getEntitiesOfClass(Husk.class,player.getBoundingBox().inflate(12),t->!before.contains(t.getUUID()));
                require(spawned.size()==1,"peaceful command creates target: "+command);
                var target=spawned.getFirst();created.add(target);
                for(int i=0;i<120;i++) {target.checkDespawn();target.tick();target.tickCount++;}
                require(target.isAlive()&&!target.isRemoved(),"target survives repeated peaceful despawn/tick checks: "+command);
                // Previously saved targets have their original role tag, but no shared factory tag.
                target.removeTag("tactical_development_target");
                var restored=new Husk(EntityType.HUSK,level);created.add(restored);
                restored.load(target.saveWithoutId(new CompoundTag()));restored.checkDespawn();
                require(restored.isAlive()&&!restored.isRemoved(),"legacy saved target survives peaceful reload: "+command);
                target.discard();restored.discard();
            }
            var ordinary=new Husk(EntityType.HUSK,level);created.add(ordinary);ordinary.setPersistenceRequired();
            ordinary.checkDespawn();require(ordinary.isRemoved(),"ordinary persistent hostile mobs retain vanilla peaceful removal");
            System.out.println("TARGET_LIFETIME_SMOKE PASS: three real commands, peaceful repeated lifetime checks, legacy reload, ordinary mob removal unchanged");
        } finally {created.forEach(Entity::discard);level.getServer().setDifficulty(difficulty,true);}
    }
    static void run(ServerPlayer player,ServerLevel level,Supplier<EntityKineticBullet> fire,Runnable cancelPre) throws Exception {
        verifyLifetime(player,level);
        float yaw=player.getYRot(),pitch=player.getXRot();
        var created=new ArrayList<Entity>();
        var method=EntityKineticBullet.class.getDeclaredMethod("onHitEntity",TacHitResult.class,Vec3.class,Vec3.class);method.setAccessible(true);
        try {
            for(float facing:new float[]{0,90,180,270}) {
                player.setYRot(facing);player.setXRot(80);
                var oldTargets=new HashSet<UUID>();
                level.getEntitiesOfClass(Husk.class,player.getBoundingBox().inflate(10)).forEach(t->oldTargets.add(t.getUUID()));
                level.getServer().getCommands().getDispatcher().execute("armortest ricochet",player.createCommandSourceStack().withPermission(2));
                var targets=level.getEntitiesOfClass(Husk.class,player.getBoundingBox().inflate(10),t->t.getTags().contains(RicochetTargetVerification.TAG)&&!oldTargets.contains(t.getUUID()));
                require(targets.size()==1,"command spawns one marked target at yaw "+facing);
                var target=targets.getFirst();created.add(target);
                require(Math.abs(target.position().subtract(player.position()).horizontalDistance()-6)<.001,"spawn distance ignores vertical look angle");
                var point=ArmorTargetGeometry.world(new Vec3(0,1.15,2*1.0625/16),target.position(),target.getYRot());
                var start=player.getEyePosition();var velocity=point.subtract(start).normalize().scale(8);
                var geometry=RicochetTargetVerification.trace(target,start,start.add(velocity)).orElseThrow();
                require(geometry.protectedHit(),"aiming visible chest reaches demonstration face: yaw="+facing+" hit="+geometry+" local="+ArmorTargetGeometry.local(geometry.point(),target.position(),target.getYRot(),target.getBbHeight()));
                double angle=Math.toDegrees(Math.acos(-velocity.normalize().dot(geometry.normal())));
                require(angle>30&&angle<60,"real face gives an accessible oblique impact");
                var bullet=fire.get();created.add(bullet);bullet.setOwner(player);bullet.setPos(start);bullet.setDeltaMovement(velocity);
                var before=new HashSet<UUID>();level.getEntitiesOfClass(EntityKineticBullet.class,target.getBoundingBox().inflate(12)).forEach(b->before.add(b.getUUID()));
                float durability=RicochetTargetVerification.durability(target);
                bullet.tick();
                require(bullet.isRemoved(),"original projectile stops");
                var children=level.getEntitiesOfClass(EntityKineticBullet.class,target.getBoundingBox().inflate(12),b->!before.contains(b.getUUID()));
                require(children.size()==1,"actual command target creates exactly one continuation");
                var child=children.getFirst();created.add(child);
                require(RicochetTargetVerification.durability(target)<durability,"demonstration armor wears");
                require(RicochetTargetVerification.body(target).total()<440,"deflection still applies blunt injury");
                var after=target.getPersistentData().getCompound(RicochetTargetVerification.STATE).copy();
                method.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(target,point,false)),start,start.add(velocity));
                require(after.equals(target.getPersistentData().getCompound(RicochetTargetVerification.STATE)),"duplicate impact leaves state unchanged");
                var receiver=EntityType.COW.create(level);created.add(receiver);receiver.setNoAi(true);receiver.setNoGravity(true);
                receiver.setPos(child.position().add(child.getDeltaMovement().normalize().scale(3)).add(0,-.7,0));receiver.setOldPosAndRot();
                receiver.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);receiver.setHealth(200);
                require(level.addFreshEntity(receiver),"receiver enters world");child.tick();
                require(receiver.getHealth()<200,"real continuation from manual target hits receiver");
                child.discard();receiver.discard();
                var restored=RicochetTargetVerification.target(level,target.position(),target.getYRot());
                restored.load(target.saveWithoutId(new CompoundTag()));
                require(restored.getPersistentData().getCompound(RicochetTargetVerification.STATE).equals(after),"damage and durability survive entity save/load");restored.discard();
                var canceled=fire.get();created.add(canceled);canceled.setOwner(player);canceled.setPos(start);canceled.setDeltaMovement(velocity);
                cancelPre.run();canceled.tick();
                require(after.equals(target.getPersistentData().getCompound(RicochetTargetVerification.STATE)),"Pre cancellation preserves body and armor");
                canceled.discard();target.discard();
            }
            System.out.println("RICOCHET_TARGET_SMOKE PASS: real command, four orientations, chest face, actual continuation/second target, local wear, duplicate/cancel and entity persistence");
        } finally {
            created.forEach(Entity::discard);player.setYRot(yaw);player.setXRot(pitch);
        }
    }
}
