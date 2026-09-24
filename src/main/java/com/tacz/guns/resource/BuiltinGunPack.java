package com.tacz.guns.resource;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Reads the shipped nested pack directly, including from NeoForge's mod-file filesystem. */
public final class BuiltinGunPack {
    public static final String RESOURCE_PATH = "assets/tacz/custom/tacz_default_gun";
    public static final PackLocationInfo INFO = new PackLocationInfo("tacz_resources",
            Component.literal("TACZ Resources"), PackSource.BUILT_IN, Optional.empty());

    private BuiltinGunPack() {}

    /** The only input is the owning mod's root, never a game directory or an external pack list. */
    public static DelegatingPackResources open(Path modRoot, PackType type, int packVersion) {
        Path root = modRoot.resolve(RESOURCE_PATH);
        if (!Files.isDirectory(root.resolve("assets")) || !Files.isDirectory(root.resolve("data"))) {
            throw new IllegalStateException("Required built-in TaCZ resources missing: " + root);
        }
        var resources = new PathPackResources.PathResourcesSupplier(root).openPrimary(INFO);
        var metadata = new PackMetadataSection(Component.translatable("tacz.resources.modresources"),
                packVersion, Optional.empty());
        return new DelegatingPackResources(INFO, metadata, List.of(resources));
    }
}
