package com.tacz.guns.api.event.common;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.LogicalSide;

/**
 * 生物结束更换枪械弹药时触发的事件。
 */
public class GunFinishReloadEvent extends Event implements ICancellableEvent{
    private final ItemStack gunItemStack;
    private final LogicalSide logicalSide;

    public GunFinishReloadEvent(ItemStack gunItemStack, LogicalSide side) {
        this.gunItemStack = gunItemStack;
        this.logicalSide = side;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public LogicalSide getLogicalSide() {
        return logicalSide;
    }
}
