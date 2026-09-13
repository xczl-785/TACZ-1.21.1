package com.tacz.guns.client.event;

import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class PreventsHotbarEvent {
    @SubscribeEvent
    public static void onRenderHotbarEvent(RenderGuiEvent.Pre event) {
        // todo 需要测试行为
        Screen screen = Minecraft.getInstance().screen;
        // 枪械改装界面关闭背景
        if (screen instanceof GunRefitScreen) {
            event.setCanceled(true);
        }
    }
}
