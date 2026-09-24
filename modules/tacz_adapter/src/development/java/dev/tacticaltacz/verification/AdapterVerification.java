package dev.tacticaltacz.verification;

import dev.tarkovcontent.ammunition.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.util.TacHitResult;
import dev.tacticalcombat.api.BulletImpactEvent;
import dev.tacticaltacz.*;
import dev.tarkovcontent.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Development-only test sink and commands. Never shipped in the adapter Jar. */
@EventBusSubscriber(modid="tacz")
// DEVELOPMENT_COMMANDS: source/development/commands.json; retire this registration method with the catalog entry.
public final class AdapterVerification {
    private static final String TARGET = "tactical_tacz_test_target";
    private static final List<EntityKineticBullet> FIRED = new ArrayList<>();
    private static int impacts;
    private static boolean cancelNext;
    private static float lastFeedbackDamage;
    private static int killEvents;
    static int killCount() { return killEvents; }
    @SubscribeEvent public static void cancel(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre event) {
        if (cancelNext) { cancelNext = false; event.setCanceled(true); }
    }
    @SubscribeEvent public static void feedback(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Post event) {
        lastFeedbackDamage = event.getBaseAmount();
    }
    @SubscribeEvent public static void kill(com.tacz.guns.api.event.common.EntityKillByGunEvent event) { killEvents++; }

