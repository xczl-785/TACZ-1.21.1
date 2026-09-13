package com.tacz.guns.resource.pojo.data.block;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

public record TabConfig(ResourceLocation id, String name, ItemStack icon) {
    public static final ResourceLocation TAB_AMMO = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "ammo");

    public static final ResourceLocation TAB_PISTOL = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "pistol");
    public static final ResourceLocation TAB_SNIPER = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "sniper");
    public static final ResourceLocation TAB_RIFLE = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "rifle");
    public static final ResourceLocation TAB_SHOTGUN = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "shotgun");
    public static final ResourceLocation TAB_SMG = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "smg");
    public static final ResourceLocation TAB_RPG = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "rpg");
    public static final ResourceLocation TAB_MG = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "mg");

    public static final ResourceLocation TAB_SCOPE = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "scope");
    public static final ResourceLocation TAB_MUZZLE = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "muzzle");
    public static final ResourceLocation TAB_STOCK = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "stock");
    public static final ResourceLocation TAB_GRIP = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "grip");
    public static final ResourceLocation TAB_EXTENDED_MAG = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "extended_mag");
    public static final ResourceLocation TAB_LASER = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "laser");

    public static final ResourceLocation TAB_MISC = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "misc");
    public static final ResourceLocation TAB_EMPTY = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "empty");

    public static final List<TabConfig> DEFAULT_TABS = List.of();

    public static class Deserializer implements JsonDeserializer<TabConfig> {
        @Override
        public TabConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("TabConfig must be a JSON object");
            }
            JsonObject object = json.getAsJsonObject();
            if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
                throw new JsonParseException("TabConfig must have an id");
            }
            ResourceLocation id = context.deserialize(object.get("id"), ResourceLocation.class);
            ItemStack icon = ItemStack.CODEC.parse(JsonOps.INSTANCE, GsonHelper.getAsJsonObject(object, "icon")).getOrThrow();
            String name = GsonHelper.getAsString(object, "name", "tacz.type.unknown.name");
            return new TabConfig(id, name, icon);
        }
    }

    @NotNull
    public Component getName() {
        return Component.translatable(name==null ? "tacz.type.unknown.name" : name);
    }
}