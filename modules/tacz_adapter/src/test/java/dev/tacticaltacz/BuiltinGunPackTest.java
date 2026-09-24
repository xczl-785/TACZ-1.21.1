package dev.tacticaltacz;

import com.tacz.guns.resource.BuiltinGunPack;
import com.tacz.guns.resource.SelectedContentPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinGunPackTest {
    @TempDir Path temporary;
    private final Path source = Path.of("../../src/main/resources").toAbsolutePath().normalize();

    @Test void readsAllShippedResourcesWithoutExportingAndIgnoresExternalConflicts() throws Exception {
        Path game = temporary.resolve("game");
        Files.createDirectories(game);
        assertShippedContents(source);
        try (var children = Files.list(game)) { assertEquals(0, children.count()); }

        // Both external formats conflict with real built-in resources. Neither is an input to the loader.
        Path external = game.resolve("tacz/conflicting/assets/tacz/animations/m4a1.animation.json");
        Files.createDirectories(external.getParent());
        Files.writeString(external, "external override must never be loaded");
        Path archive = game.resolve("tacz/conflicting.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("assets/tacz/animations/m4a1.animation.json"));
            zip.write("zip override must never be loaded".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] archiveBefore = Files.readAllBytes(archive);
        assertShippedContents(source);
        assertEquals("external override must never be loaded", Files.readString(external));
        assertArrayEquals(archiveBefore, Files.readAllBytes(archive));
    }

    @Test void loadsTheSameNestedTreeFromAJarFilesystemAndCanReopenOnReload() throws Exception {
        Path archive = temporary.resolve("mod.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive));
             var paths = Files.walk(source.resolve(BuiltinGunPack.RESOURCE_PATH))) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(source.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        try (var fs = FileSystems.newFileSystem(archive)) {
            assertShippedContents(fs.getPath("/"));
            assertShippedContents(fs.getPath("/"));
        }
    }

    @Test void missingBuiltinResourcesFailInsteadOfFallingBackToGameFiles() {
        assertThrows(IllegalStateException.class, () -> BuiltinGunPack.open(temporary, PackType.CLIENT_RESOURCES, 34));
        assertThrows(IllegalStateException.class, () -> BuiltinGunPack.open(temporary, PackType.SERVER_DATA, 48));
    }

    private void assertShippedContents(Path root) throws Exception {
        for (PackType type : PackType.values()) {
            Path expectedRoot = source.resolve(BuiltinGunPack.RESOURCE_PATH).resolve(type.getDirectory());
            Map<ResourceLocation, Path> expected = new HashMap<>();
            try (var files = Files.walk(expectedRoot)) {
                for (Path path : files.filter(Files::isRegularFile).toList()) {
                    Path relative = expectedRoot.relativize(path);
                    String namespace = relative.getName(0).toString();
                    String name = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                    if (type == PackType.SERVER_DATA && SelectedContentPolicy.excludesResource(namespace, name)) continue;
                    // PathPackResources already skips invalid legacy names (e.g. lang/tr-TR.json).
                    var id = ResourceLocation.tryBuild(namespace, name);
                    if (id != null) expected.put(id, path);
                }
            }
            assertFalse(expected.isEmpty());
            try (var pack = BuiltinGunPack.open(root, type, type == PackType.CLIENT_RESOURCES ? 34 : 48)) {
                Set<ResourceLocation> listed = new HashSet<>();
                Set<ResourceLocation> enumerable = new HashSet<>();
                for (String namespace : pack.getNamespaces(type)) {
                    Set<String> directories = new HashSet<>();
                    for (var id : expected.keySet()) {
                        if (id.getNamespace().equals(namespace) && id.getPath().contains("/")) {
                            directories.add(id.getPath().substring(0, id.getPath().indexOf('/')));
                            enumerable.add(id);
                        }
                    }
                    // Vanilla's path validation rejects an empty listResources prefix.
                    for (String directory : directories) {
                        pack.listResources(type, namespace, directory, (id, resource) -> listed.add(id));
                    }
                }
                assertEquals(enumerable, listed, "Enumeration must expose every retained built-in resource");
                for (var entry : expected.entrySet()) {
                    var resource = pack.getResource(type, entry.getKey());
                    assertNotNull(resource, entry.getKey().toString());
                    try (var stream = resource.get()) {
                        assertArrayEquals(Files.readAllBytes(entry.getValue()), stream.readAllBytes(), entry.getKey().toString());
                    }
                }
            }
        }
    }
}
