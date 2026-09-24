package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.GunMod;
import net.minecraft.resources.ResourceLocation;

public class BlockIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("display")
    private ResourceLocation display;

    @SerializedName("data")
    private ResourceLocation data;

    @SerializedName("id")
    private ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table");

    @SerializedName("stack_size")
    private int stackSize;

    public String getName() {
        return name;
    }

    public ResourceLocation getId() {
        return id;
    }

    public ResourceLocation getDisplay() {
        return display;
    }

    public int getStackSize() {
        return stackSize;
    }

    public ResourceLocation getData() {
        return data;
    }

}
