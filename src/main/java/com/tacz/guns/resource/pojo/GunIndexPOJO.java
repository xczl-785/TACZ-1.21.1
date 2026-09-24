package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;

public class GunIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("display")
    private ResourceLocation display;

    @SerializedName("data")
    private ResourceLocation data;

    @SerializedName("type")
    private String type;

    @SerializedName("item_type")
    private String itemType = "modern_kinetic";

    @SerializedName("sort")
    private int sort;

    public String getName() {
        return name;
    }

    public ResourceLocation getDisplay() {
        return display;
    }

    public ResourceLocation getData() {
        return data;
    }

    public String getType() {
        return type;
    }

    public String getItemType() {
        return itemType;
    }

    public int getSort() {
        return sort;
    }
}
