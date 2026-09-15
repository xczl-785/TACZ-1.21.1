package dev.tacticaltacz.development;

import com.tacz.guns.ammunition.TarkovAmmoItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Existing fork content selected for developer commands; no item registration or duplicate data. */
public final class VerificationRounds {
    private VerificationRounds() {}
    public static TarkovAmmoItem flesh() { return existing("5a3c16fe86f77452b62de32a"); } // Luger CCI
    public static TarkovAmmoItem ap() { return existing("5efb0da7a29a85116f6ea05f"); } // PBP gzh
    private static TarkovAmmoItem existing(String id) {
        return (TarkovAmmoItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("tarkov_content:ammo_" + id));
    }
}
