package dev.tacticaltacz.assembled;

import com.google.gson.JsonParser;
import dev.weaponruntime.WeaponCapabilities;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Data-driven registration. Adding content never adds an if/switch for a gun name. */
@EventBusSubscriber(modid="tacz",bus=EventBusSubscriber.Bus.MOD)
public final class AssembledWeapons {
    private static final Map<ResourceLocation,AssembledWeapon> WEAPONS;
    static {
        var index=JsonParser.parseString(AssembledWeapon.resource("data/tactical_tacz_adapter/assembled_weapons.json")).getAsJsonObject();
        if(index.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported weapon index");
        var guns=new LinkedHashMap<ResourceLocation,AssembledWeapon>();var items=new HashSet<String>();var modelTypes=new HashSet<String>();var itemTypes=new HashSet<String>();
        for(var path:index.getAsJsonArray("weapons")){
            var weapon=new AssembledWeapon(path.getAsString());
            if(guns.put(weapon.GUN,weapon)!=null||!modelTypes.add(weapon.modelType)||!itemTypes.add(weapon.itemType))throw new IllegalArgumentException("Duplicate gun/model/item type");
            for(var id:weapon.ITEMS.values())if(!weapon.nativeAttachments.containsKey(id)&&!items.add(id))throw new IllegalArgumentException("Duplicate registered item: "+id);
        }
        WEAPONS=Collections.unmodifiableMap(guns);
    }
    public static Collection<AssembledWeapon> all(){return WEAPONS.values();}
    public static AssembledWeapon byId(ResourceLocation id){return WEAPONS.get(id);}
    public static AssembledWeapon from(ItemStack stack){return stack.getItem() instanceof AssemblyGunItem gun?gun.weapon():null;}
    public static boolean isGun(ItemStack stack){return from(stack)!=null;}
    @SubscribeEvent public static void items(net.neoforged.neoforge.registries.RegisterEvent event){event.register(Registries.ITEM,h->{
        for(var weapon:all()){
            h.register(weapon.GUN,new AssemblyGunItem(weapon));
            com.tacz.guns.api.item.gun.GunItemManager.registerGunItem(weapon.itemType,net.neoforged.neoforge.registries.DeferredItem.<AssemblyGunItem>createItem(weapon.GUN));
            weapon.ITEMS.forEach((definition,id)->{if(!definition.equals(weapon.ROOT)&&!weapon.nativeAttachments.containsKey(id))h.register(ResourceLocation.parse(id),new Item(new Item.Properties().stacksTo(1)));});
        }
    });}
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event){event.enqueueWork(()->{
        for(var weapon:all()){
            WeaponCapabilities.register(weapon.PROFILE,new WeaponCapabilities.Profile(weapon.PROFILE,weapon.CATALOG,weapon.ROOT,weapon.DEFINITIONS,weapon.requiredPaths,weapon::definition));
            var item=BuiltInRegistries.ITEM.get(weapon.GUN);var provider=ResourceLocation.fromNamespaceAndPath(weapon.GUN.getNamespace(),weapon.GUN.getPath()+"_firearm");
            dev.itemfoundation.api.identity.ItemIdentities.server().register(item,provider,s->Set.of(ResourceLocation.parse("item_foundation:type/weapon/firearm")));
            dev.itemfoundation.api.equipment.WearableQualifications.register(item,provider,weapon.wearableSlots,s->weapon.wearableSlots);
        }
    });}
    private AssembledWeapons(){}
}
