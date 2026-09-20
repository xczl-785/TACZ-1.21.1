package dev.tacticaltacz.verification;
import dev.tarkovcontent.ammunition.*;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import dev.tacticalcombat.api.*;
import dev.tacticalcombat.core.*;
import dev.tacticalcombat.player.*;
import dev.tacticalinventory.api.TacticalEquipment;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid="tacz")
public final class PlayerArmorSmoke {
    private static ServerPlayer cancelIncoming, dyingPlayer;
    private static Collection<net.minecraft.world.entity.item.ItemEntity> deathDrops;
    @SubscribeEvent public static void drops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if(event.getEntity()==dyingPlayer)deathDrops=event.getDrops();
    }
    @SubscribeEvent public static void cancel(LivingIncomingDamageEvent event) {
        if(event.getEntity()==cancelIncoming){event.setCanceled(true);cancelIncoming=null;}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("Player armor: "+message);}
    private static dev.itemfoundation.api.inspection.InspectionSection inspectionSection(
            List<dev.itemfoundation.api.inspection.InspectionSection> sections,String id) {
        return sections.stream().filter(section->section.id().equals(id)).findFirst().orElseThrow();
    }
    private static Set<String> translationKeys(net.minecraft.network.chat.Component text) {
        var result=new HashSet<String>();
        if(text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents value) {
            result.add(value.getKey());
            for(var argument:value.getArgs())if(argument instanceof net.minecraft.network.chat.Component child)result.addAll(translationKeys(child));
        }
        for(var sibling:text.getSiblings())result.addAll(translationKeys(sibling));
        return result;
    }
    private static void verifyInspection(ProtectionProfiles.Profile profile,ItemStack original) {
        var sample=original.copy();
        var sections=dev.tacticalcombat.protection.ProtectionInspection.inspect(sample);
        if(profile.armor().segments().isEmpty()) {
            require(sections.stream().noneMatch(section->section.id().equals("tactical_combat:fixed")),"plate-only host has no fabricated fixed liner");
            require(inspectionSection(sections,"tactical_combat:summary").rows().stream().anyMatch(row->translationKeys(row.descriptionText()).contains("protection.tactical_combat.coverage.uninstalled")),"empty plate host reports no installed protection");
            return;
        }
        var fixed=inspectionSection(sections,"tactical_combat:fixed");
        require(fixed.rows().size()==profile.armor().segments().size(),"all adopted fixed segments visible in inspection");
        require(fixed.rows().stream().allMatch(row->row.card().orElseThrow().state()==(profile.armor().equipmentSlot().equals("module")?dev.itemfoundation.api.inspection.InspectionSection.CardState.OCCUPIED:dev.itemfoundation.api.inspection.InspectionSection.CardState.FIXED)),"fresh fixed segments expose fixed cards");
        if(profile.armor().layout().equals("head")&&!profile.armor().equipmentSlot().equals("module"))require(translationKeys(fixed.titleText()).contains("protection.tactical_combat.section.body"),"head uses body protection title instead of liner title");
        if(profile.armor().slots().isEmpty())require(sections.stream().noneMatch(section->section.id().equals("tactical_combat:slots")),"armor without plate slots generates no empty slot section");
        else {
            var slots=inspectionSection(sections,"tactical_combat:slots");
            require(slots.rows().size()==profile.armor().slots().size(),"all declared empty plate slots visible");
            require(slots.rows().stream().allMatch(row->{
                boolean open=dev.itemfoundation.api.assembly.AssemblyDefinitions.find(sample).flatMap(d->d.slot(row.id())).isPresent();
                return row.card().orElseThrow().state()==(open?dev.itemfoundation.api.inspection.InspectionSection.CardState.EMPTY:dev.itemfoundation.api.inspection.InspectionSection.CardState.LOCKED)
                        &&row.card().orElseThrow().meter().isEmpty();
            }),"actual assembly catalog controls empty or locked slots; neither fabricates durability");
        }
        require(sections.stream().flatMap(section->section.rows().stream()).flatMap(row->row.actions().stream()).noneMatch(action->action.enabled()),"unimplemented installation never advertised as enabled");
        var worn=profile.state(sample);
        for(var segment:profile.armor().segments())worn=worn.damaged(segment.id(),.25f);
        sample.set(CombatComponents.PROTECTION.get(),worn);
        sections=dev.tacticalcombat.protection.ProtectionInspection.inspect(sample);
        var summary=inspectionSection(sections,"tactical_combat:summary");
        var total=summary.rows().stream().filter(row->row.id().equals("durability_total")).findFirst().orElseThrow().fields().getFirst().number().orElseThrow();
        require(Math.abs(total-profile.armor().segments().size()*.25)<.00001,"durability summary sums raw fractions before display rounding");
        var lost=profile.armor().segments().getFirst();worn=worn.damaged(lost.id(),0);
        sample.set(CombatComponents.PROTECTION.get(),worn);
        sections=dev.tacticalcombat.protection.ProtectionInspection.inspect(sample);
        var destroyed=inspectionSection(sections,"tactical_combat:fixed").rows().stream().filter(row->row.id().equals(lost.id())).findFirst().orElseThrow();
        require(destroyed.card().orElseThrow().state()==dev.itemfoundation.api.inspection.InspectionSection.CardState.DAMAGED,"zero durability has damaged card state");
        var expected=profile.armor().segments().stream().filter(segment->!segment.id().equals(lost.id()))
                .flatMap(segment->profile.catalog().coverages().get(segment.coverage()).stream()).distinct().sorted()
                .map(region -> "protection.tactical_combat.region." + region).collect(java.util.stream.Collectors.toSet());
        var coverage=inspectionSection(sections,"tactical_combat:summary").rows().stream().filter(row->row.id().equals("coverage")).findFirst().orElseThrow();
        var actualKeys=translationKeys(coverage.descriptionText());
        var actualRegions=actualKeys.stream().filter(key->key.startsWith("protection.tactical_combat.region.")).collect(java.util.stream.Collectors.toSet());
        require(expected.isEmpty()?actualKeys.contains("protection.tactical_combat.coverage.inactive"):actualRegions.equals(expected),"effective coverage removes destroyed segment while retaining live regions");
        var encoded=dev.itemfoundation.api.inspection.InspectionSection.CODEC.listOf().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,sections).getOrThrow();
        require(dev.itemfoundation.api.inspection.InspectionSection.CODEC.listOf().parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow().equals(sections),"card state meters attributes and hover details survive codec");
        require(original.get(CombatComponents.PROTECTION.get())==null,"inspection fixture never mutates original item");
    }
    // Independent expectations transcribed from docs/armor.json original.properties.armorSlots,
    // the approved public snapshot. Totals exclude open plates and the host's aggregate durability.
    private record SupplementalArmor(String id,GearSlot slot,int segments,int emptySlots,float total,float frontMaximum,float frontBlunt) {}
    private static final List<SupplementalArmor> SUPPLEMENTAL_ARMORS=List.of(
            new SupplementalArmor("689479a4a733b1602007e2eb",GearSlot.CHEST_RIG,4,2,120f,44f,0.33f),
            new SupplementalArmor("689479cb47e5acd1e10be986",GearSlot.CHEST_RIG,4,2,108f,40f,0.33f),
            new SupplementalArmor("689479eb30cc5ba7be00f5ff",GearSlot.CHEST_RIG,2,2,88f,44f,0.33f),
            new SupplementalArmor("68947a4be4bf255d1b0ca746",GearSlot.CHEST_RIG,8,4,220f,44f,0.36f),
            new SupplementalArmor("68948a95d8f2b85fb705e2a6",GearSlot.BODY_ARMOR,5,4,254f,85f,0.36f),
            new SupplementalArmor("68948ad72c87773b9f06d73f",GearSlot.CHEST_RIG,5,4,254f,85f,0.36f),
            new SupplementalArmor("68948aebd8f2b85fb705e2b0",GearSlot.CHEST_RIG,5,4,254f,85f,0.36f),
            new SupplementalArmor("68948b118c57a8a52301d7ae",GearSlot.CHEST_RIG,5,4,254f,85f,0.36f),
            new SupplementalArmor("69412e5573dcf473e50be464",GearSlot.CHEST_RIG,4,4,96f,40f,0.35f),
            new SupplementalArmor("69b11935f3783ec37c03a105",GearSlot.BODY_ARMOR,6,2,210f,64f,0.33f),
            new SupplementalArmor("69cf9696b96c8e8d3e002925",GearSlot.BODY_ARMOR,7,4,248f,64f,0.36f),
            new SupplementalArmor("69cfef0d6242b966d40803e7",GearSlot.BODY_ARMOR,7,4,248f,64f,0.36f),
            new SupplementalArmor("69d26ff4b855150a70092b8c",GearSlot.CHEST_RIG,8,4,330f,80f,0.36f),
            new SupplementalArmor("69d27ebeb855150a70092ba3",GearSlot.CHEST_RIG,2,2,160f,80f,0.36f),
            new SupplementalArmor("69d28cdc274c032dd804afe0",GearSlot.CHEST_RIG,5,2,235f,80f,0.36f),
            new SupplementalArmor("69d3708c8d8009073d0a9df4",GearSlot.BODY_ARMOR,5,4,235f,80f,0.36f),
            new SupplementalArmor("69e2441a18cb3157560855ec",GearSlot.CHEST_RIG,2,2,88f,44f,0.33f));
    private static void verifySupplementalArmors(ServerPlayer p,ServerLevel level,Supplier<EntityKineticBullet> fire,
            java.lang.reflect.Method hit) throws Exception {
        var originalGear=p.getData(ModRegistries.PLAYER_GEAR);
        var start=p.position().add(0,1.15,3);var end=start.add(0,0,-6);
        try {
            for(var expected:SUPPLEMENTAL_ARMORS) {
                String context="supplemental "+expected.id()+": ";
                String slot=expected.slot()==GearSlot.CHEST_RIG?"chest_rig":"body_armor";
                var key=net.minecraft.resources.ResourceLocation.parse("tarkov_content:"
                        +(expected.slot()==GearSlot.CHEST_RIG?"container_":"armor_")+expected.id());
                require(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key),context+"real item registered");
                var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);
                var sample=new ItemStack(item);var untouched=new ItemStack(item);
                var profile=ProtectionProfiles.find(sample);
                require(profile!=null&&profile.armor().equipmentSlot().equals(slot),context+"correct physical equipment slot");
                require(profile.armor().segments().size()==expected.segments()&&profile.armor().slots().size()==expected.emptySlots(),context+"source fixed segments and empty plates preserved");
                var front=profile.catalog().covering(profile.armor(),"front_chest").orElseThrow();
                require(front.id().equals("soft_armor_front"),context+"front chest selects source front liner");
                var spec=profile.catalog().specs().get(front.spec());
                require(spec.armorClass()==3&&spec.material().equals("Aramid"),context+"front source class and material, not host aggregate");
                near(spec.maximum(),expected.frontMaximum(),context+"front source durability");
                near(spec.bluntThroughput(),expected.frontBlunt(),context+"front source blunt throughput");
                var fresh=profile.state(sample);
                near((float)fresh.segments().values().stream().mapToDouble(v->v.current()).sum(),expected.total(),context+"fixed-only source durability total");
                if(expected.id().equals("689479a4a733b1602007e2eb"))for(String side:List.of("left_torso","right_torso")) {
                    var sideSpec=profile.catalog().specs().get(profile.catalog().covering(profile.armor(),side).orElseThrow().spec());
                    require(sideSpec.armorClass()==4&&sideSpec.material().equals("Combined"),context+"mixed side class/material retained");
                    near(sideSpec.maximum(),16,context+"side source durability");
                    near(sideSpec.bluntThroughput(),.18f,context+"side source blunt throughput");
                }
                p.setData(ModRegistries.PLAYER_GEAR,PlayerGearState.empty());reset(p);equipSlot(p,expected.slot(),sample);
                var bullet=fire.get();
                try {hit.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);}
                finally {bullet.discard();}
                var physical=TacticalEquipment.read(p,slot).stack();
                var damaged=physical.get(CombatComponents.PROTECTION.get());
                require(damaged!=null,context+"actual hit commits to actual equipped item");
                for(var entry:fresh.segments().entrySet()) {
                    float actual=damaged.segments().get(entry.getKey()).current();
                    if(entry.getKey().equals(front.id()))require(actual<entry.getValue().current(),context+"hit front loses durability");
                    else near(actual,entry.getValue().current(),context+"unhit "+entry.getKey()+" retains durability");
                }
                float chest=PlayerCombat.state(p).health().health(BodyPart.CHEST);
                require(chest>55&&chest<85,context+"real AP hit injures chest with armor mitigation");
                require(untouched.get(CombatComponents.PROTECTION.get())==null&&profile.state(untouched).equals(fresh),context+"second physical instance keeps full independent durability");
                var restored=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)physical.save(level.registryAccess()));
                require(ItemStack.matches(physical,restored),context+"worn item saves and restores all components");
                equipSlot(p,expected.slot(),ItemStack.EMPTY);equipSlot(p,expected.slot(),restored);reset(p);
                require(TacticalEquipment.read(p,slot).stack().get(CombatComponents.PROTECTION.get()).equals(damaged),context+"reequip does not refill wear");
                var rear=profile.catalog().covering(profile.armor(),"back_chest").orElseThrow();
                var rearBullet=fire.get();
                try {hit.invoke(rearBullet,new TacHitResult(new EntityKineticBullet.EntityResult(p,end,false)),end,start);}
                finally {rearBullet.discard();}
                var afterRear=TacticalEquipment.read(p,slot).stack().get(CombatComponents.PROTECTION.get());
                require(afterRear.segments().get(rear.id()).current()<damaged.segments().get(rear.id()).current(),context+"restored item's rear takes the rear hit");
                near(afterRear.segments().get(front.id()).current(),damaged.segments().get(front.id()).current(),context+"rear hit preserves previous front wear");
            }
            System.out.println("SUPPLEMENTAL_ARMOR_SMOKE PASS: 17 source-backed actual items, correct slots, mixed source specs, front/rear local wear, independent instances, save and reequip");
        } finally {p.setData(ModRegistries.PLAYER_GEAR,originalGear);reset(p);}
    }
    private static void verifySlotTemplateCoverage(ServerPlayer p,Supplier<EntityKineticBullet> fire,java.lang.reflect.Method hit) throws Exception {
        var originalGear=p.getData(ModRegistries.PLAYER_GEAR);
        try {
            for(String id:List.of("66b6296d7994640992013b17","68a99207aa809946e507c2f6")) {
                p.setData(ModRegistries.PLAYER_GEAR,PlayerGearState.empty());reset(p);
                var sample=new ItemStack(dev.tarkovcontent.TarkovContent.CONTAINERS.get(id).get());
                var profile=ProtectionProfiles.find(sample);require(profile!=null,"Stich V2 effective coverage is adopted");
                require(profile.armor().segments().size()==5,"five fixed liners, no invented extra Plate layer");
                var rear=profile.catalog().covering(profile.armor(),"back_chest").orElseThrow();
                require(profile.catalog().covering(profile.armor(),"back_abdomen").orElseThrow().id().equals(rear.id()),"one rear liner owns both back regions");
                equipSlot(p,GearSlot.CHEST_RIG,sample);var fresh=profile.state(sample);
                var start=p.position().add(0,1.15,-3);var end=start.add(0,0,6);
                var bullet=fire.get();
                try {hit.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);}
                finally {bullet.discard();}
                var actual=TacticalEquipment.read(p,"chest_rig").stack().get(CombatComponents.PROTECTION.get());
                require(actual!=null&&actual.segments().get(rear.id()).current()<fresh.segments().get(rear.id()).current(),"real rear hit consumes effective rear liner");
                for(var segment:profile.armor().segments())if(!segment.id().equals(rear.id()))
                    near(actual.segments().get(segment.id()).current(),fresh.segments().get(segment.id()).current(),"rear hit does not consume another liner");
                require(ProtectionProfiles.find(sample).armor().slots().stream().allMatch(slot->!slot.compatibleSourceIds().isEmpty()),"empty plate slots retain compatibility declarations");
            }
            System.out.println("COVERAGE_INTERSECTION_SMOKE PASS: two Stich V2 variants, actual rear hits, one physical liner, other segments unchanged");
        } finally {p.setData(ModRegistries.PLAYER_GEAR,originalGear);reset(p);}
    }
    private static void near(float a,float b,String message){require(Math.abs(a-b)<.002,message+" actual="+a+" expected="+b);}
    static void equip(ServerPlayer p,ItemStack armor) {equipSlot(p,GearSlot.CHEST_RIG,armor);}
    static void equipSlot(ServerPlayer p,GearSlot targetSlot,ItemStack armor) {
        var before=p.getData(ModRegistries.PLAYER_GEAR);var slots=new ArrayList<>(before.fixedSlots());
        slots.set(targetSlot.ordinal(),new FixedSlotSnapshot(targetSlot,armor.isEmpty()?Optional.empty():Optional.of(new FixedSlotEntry(UUID.randomUUID(),armor))));
        p.setData(ModRegistries.PLAYER_GEAR,new PlayerGearState(PlayerGearState.CURRENT_SCHEMA_VERSION,before.stateRevision()+1,
                before.pocket(),slots,before.quickReferences(),before.mainHandLease()));
    }
    static void reset(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);p.invulnerableTime=0;p.walkAnimation.setSpeed(0);
        p.setData(PlayerBody.STATE,new PlayerBody(BodyHealth.full(),p.getMaxHealth(),p.getMaxHealth(),0));
    }
    static void run(ServerLevel level,Supplier<EntityKineticBullet> fire,Runnable cancelPre) throws Exception {
        var p=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ArmorPlayer"),net.minecraft.server.level.ClientInformation.createDefault());
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
        };
        // Exercise real ServerPlayer gates, including the normal sixty-tick spawn grace.
        for(int i=0;i<61;i++)p.tick();
        // Use the loaded spawn arena, independently of the random world's spawn coordinates.
        // A fixed (20,20) player can live in an unloaded entity section: direct hit calls work,
        // while the first actual projectile tick cannot discover that player spatially.
        var arena=level.getSharedSpawnPos();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setPos(arena.getX()+8.5,250,arena.getZ()+8.5);p.setYRot(0);p.yBodyRot=0;p.setYHeadRot(0);
        require(level.hasChunkAt(p.blockPosition()),"player smoke arena must be in a loaded spawn chunk: "+p.blockPosition());
        p.setData(ModRegistries.PLAYER_GEAR,PlayerGearState.empty());reset(p);level.addNewPlayer(p);
        var hit=EntityKineticBullet.class.getDeclaredMethod("onHitEntity",TacHitResult.class,Vec3.class,Vec3.class);hit.setAccessible(true);
        try {
            require(ProtectionProfiles.available(),"content profile loaded through resource reload");
            AssemblyInventorySmoke.run(p);
            AssemblyCombatSmoke.run(p,level,fire,hit);
            FaceEquipmentSmoke.run(p,level,fire,hit);
            // The manual client and automated runs load the same warehouse provider.
            var originalExternal = p.getData(ModRegistries.ACTIVE_EXTERNAL_STORAGE);
            level.getServer().getCommands().getDispatcher().execute("tacticalinventory warehouse", p.createCommandSourceStack());
            var warehouse = p.getData(ModRegistries.ACTIVE_EXTERNAL_STORAGE);
            require(warehouse.storage().isPresent(), "unified client has a working private warehouse provider");
            var stored = warehouse.storage().orElseThrow();
            level.getServer().getCommands().getDispatcher().execute("tacticalinventory warehouse", p.createCommandSourceStack());
            require(p.getData(ModRegistries.ACTIVE_EXTERNAL_STORAGE).storage().orElseThrow().equals(stored), "reopening preserves warehouse contents");
            p.setData(ModRegistries.ACTIVE_EXTERNAL_STORAGE, originalExternal);
            p.setData(ModRegistries.PLAYER_GEAR, PlayerGearState.empty());
            System.out.println("UNIFIED_WAREHOUSE_SMOKE PASS: command opens real storage and reopening preserves contents");

            require(level.getEntities(p,p.getBoundingBox().inflate(4),entity->entity instanceof ServerPlayer).isEmpty(),
                    "player command arena must not contain another player");
            require(level.getEntities((net.minecraft.world.entity.Entity)null,p.getBoundingBox().inflate(4),entity->entity==p).contains(p),
                    "player must be discoverable through the actual projectile entity query");
            for(boolean crouch:new boolean[]{false,true})for(float yaw:new float[]{0,90,180,270})for(String ammo:new String[]{"ap","flesh"}) {
                p.setPose(crouch?Pose.CROUCHING:Pose.STANDING);p.setShiftKeyDown(crouch);
                p.yBodyRot=yaw;p.setYHeadRot(yaw);p.setYRot(yaw);
                var previous=AdapterVerification.lastFired();
                level.getServer().getCommands().getDispatcher().execute("armortest shoot_me "+ammo,p.createCommandSourceStack().withPermission(2));
                var commandBullet=AdapterVerification.lastFired();
                require(commandBullet!=previous,"command actually creates a new projectile");
                require(commandBullet.position().distanceTo(p.position())<4,"command projectile starts three blocks in front, not halfway to world origin");
                commandBullet.tick();
                near(PlayerCombat.state(p).health().health(BodyPart.CHEST),ammo.equals("ap")?41:15,"shoot_me hits chest: "+ammo+" crouch="+crouch+" yaw="+yaw);
                commandBullet.discard();reset(p);
            }
            p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.yBodyRot=0;p.setYHeadRot(0);p.setYRot(0);
            System.out.println("PLAYER_COMMAND_SMOKE PASS: real dispatcher, projectile spawn and tick, both ammo types, stand/crouch and four yaws");
            // Real TaCZ tick must discover the ServerPlayer candidate and pass its narrow-phase shape.
            var flying=fire.get();flying.setPos(p.position().add(0,1.15,3));flying.setDeltaMovement(new Vec3(0,0,-6));flying.tick();
            near(PlayerCombat.state(p).health().health(BodyPart.CHEST),55,"real projectile tick hits the player chest");
            reset(p);
            var worn=ArmorVerification.armor();equip(p,worn);
            for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("tarkov_content"))
                    require(dev.itemfoundation.api.definition.ItemProfiles.definition(new ItemStack(item)).isPresent(),"every registered content item has a public profile: "+item);
            }
            var spaces=worn.get(dev.itemfoundation.api.storage.ContainerComponents.STATE.get()).areas();
            var inventory=InventoryStateAdapter.combine(p.getData(ModRegistries.PLAYER_GEAR));
            var intakeRounds=new java.util.ArrayList<dev.tarkovcontent.ammunition.TarkovAmmunitionItem>();
            dev.tarkovcontent.TarkovContent.AMMUNITION.values().forEach(item->intakeRounds.add(item.get()));
            for(var ammo:intakeRounds)for(var space:spaces) {
                var placed=TacticalGrantPlanner.plan(inventory,dev.tacticalinventory.definition.InventoryDefinitions.CURRENT,
                        List.of(space.storage().storageId()),List.of(new ItemStack(ammo,5)));
                require(placed.isPresent(),"real intake accepts adopted ammo into Osprey "+space.areaId());
                var packed=InventoryStateAdapter.split(p.getData(ModRegistries.PLAYER_GEAR),placed.orElseThrow());
                var carrier=packed.fixedSlot(GearSlot.CHEST_RIG).entry().orElseThrow().stack();
                var contents=carrier.get(dev.itemfoundation.api.storage.ContainerComponents.STATE.get()).areas().stream()
                        .flatMap(a->a.storage().entries().stream()).filter(e->e.stack().is(ammo)).mapToInt(e->e.stack().getCount()).sum();
                require(contents==5,"intake persists all ammo inside actual rig component");
                var originalGear=p.getData(ModRegistries.PLAYER_GEAR);p.setData(ModRegistries.PLAYER_GEAR,packed);
                require(dev.tacticalinventory.api.TacticalAmmunition.count(p,a->a.is(ammo))==5,"ammo reserve finds rig rounds without pocket fallback");
                p.setData(ModRegistries.PLAYER_GEAR,originalGear);
            }
            System.out.println("CONTENT_ADMISSION_SMOKE PASS: every owned item declared; all 86 registered adopted rounds enter every Osprey compartment through validated intake");
            var view=TacticalEquipment.read(p,"chest_rig");var stale=view;
            equip(p,worn);require(!stale.setComponent(CombatComponents.ARMOR.get(),new ArmorDurability(Map.of())),"stale gear view cannot commit");
            for(var profile:ProtectionProfiles.all()) {
                var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(profile.armor().item()));
                var sample=new ItemStack(item);var publicProfile=dev.itemfoundation.api.definition.ItemProfiles.definition(sample).orElseThrow();
                require(profile.armor().equipmentSlot().equals("module")?publicProfile.wearableSlots().isEmpty():publicProfile.wearableSlots().contains("tactical_inventory:"+profile.armor().equipmentSlot()),"hosts declare wearable slot; modules cannot be worn as hosts");
                var ordinary=(dev.itemfoundation.api.storage.ContainerAdmissionProvider)worn.getItem();
                for(var area:spaces)require(ordinary.canStore(worn,area.areaId(),sample),"adopted armor admits ordinary rig storage independent of physical fit");
                for(var container:dev.tarkovcontent.TarkovContent.CATALOG)if(container.category().equals("secure_container")) {
                    var secure=dev.tarkovcontent.TarkovContent.CONTAINERS.get(container.tarkovId()).get();var secureStack=new ItemStack(secure);
                    // Containers now use adopted source filters; the historical blanket denial was retired.
                    try(var in=dev.tarkovcontent.TarkovContent.class.getResourceAsStream("/data/tarkov_content/catalog/container_admission.json")) {
                        var entries=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("items");
                        var source=entries.getAsJsonObject(profile.armor().item());
                        var sourceId=source.get("source_id").getAsString();
                        var parents=new java.util.HashSet<String>();source.getAsJsonArray("categories").forEach(v->parents.add(v.getAsString()));
                        for(var area:container.areas().entrySet()) {
                            var filter=area.getValue();
                            boolean denied=filter.excludedItems().contains(sourceId)||filter.excludedCategories().stream().anyMatch(parents::contains);
                            boolean allowed=filter.allowedItems().contains(sourceId)||filter.allowedCategories().stream().anyMatch(parents::contains)
                                ||(filter.allowedItems().isEmpty()&&filter.allowedCategories().isEmpty());
                            require(secure.canStore(secureStack,area.getKey(),sample)==(!denied&&allowed),"secure storage obeys adopted source allow/exclude rules");
                        }
                    }
                }
                verifyInspection(profile,sample);
                require(profile.compatible(profile.state(sample)),"all adopted definitions initialize coherent state");
            }
            var start=p.position().add(0,1.15,3);var end=start.add(0,0,-6);
            var before=PlayerCombat.state(p);var gear=p.getData(ModRegistries.PLAYER_GEAR);
            var b=fire.get();cancelPre.run();hit.invoke(b,new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            require(p.getData(PlayerBody.STATE)==before&&p.getData(ModRegistries.PLAYER_GEAR)==gear,"TaCZ Pre cancellation changes neither body nor gear");
            cancelIncoming=p;hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            require(p.getData(PlayerBody.STATE)==before&&p.getData(ModRegistries.PLAYER_GEAR)==gear,"native incoming cancellation changes neither body nor gear");
            var bullet=fire.get();hit.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            require(p.getHealth()<20,"actual player hurt changed MC health");
            var armor=TacticalEquipment.read(p,"chest_rig").stack().get(CombatComponents.PROTECTION.get());
            require(armor.segments().get("soft_armor_front").current()<36&&armor.segments().get("soft_armor_back").current()==36,"real equipped front alone loses durability");
            near(PlayerCombat.state(p).health().total()/440*20,p.getHealth(),"seven-part projection agrees with native health");
            var projection=dev.tacticalinventory.presentation.InventoryProjection.from(InventoryStateAdapter.combine(p.getData(ModRegistries.PLAYER_GEAR)),
                    dev.tacticalinventory.definition.InventoryDefinitions.CURRENT,Map.of());
            var inspectedId=p.getData(ModRegistries.PLAYER_GEAR).fixedSlot(GearSlot.CHEST_RIG).entry().orElseThrow().entryId();
            var sections=projection.inspectionSections().get(inspectedId);
            require(sections!=null&&inspectionSection(sections,"tactical_combat:fixed").rows().size()==7&&inspectionSection(sections,"tactical_combat:slots").rows().size()==4,"worn Osprey projection contains seven liners and four reserved slots");
            var front=inspectionSection(sections,"tactical_combat:fixed").rows().stream().filter(row->row.id().equals("soft_armor_front")).findFirst().orElseThrow();
            require(front.fields().stream().filter(f->f.id().equals("current")).findFirst().orElseThrow().number().orElseThrow()<36,"inspection reflects physical item wear");
            var ops=net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,level.registryAccess());
            var encodedProjection=dev.tacticalinventory.presentation.InventoryProjection.CODEC.encodeStart(ops,projection).getOrThrow();
            var decodedProjection=dev.tacticalinventory.presentation.InventoryProjection.CODEC.parse(ops,encodedProjection).getOrThrow();
            require(decodedProjection.inspectionSections().equals(projection.inspectionSections()),"typed inspection sections survive server-client codec");
            java.nio.file.Files.writeString(java.nio.file.Path.of("protection-inspection.json"),dev.itemfoundation.api.inspection.InspectionSection.CODEC.listOf()
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE,sections).getOrThrow().toString());
            java.nio.file.Files.writeString(java.nio.file.Path.of("protection-inspection-display.json"),dev.itemfoundation.api.definition.ItemDisplayData.CODEC
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE,projection.displays().get(inspectedId)).getOrThrow().toString());
            var savedBody=p.getData(PlayerBody.STATE);var savedGear=p.getData(ModRegistries.PLAYER_GEAR);
            hit.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            require(p.getData(PlayerBody.STATE)==savedBody&&p.getData(ModRegistries.PLAYER_GEAR)==savedGear,"duplicate hit cannot change player twice");
            var persisted=p.saveWithoutId(new CompoundTag());
            var data=(CompoundTag)PlayerBody.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,savedBody).getOrThrow();
            require(PlayerBody.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,data).getOrThrow().equals(savedBody),"body codec round trip");
            require(persisted.toString().contains("tactical_combat:player_body"),"actual player save contains body attachment");
            var physical=TacticalEquipment.read(p,"chest_rig").stack();
            var restored=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)physical.save(level.registryAccess()));
            require(ItemStack.matches(physical,restored),"damaged worn item serializes");
            equip(p,ItemStack.EMPTY);reset(p);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            near(PlayerCombat.state(p).health().health(BodyPart.CHEST),55,"unequipped player receives full AP flesh damage");
            equip(p,restored);require(TacticalEquipment.read(p,"chest_rig").stack().get(CombatComponents.PROTECTION.get()).equals(armor),"reequip retains local wear");
            var settled=PlayerGearLifecycle.settle(p.getData(ModRegistries.PLAYER_GEAR),false);
            require(settled.drops().stream().anyMatch(s->ItemStack.matches(s,restored)),"existing death planner drops damaged physical item");
            require(PlayerGearLifecycle.settle(p.getData(ModRegistries.PLAYER_GEAR),true).drops().isEmpty(),"keepInventory policy preserved");
            var incompatible=restored.copy();incompatible.set(CombatComponents.PROTECTION.get(),new dev.tacticalcombat.protection.ProtectionState(
                    1,armor.definitionId(),armor.definitionRevision()+1,armor.stateRevision(),armor.segments()));
            equip(p,incompatible);reset(p);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            near(PlayerCombat.state(p).health().health(BodyPart.CHEST),55,"incompatible saved armor does not confer immunity");
            equip(p,ItemStack.EMPTY);reset(p);
            var paca=new ItemStack(dev.tarkovcontent.TarkovContent.ARMORS.get("5648a7494bdc2d9d488b4583").get());
            equipSlot(p,GearSlot.BODY_ARMOR,paca);
            var pacaProfile=ProtectionProfiles.find(paca);
            require(pacaProfile.armor().segments().size()==4&&pacaProfile.armor().slots().isEmpty(),"PACA selects four shared liner types without plate slots");
            require(pacaProfile.catalog().covering(pacaProfile.armor(),"left_shoulder").isEmpty(),"missing shoulder stays unprotected");
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            require(TacticalEquipment.read(p,"body_armor").stack().get(CombatComponents.PROTECTION.get())!=null,"body armor slot actually commits local wear");
            equipSlot(p,GearSlot.BODY_ARMOR,ItemStack.EMPTY);reset(p);
            var cap=new ItemStack(dev.tarkovcontent.TarkovContent.ARMORS.get("60bf74184a63fc79b60c57f6").get());equipSlot(p,GearSlot.HEAD_ARMOR,cap);
            var backHead=p.position().add(0,1.65,-3);var throughHead=backHead.add(0,0,6);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,backHead,false)),backHead,throughHead);
            require(TacticalEquipment.read(p,"head_armor").stack().get(CombatComponents.PROTECTION.get()).segments().get("shell").current()<150,"head back uses declared head-armor shell");
            reset(p);var headGear=p.getData(ModRegistries.PLAYER_GEAR);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,throughHead,false)),throughHead,backHead);
            require(p.getData(ModRegistries.PLAYER_GEAR)==headGear,"uncovered face does not consume rear shell");
            equipSlot(p,GearSlot.HEAD_ARMOR,ItemStack.EMPTY);equip(p,restored);
            verifySupplementalArmors(p,level,fire,hit);
            verifySlotTemplateCoverage(p,fire,hit);
            RicochetSmoke.run(p,level,fire,cancelPre);
            RicochetTargetSmoke.run(p,level,fire,cancelPre);
            System.out.println("PROTECTION_CATALOG_SMOKE PASS: whole adopted catalog, authoritative inspection roundtrip, missing segments, body armor and head armor coverage");
            reset(p);p.hurt(p.damageSources().generic(),2);PlayerCombat.reconcile(p);
            near(PlayerCombat.state(p).health().total(),396,"unlocalized native damage compatibility");
            p.heal(1);PlayerCombat.reconcile(p);near(PlayerCombat.state(p).health().total(),418,"native healing compatibility");
            reset(p);
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);PlayerCombat.reconcile(p);
            near(p.getHealth(),40,"maximum HP changes projection");near(PlayerCombat.state(p).health().total(),440,"maximum HP preserves injury scale");
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);PlayerCombat.reconcile(p);
            reset(p);equip(p,ItemStack.EMPTY);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(4);p.setAbsorptionAmount(4);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);
            near(PlayerCombat.state(p).health().total(),440,"absorption protects body");require(p.getAbsorptionAmount()<4,"absorption is consumed");
            reset(p);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,start,false)),start,end);near(p.getHealth(),20,"creative immunity");
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);reset(p);
            for(boolean crouch:new boolean[]{false,true}) {
                p.setShiftKeyDown(crouch);p.setPose(crouch?Pose.CROUCHING:Pose.STANDING);
                var pose=PlayerCombat.pose(p).orElseThrow();
                var middle=PlayerGeometry.world(new Vec3(0,crouch?7.2:4,0),p.position(),pose);
                require(PlayerGeometry.trace(middle.add(0,0,3),middle.add(0,0,-3),p.position(),pose).isPresent(),"stand/crouch geometry");
            }
            p.setShiftKeyDown(false);p.setPose(Pose.STANDING);p.walkAnimation.setSpeed(0);
            var head=p.position().add(0,1.65,3);var headEnd=head.add(0,0,-6);
            reset(p);equip(p,ItemStack.EMPTY);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,head,false)),head,headEnd);
            // Fixture bypasses inventory setup guard solely to exercise the native totem hook; no gameplay offhand added.
            p.getInventory().offhand.set(0,new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
            p.walkAnimation.setSpeed(0);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,head,false)),head,headEnd);
            require(p.isAlive()&&p.getOffhandItem().isEmpty()&&!PlayerCombat.state(p).health().dead(),"native totem intervention revives the body");
            p.removeAllEffects();reset(p);
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,head,false)),head,headEnd);
            near(PlayerCombat.state(p).health().health(BodyPart.HEAD),5,"player head is an independent HP pool");
            equip(p,restored);dyingPlayer=p;deathDrops=null;
            hit.invoke(fire.get(),new TacHitResult(new EntityKineticBullet.EntityResult(p,head,false)),head,headEnd);
            require(p.isDeadOrDying(),"critical head invokes native player death");
            require(deathDrops!=null&&deathDrops.stream().anyMatch(d->ItemStack.matches(d.getItem(),restored)),"native death event drops the worn component intact");
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p,false));
            near(PlayerCombat.state(p).health().total(),440,"respawn restores all seven parts");
            reset(p);
            var operated=BodyHealth.full().damage(BodyPart.LEFT_ARM,60).surgery(BodyPart.LEFT_ARM,2f/3f).withTotal(440);
            p.setHealth(operated.total()/22);
            p.setData(PlayerBody.STATE,new PlayerBody(operated,p.getHealth(),p.getMaxHealth(),9));
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p,true));
            near(PlayerCombat.state(p).health().maximum(BodyPart.LEFT_ARM),40,"End return preserves surgery cap");
            var bodyTag=PlayerBody.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,p.getData(PlayerBody.STATE)).getOrThrow();
            near(PlayerBody.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,bodyTag).getOrThrow().health().maximum(BodyPart.LEFT_ARM),40,"surgery NBT roundtrip");
            p.setData(dev.tacticalcharacter.status.CharacterStatuses.STATE,dev.tacticalcharacter.status.StatusLedger.EMPTY); // Isolate the timed-status fixture from earlier real gun injuries.
            var effectId=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tactical_character","smoke_effect");
            var definition=new dev.tacticalcharacter.status.StatusDefinition(effectId,dev.tacticalcharacter.status.StatusDefinition.Kind.TIMED,
                dev.tacticalcharacter.status.StatusDefinition.Stacking.INDEPENDENT,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/mob_effect/speed.png"),true,false);
            dev.tacticalcharacter.status.CharacterStatuses.define(definition);
            var effect=new dev.tacticalcharacter.status.StatusInstance(UUID.randomUUID(),effectId,"smoke","",2,1);
            dev.tacticalcharacter.status.CharacterStatuses.apply(p,effect);
            require(dev.tacticalcharacter.status.CharacterStatuses.visible(p).size()==1,"real status projects one icon");
            dev.tacticalcharacter.status.CharacterStatuses.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            dev.tacticalcharacter.status.CharacterStatuses.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            require(dev.tacticalcharacter.status.CharacterStatuses.visible(p).isEmpty(),"expired status disappears without placeholder");
            ResourceSmoke.verify(p);
            MedicalSmoke.verify(p); SurvivalSmoke.verify(p);
            System.out.println("CHARACTER_FRAMEWORK_SMOKE PASS: surgery cap, End return, NBT, authoritative timed status and icon removal");
            System.out.println("PLAYER_ARMOR_SMOKE PASS: real ServerPlayer, worn component commit, cancellation, serialization, unequip, native damage/heal, absorption, critical death, respawn");
        }finally {cancelIncoming=null;dyingPlayer=null;deathDrops=null;p.setData(ModRegistries.PLAYER_GEAR,PlayerGearState.empty());reset(p);p.setShiftKeyDown(false);p.setPose(Pose.STANDING);p.discard();}
    }
}
