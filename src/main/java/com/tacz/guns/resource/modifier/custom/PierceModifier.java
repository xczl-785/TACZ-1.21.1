package com.tacz.guns.resource.modifier.custom;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

public class PierceModifier implements IAttachmentModifier<Modifier, Integer> {
    public static final String ID = GunProperties.PIERCE.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Modifier> readJson(String json) {
        PierceModifier.Data data = CommonAssetsManager.GSON.fromJson(json, PierceModifier.Data.class);
        return new JsonProperty<>(data.getPierce());
    }

    @Override
    public CacheValue<Integer> initCache(ItemStack gunItem, GunData gunData) {
        int pierce = gunData.getBulletData().getPierce();
        return new CacheValue<>(pierce);
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<Integer> cache) {
        double eval = AttachmentPropertyManager.eval(modifiers, cache.getValue());
        cache.setValue((int) Math.round(eval));
    }

    public static class Data {
        @SerializedName("pierce")
        @Nullable
        private Modifier pierce = null;

        @Nullable
        public Modifier getPierce() {
            return pierce;
        }
    }
}
