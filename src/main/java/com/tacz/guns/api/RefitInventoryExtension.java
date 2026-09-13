package com.tacz.guns.api;

import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Optional inventory owner. The owner handles authenticated requests and atomic exchanges.
 * No handler means the original vanilla-inventory refit behavior. Client methods run only in the screen. */
public final class RefitInventoryExtension {
    public record Choice(String id, ItemStack stack) {
        public Choice { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public interface Handler {
        boolean active(Player player);
        void opened(Player player);
        List<Choice> choices(Player player);
        void install(Player player, String choiceId);
        void unload(Player player, AttachmentType type);
    }
    private static Handler handler;
    private RefitInventoryExtension() {}
    public static void register(Handler value) {
        if (handler != null) throw new IllegalStateException("Refit inventory owner already registered");
        handler = java.util.Objects.requireNonNull(value);
    }
    public static Handler get(Player player) { return handler != null && handler.active(player) ? handler : null; }
}
