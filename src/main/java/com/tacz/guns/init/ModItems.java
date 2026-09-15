package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GunMod.MOD_ID);

    public static DeferredItem<ModernKineticGunItem> MODERN_KINETIC_GUN = ITEMS.register("modern_kinetic_gun", ModernKineticGunItem::new);

//    public static RegistryObject<ThrowableItem> M67 = ITEMS.register("m67", ThrowableItem::new);

    public static DeferredItem<AttachmentItem> ATTACHMENT = ITEMS.register("attachment", AttachmentItem::new);



    public static DeferredItem<Item> TARGET = ITEMS.register("target", () -> new BlockItem(ModBlocks.TARGET.get(), new Item.Properties()));
    public static DeferredItem<Item> STATUE = ITEMS.register("statue", () -> new BlockItem(ModBlocks.STATUE.get(), new Item.Properties()));
    public static DeferredItem<Item> TARGET_MINECART = ITEMS.register("target_minecart", TargetMinecartItem::new);

    @SubscribeEvent
    public static void onItemRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(BuiltInRegistries.ITEM.key())) {
            GunItemManager.registerGunItem(ModernKineticGunItem.TYPE_NAME, MODERN_KINETIC_GUN);
        }
    }
}