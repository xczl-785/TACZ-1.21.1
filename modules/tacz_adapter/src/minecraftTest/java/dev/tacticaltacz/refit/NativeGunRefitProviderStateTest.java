package dev.tacticaltacz.refit;

import java.util.List;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Registry-backed: what the native family refuses to serve, and how it fails closed. */
class NativeGunRefitProviderStateTest {
    @BeforeAll static void boot() { Bootstrap.bootStrap(); }

    @Test void plainItemsAreNeverServedAndUnplannedRequestsFailClosed() {
        var provider = new NativeGunRefitProvider();
        var stick = new ItemStack(Items.STICK);
        assertFalse(provider.handles(stick));
        assertTrue(provider.plan(null, stick, ItemStack.EMPTY, List.of("SCOPE")).isEmpty());
        assertTrue(provider.plan(null, stick, new ItemStack(Items.IRON_SWORD), List.of("SCOPE")).isEmpty());
    }

    @Test void anUnclaimedTargetNeverInstallsOrRemoves() {
        var provider = new NativeGunRefitProvider();
        var stick = new ItemStack(Items.STICK);
        assertTrue(provider.plan(null, stick, ItemStack.EMPTY, List.of()).isEmpty());
        assertTrue(provider.plan(null, stick, ItemStack.EMPTY, List.of("SCOPE", "EXTRA")).isEmpty());
    }
}
