package com.tacz.guns;

import com.tacz.guns.config.ClientConfig;
import com.tacz.guns.config.CommonConfig;
import com.tacz.guns.config.ServerConfig;
import com.tacz.guns.init.*;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GunMod.MOD_ID)
public class GunMod {
    public static final String MOD_ID = "tacz";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static net.neoforged.fml.ModContainer container;

    public GunMod(IEventBus bus, net.neoforged.fml.ModContainer container) {
        GunMod.container = container;
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.spec);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.spec);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.spec);


        CapabilityRegistry.ATTACHMENT_TYPES.register(bus);
        ModCreativeTabs.TABS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITY_TYPES.register(bus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModParticles.PARTICLE_TYPES.register(bus);
        ModAttributes.ATTRIBUTES.register(bus);

        AttachmentPropertyManager.registerModifier();
        com.tacz.guns.api.extension.GunPlatformExtensions.register(bus);
    }

}
