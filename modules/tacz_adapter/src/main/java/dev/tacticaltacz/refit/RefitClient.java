package dev.tacticaltacz.refit;

import com.tacz.guns.api.RefitInventoryExtension.Choice;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** UI cache only. Opaque server token and entry ID are intent, never client-owned item authority. */
@net.neoforged.fml.common.EventBusSubscriber(modid="tacz",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class RefitClient {
    private static UUID request=RefitProtocol.EMPTY,token=RefitProtocol.EMPTY;
    private static List<Choice> choices=List.of();
    private static GunRefitScreen owner;
    private static boolean pending;
    private static net.minecraft.world.item.ItemStack soundItem=net.minecraft.world.item.ItemStack.EMPTY;
    private static int idleTicks;
    private static String lastMessage="";
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        if(owner!=null&&Minecraft.getInstance().screen!=owner){owner=null;choices=List.of();token=RefitProtocol.EMPTY;pending=false;return;}
        if(owner!=null&&!pending&&token.equals(RefitProtocol.EMPTY)&&++idleTicks>=20){idleTicks=0;send(0,"",0);}
    }
    private RefitClient(){}
    public static void open(){owner=(GunRefitScreen)Minecraft.getInstance().screen;choices=List.of();token=RefitProtocol.EMPTY;send(0,"",0);}
    public static List<Choice> choices(){return choices;}
    public static void install(String id){if(!pending&&!token.equals(RefitProtocol.EMPTY)){soundItem=choices.stream().filter(c->c.id().equals(id)).map(Choice::stack).findFirst().orElse(net.minecraft.world.item.ItemStack.EMPTY);send(1,id,0);}}
    public static void unload(AttachmentType type){if(!pending&&!token.equals(RefitProtocol.EMPTY)){var player=Minecraft.getInstance().player;var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(player.getMainHandItem());soundItem=gun==null?net.minecraft.world.item.ItemStack.EMPTY:gun.getAttachment(player.registryAccess(),player.getMainHandItem(),type);send(2,"",type.ordinal());}}
    private static void send(int action,String id,int type){request=UUID.randomUUID();pending=true;PacketDistributor.sendToServer(new RefitProtocol.Request(request,token,action,id,type));}
    public static void receive(RefitProtocol.View view){
        var mc=Minecraft.getInstance();
        if(!request.equals(view.requestId())||mc.player==null||mc.screen!=owner)return;
        token=view.token();choices=view.choices();pending=false;
        // This is an authoritative server reply; vanilla slot synchronization is sent before it.
        if(!view.result().isEmpty()&&(!view.result().equals("unavailable")||!lastMessage.equals(view.result())))
            mc.player.displayClientMessage(Component.translatable("message.tactical_tacz_adapter.refit."+view.result()),true);
        lastMessage=view.result();
        if(!soundItem.isEmpty()&&(view.result().equals("installed")||view.result().equals("unloaded")))
            com.tacz.guns.client.sound.SoundPlayManager.playerRefitSound(soundItem,mc.player,view.result().equals("installed")?com.tacz.guns.sound.SoundManager.INSTALL_SOUND:com.tacz.guns.sound.SoundManager.UNINSTALL_SOUND);
        soundItem=net.minecraft.world.item.ItemStack.EMPTY;
        var assembled=dev.tacticaltacz.assembled.AssembledWeapons.from(view.held());
        if(assembled==null||!assembled.nativeRig||view.result().equals("installed")||view.result().equals("unloaded"))AttachmentPropertyManager.postChangeEvent(mc.player,mc.player.getMainHandItem());
        owner.init();
    }
}