    @SubscribeEvent public static void resolve(BulletImpactEvent event) {
        if (!event.impact().target().getTags().contains(TARGET)) return;
        // Explicit integration-test scale; not a conversion rule for the final seven-part system.
        event.resolve(event.impact().ammunition().fleshDamage() / 10f);
        impacts++;
        if (((net.minecraft.world.entity.projectile.Projectile) event.impact().projectile()).getOwner() instanceof net.minecraft.server.level.ServerPlayer player)
            player.sendSystemMessage(DevelopmentText.text("ammo.hit", event.impact().ammunition().id(), event.impact().ammunition().fleshDamage(),
                    event.impact().ammunition().penetrationPower(), event.impact().ammunition().armorDamage(), event.damage()));
    }
    @SubscribeEvent public static void capture(EntityJoinLevelEvent event) {
        if (Boolean.getBoolean("tacticaltacz.smoke") && event.getEntity() instanceof EntityKineticBullet b) FIRED.add(b);
    }
    static int firedCount(){return FIRED.size();}
    static EntityKineticBullet lastFired(){return FIRED.getLast();}
    public static ItemStack gun(TarkovAmmunitionItem ammo, ServerLevel level) { return gun(ammo,level,AmmoBridge.GUN); }
    private static ItemStack gun(TarkovAmmunitionItem ammo, ServerLevel level,net.minecraft.resources.ResourceLocation gunId) {
        var stack = GunItemBuilder.create().setId(gunId).setFireMode(FireMode.SEMI).build(level.registryAccess());
        var assembled=dev.tacticaltacz.assembled.AssembledWeapons.byId(gunId);
        if(assembled!=null)stack=assembled.preset();
        require(!stack.isEmpty(), "adopted gun index available");
        AmmoBridge.select(stack, ammo);
        return stack;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tacztest").requires(source -> source.hasPermission(2))
            .then(Commands.literal("kit").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                for (var ammo : List.of(dev.tacticaltacz.development.VerificationRounds.flesh(), dev.tacticaltacz.development.VerificationRounds.ap())) {
                    var weapon = gun(ammo, p.serverLevel());
                    weapon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            DevelopmentText.text("gun.name", ammo.getDescription()));
                    for (var item : List.of(weapon, new ItemStack(ammo, 60)))
                        if (!dev.tacticalinventory.api.TacticalContent.tryGrant(p, List.of(item))) p.drop(item, false);
                }
                ctx.getSource().sendSuccess(() -> DevelopmentText.text("gun.kit"), false);
                return 1;
            }))
            .then(Commands.literal("target").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                var pos = p.position().add(p.getLookAngle().multiply(6, 0, 6));
                var target = target(p.serverLevel(), pos);
                p.serverLevel().addFreshEntity(target);
                return 1;
            }))
            .then(Commands.literal("ammo_kit").executes(ctx -> {
                var player=ctx.getSource().getPlayerOrException();
                for(var id:PistolAdoption.IDS) {
                    var stack=GunItemBuilder.create().setId(id).setFireMode(FireMode.SEMI).build(player.registryAccess());
                    if(!dev.tacticalinventory.api.TacticalContent.tryGrant(player,List.of(stack)))player.drop(stack,false);
                }
                for(var item:dev.tarkovcontent.TarkovContent.AMMUNITION.values()) {
                    if(!item.get().definition().caliber().equals("9x19"))continue;
                    var stack=new ItemStack(item.get(),item.get().getDefaultMaxStackSize());
                    if(!dev.tacticalinventory.api.TacticalContent.tryGrant(player,List.of(stack)))player.drop(stack,false);
                }
                return 1;
            }))
            .then(Commands.literal("scope_kit").executes(ctx -> {
                var player=ctx.getSource().getPlayerOrException();
                var old=new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
                var gun=(IGun)old.getItem();
                gun.setGunId(old,net.minecraft.resources.ResourceLocation.parse("tacz:m4a1"));
                gun.setFireMode(old,FireMode.AUTO);
                gun.setCurrentAmmoCount(old,30);
                gun.setBulletInBarrel(old,true);
                if(!dev.tacticalinventory.api.TacticalContent.tryGrant(player,List.of(old)))player.drop(old,false);
                ctx.getSource().sendSuccess(() -> Component.literal("Legacy TaCZ M4A1 fixture supplied; it must not aim, reload or fire."),false);
                return 1;
            }))
            .then(Commands.literal("select_flesh").executes(ctx -> select(ctx.getSource().getPlayerOrException(), dev.tacticaltacz.development.VerificationRounds.flesh())))
            .then(Commands.literal("select_ap").executes(ctx -> select(ctx.getSource().getPlayerOrException(), dev.tacticaltacz.development.VerificationRounds.ap()))));
    }
    private static int select(net.minecraft.server.level.ServerPlayer player, TarkovAmmunitionItem ammo) {
        try { AmmoBridge.select(player.getMainHandItem(), ammo); player.sendSystemMessage(DevelopmentText.text("ammo.selected", ammo.getDescription())); return 1; }
        catch (IllegalArgumentException | IllegalStateException e) { com.mojang.logging.LogUtils.getLogger().debug("Test ammunition selection rejected", e); player.sendSystemMessage(DevelopmentText.text("ammo.rejected")); return 0; }
    }
    private static Zombie target(ServerLevel level, Vec3 pos) {
        var z = DevelopmentTargets.create(level,pos,DevelopmentText.text("target.ammo"),200,false);
        z.addTag(TARGET);
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        return z;
    }
    @SubscribeEvent public static void smoke(ServerStartedEvent event) {
        if (!Boolean.getBoolean("tacticaltacz.smoke")) return;
        var server = event.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            try {
                require(Path.of("").toAbsolutePath().normalize().endsWith("runs/tacz-adapter-smoke"), "isolated smoke directory");
                cleanup(server.overworld());
                run(server.overworld());
                Files.writeString(Path.of("tacz-adapter-smoke.pass"), "TACZ_ADAPTER_SMOKE PASS: tactical reload/unload/lease/count, snapshot, chamber guard, real shootOnce and ray collision, resolved body/head damage, feedback/kill, cancellation, native fallback, duplicate suppression, ARMOR_FLOW_SMOKE\n");
                System.out.println("TACZ_ADAPTER_SMOKE PASS");
            } catch (Throwable failure) { failure.printStackTrace(); }
            finally { cleanup(server.overworld()); server.halt(false); }
        }));
    }
    private static void run(ServerLevel level) throws Exception {
        dev.tacticalinventory.verification.DevelopmentItemsSmoke.verify(level);
        DevelopmentTaczCatalogSmoke.verify(level);
        UnregisteredAttachmentSmoke.verify(level);
        BarrelFireSmoke.verify(level);
        AutomaticAmmoSmoke.run(level);
        for(var item:dev.tarkovcontent.TarkovContent.AMMUNITION.values()) {
            var stack=item.get().getDefaultInstance();
            var sections=dev.itemfoundation.api.inspection.InspectionProviders.inspect(stack);
            require(sections.stream().anyMatch(section->section.id().equals("tarkov_content:ammunition")&&section.rows().size()==5),"adopted ammunition supplies five inspection attributes");
            var display=dev.itemfoundation.api.definition.ItemProfiles.definition(stack).orElseThrow().display();
            require(display.descriptionText().isPresent(),"ammunition source description reaches display profile");
            if(item.get().definition().caliber().equals("9x19")) {
                java.nio.file.Files.writeString(java.nio.file.Path.of("ammunition-inspection.json"),dev.itemfoundation.api.inspection.InspectionSection.CODEC.listOf().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,sections).getOrThrow().toString());
                java.nio.file.Files.writeString(java.nio.file.Path.of("ammunition-inspection-display.json"),dev.itemfoundation.api.definition.ItemDisplayData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,display).getOrThrow().toString());
                java.nio.file.Files.writeString(java.nio.file.Path.of("ammunition-inspection-id.txt"),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.get()).toString());
            }
        }

        verifyReloadFromTacticalStorage(level,dev.tacticaltacz.development.VerificationRounds.flesh(),AmmoBridge.GUN);
        for(var gunId:dev.tacticaltacz.GunAdoption.CALIBERS.keySet()) for(var item:dev.tarkovcontent.TarkovContent.AMMUNITION.values()) {
            if(!item.get().definition().caliber().equals(dev.tacticaltacz.GunAdoption.CALIBERS.get(gunId)))continue;
            verifyReloadFromTacticalStorage(level,item.get(),gunId);
            verifyAdoptedShot(level,item.get(),gunId);
        }
        require(dev.tarkovcontent.TarkovContent.AMMUNITION.values().stream().filter(i->i.get().definition().caliber().equals("9x19")).count()==9,"all nine adopted rounds exercised");
        System.out.println("ADOPTED_GUN_SMOKE PASS: all adopted guns x every matching approved round reload/unload/serialize/shoot/impact; native bolt and source projectile counts");
        var origin = level.getSharedSpawnPos();
        var shooter = new Zombie(level); shooter.setPos(origin.getX() + 0.5, 250, origin.getZ() + 0.5); shooter.setNoAi(true);
        shooter.xOld = shooter.getX(); shooter.yOld = shooter.getY(); shooter.zOld = shooter.getZ();
        var weapon = gun(dev.tacticaltacz.development.VerificationRounds.flesh(), level);
        var g = (IGun) weapon.getItem();
        shooter.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        var operator = IGunOperator.fromLivingEntity(shooter);
        operator.draw(shooter::getMainHandItem);
        require(operator.getCacheProperty() != null, "TaCZ draw initialized attachment cache");
        var encoded = weapon.save(level.registryAccess());
        var restored = ItemStack.parseOptional(level.registryAccess(), (net.minecraft.nbt.CompoundTag) encoded);
        require(AmmoBridge.snapshot(restored).equals(AmmoBridge.snapshot(weapon)), "variant survives ItemStack serialization");
        g.setBulletInBarrel(weapon, true);
        boolean refused = false;
        try { AmmoBridge.select(weapon, dev.tacticaltacz.development.VerificationRounds.ap()); } catch (IllegalStateException expected) { refused = true; }
        require(refused, "cannot change variant with a chambered round");
        var api = new ModernKineticGunScriptAPI();
        api.setShooter(shooter); api.setItemStack(weapon); api.setDataHolder(new ShooterDataHolder());
        api.setPitchSupplier(() -> 0f); api.setYawSupplier(() -> 0f);
        int before = FIRED.size();
        api.shootOnce(true);
        require(FIRED.size() == before + 1, "real TaCZ shootOnce creates exactly one projectile");
        var fired = FIRED.getLast();
        require(!g.hasBulletInBarrel(weapon) && g.getCurrentAmmoCount(weapon) == 0, "shot consumes final chamber round");
        require(TacticalGunPlatformExtension.installed().ammunition(fired).fleshDamage() == 70, "real projectile carries fired variant");
        AmmoBridge.select(weapon, dev.tacticaltacz.development.VerificationRounds.ap());
        require(TacticalGunPlatformExtension.installed().ammunition(fired).fleshDamage() == 70, "in-flight identity unaffected by changing empty gun");
        var target = target(level, shooter.position().add(0, 0, 4));
        level.addFreshEntity(target);
        // Invoke the real protected collision method, preserving Pre/Post and the production mixin.
        var hit = EntityKineticBullet.class.getDeclaredMethod("onHitEntity", TacHitResult.class, Vec3.class, Vec3.class);
        hit.setAccessible(true);
        int count = impacts;
        hit.invoke(fired, new TacHitResult(new EntityKineticBullet.EntityResult(target, target.position().add(0, 1, 0), true)), shooter.position(), target.position());
        require(Math.abs(target.getHealth() - 193) < 0.001, "7 damage despite armor and native headshot multiplier");
        require(lastFeedbackDamage == 7, "TaCZ feedback uses resolved damage");
        hit.invoke(fired, new TacHitResult(new EntityKineticBullet.EntityResult(target, target.position(), false)), shooter.position(), target.position());
        require(target.getHealth() == 193 && impacts == count + 1, "same projectile-target does not resolve twice");
        g.setBulletInBarrel(weapon, true);
        api.shootOnce(true);
        var ap = FIRED.getLast();
        hit.invoke(ap, new TacHitResult(new EntityKineticBullet.EntityResult(target, target.position(), false)), shooter.position(), target.position());
        require(Math.abs(target.getHealth() - 188.6f) < 0.001, "AP variant deals distinct 4.4 test health damage");
        g.setBulletInBarrel(weapon, true); api.shootOnce(true);
        var cancelledBullet = FIRED.getLast(); cancelNext = true;
        hit.invoke(cancelledBullet, new TacHitResult(new EntityKineticBullet.EntityResult(target, target.position(), false)), shooter.position(), target.position());
        require(target.getHealth() == 188.6f, "cancelled Pre applies no damage");
        target.removeTag(TARGET); target.invulnerableTime = 0;
        g.setBulletInBarrel(weapon, true); api.shootOnce(true);
        float health = target.getHealth(); count = impacts;
        hit.invoke(FIRED.getLast(), new TacHitResult(new EntityKineticBullet.EntityResult(target, target.position(), false)), shooter.position(), target.position());
        require(target.getHealth() < health && impacts == count, "unclaimed targets retain native damage");
        target.addTag(TARGET); target.setHealth(200); target.invulnerableTime = 0;
        api.setPitchSupplier(() -> 10f);
        g.setBulletInBarrel(weapon, true); api.shootOnce(true);
        var flying = FIRED.getLast();
        flying.tick();
        require(Math.abs(target.getHealth() - 195.6f) < 0.001, "actual bullet tick ray collision applies AP damage: health="
                + target.getHealth() + " pos=" + flying.position() + " target=" + target.getBoundingBox());
        target.setHealth(2); target.invulnerableTime = 0;
        int killsBefore = killEvents;
        g.setBulletInBarrel(weapon, true); api.shootOnce(true);
        hit.invoke(FIRED.getLast(), new TacHitResult(new EntityKineticBullet.EntityResult(target, target.position(), false)), shooter.position(), target.position());
        require(target.isDeadOrDying() && killEvents == killsBefore + 1, "TaCZ kill event survives damage replacement");
        target.discard();
        ArmorFlowSmoke.run(level,shooter.position(),()->{g.setBulletInBarrel(weapon,true);api.shootOnce(true);return armorFixture(FIRED.getLast());},()->cancelNext=true);
        PlayerArmorSmoke.run(level,()->{g.setBulletInBarrel(weapon,true);api.shootOnce(true);return armorFixture(FIRED.getLast());},()->cancelNext=true);
        FIRED.forEach(Entity::discard); FIRED.clear();
    }
    /** Fixed numerical armor regression input, never a registered/obtainable ammunition item.
     * The all-gun suite above verifies real source profiles; this isolated suite retains its formula oracle. */
    private static EntityKineticBullet armorFixture(EntityKineticBullet bullet) {
        if (!Boolean.getBoolean("tacticaltacz.smoke")) throw new IllegalStateException("Smoke only");
        TacticalGunPlatformExtension.installed().setAmmunition(bullet,
                new dev.tacticalcombat.api.BallisticProfile("verification:armor_numeric", "9x19",30,40,50));
        return bullet;
    }
    private static TarkovAmmunitionItem otherRound(TarkovAmmunitionItem selected) {
        return selected==dev.tacticaltacz.development.VerificationRounds.ap()
                ?dev.tacticaltacz.development.VerificationRounds.flesh():dev.tacticaltacz.development.VerificationRounds.ap();
    }
    private static void verifyAdoptedShot(ServerLevel level,TarkovAmmunitionItem ammo,net.minecraft.resources.ResourceLocation gunId) throws Exception {
        var shooter=new Zombie(level);shooter.setPos(0,250,0);shooter.setNoAi(true);
        var weapon=gun(ammo,level,gunId);var g=(IGun)weapon.getItem();
        var encoded=weapon.save(level.registryAccess());
        var restored=ItemStack.parseOptional(level.registryAccess(),(net.minecraft.nbt.CompoundTag)encoded);
        require(AmmoBridge.snapshot(restored).equals(AmmoBridge.snapshot(weapon)),"adopted variant survives serialization");
        shooter.setItemSlot(EquipmentSlot.MAINHAND,weapon);
        IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
        var api=new ModernKineticGunScriptAPI();api.setShooter(shooter);api.setItemStack(weapon);
        api.setDataHolder(new ShooterDataHolder());api.setPitchSupplier(()->0f);api.setYawSupplier(()->0f);
        var alternate=dev.tarkovcontent.TarkovContent.AMMUNITION.values().stream().map(java.util.function.Supplier::get).filter(i->i!=ammo && i.definition().caliber().equals(ammo.definition().caliber())).findFirst().orElseThrow();
        boolean openBolt=TimelessAPI.getCommonGunIndex(gunId).orElseThrow().getGunData().getBolt()==com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT;
        g.setBulletInBarrel(weapon,!openBolt);g.setCurrentAmmoCount(weapon,openBolt?1:0);
        boolean rejected=false;
        try{AmmoBridge.select(weapon,alternate);}catch(IllegalStateException expected){rejected=true;}
        require(rejected,"adopted chamber prevents changing variant");
        int before=FIRED.size();api.shootOnce(true);require(FIRED.size()==before+AmmoBridge.definition(weapon).projectileCount(),"adopted real shot projectile count "+gunId+" "+ammo.definition().id());
        require(g.getCurrentAmmoCount(weapon)==0&&!g.hasBulletInBarrel(weapon),"shot consumes exactly one cartridge "+gunId);
        var fired=FIRED.getLast();var snapshot=TacticalGunPlatformExtension.installed().ammunition(fired);
        require(snapshot.equals(dev.tacticalcombat.api.FirearmBallistics.profile(AmmoBridge.snapshot(weapon))),"adopted projectile exact parameters");
        AmmoBridge.select(weapon,alternate);
        require(snapshot.equals(TacticalGunPlatformExtension.installed().ammunition(fired)),"adopted in-flight snapshot immutable");
        var target=target(level,new Vec3(0,250,4));level.addFreshEntity(target);
        var hit=EntityKineticBullet.class.getDeclaredMethod("onHitEntity",TacHitResult.class,Vec3.class,Vec3.class);hit.setAccessible(true);
        hit.invoke(fired,new TacHitResult(new EntityKineticBullet.EntityResult(target,target.position(),false)),shooter.position(),target.position());
        require(Math.abs(target.getHealth()-(200-ammo.definition().fleshDamage()/10))<0.001,"adopted exact damage at test receiver");
        target.discard();fired.discard();shooter.discard();
    }
    private static void verifyReloadFromTacticalStorage(ServerLevel level, TarkovAmmunitionItem selected,net.minecraft.resources.ResourceLocation gunId) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.fromString("c5948a9f-6bd8-4092-8a1e-f5b646061031"), "[TaCZSmoke]"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(0, 100, 0);
        player.setData(dev.tacticalinventory.registry.ModRegistries.PLAYER_GEAR,
                dev.tacticalinventory.platform.PlayerGearState.empty());
        var weapon = gun(selected, level,gunId);
        weapon.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY,data->data.update(tag->tag.remove(AmmoBridge.KEY))); // Normal acquired gun: no selected variant.
        if(PistolAdoption.contains(weapon)) {
        require(dev.tacticalinventory.definition.InventoryDefinitions.CURRENT.compatibleGearSlots(weapon).equals(Set.of(dev.tacticalinventory.core.GearSlot.SIDEARM)),"adopted pistol qualifies only for sidearm");
        require(dev.itemfoundation.api.identity.ItemIdentities.server().resolve(weapon).identities().orElseThrow().matches(net.minecraft.resources.ResourceLocation.parse("item_foundation:type/weapon/firearm/pistol"),false),"dynamic pistol identity");
        }
        var slots = new ArrayList<>(dev.tacticalinventory.platform.PlayerGearState.emptyFixedSlots());
        var slot = PistolAdoption.contains(weapon)?dev.tacticalinventory.core.GearSlot.SIDEARM:dev.tacticalinventory.core.GearSlot.PRIMARY_WEAPON_1;
        slots.set(slot.ordinal(), new dev.tacticalinventory.core.FixedSlotSnapshot(slot,
                Optional.of(new dev.tacticalinventory.core.FixedSlotEntry(UUID.randomUUID(), weapon))));
        player.setData(dev.tacticalinventory.registry.ModRegistries.PLAYER_GEAR,
                new dev.tacticalinventory.platform.PlayerGearState(dev.tacticalinventory.platform.PlayerGearState.CURRENT_SCHEMA_VERSION, 0, Optional.empty(), slots, List.of(), Optional.empty()));
        require(dev.tacticalinventory.platform.TacticalGrantService.tryGrant(player,
                List.of(new ItemStack(selected, 5), new ItemStack(otherRound(selected), 5))), "grant tactical carried rounds");
        require(dev.tacticalinventory.platform.PlayerInventoryService.activateSource(player,
                new dev.tacticalinventory.core.FixedSlotLocation(slot), 0, UUID.randomUUID()), "real weapon-slot hand lease");
        var held = player.getMainHandItem();
        var g = (com.tacz.guns.api.item.gun.AbstractGunItem) held.getItem();
        var operator = IGunOperator.fromLivingEntity(player);
        operator.draw(player::getMainHandItem);
        var data = operator.getDataHolder();
        data.drawTimestamp = System.currentTimeMillis() - 10000;
        data.baseTimestamp = System.currentTimeMillis() - 10000;
        require(AmmoBridge.reserveCount(player,held) == 5,
                "reserve count reports only matching carried ammunition");
        require(g.canReload(player, held), "TaCZ detects tactical pocket ammunition");
        operator.reload();
        require(data.reloadStateType.isReloading(), "real TaCZ reload starts");
        for(int tick=0;tick<600 && data.reloadStateType.isReloading();tick++) {
            data.reloadTimestamp-=50;
            data.reloadStateType=g.tickReload(data,held,player).getStateType();
        }
        int loaded = g.getCurrentAmmoCount(held) + (g.hasBulletInBarrel(held) ? 1 : 0);
        require(loaded == 5, "Lua reload loads only five matching rounds for "+gunId+", got " + loaded);
        require(dev.tacticalinventory.api.TacticalAmmunition.count(player, a -> AmmoBridge.matches(held, a)) == 0,
                "reserve count updates after reload payment");
        require(!AmmoBridge.hasAmmo(player, held), "matching rounds paid from tactical pocket");
        require(!dev.tacticalinventory.api.TacticalAmmunition.find(player, a -> a.is(otherRound(selected))).isEmpty(), "other variant preserved");
        require(!g.canReload(player, held), "other variant cannot top up selected magazine");
        for (var stack : player.getInventory().items)
            require(!(stack.getItem() instanceof TarkovAmmunitionItem), "no native hidden inventory rounds");
        int chamber = g.hasBulletInBarrel(held) ? 1 : 0;
        g.dropAllAmmo(player, held);
        require(g.getCurrentAmmoCount(held) == 0 && (g.hasBulletInBarrel(held) ? 1 : 0) == chamber,
                "magazine unload preserves chamber");
        require(AmmoBridge.hasAmmo(player, held), "unload returns same variant to tactical storage");
        require(dev.tacticalinventory.platform.PlayerInventoryService.stow(player, UUID.randomUUID()), "gun returns from hand lease");
        var state = player.getData(dev.tacticalinventory.registry.ModRegistries.PLAYER_GEAR);
        var returned = state.fixedSlots().get(slot.ordinal()).entry().orElseThrow().stack();
        require(AmmoBridge.snapshot(returned).identity().id().equals(selected.definition().id()), "stowed gun retains selected ammo");
    }
    private static void cleanup(ServerLevel level) {
        var targets = new ArrayList<Entity>();
        level.getAllEntities().forEach(entity -> { if (entity.getTags().contains(TARGET)) targets.add(entity); });
        targets.forEach(Entity::discard); FIRED.forEach(Entity::discard); FIRED.clear();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
