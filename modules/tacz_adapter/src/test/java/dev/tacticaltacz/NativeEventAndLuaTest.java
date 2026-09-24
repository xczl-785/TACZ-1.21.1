package dev.tacticaltacz;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.LogicalSide;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeEventAndLuaTest {
    @Test
    void nativeFireAndReloadEventsRemainCancellableWithoutScriptingMods() {
        var bus = BusBuilder.builder().build();
        var ordinaryListener = new AtomicInteger();
        var cancelledListener = new AtomicInteger();
        bus.addListener(EventPriority.HIGH, GunFireEvent.class, event -> event.setCanceled(true));
        bus.addListener(GunFireEvent.class, event -> ordinaryListener.incrementAndGet());
        bus.addListener(true, GunFireEvent.class, event -> cancelledListener.incrementAndGet());
        var fire = new GunFireEvent(null, null, LogicalSide.SERVER);
        assertFalse(fire.isCanceled());
        assertSame(fire, bus.post(fire));
        assertTrue(fire.isCanceled());
        assertEquals(0, ordinaryListener.get());
        assertEquals(1, cancelledListener.get());
        bus.addListener(GunReloadEvent.class, event -> event.setCanceled(true));
        var reload = new GunReloadEvent(null, null, LogicalSide.SERVER);
        assertTrue(bus.post(reload).isCanceled());
    }

    @Test
    void attachmentExpressionsStillRunThroughLuaJ() {
        assertEquals(17.0, AttachmentPropertyManager.functionEval(5.0, 7.0, "y = x * 2 + r"));
        assertEquals(9.0, AttachmentPropertyManager.functionEval(2.0, 3.0, "y = x * 3 + r"));
    }
}
