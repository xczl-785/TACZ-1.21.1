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

public class AdsModifier implements IAttachmentModifier<Modifier, Float> {
    public static final String ID = GunProperties.ADS_TIME.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getOptionalFields() {
        return "ads_addend";
    }

    @Override
    @SuppressWarnings("deprecation")
    public JsonProperty<Modifier> readJson(String json) {
        Data data = CommonAssetsManager.GSON.fromJson(json, Data.class);
        Modifier ads = data.getAds();
        // 兼容旧版本写法
        if (ads == null) {
            ads = new Modifier();
            ads.setAddend(data.getAdsAddendTime());
        }
        return new JsonProperty<>(ads);
    }

    @Override
    public CacheValue<Float> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(gunData.getAimTime());
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<Float> cache) {
        double eval = AttachmentPropertyManager.eval(modifiers, cache.getValue());
        cache.setValue((float) eval);
    }

    public static class Data {
        @Nullable
        @SerializedName("ads")
        private Modifier ads;

        @SerializedName("ads_addend")
        @Deprecated
        private float adsAddendTime = 0;

        @Nullable
        public Modifier getAds() {
            return ads;
        }

        @Deprecated
        public float getAdsAddendTime() {
            return adsAddendTime;
        }
    }
}
