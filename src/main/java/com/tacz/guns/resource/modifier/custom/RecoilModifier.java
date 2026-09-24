package com.tacz.guns.resource.modifier.custom;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * left 是 Pitch
 * right 是 Yaw
 */
public class RecoilModifier implements IAttachmentModifier<Pair<Modifier, Modifier>, ParameterizedCachePair<Float, Float>> {
    public static final String ID = GunProperties.RECOIL.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getOptionalFields() {
        return "recoil_modifier";
    }

    @Override
    @SuppressWarnings("deprecation")
    public JsonProperty<Pair<Modifier, Modifier>> readJson(String json) {
        RecoilModifier.Data data = CommonAssetsManager.GSON.fromJson(json, RecoilModifier.Data.class);
        NewRecoilData newRecoilData = data.newRecoilData;
        OldRecoilData oldRecoilData = data.oldRecoilData;
        // 兼容旧版本写法
        if (newRecoilData == null && oldRecoilData != null) {
            Modifier pitch = new Modifier();
            Modifier yaw = new Modifier();
            pitch.setPercent(oldRecoilData.getPitch());
            yaw.setPercent(oldRecoilData.getYaw());
            return new JsonProperty<>(Pair.of(pitch, yaw));
        }
        assert newRecoilData != null;
        return new JsonProperty<>(Pair.of(newRecoilData.getPitch(), newRecoilData.getYaw()));
    }

    @Override
    public CacheValue<ParameterizedCachePair<Float, Float>> initCache(ItemStack gunItem, GunData gunData) {
        GunRecoil recoil = gunData.getRecoil();
        if (recoil == null) {
            return new CacheValue<>(ParameterizedCachePair.of(0f, 0f));
        }
        float pitch = getMaxInGunRecoilKeyFrame(recoil.getPitch());
        float yaw = getMaxInGunRecoilKeyFrame(recoil.getYaw());
        return new CacheValue<>(ParameterizedCachePair.of(pitch, yaw));
    }

    @Override
    public void eval(List<Pair<Modifier, Modifier>> modifiedValues, CacheValue<ParameterizedCachePair<Float, Float>> cache) {
        List<Modifier> yaw = Lists.newArrayList();
        List<Modifier> pitch = Lists.newArrayList();
        for (var modifiedValue : modifiedValues) {
            pitch.add(modifiedValue.left());
            yaw.add(modifiedValue.right());
        }
        var newCache = ParameterizedCachePair.of(pitch, yaw, cache.getValue().left().getDefaultValue(),
                cache.getValue().right().getDefaultValue());
        cache.setValue(newCache);
    }

    private static float getMaxInGunRecoilKeyFrame(GunRecoilKeyFrame[] frames) {
        if (frames.length == 0) {
            return 0;
        }
        float[] value = frames[0].getValue();
        float leftValue = Math.abs(value[0]);
        float rightValue = Math.abs(value[1]);
        return Math.max(leftValue, rightValue);
    }

    public static class Data {
        @SerializedName("recoil_modifier")
        @Deprecated
        @Nullable
        private OldRecoilData oldRecoilData = null;

        @SerializedName("recoil")
        @Nullable
        private NewRecoilData newRecoilData = null;
    }

    @Deprecated
    private static class OldRecoilData {
        @SerializedName("pitch")
        private float pitch = 0;

        @SerializedName("yaw")
        private float yaw = 0;

        public float getPitch() {
            return pitch;
        }

        public float getYaw() {
            return yaw;
        }
    }

    private static class NewRecoilData {
        @SerializedName("pitch")
        private Modifier pitch = new Modifier();

        @SerializedName("yaw")
        private Modifier yaw = new Modifier();

        public Modifier getPitch() {
            return pitch;
        }

        public Modifier getYaw() {
            return yaw;
        }
    }
}
