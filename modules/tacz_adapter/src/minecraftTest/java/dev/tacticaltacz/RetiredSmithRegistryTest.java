package dev.tacticaltacz;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RetiredSmithRegistryTest {
    @Test
    void retiredRegistrationsAreAbsentAndRetainedRecipesStillDecode() throws Exception {
        Bootstrap.bootStrap();
        var retired = ResourceLocation.parse("tacz:gun_smith_table_crafting");
        assertFalse(BuiltInRegistries.RECIPE_TYPE.containsKey(retired));
        assertFalse(BuiltInRegistries.RECIPE_SERIALIZER.containsKey(retired));
        assertFalse(NeoForgeRegistries.INGREDIENT_TYPES.containsKey(ResourceLocation.parse("tacz:nbt")));
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        for (String name : new String[]{"gunpowder"}) {
            try (var stream = com.tacz.guns.GunMod.class.getResourceAsStream("/data/tacz/recipe/" + name + ".json")) {
                assertNotNull(stream, name);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                assertNotNull(Recipe.CODEC.parse(ops, json).getOrThrow(), name);
            }
        }
        for (String name : new String[]{"target", "statue", "target_minecart"}) {
            assertFalse(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("tacz:" + name)));
            assertNull(com.tacz.guns.GunMod.class.getResourceAsStream("/data/tacz/recipe/" + name + ".json"));
        }
        assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse("tacz:bullet")));
        assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("tacz:modern_kinetic_gun")));
        assertFalse(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("tacz:target")));
        assertFalse(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("tacz:statue")));
        assertFalse(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse("tacz:target_minecart")));
    }
}
