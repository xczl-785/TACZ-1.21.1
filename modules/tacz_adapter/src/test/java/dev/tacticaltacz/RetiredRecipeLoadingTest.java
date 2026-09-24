package dev.tacticaltacz;

import com.tacz.guns.resource.DelegatingPackResources;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RetiredRecipeLoadingTest {
    @TempDir Path folder;

    @Test
    void oldExportedRecipesAreHiddenInLookupAndEnumerationWithoutChangingFiles() throws Exception {
        var files = Map.of(
                "recipe/old.json", "{\"type\":\"tacz:gun_smith_table_crafting\"}",
                "recipes/legacy.json", "{\"type\":\"tacz:gun_smith_table_crafting\"}",
                "recipe/ingredient.json", "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"type\":\"tacz:nbt\"}]}",
                "recipe/kept.json", "{\"type\":\"minecraft:crafting_shaped\"}",
                "recipe/broken.json", "{broken",
                "data/guns/kept.json", "{\"type\":\"tacz:nbt\"}");
        for (var entry : files.entrySet()) {
            Path path = folder.resolve("data/custom/" + entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
        }
        var info = new PackLocationInfo("fixture", Component.literal("fixture"), PackSource.BUILT_IN, Optional.empty());
        var delegate = new PathPackResources.PathResourcesSupplier(folder).openPrimary(info);
        try (var pack = new DelegatingPackResources(info,
                new PackMetadataSection(Component.literal("fixture"), 48, Optional.empty()), List.of(delegate))) {
            for (String path : List.of("recipe/old.json", "recipes/legacy.json", "recipe/ingredient.json")) {
                assertNull(pack.getResource(PackType.SERVER_DATA, ResourceLocation.parse("custom:" + path)));
            }
            for (String path : List.of("recipe/kept.json", "recipe/broken.json", "data/guns/kept.json")) {
                assertNotNull(pack.getResource(PackType.SERVER_DATA, ResourceLocation.parse("custom:" + path)));
            }
            var visible = new HashSet<String>();
            pack.listResources(PackType.SERVER_DATA, "custom", "recipe", (id, supplier) -> visible.add(id.getPath()));
            assertEquals(Set.of("recipe/kept.json", "recipe/broken.json"), visible);
        }
        for (var entry : files.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(folder.resolve("data/custom/" + entry.getKey())));
        }
    }
}
