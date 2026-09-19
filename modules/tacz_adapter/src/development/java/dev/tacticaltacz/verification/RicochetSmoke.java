package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;


import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import dev.tacticalcombat.api.*;
import dev.tacticalcombat.core.*;
import dev.tacticalcombat.player.*;
import dev.tacticalcombat.protection.*;
import dev.tacticaltacz.TacticalGunPlatformExtension;
import dev.tacticalinventory.api.TacticalEquipment;
import dev.tacticalinventory.core.GearSlot;
import dev.tacticalinventory.registry.ModRegistries;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;

/** Isolated runtime fixture; no production armor data or probability is changed. */
final class RicochetSmoke {
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError("Ricochet: "+message);}
    private static void near(double actual,double expected,String message) {
        require(Math.abs(actual-expected)<.002,message+" actual="+actual+" expected="+expected);
    }
    private static Field field(String name) throws Exception {
        var f=EntityKineticBullet.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static List<EntityKineticBullet> bullets(ServerLevel level,ServerPlayer p) {
        return level.getEntitiesOfClass(EntityKineticBullet.class,p.getBoundingBox().inflate(12));
    }
    private static Cow cow(ServerLevel level,Vec3 position) {
        var cow=EntityType.COW.create(level);cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(position);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        cow.setOldPosAndRot(); // A freshly positioned fixture has no five-tick motion history.
        cow.setHealth(200);require(level.addFreshEntity(cow),"test cow enters world");return cow;
    }
    private static void verifySpawnProtocol(EntityKineticBullet source,ServerLevel level,boolean continuation) throws Exception {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        var originalOwner=source.getOwner();
        var networkOwner=cow(level,source.position().add(0,5,0));
        try {
            require(level.getEntity(networkOwner.getId())==networkOwner,"spawn protocol owner fixture is registered by entity id");
            source.setOwner(networkOwner);
            source.writeSpawnData(buffer);
            var replica=new EntityKineticBullet(EntityKineticBullet.TYPE,level);
            replica.readSpawnData(buffer);
            require(buffer.readableBytes()==0,"spawn codec consumes native payload and continuation marker exactly");
            require(replica.getDeltaMovement().equals(source.getDeltaMovement()),"spawn codec retains native velocity");
            require(replica.getOwner()==source.getOwner(),"spawn codec resolves the same owner");
            require(replica.getAmmoId().equals(source.getAmmoId())&&replica.getGunId().equals(source.getGunId())
                    &&replica.getGunDisplayId().equals(source.getGunDisplayId()),"spawn codec retains ammunition/gun/display identity");
            var offset=replica.getFirstPersonRenderOffset();
            if(continuation)require(offset!=null&&offset.lengthSquared()==0,"continuation spawn suppresses muzzle offset");
            else require(offset==null,"ordinary spawn retains upstream lazy muzzle offset behavior");
        } finally {source.setOwner(originalOwner);networkOwner.discard();buffer.release();}
    }
    private static void verifyAdopted(ServerPlayer p,ServerLevel level,Supplier<EntityKineticBullet> fire,
            java.lang.reflect.Method hit,List<Entity> created) throws Exception {
        // Pin the accepted first-batch regression set; later module batches also have ricochet parameters.
        var firstBatch=Set.of("tarkov_content:5aa7d03ae5b5b00016327db5",
                "tarkov_content:5aa7d193e5b5b000171d063f",
                "tarkov_content:5b40e1525acfc4771e1c6611",
                "tarkov_content:5b40e2bc5acfc40016388216",
                "tarkov_content:5b40e3f35acfc40016388218",
                "tarkov_content:5b40e4035acfc47a87740943",
                "tarkov_content:5c06c6a80db834001b735491",
                "tarkov_content:5c0e3eb886f7742015526062",
                "tarkov_content:5d5d646386f7742797261fd9",
                "tarkov_content:5df8a2ca86f7740bfe6df777",
                "tarkov_content:64be79c487d1510151095552",
                "tarkov_content:64be79e2bf8412471d0d9bcc",
                "tarkov_content:68bee2e876e02b9e340ef113");
        var adopted=ProtectionProfiles.all().stream().filter(profile->firstBatch.contains(profile.id())).toList();
        require(adopted.size()==13,"first approved ricochet catalog contains thirteen items");
        for(var profile:adopted) {
            boolean head=profile.armor().equipmentSlot().equals("head_armor");
            String region=head?"head_back":"front_chest";
            var segment=profile.catalog().covering(profile.armor(),region).orElseThrow();
            var sample=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(profile.armor().item())));
            p.setData(ModRegistries.PLAYER_GEAR,dev.tacticalinventory.platform.PlayerGearState.empty());
            PlayerArmorSmoke.reset(p);
            var slot=switch(profile.armor().equipmentSlot()) {
                case "head_armor"->GearSlot.HEAD_ARMOR;case "body_armor"->GearSlot.BODY_ARMOR;default->GearSlot.CHEST_RIG;
            };
            PlayerArmorSmoke.equipSlot(p,slot,sample);
            Vec3 start=p.position().add(0,head?1.65:1.15,head?-3:3);
            Vec3 end=start.add(0,0,head?6:-6);
            var bullet=fire.get();created.add(bullet);bullet.setPos(start);bullet.setDeltaMovement(end.subtract(start));
            int count=bullets(level,p).size();
            hit.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            require(bullets(level,p).size()==count,"normal incidence does not ricochet: "+profile.id());
            var actual=TacticalEquipment.read(p,profile.armor().equipmentSlot()).stack().get(CombatComponents.PROTECTION.get());
            require(actual!=null&&actual.segments().get(segment.id()).current()<profile.state(sample).segments().get(segment.id()).current(),
                    "source-backed equipped segment loses durability: "+profile.id());
            bullet.discard();
        }
        System.out.println("RICOCHET_ADOPTION_SMOKE PASS: thirteen actual source-backed items, correct wearing slots, normal-incidence local durability");
    }
    static void run(ServerPlayer p,ServerLevel level,Supplier<EntityKineticBullet> fire,Runnable cancelPre) throws Exception {
        var profilesField=ProtectionProfiles.class.getDeclaredField("profiles");profilesField.setAccessible(true);
        @SuppressWarnings("unchecked") var originalProfiles=(Map<ResourceLocation,ProtectionProfiles.Profile>)profilesField.get(null);
        var originalGear=p.getData(ModRegistries.PLAYER_GEAR);var originalBody=p.getData(PlayerBody.STATE);
        float originalHealth=p.getHealth();
        var created=new ArrayList<Entity>();
        var sample=new ItemStack(dev.tarkovcontent.TarkovContent.ARMORS.get("5648a7494bdc2d9d488b4583").get());
        var base=ProtectionProfiles.find(sample);var baseCatalog=base.catalog();
        var front=baseCatalog.covering(base.armor(),"front_chest").orElseThrow();
        var specs=new HashMap<>(baseCatalog.specs());var old=specs.get(front.spec());
        specs.put(front.spec(),new ProtectionCatalog.Spec(old.armorClass(),old.material(),old.maximum(),old.bluntThroughput(),old.algorithm(),new RicochetParameters(1,1,0)));
        var catalog=new ProtectionCatalog(baseCatalog.materials(),baseCatalog.classes(),baseCatalog.segmentTypes(),baseCatalog.slotTypes(),specs,baseCatalog.coverages(),baseCatalog.armors());
        var profile=new ProtectionProfiles.Profile(base.id(),catalog,base.armor());
        var temporary=new HashMap<>(originalProfiles);temporary.put(ResourceLocation.parse(base.armor().item()),profile);
        var hit=EntityKineticBullet.class.getDeclaredMethod("onHitEntity",TacHitResult.class,Vec3.class,Vec3.class);hit.setAccessible(true);
        // Frontal chest face is z=+0.1171875 at yaw=0; a shallow crossing hits x=0.
        Vec3 contact=p.position().add(0,1.15,.1171875);
        Vec3 velocity=new Vec3(6,0,-1), start=contact.subtract(velocity.scale(.5));
        try {
            verifyAdopted(p,level,fire,hit,created);
            profilesField.set(null,Map.copyOf(temporary));
            p.setData(ModRegistries.PLAYER_GEAR,dev.tacticalinventory.platform.PlayerGearState.empty());
            PlayerArmorSmoke.equipSlot(p,GearSlot.BODY_ARMOR,sample);PlayerArmorSmoke.reset(p);
            var geometry=PlayerGeometry.trace(start,start.add(velocity),p.position(),PlayerCombat.pose(p).orElseThrow()).orElseThrow();
            require(geometry.region().equals("front_chest"),"grazing fixture strikes front chest");
            var forward=cow(level,contact.add(3,-1.15,-.5));created.add(forward);
            var reflected=cow(level,contact.add(3,-1.15,.5));created.add(reflected);
            var bullet=fire.get();created.add(bullet);
            bullet.setPos(start);bullet.setDeltaMovement(velocity);bullet.tickCount=8; // ServerLevel increments age before invoking tick.
            field("life").setInt(bullet,40);field("pierce").setInt(bullet,3);
            var beforeIds=new HashSet<UUID>();bullets(level,p).forEach(b->beforeIds.add(b.getUUID()));
            bullet.tick();
            require(bullet.isRemoved(),"original projectile stops after ricochet");
            near(forward.getHealth(),200,"old-direction second target receives no damage despite pierce=3");
            var spawned=bullets(level,p).stream().filter(b->!beforeIds.contains(b.getUUID())).toList();
            require(spawned.size()==1,"one continuation enters real level");var child=spawned.getFirst();created.add(child);
            require(child.getOwner()==bullet.getOwner(),"continuation retains shooter ownership");
            require(TacticalGunPlatformExtension.installed().ricochetCount(child)==1,"first continuation inherits incremented ricochet count");
            verifySpawnProtocol(child,level,true);
            verifySpawnProtocol(bullet,level,false);
            near(child.getDeltaMovement().x,4.2,"reflected x velocity");near(child.getDeltaMovement().z,.7,"reflected z velocity");
            near(field("life").getInt(child),32,"remaining lifetime copied without reset");
            near(TacticalGunPlatformExtension.installed().ammunition(child).fleshDamage(),TacticalGunPlatformExtension.installed().ammunition(bullet).fleshDamage()*.5,"domain flesh damage attenuates");
            near(TacticalGunPlatformExtension.installed().ammunition(child).penetrationPower(),TacticalGunPlatformExtension.installed().ammunition(bullet).penetrationPower()*.5,"domain penetration attenuates");
            near(child.getDamage(reflected.position()),bullet.getDamage(reflected.position())*.5,"native Minecraft damage curve scales rather than adopting domain units");
            near(field("headShot").getFloat(child),field("headShot").getFloat(bullet),"native headshot multiplier retained");
            near(field("armorIgnore").getFloat(child),field("armorIgnore").getFloat(bullet),"native armor ignore retained");
            near(field("knockback").getFloat(child),field("knockback").getFloat(bullet),"native knockback retained");
            float chest=PlayerCombat.state(p).health().health(BodyPart.CHEST);
            require(chest<85&&chest>70,"ricochet applies current blunt injury, not immunity or full flesh damage");
            var damaged=TacticalEquipment.read(p,"body_armor").stack().get(CombatComponents.PROTECTION.get());
            require(damaged!=null&&damaged.segments().get(front.id()).current()<old.maximum(),"current physical armor segment loses durability");
            for(var segment:base.armor().segments())if(!segment.id().equals(front.id()))
                near(damaged.segments().get(segment.id()).current(),profile.state(sample).segments().get(segment.id()).current(),"unhit segment stays unchanged");
            require(com.tacz.guns.util.HitboxHelper.getFixedBoundingBox(reflected,child.getOwner()).equals(reflected.getBoundingBox()),
                    "stationary cow history preserves actual collision box");
            child.tick();
            require(reflected.getHealth()<200,"real reflected trajectory hits second living target");
            near(forward.getHealth(),200,"forward target remains safe after continuation tick");
            child.discard();forward.discard();reflected.discard();

            // Explicit native body hit avoids uncertainty in the upstream cow headshot classifier.
            var nativeTarget=cow(level,p.position().add(5,0,5));created.add(nativeTarget);
            nativeTarget.invulnerableTime=0;float nativeDamage=child.getDamage(nativeTarget.position().add(0,.6,0));
            require(nativeDamage>0,"native continuation fixture has positive damage");
            hit.invoke(child,new TacHitResult(new EntityKineticBullet.EntityResult(nativeTarget,nativeTarget.position().add(0,.6,0),false)),child.position(),nativeTarget.position());
            near(200-nativeTarget.getHealth(),nativeDamage,"continuation unclaimed mob receives native units");nativeTarget.discard();

            for(String mode:List.of("pre_cancel","incoming_cancel","cap","zero")) {
                PlayerArmorSmoke.reset(p);PlayerArmorSmoke.equipSlot(p,GearSlot.BODY_ARMOR,sample);
                if(mode.equals("zero")) {
                    var broken=sample.copy();broken.set(CombatComponents.PROTECTION.get(),profile.state(broken).damaged(front.id(),0));
                    PlayerArmorSmoke.equipSlot(p,GearSlot.BODY_ARMOR,broken);
                }
                var body=p.getData(PlayerBody.STATE);var gear=p.getData(ModRegistries.PLAYER_GEAR);
                var trial=fire.get();created.add(trial);trial.setPos(start);trial.setDeltaMovement(velocity);
                if(mode.equals("cap"))TacticalGunPlatformExtension.installed().setRicochetCount(trial,2);
                int before=bullets(level,p).size();if(mode.equals("pre_cancel"))cancelPre.run();
                if(mode.equals("incoming_cancel")) {
                    var flag=PlayerArmorSmoke.class.getDeclaredField("cancelIncoming");flag.setAccessible(true);flag.set(null,p);
                }
                hit.invoke(trial,new TacHitResult(new EntityKineticBullet.EntityResult(p,contact,false)),start,start.add(velocity));
                require(bullets(level,p).size()==before,"no continuation for "+mode);
                if(mode.endsWith("cancel"))require(p.getData(PlayerBody.STATE)==body&&p.getData(ModRegistries.PLAYER_GEAR)==gear,"cancellation preserves armor and body: "+mode);
                else require(PlayerCombat.state(p).health().health(BodyPart.CHEST)<85,"non-ricochet impact still injures for "+mode);
                if(mode.equals("zero"))near(PlayerCombat.state(p).health().health(BodyPart.CHEST),55,"zero armor takes full AP damage");
                trial.discard();
            }
            System.out.println("RICOCHET_RUNTIME_SMOKE PASS: grazing physical impact, current injury/local wear, one real reflected target, no forward hit, owner/lifetime/native units, Pre cancellation, cap and zero durability");
        } finally {
            profilesField.set(null,originalProfiles);created.forEach(Entity::discard);
            p.setData(ModRegistries.PLAYER_GEAR,originalGear);p.setData(PlayerBody.STATE,originalBody);p.setHealth(originalHealth);p.invulnerableTime=0;
        }
    }
}
