package dev.tacticaltacz;
import com.tacz.guns.api.item.IGun;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
/** Derived pistol identities for development verification; equipment registration belongs to each assembly. */
public final class PistolAdoption {
    public static final Set<ResourceLocation> IDS=dev.tacticaltacz.assembled.AssembledWeapons.all().stream()
        .filter(weapon->weapon.wearableSlots.contains("tactical_inventory:sidearm"))
        .map(weapon->weapon.GUN).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private PistolAdoption() {}
    public static boolean contains(ItemStack stack){return stack.getItem() instanceof IGun gun&&IDS.contains(gun.getGunId(stack));}
}
