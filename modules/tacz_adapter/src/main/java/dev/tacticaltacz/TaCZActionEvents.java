package dev.tacticaltacz;

import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import dev.firearms.api.FirearmActionEvent;
import dev.firearms.runtime.ActionSession;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

/** Observes accepted TaCZ lifecycle events without changing TaCZ state or animation ownership. */
@EventBusSubscriber(modid = "tacz")
public final class TaCZActionEvents {
    public enum Source { DRAW, SHOT, RELOAD_STARTED }

    public static ActionSession.Event action(Source source) {
        return switch (source) {
            case DRAW -> ActionSession.Event.DRAW_STARTED;
            case SHOT -> ActionSession.Event.SHOT;
            case RELOAD_STARTED -> ActionSession.Event.RELOAD_STARTED;
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void draw(GunDrawEvent event) {
        if (!event.getCurrentGunItem().isEmpty())
            publish(event.getEntity(), event.getCurrentGunItem(), Source.DRAW, event.getLogicalSide());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void shot(GunFireEvent event) {
        publish(event.getShooter(), event.getGunItemStack(), Source.SHOT, event.getLogicalSide());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void reload(GunReloadEvent event) {
        publish(event.getEntity(), event.getGunItemStack(), Source.RELOAD_STARTED, event.getLogicalSide());
    }

    private static void publish(LivingEntity actor, ItemStack firearm, Source source, LogicalSide side) {
        NeoForge.EVENT_BUS.post(new FirearmActionEvent(actor, firearm, action(source), side,
                Math.max(0L, System.nanoTime())));
    }

    private TaCZActionEvents() {}
}
