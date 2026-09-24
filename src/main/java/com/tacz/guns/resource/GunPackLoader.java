package com.tacz.guns.resource;

import com.tacz.guns.GunMod;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;
import net.neoforged.fml.ModList;

import java.util.function.Consumer;

/** Registers only the shipped pack; ordinary Minecraft pack repositories remain untouched. */
public record GunPackLoader(PackType packType) implements RepositorySource {
    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        var modRoot = ModList.get().getModFileById(GunMod.MOD_ID).getFile().findResource("");
        var resources = BuiltinGunPack.open(modRoot, packType,
                SharedConstants.getCurrentVersion().getPackVersion(packType));
        // Required built-in dependency, with the same bottom priority as the previous loader.
        var selection = new PackSelectionConfig(true, Pack.Position.BOTTOM, true);
        var pack = Pack.readMetaAndCreate(BuiltinGunPack.INFO, resources, packType, selection);
        if (pack == null) {
            resources.close();
            throw new IllegalStateException("Cannot register required built-in TaCZ resources for " + packType);
        }
        onLoad.accept(pack);
    }
}
