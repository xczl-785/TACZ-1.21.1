package dev.tacticaltacz;
import com.tacz.guns.api.item.IGun;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
/** Public identities for retained firearms; pistol equipment qualification remains a separate boundary. */
public final class PistolAdoption {
    public static final Set<ResourceLocation> IDS=Set.of(id("glock_17"));
    private static ResourceLocation id(String value){return ResourceLocation.fromNamespaceAndPath("tacz",value);}
    private PistolAdoption() {}
    public static boolean contains(ItemStack stack){return stack.getItem() instanceof IGun gun&&IDS.contains(gun.getGunId(stack));}
    public static void register(){
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id("modern_kinetic_gun"));
        var provider=ResourceLocation.parse("tactical_tacz_adapter:adopted_pistols");
        dev.itemfoundation.api.identity.ItemIdentities.server().register(item,provider,stack->contains(stack)
            ?Set.of(ResourceLocation.parse("item_foundation:type/weapon/firearm/pistol"))
            :GunAdoption.contains(stack)?Set.of(ResourceLocation.parse("item_foundation:type/weapon/firearm")):Set.of());
        dev.itemfoundation.api.equipment.WearableQualifications.register(item,provider,Set.of("tactical_inventory:sidearm","tactical_inventory:primary_weapon_1","tactical_inventory:primary_weapon_2"),stack->contains(stack)
            ?Set.of("tactical_inventory:sidearm"):Set.of("tactical_inventory:primary_weapon_1","tactical_inventory:primary_weapon_2"));
    }
}
