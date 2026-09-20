package dev.tacticaltacz.mixin;

import com.tacz.guns.api.item.IAmmo;
import dev.tarkovcontent.ammunition.TarkovAmmunitionItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** TaCZ capability for content-owned ammunition; concrete data remains in tarkov_content. */
@Mixin(TarkovAmmunitionItem.class)
public abstract class ContentAmmoMixin implements IAmmo {
    private TarkovAmmunitionItem self() {
        return (TarkovAmmunitionItem) (Object) this;
    }

    @Override
    public ResourceLocation getAmmoId(ItemStack ammo) {
        return switch (self().definition().caliber()) {
            case "9x19" -> ResourceLocation.fromNamespaceAndPath("tacz", "9mm");
            case "45acp" -> ResourceLocation.fromNamespaceAndPath("tacz", "45acp");
            case "57x28" -> ResourceLocation.fromNamespaceAndPath("tacz", "57x28");
            case "556x45" -> ResourceLocation.fromNamespaceAndPath("tacz", "556x45");
            case "58x42" -> ResourceLocation.fromNamespaceAndPath("tacz", "58x42");
            case "762x39" -> ResourceLocation.fromNamespaceAndPath("tacz", "762x39");
            case "762x51" -> ResourceLocation.fromNamespaceAndPath("tacz", "308");
            case "338lapua" -> ResourceLocation.fromNamespaceAndPath("tacz", "338");
            case "12/70" -> ResourceLocation.fromNamespaceAndPath("tacz", "12g");
            case "357mag" -> ResourceLocation.fromNamespaceAndPath("tacz", "357mag");
            case "50ae" -> ResourceLocation.fromNamespaceAndPath("tacz", "50ae");
            case "50bmg" -> ResourceLocation.fromNamespaceAndPath("tacz", "50bmg");
            default -> throw new IllegalArgumentException("Unknown caliber: " + self().definition().caliber());
        };
    }

    @Override
    public void setAmmoId(ItemStack ammo, ResourceLocation id) {
        if (!getAmmoId(ammo).equals(id)) throw new IllegalArgumentException("Registered ammunition identity is immutable");
    }

    @Override
    public boolean isAmmoOfGun(ItemStack gun, ItemStack ammo) {
        return com.tacz.guns.api.extension.GunPlatformExtensions.current().matchesAmmunition(gun, ammo);
    }
}
