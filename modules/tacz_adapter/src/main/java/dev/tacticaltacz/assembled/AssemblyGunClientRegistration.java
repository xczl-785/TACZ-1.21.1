package dev.tacticaltacz.assembled;

@net.neoforged.fml.common.EventBusSubscriber(modid="tacz",value=net.neoforged.api.distmarker.Dist.CLIENT,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class AssemblyGunClientRegistration {
    @net.neoforged.bus.api.SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources->NativeAssemblyIcons.clear());
    }
    @net.neoforged.bus.api.SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event){event.register(AssemblyPresentationClient.NEXT);}
    @net.neoforged.bus.api.SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){
        event.enqueueWork(()->dev.firearms.client.workbench.WorkbenchClientProviders.register(new AssemblyGunClientProvider()));
        event.enqueueWork(()->AssembledWeapons.all().stream().filter(w->w.assemblyIcons).forEach(w->dev.itemfoundation.client.api.ItemModelBounds.register(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(w.GUN),NativeAssemblyIcons::bounds)));
        AssembledWeapons.all().stream().filter(weapon->weapon.nativeRig).forEach(weapon->com.tacz.guns.api.client.other.GunModelTypeManager.registerLodModelType(weapon.modelType,(pojo,version)->new NativeAssemblyGunModel(pojo,version,weapon)));
        AssembledWeapons.all().forEach(weapon->com.tacz.guns.api.client.other.GunModelTypeManager.registerModelType(weapon.modelType,(pojo,version)->weapon.nativeRig?new NativeAssemblyGunModel(pojo,version,weapon):new AssemblyGunModel(pojo,version,weapon)));
    }
}
