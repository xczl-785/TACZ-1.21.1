package com.tacz.guns.client.gui.components.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class InventoryAttachmentSlot extends Button implements IStackTooltip {
    private final int slotIndex;
    private final Inventory inventory;
    private final java.util.function.Supplier<ItemStack> stack;

    public InventoryAttachmentSlot(int pX, int pY, int slotIndex, Inventory inventory, Button.OnPress onPress) {
        super(pX, pY, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.slotIndex = slotIndex;
        this.inventory = inventory;
        this.stack = () -> inventory.getItem(slotIndex);
    }

    /** Snapshot entry from an external inventory owner; no fake vanilla slot is allocated. */
    public InventoryAttachmentSlot(int x, int y, ItemStack item, Button.OnPress onPress) {
        super(x, y, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.slotIndex = -1;
        this.inventory = null;
        var copy = item.copy();
        this.stack = copy::copy;
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (this.isHoveredOrFocused()) {
            ItemStack item = stack.get();
            consumer.accept(item);
        }
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int pMouseX, int pMouseY, float pPartialTick) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        int x = getX(), y = getY();
        if (isHoveredOrFocused()) {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, width, height, 18, 18);
        } else {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, width - 2, height - 2, 18, 18);
        }
        graphics.renderItem(stack.get(), x + 1, y + 1);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public int getSlotIndex() {
        return slotIndex;
    }
}
