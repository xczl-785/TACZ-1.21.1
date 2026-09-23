package dev.tacticaltacz;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
/** TaCZ execution bridge; the independent runtime owns all component prerequisites. */
public final class AssemblyFireGate {
    public static boolean outOfScope(ItemStack stack) {
        var gun=IGun.getIGunOrNull(stack);
        if (gun==null) return false;
        var weapon=dev.tacticaltacz.assembled.AssembledWeapons.byId(gun.getGunId(stack));
        return weapon==null||!weapon.isGun(stack);
    }
    public static boolean blocked(ItemStack stack){
        if(outOfScope(stack))return true;
        var gun=IGun.getIGunOrNull(stack);
        var weapon=gun==null?null:dev.tacticaltacz.assembled.AssembledWeapons.byId(gun.getGunId(stack));
        var physical=dev.tacticaltacz.assembled.AssembledWeapons.from(stack);
        if(physical!=null&&physical!=weapon)return true;
        if(weapon!=null&&(!weapon.isGun(stack)||!LegacyFirearmProfiles.profileId(stack).filter(weapon.PROFILE::equals).isPresent()))return true;
        return gun!=null&&!LegacyFirearmProfiles.firing(stack,gun.getGunId(stack).toString()).ready();
    }
    public static void fire(com.tacz.guns.api.event.common.GunFireEvent event){if(blocked(event.getGunItemStack()))event.setCanceled(true);}
    private AssemblyFireGate(){}
}
