package com.tacz.guns.ammunition;
import com.tacz.guns.api.item.IAmmo;
import dev.tacticaltacz.AmmoBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.Item;
/** Shared ammunition item behavior; concrete ammunition definitions are provided by content. */
public final class TarkovAmmoItem extends Item implements IAmmo {
    public record Definition(String id, String caliber, float fleshDamage, float penetrationPower, float armorDamage) {}
    private final Definition definition;
    public TarkovAmmoItem(Definition definition) { this(definition, 60); }
    public TarkovAmmoItem(Definition definition, int stackMaxSize) { super(new Item.Properties().stacksTo(stackMaxSize)); this.definition = definition; }
    public Definition definition() { return definition; }
    public ResourceLocation getAmmoId(ItemStack ammo) {
        String caliber=((TarkovAmmoItem)ammo.getItem()).definition().caliber();
        String nativeId=switch(caliber) {
            case "9x19" -> "9mm"; case "45acp" -> "45acp"; case "57x28" -> "57x28";
            case "556x45" -> "556x45"; case "58x42" -> "58x42"; case "762x39" -> "762x39";
            case "762x51" -> "308"; case "338lapua" -> "338"; case "12/70" -> "12g";
            case "357mag" -> "357mag"; case "50ae" -> "50ae"; case "50bmg" -> "50bmg";
            default -> throw new IllegalArgumentException("Unknown caliber: "+caliber);
        };
        return ResourceLocation.fromNamespaceAndPath("tacz",nativeId);
    }
    public void setAmmoId(ItemStack ammo, ResourceLocation id) {
        if (!getAmmoId(ammo).equals(id)) throw new IllegalArgumentException("Registered ammunition identity is immutable");
    }
    public boolean isAmmoOfGun(ItemStack gun, ItemStack ammo) { return AmmoBridge.matches(gun, ammo); }
}
