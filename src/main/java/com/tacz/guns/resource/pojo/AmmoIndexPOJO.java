package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class AmmoIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("display")
    private ResourceLocation display;


    @SerializedName("tooltip")
    @Nullable
    private String tooltip;


    public String getName() {
        return name;
    }

    public ResourceLocation getDisplay() {
        return display;
    }


    @Nullable
    public String getTooltip() {
        return tooltip;
    }

}
