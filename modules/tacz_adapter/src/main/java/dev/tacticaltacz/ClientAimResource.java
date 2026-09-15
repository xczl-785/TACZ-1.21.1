package dev.tacticaltacz;
@net.neoforged.fml.common.EventBusSubscriber(modid="tacz",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientAimResource {
 @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
 var p=net.minecraft.client.Minecraft.getInstance().player;
 if(p!=null&&GunAdoption.contains(p.getMainHandItem())&&dev.tacticalinventory.api.ClientCharacterDisplay.resources().map(r->!r.canAim()).orElse(false))com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(p).aim(false);
 }
}
