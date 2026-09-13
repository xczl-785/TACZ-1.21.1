package com.tacz.guns.ammunition;
import net.minecraft.world.item.Item;
/** Shared ammunition item behavior; concrete ammunition definitions are provided by content. */
public final class TarkovAmmoItem extends Item {
    public record Definition(String id, String caliber, float fleshDamage, float penetrationPower, float armorDamage) {}
    private final Definition definition;
    public TarkovAmmoItem(Definition definition) { this(definition, 60); }
    public TarkovAmmoItem(Definition definition, int stackMaxSize) { super(new Item.Properties().stacksTo(stackMaxSize)); this.definition = definition; }
    public Definition definition() { return definition; }
}
