package dev.tacticaltacz.assembled;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Optional;

@EventBusSubscriber(modid="tacz",value=Dist.CLIENT)
public final class AssemblyPresentationClient {
    public static final KeyMapping NEXT = new KeyMapping("key.tactical_tacz_adapter.next_sight",org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET,"key.categories.tactical_tacz_adapter");
    public static Optional<AssemblyGunModel> model(ItemStack stack){
        if(!AssembledWeapons.isGun(stack))return Optional.empty();
        return TimelessAPI.getGunDisplay(stack).map(d->d.getGunModel()).filter(AssemblyGunModel.class::isInstance).map(AssemblyGunModel.class::cast);
    }
    public static float zoom(ItemStack stack,float fallback){return model(stack).map(m->m.presentation.aim(stack).map(a->(float)a.zoom()).orElse(1f)).orElse(fallback);}
    public static WeaponHandling.Factors factors(){
        var player=Minecraft.getInstance().player;if(player==null)return WeaponHandling.Factors.IDENTITY;
        var stack=player.getMainHandItem();if(!AssembledWeapons.isGun(stack))return WeaponHandling.Factors.IDENTITY;
        var weapon=AssembledWeapons.from(stack);return weapon.handling.factors(weapon,stack);
    }
    @SubscribeEvent public static void shot(GunFireEvent event){
        var player=Minecraft.getInstance().player;
        if(!event.getLogicalSide().isClient()||player==null||event.getShooter()!=player)return;
        model(player.getMainHandItem()).ifPresent(m->m.presentation.shot(player.getMainHandItem()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();while(NEXT.consumeClick())if(mc.player!=null&&mc.screen==null)model(mc.player.getMainHandItem()).ifPresent(m->{
            String selected=m.presentation.cycle(mc.player.getMainHandItem());
            mc.player.displayClientMessage(Component.translatable(selected.isEmpty()?"tactical_tacz_adapter.sight.unavailable":"tactical_tacz_adapter.sight.changed"),true);
        });
    }
    private AssemblyPresentationClient(){}
}
