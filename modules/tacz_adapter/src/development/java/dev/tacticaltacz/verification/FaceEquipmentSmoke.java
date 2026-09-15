package dev.tacticaltacz.verification;
import dev.tacticalcharacter.core.*;
import dev.tacticalcharacter.player.PlayerBody;


import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import dev.itemfoundation.api.assembly.AssemblyDefinitions;
import dev.itemfoundation.api.definition.ItemProfiles;
import dev.itemfoundation.api.inspection.InspectionSection;
import dev.tacticalcombat.api.CombatComponents;
import dev.tacticalcombat.player.*;
import dev.tacticalcombat.protection.ProtectionInspection;
import dev.tacticalinventory.api.TacticalEquipment;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.definition.InventoryDefinitions;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Real registered content, equipment compatibility, actual TaCZ damage and saved player inventory. */
final class FaceEquipmentSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError("Head/face batch: " + message);
    }
    private static ItemStack item(String source) {
        var id = ResourceLocation.parse("tarkov_content:armor_" + source);
        require(BuiltInRegistries.ITEM.containsKey(id), "approved item registered: " + source);
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
    static void run(ServerPlayer player, ServerLevel level, Supplier<EntityKineticBullet> fire, Method hit) throws Exception {
        var gear = player.getData(ModRegistries.PLAYER_GEAR);
        var body = player.getData(PlayerBody.STATE);
        float health = player.getHealth(), absorption = player.getAbsorptionAmount();
        try {
            var helmet = item("5a154d5cfcdbcb001a3b00da"); // FAST MT, optional slots are deliberately unavailable.
            var profile = ProtectionProfiles.find(helmet);
            require(profile != null && profile.armor().equipmentSlot().equals("head_armor"), "FAST body profile");
            require(!profile.armor().segments().isEmpty(), "FAST keeps sourced fixed protection");
            require(!profile.armor().slots().isEmpty(), "unavailable attachment slots remain declared");
            require(AssemblyDefinitions.find(helmet).map(d -> d.slots().isEmpty()).orElse(true), "new helmet opens no attachment operations");
            require(ProtectionInspection.inspect(helmet).stream().filter(s -> s.id().equals("tactical_combat:slots"))
                    .flatMap(s -> s.rows().stream()).allMatch(r -> r.card().orElseThrow().state() == InspectionSection.CardState.LOCKED),
                    "new helmet slots are visibly unavailable");
            require(ProtectionProfiles.all().stream().filter(p -> p.armor().equipmentSlot().equals("face_cover")).count() == 25,
                    "25 normal masks adopted, special neck mask stays deferred");
            require(ProtectionProfiles.all().stream().filter(p -> p.armor().equipmentSlot().equals("eyewear")).count() == 2,
                    "two protective glasses adopted");
            for (String absent : List.of("678f84bb9e85556ca60f0362", "59ef13ca86f77445fd0e2483", "5c08f87c0db8340019124324",
                    "68a6d95addf0111c2f04c9c3"))
                require(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("tarkov_content:armor_" + absent)),
                        "deferred/excluded item not registered: " + absent);

            verifyHit(player, level, fire, hit, "5b432b2f5acfc4771e1c6622", GearSlot.FACE_COVER, -1);
            verifyHit(player, level, fire, hit, "603409c80ca681766b6a0fb2", GearSlot.EYEWEAR, -5);
            verifyHit(player, level, fire, hit, "62a61c988ec41a51b34758d5", GearSlot.EYEWEAR, -5);

            player.setData(ModRegistries.PLAYER_GEAR, PlayerGearState.empty());
            PlayerArmorSmoke.equipSlot(player, GearSlot.FACE_COVER, item("5b432b2f5acfc4771e1c6622"));
            require(valid(player), "mask alone is wearable");
            PlayerArmorSmoke.equipSlot(player, GearSlot.EYEWEAR, item("603409c80ca681766b6a0fb2"));
            require(!valid(player), "source BlocksEyewear rejects mask plus glasses");
            PlayerArmorSmoke.equipSlot(player, GearSlot.FACE_COVER, item("68bee238a48c3c320808abc4"));
            require(valid(player), "gold lower-jaw mask allows sourced glasses combination");
            PlayerArmorSmoke.equipSlot(player, GearSlot.HEAD_ARMOR, helmet);
            require(!valid(player), "source item-pair conflict rejects FAST plus lower-jaw mask");
            PlayerArmorSmoke.equipSlot(player, GearSlot.FACE_COVER, ItemStack.EMPTY);
            require(valid(player), "FAST body plus glasses remains allowed");
            System.out.println("FACE_EQUIPMENT_SMOKE PASS: 37-head batch representative, locked optional slots, 25 masks/2 glasses, real hits, wear persistence and source compatibility");
        } finally {
            player.setData(ModRegistries.PLAYER_GEAR, gear);
            player.setData(PlayerBody.STATE, body);
            player.setHealth(health); player.setAbsorptionAmount(absorption); player.invulnerableTime = 0;
        }
    }
    private static boolean valid(ServerPlayer player) {
        return new InventoryValidator().validate(InventoryStateAdapter.combine(player.getData(ModRegistries.PLAYER_GEAR)),
                InventoryDefinitions.CURRENT).isEmpty();
    }
    private static void verifyHit(ServerPlayer player, ServerLevel level, Supplier<EntityKineticBullet> fire, Method hit,
                                  String source, GearSlot slot, double localY) throws Exception {
        var sample = item(source);
        var publicProfile = ItemProfiles.definition(sample).orElseThrow();
        String slotId = slot.name().toLowerCase(Locale.ROOT);
        require(publicProfile.wearableSlots().contains("tactical_inventory:" + slotId), "public wearable slot: " + source);
        player.setData(ModRegistries.PLAYER_GEAR, PlayerGearState.empty());
        PlayerArmorSmoke.reset(player); PlayerArmorSmoke.equipSlot(player, slot, sample);
        require(valid(player), "real item admissible in its existing equipment slot");
        var wornEntry = player.getData(ModRegistries.PLAYER_GEAR).fixedSlot(slot).entry().orElseThrow();
        var center = PlayerGeometry.world(new Vec3(0, localY, -4), player.position(), PlayerCombat.pose(player).orElseThrow());
        var start = center.add(0, 0, 3); var end = center.add(0, 0, -3);
        shoot(player, fire, hit, start, end);
        var worn = TacticalEquipment.read(player, slotId).stack();
        var protection = ProtectionProfiles.find(worn);
        var state = worn.get(CombatComponents.PROTECTION.get());
        require(state != null && state.segments().entrySet().stream().anyMatch(e -> e.getValue().current() < e.getValue().maximum()),
                "actual face ray damages equipped protection: " + source);
        require(player.getData(ModRegistries.PLAYER_GEAR).fixedSlot(slot).entry().orElseThrow().entryId().equals(wornEntry.entryId()),
                "damage keeps physical equipment identity");
        var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var saved = PlayerGearState.CODEC.encodeStart(ops, player.getData(ModRegistries.PLAYER_GEAR)).getOrThrow();
        var loaded = PlayerGearState.CODEC.parse(ops, saved).getOrThrow();
        require(ItemStack.matches(worn, loaded.fixedSlot(slot).entry().orElseThrow().stack()), "saved gear preserves face wear");
        PlayerArmorSmoke.reset(player);
        shoot(player, fire, hit, end, start);
        require(ItemStack.matches(worn, TacticalEquipment.read(player, slotId).stack()), "back-of-head ray does not wear frontal mask/glasses");
    }
    private static void shoot(ServerPlayer player, Supplier<EntityKineticBullet> fire, Method hit, Vec3 start, Vec3 end) throws Exception {
        var bullet = fire.get();
        try { hit.invoke(bullet, new TacHitResult(new EntityKineticBullet.EntityResult(player, start, false)), start, end); }
        finally { bullet.discard(); }
    }
}
