package dev.tacticaltacz.assembled;

@net.neoforged.fml.common.EventBusSubscriber(modid="tacz",value=net.neoforged.api.distmarker.Dist.CLIENT,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class AssemblyGunClientRegistration {
    @net.neoforged.bus.api.SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event){event.register(AssemblyPresentationClient.NEXT);}
    @net.neoforged.bus.api.SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){
        AssembledWeapons.all().forEach(weapon->com.tacz.guns.api.client.other.GunModelTypeManager.registerModelType(weapon.modelType,(pojo,version)->new AssemblyGunModel(pojo,version,weapon)));
    }
}
