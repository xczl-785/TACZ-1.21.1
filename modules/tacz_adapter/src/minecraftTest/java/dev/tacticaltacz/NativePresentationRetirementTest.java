package dev.tacticaltacz;

import com.mojang.brigadier.CommandDispatcher;
import com.tacz.guns.command.RootCommand;
import com.tacz.guns.init.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativePresentationRetirementTest {
    @Test void nativeItemsStopSupplyingCustomTooltipImages() {
        Bootstrap.bootStrap();
        for (var item : new net.minecraft.world.item.Item[]{ModItems.MODERN_KINETIC_GUN.get(), ModItems.ATTACHMENT.get()}) {
            assertTrue(item.getTooltipImage(item.getDefaultInstance()).isEmpty());
        }
    }

    @Test void productionCommandsKeepContentControlsWithoutDebugOrHideTooltip() {
        Bootstrap.bootStrap();
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        RootCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("tacz");
        assertNotNull(root);
        assertNull(root.getChild("debug"));
        assertNull(root.getChild("hide_tooltip_part"));
        for (var name : new String[]{"attachment_lock", "dummy", "config", "reload"}) {
            assertNotNull(root.getChild(name), name);
        }
    }
}
