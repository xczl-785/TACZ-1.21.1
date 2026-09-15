package dev.tacticaltacz.assembled;

public final class AssemblyGunItem extends com.tacz.guns.item.ModernKineticGunItem {
    private final AssembledWeapon weapon;
    public AssemblyGunItem(AssembledWeapon weapon){this.weapon=weapon;}
    public AssembledWeapon weapon(){return weapon;}
    @Override public net.minecraft.world.item.ItemStack getDefaultInstance(){return weapon.preset();}
}
