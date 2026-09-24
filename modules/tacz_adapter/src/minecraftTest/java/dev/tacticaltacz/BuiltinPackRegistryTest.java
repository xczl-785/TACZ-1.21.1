package dev.tacticaltacz;

import com.tacz.guns.resource.GunPackLoader;
import dev.tacticaltacz.assembled.AssembledWeapons;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the real NeoForge mod-file path and both event pack types, without starting a game/server. */
class BuiltinPackRegistryTest {
    @Test void modFileRepositorySuppliesBothSidesAndAllFifteenManagedGunIndexes() throws Exception {
        Bootstrap.bootStrap();
        var root = ModList.get().getModFileById("tacz").getFile().findResource("");
        for (PackType type : PackType.values()) {
            var found = new ArrayList<Pack>();
            new GunPackLoader(type).loadPacks(found::add);
            assertEquals(1, found.size());
            assertTrue(found.getFirst().isRequired());
            var builtin = found.getFirst().open();
            var main = new PathPackResources.PathResourcesSupplier(root).openPrimary(builtin.location());
            try (var resources = new MultiPackResourceManager(type, List.of(builtin, main))) {
                if (type == PackType.SERVER_DATA) {
                    var guns = resources.listResources("index/guns", id -> id.getPath().endsWith(".json"));
                    var expected = AssembledWeapons.all().stream().map(weapon -> ResourceLocation.fromNamespaceAndPath(
                            weapon.GUN.getNamespace(), "index/guns/" + weapon.GUN.getPath() + ".json"))
                            .collect(Collectors.toSet());
                    assertEquals(15, expected.size());
                    assertEquals(expected, guns.keySet());
                    assertTrue(resources.getResource(ResourceLocation.parse("tacz:data/attachments/sight_p90_data.json")).isPresent());
                } else {
                    assertTrue(resources.getResource(ResourceLocation.parse("tacz:animations/m4a1.animation.json")).isPresent());
                    assertTrue(resources.getResource(ResourceLocation.parse("tacz:scripts/default_state_machine.lua")).isPresent());
                    assertTrue(resources.getResource(ResourceLocation.parse("tacz:sounds.json")).isPresent());
                }
            }
        }
    }
}
