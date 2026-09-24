package com.tacz.guns.resource.modifier.custom;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

public class EffectiveRangeModifier implements IAttachmentModifier<Modifier, Float> {
    public static final String ID = GunProperties.EFFECTIVE_RANGE.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Modifier> readJson(String json) {
        Data data = CommonAssetsManager.GSON.fromJson(json, Data.class);
        return new JsonProperty<>(data.getEffectiveRange());
    }

    @Override
    public CacheValue<Float> initCache(ItemStack gunItem, GunData gunData) {
        LinkedList<DistanceDamagePair> damageAdjust = null;
        if (gunData.getBulletData().getExtraDamage() != null) {
            damageAdjust = gunData.getBulletData().getExtraDamage().getDamageAdjust();
        }
        float effectiveRange;
        if (damageAdjust != null) {
            effectiveRange = damageAdjust.get(0).getDistance();
        } else {
            effectiveRange = Integer.MAX_VALUE;
        }
        return new CacheValue<>(effectiveRange);
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<Float> cache) {
        double eval = AttachmentPropertyManager.eval(modifiers, cache.getValue());
        cache.setValue((float) eval);
    }

    public static class Data {
        @SerializedName("effective_range")
        @Nullable
        private Modifier effectiveRange = null;

        @Nullable
        public Modifier getEffectiveRange() {
            return effectiveRange;
        }
    }
}
