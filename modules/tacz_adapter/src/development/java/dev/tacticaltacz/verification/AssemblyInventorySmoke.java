package dev.tacticaltacz.verification;

import dev.itemfoundation.api.assembly.*;
import dev.itemfoundation.api.inventory.*;
import dev.tacticalinventory.core.*;
import dev.tacticalinventory.network.AssemblyIntentPayload;
import dev.tacticalinventory.platform.*;
import dev.tacticalinventory.registry.ModRegistries;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Actual authenticated handler, receipt and persistence boundary; isolated smoke player only. */
final class AssemblyInventorySmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Assembly inventory: " + message);
    }
    static void run(ServerPlayer player) {
        var gear = player.getData(ModRegistries.PLAYER_GEAR);
        var external = player.getData(ModRegistries.ACTIVE_EXTERNAL_STORAGE);
        try {
            var definition = AssemblyDefinitions.all().stream().filter(d -> d.slots().stream().anyMatch(s -> s.toggleable()
                    && !s.compatibleItems().isEmpty())).findFirst().orElseThrow();
            var slot = definition.slots().stream().filter(s -> s.toggleable() && !s.compatibleItems().isEmpty()).findFirst().orElseThrow();
            var host = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(definition.itemId())));
            var child = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(slot.compatibleItems().iterator().next())));
            var hostId = UUID.randomUUID(); var childId = UUID.randomUUID();
            var profile = dev.tacticalcombat.player.ProtectionProfiles.find(child);
            require(profile != null, "module has real protection profile");
            var segment = profile.armor().segments().getFirst();
            child.set(dev.tacticalcombat.api.CombatComponents.PROTECTION.get(), profile.state(child).damaged(segment.id(), 7));
            var worn = PlayerGearState.emptyFixedSlots().stream().map(s -> s.slot() == GearSlot.HEAD_ARMOR
                    ? new FixedSlotSnapshot(s.slot(), Optional.of(new FixedSlotEntry(hostId, host))) : s).toList();
            var pocket = new GridStorageSnapshot("player:pocket", new GridSize(12, 12), List.of(
                    new InventoryEntry(childId, child, new GridPosition(0, 0), Orientation.DEFAULT)));
            player.setData(ModRegistries.PLAYER_GEAR, new PlayerGearState(PlayerGearState.CURRENT_SCHEMA_VERSION, 100,
                    Optional.of(pocket), worn, List.of(), Optional.empty()));
            player.setData(ModRegistries.ACTIVE_EXTERNAL_STORAGE, ExternalStorageState.inactive());
            var install = new AssemblyIntentPayload(UUID.randomUUID(), 100, "install", "gear:HEAD_ARMOR", hostId,
                    List.of(slot.id()), "player:pocket", Optional.of(childId), true);
            require(TacticalAssemblies.execute(player, install), "real handler installs");
            var installed = player.getData(ModRegistries.PLAYER_GEAR);
            var hostDisplay = dev.itemfoundation.api.definition.ItemProfiles.definition(host).orElseThrow().display();
            var moduleDisplay = dev.itemfoundation.api.definition.ItemProfiles.definition(child).orElseThrow().display();
            var combinedDisplay = dev.itemfoundation.api.definition.ItemStateView.displayOf(
                    installed.fixedSlot(GearSlot.HEAD_ARMOR).entry().orElseThrow().stack(), hostDisplay);
            require(Math.abs(combinedDisplay.unitWeightKg().orElseThrow() - hostDisplay.unitWeightKg().orElseThrow()
                    - moduleDisplay.unitWeightKg().orElseThrow()) < .000001, "installed module weight included once");
            require(installed.stateRevision() == 101 && installed.pocket().orElseThrow().entry(childId).isEmpty(), "one revision consumes loose child");
            require(TacticalAssemblies.execute(player, install) && player.getData(ModRegistries.PLAYER_GEAR) == installed,
                    "duplicate receipt never performs second installation");
            var equipment = dev.tacticalinventory.api.TacticalEquipment.read(player, "head_armor");
            boolean rejectedDirectWrite = false;
            try { equipment.setComponent(AssemblyComponents.STATE.get(), AssemblyState.empty()); }
            catch (IllegalArgumentException expected) { rejectedDirectWrite = true; }
            require(rejectedDirectWrite && player.getData(ModRegistries.PLAYER_GEAR) == installed,
                    "equipment component API cannot bypass assembly inventory transfer");
            try {
                var fixture=InventoryStateAdapter.combine(installed);
                var area=fixture.storage("player:pocket").orElseThrow()
                        .with(new InventoryEntry(UUID.randomUUID(),child,new GridPosition(0,0),Orientation.DEFAULT))
                        .with(new InventoryEntry(UUID.randomUUID(),new ItemStack(net.minecraft.world.item.Items.STONE),new GridPosition(5,0),Orientation.DEFAULT));
                fixture=fixture.replaceStorage(area);
                var projection=dev.tacticalinventory.presentation.InventoryProjection.from(fixture,
                        dev.tacticalinventory.definition.InventoryDefinitions.CURRENT,InventoryStateAdapter.containerOwners(installed));
                var json=dev.tacticalinventory.presentation.InventoryProjection.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,projection).getOrThrow();
                java.nio.file.Files.writeString(java.nio.file.Path.of("assembly-drag-projection.json"),json.toString());
                java.nio.file.Files.writeString(java.nio.file.Path.of("assembly-drag-definitions.json"),new com.google.gson.Gson().toJson(AssemblyDefinitions.all().toArray(AssemblyDefinition[]::new)));
            } catch(java.io.IOException failure) { throw new RuntimeException(failure); }
            var off = new AssemblyIntentPayload(UUID.randomUUID(), 101, "enable", "gear:HEAD_ARMOR", hostId,
                    List.of(slot.id()), "", Optional.empty(), false);
            require(TacticalAssemblies.execute(player, off), "server hinge state changes");
            var state = player.getData(ModRegistries.PLAYER_GEAR);
            var ops = player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var tag = PlayerGearState.CODEC.encodeStart(ops, state).getOrThrow();
            var loaded = PlayerGearState.CODEC.parse(ops, tag).getOrThrow();
            var installedChild = AssemblyTrees.state(loaded.fixedSlot(GearSlot.HEAD_ARMOR).entry().orElseThrow().stack()).in(slot.id()).orElseThrow();
            require(!installedChild.enabled() && installedChild.instanceId().equals(childId), "persistence retains identity and hinge");
            require(profile.state(installedChild.stack()).segments().get(segment.id()).current() == 7, "persistence retains independent damage");
            player.setData(ModRegistries.PLAYER_GEAR, loaded);
            var remove = new AssemblyIntentPayload(UUID.randomUUID(), loaded.stateRevision(), "remove", "gear:HEAD_ARMOR", hostId,
                    List.of(slot.id()), "", Optional.empty(), true);
            require(TacticalAssemblies.execute(player, remove), "real handler returns module");
            var returned = player.getData(ModRegistries.PLAYER_GEAR).pocket().orElseThrow().entry(childId).orElseThrow();
            require(profile.state(returned.stack()).segments().get(segment.id()).current() == 7, "uninstall never repairs module");
            System.out.println("ASSEMBLY_INVENTORY_SMOKE PASS: authenticated install, replay, hinge, persisted identity/damage, uninstall");
        } finally {
            player.setData(ModRegistries.PLAYER_GEAR, gear);
            player.setData(ModRegistries.ACTIVE_EXTERNAL_STORAGE, external);
        }
    }
}
