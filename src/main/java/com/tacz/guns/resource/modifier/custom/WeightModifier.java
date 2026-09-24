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

public class WeightModifier implements IAttachmentModifier<Modifier, Float> {
    public static final String ID = GunProperties.WEIGHT.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    @SuppressWarnings("deprecation")
    public JsonProperty<Modifier> readJson(String json) {
        WeightModifier.Data data = CommonAssetsManager.GSON.fromJson(json, WeightModifier.Data.class);
        Modifier weightModifier = data.getWeightModifier();
        // 兼容旧版本写法
        if (weightModifier == null) {
            weightModifier = new Modifier();
            weightModifier.setAddend(data.getWeightAddend());
        }
        return new JsonProperty<>(weightModifier);
    }

    @Override
    public CacheValue<Float> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(gunData.getWeight());
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<Float> cache) {
        double eval = AttachmentPropertyManager.eval(modifiers, cache.getValue());
        cache.setValue((float) eval);
    }

    @Override
    public String getOptionalFields() {
        return "weight";
    }

    public static class Data {
        @Nullable
        @SerializedName("weight_modifier")
        private Modifier weightModifier;

        @SerializedName("weight")
        @Deprecated
        private float weightAddend = 0;

        @Nullable
        public Modifier getWeightModifier() {
            return weightModifier;
        }

        @Deprecated
        public float getWeightAddend() {
            return weightAddend;
        }
    }
}
