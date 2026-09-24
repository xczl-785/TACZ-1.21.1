package com.tacz.guns.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Hide retired recipe types in old exported gun packs without modifying their files. */
public final class RetiredRecipePolicy {
    private RetiredRecipePolicy() {}

    public static boolean excludes(ResourceLocation id, IoSupplier<InputStream> resource) {
        String path = id.getPath();
        if (!(path.startsWith("recipe/") || path.startsWith("recipes/")) || !path.endsWith(".json")) {
            return false;
        }
        try (var reader = new InputStreamReader(resource.get(), StandardCharsets.UTF_8)) {
            return usesRetiredType(JsonParser.parseReader(reader));
        } catch (IOException | JsonParseException e) {
            // Let the normal resource loader report malformed/unreadable recipes.
            return false;
        }
    }

    private static boolean usesRetiredType(JsonElement value) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            var type = object.get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()
                    && (type.getAsString().equals("tacz:gun_smith_table_crafting")
                    || type.getAsString().equals("tacz:nbt"))) {
                return true;
            }
            return object.entrySet().stream().anyMatch(entry -> usesRetiredType(entry.getValue()));
        }
        if (value.isJsonArray()) {
            for (var entry : value.getAsJsonArray()) {
                if (usesRetiredType(entry)) return true;
            }
        }
        return false;
    }
}
