package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;


import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import dev.itemfoundation.api.assembly.*;
import dev.tacticalcombat.api.CombatComponents;
import dev.tacticalcombat.player.*;
import dev.tacticalinventory.api.TacticalEquipment;
import dev.tacticalinventory.core.GearSlot;
import dev.tacticalinventory.platform.PlayerGearState;
import dev.tacticalinventory.registry.ModRegistries;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Isolated actual-player integration: a registered compatible front plate, never a fabricated protection item. */
public final class AssemblyCombatSmoke {
    private AssemblyCombatSmoke() {}
    private static void require(boolean value,String message){if(!value)throw new AssertionError("Assembly combat: "+message);}
    private record Fixture(ItemStack host,ItemStack module,String mount,String equipmentSlot) {}
    private static Fixture fixture() {
        for(var profile:ProtectionProfiles.all().stream().sorted(Comparator.comparing(ProtectionProfiles.Profile::id)).toList()) {
            if(!Set.of("body_armor","chest_rig").contains(profile.armor().equipmentSlot()))continue;
            var host=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(profile.armor().item())));
            var assembly=AssemblyDefinitions.find(host);if(assembly.isEmpty())continue;
            for(var slot:profile.armor().slots()) {
                if(slot.mountingCoverage().isEmpty()||!profile.catalog().covers(slot.mountingCoverage(),"front_chest",new Vec3(0,4,-2)))continue;
                var mount=assembly.get().slot(slot.id());if(mount.isEmpty()||!mount.get().requiredSiblingSlots().isEmpty())continue;
                for(String item:mount.get().compatibleItems().stream().sorted().toList()) {
                    var module=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));var child=ProtectionProfiles.find(module);
                    if(child!=null&&child.armor().equipmentSlot().equals("module")&&child.armor().segments().stream()
                            .anyMatch(s->child.catalog().covers(s.coverage(),"front_chest",new Vec3(0,4,-2))))
                        return new Fixture(host,module,slot.id(),profile.armor().equipmentSlot());
                }
            }
        }
        throw new AssertionError("No registered compatible front-plate fixture");
    }
    static void run(ServerPlayer player,ServerLevel level,Supplier<EntityKineticBullet> fire,Method hit) throws Exception {
        var gear=player.getData(ModRegistries.PLAYER_GEAR);var body=PlayerCombat.state(player);
        float health=player.getHealth(),absorption=player.getAbsorptionAmount();
        try {
            var fixture=fixture();var path=List.of(fixture.mount());var instance=UUID.randomUUID();var hostId=UUID.randomUUID();
            var assembled=AssemblyPlanner.install(fixture.host(),hostId,path,instance,fixture.module()).after();
            var slot=fixture.equipmentSlot().equals("body_armor")?GearSlot.BODY_ARMOR:GearSlot.CHEST_RIG;
            player.setData(ModRegistries.PLAYER_GEAR,PlayerGearState.empty());PlayerArmorSmoke.reset(player);
            PlayerArmorSmoke.equipSlot(player,slot,assembled);
            var pose=PlayerCombat.pose(player).orElseThrow();
            var center=PlayerGeometry.world(new Vec3(0,4,-2),player.position(),pose);
            var start=center.add(0,0,3);var end=center.add(0,0,-3);
            shoot(player,fire,hit,start,end);
            var worn=TacticalEquipment.read(player,fixture.equipmentSlot()).stack();var damaged=AssemblyTrees.at(worn,path);
            var child=ProtectionProfiles.find(damaged);var state=damaged.get(CombatComponents.PROTECTION.get());
            require(state!=null,"real TaCZ hit writes into actual installed plate");
            require(child.armor().segments().stream().anyMatch(s->state.segments().get(s.id()).current()<child.catalog().specs().get(s.spec()).maximum()),"plate has local wear");
            require(AssemblyTrees.flatten(worn).stream().anyMatch(n->n.instanceId().equals(instance)),"damage preserves installed identity");
            // An off-center front hit cannot strike a plate restricted to the central 70%.
            PlayerArmorSmoke.reset(player);var edge=PlayerGeometry.world(new Vec3(3.2,4,-2),player.position(),pose);
            shoot(player,fire,hit,edge.add(0,0,3),edge.add(0,0,-3));
            var afterEdge=TacticalEquipment.read(player,fixture.equipmentSlot()).stack();
            require(ItemStack.matches(damaged,AssemblyTrees.at(afterEdge,path)),"outside plate bounds preserves the plate instance and durability");
            // A front-mounted reusable plate must never confer back protection.
            PlayerArmorSmoke.reset(player);shoot(player,fire,hit,end,start);
            var afterBack=TacticalEquipment.read(player,fixture.equipmentSlot()).stack();
            require(ItemStack.matches(damaged,AssemblyTrees.at(afterBack,path)),"back hit does not wear the front-mounted plate");
            var restored=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)afterBack.save(level.registryAccess()));
            require(ItemStack.matches(afterBack,restored),"host plus damaged module survive actual item serialization");
            var removed=AssemblyPlanner.remove(restored,hostId,path);
            require(removed.detached().orElseThrow().instanceId().equals(instance),"detach returns same physical identity");
            require(ItemStack.matches(damaged,removed.detached().orElseThrow().stack()),"detach returns accumulated module durability");
            verifyVisor(player,fire,hit);
            System.out.println("ASSEMBLY_COMBAT_SMOKE PASS: registered plate, actual PlayerCombat hit, local bounds, mounting direction, independent wear, persistence, detach");
        }finally {
            player.setData(ModRegistries.PLAYER_GEAR,gear);player.setData(PlayerBody.STATE,body);
            player.setHealth(health);player.setAbsorptionAmount(absorption);player.invulnerableTime=0;
        }
    }
    private static void verifyVisor(ServerPlayer player,Supplier<EntityKineticBullet> fire,Method hit)throws Exception {
        var hostKey=ResourceLocation.parse("tarkov_content:armor_5645bc214bdc2d363b8b4571");
        var visorKey=ResourceLocation.parse("tarkov_content:armor_5b46238386f7741a693bcf9c");
        require(BuiltInRegistries.ITEM.containsKey(hostKey)&&BuiltInRegistries.ITEM.containsKey(visorKey),"approved Kiver and visor registered");
        var path=List.of("mod_equipment");var hostId=UUID.randomUUID();var visorId=UUID.randomUUID();
        var host=AssemblyPlanner.install(new ItemStack(BuiltInRegistries.ITEM.get(hostKey)),hostId,path,visorId,
                new ItemStack(BuiltInRegistries.ITEM.get(visorKey))).after();
        player.setData(ModRegistries.PLAYER_GEAR,PlayerGearState.empty());PlayerArmorSmoke.reset(player);
        PlayerArmorSmoke.equipSlot(player,GearSlot.HEAD_ARMOR,host);
        for(double faceY:new double[]{-5,-1}) {
            var worn=TacticalEquipment.read(player,"head_armor").stack();
            var enabled=AssemblyTrees.state(worn).in(path.getFirst()).orElseThrow().enabled()?worn:AssemblyPlanner.setEnabled(worn,hostId,path,true).after();
            PlayerArmorSmoke.equipSlot(player,GearSlot.HEAD_ARMOR,enabled);PlayerArmorSmoke.reset(player);
            var before=AssemblyTrees.at(enabled,path);var profile=ProtectionProfiles.find(before);var fresh=profile.state(before);
            var local=new Vec3(0,faceY,-4);var center=PlayerGeometry.world(local,player.position(),PlayerCombat.pose(player).orElseThrow());
            var start=center.add(0,0,3);var end=center.add(0,0,-3);
            shoot(player,fire,hit,start,end);
            var after=TacticalEquipment.read(player,"head_armor").stack();var damaged=AssemblyTrees.at(after,path);
            var state=profile.state(damaged);
            require(profile.armor().segments().stream().filter(segment->profile.catalog().covers(segment.coverage(),"head_front",local))
                    .anyMatch(segment->state.segments().get(segment.id()).current()<fresh.segments().get(segment.id()).current()),"closed visor's matching eye/jaw protection loses durability");
            var raised=AssemblyPlanner.setEnabled(after,hostId,path,false).after();
            PlayerArmorSmoke.equipSlot(player,GearSlot.HEAD_ARMOR,raised);PlayerArmorSmoke.reset(player);
            shoot(player,fire,hit,start,end);
            var afterRaised=TacticalEquipment.read(player,"head_armor").stack();
            require(ItemStack.matches(damaged,AssemblyTrees.at(afterRaised,path)),"raised visor takes no damage from same eye/jaw ray");
            require(PlayerCombat.state(player).health().health(dev.tacticalcharacter.core.BodyPart.HEAD)==5,"raised visor exposes full 30 AP damage to unprotected face");
            require(AssemblyTrees.flatten(afterRaised).stream().anyMatch(node->node.instanceId().equals(visorId)&&!node.enabled()),"raised physical visor identity and state persist");
        }
        System.out.println("ASSEMBLY_VISOR_COMBAT_SMOKE PASS: real eye and jaw hits, closed visor wear, raised visor unchanged and bare head damage");
    }
    private static void shoot(ServerPlayer player,Supplier<EntityKineticBullet> fire,Method hit,Vec3 start,Vec3 end)throws Exception {
        var bullet=fire.get();try {hit.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(player,start,false)),start,end);}
        finally{bullet.discard();}
    }
}
