package com.tacz.guns.resource.modifier.custom;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class InaccuracyModifier implements IAttachmentModifier<Map<InaccuracyType, Modifier>, Map<InaccuracyType, Float>> {
    public static final String ID = GunProperties.INACCURACY.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getOptionalFields() {
        return "inaccuracy_addend";
    }

    @Override
    @SuppressWarnings("deprecation")
    public JsonProperty<Map<InaccuracyType, Modifier>> readJson(String json) {
        Data data = CommonAssetsManager.GSON.fromJson(json, Data.class);
        Modifier inaccuracy = data.getInaccuracy();
        Modifier aimInaccuracy = data.getAimInaccuracy();
        Modifier sneakInaccuracy = data.getSneakInaccuracy();
        Modifier lieInaccuracy = data.getLieInaccuracy();

        // 兼容旧版本
        if (inaccuracy == null) {
            float inaccuracyAddendTime = data.getInaccuracyAddendTime();
            inaccuracy = new Modifier();
            inaccuracy.setAddend(inaccuracyAddendTime);
        }
        // inaccuracy会影响除了aim(开镜)和sneak(战术姿态)之外的所有类型
        Map<InaccuracyType, Modifier> jsonProperties = Maps.newHashMap();
        for (InaccuracyType type : InaccuracyType.values()) {
            switch (type) {
                case AIM -> {
                    if (aimInaccuracy != null) jsonProperties.put(type, aimInaccuracy);
                }
                case SNEAK -> {
                    if (sneakInaccuracy != null) jsonProperties.put(type, sneakInaccuracy);
                }
                case LIE -> {
                    if (lieInaccuracy != null) jsonProperties.put(type, lieInaccuracy);
                }
                default -> jsonProperties.put(type, inaccuracy);
            }
        }
        return new JsonProperty<>(jsonProperties);
    }

    @Override
    public CacheValue<Map<InaccuracyType, Float>> initCache(ItemStack gunItem, GunData gunData) {
        Map<InaccuracyType, Float> tmp = Maps.newHashMap();
        IGun iGun = Objects.requireNonNull(IGun.getIGunOrNull(gunItem));
        FireMode fireMode = iGun.getFireMode(gunItem);
        gunData.getInaccuracy().forEach((type, value) -> {
            float inaccuracyAddend = 0;
            GunFireModeAdjustData fireModeAdjustData = gunData.getFireModeAdjustData(fireMode);
            if (fireModeAdjustData != null) {
                if (type == InaccuracyType.AIM) {
                    inaccuracyAddend = fireModeAdjustData.getAimInaccuracy();
                } else {
                    inaccuracyAddend = fireModeAdjustData.getOtherInaccuracy();
                }
            }
            float inaccuracy = gunData.getInaccuracy(type, inaccuracyAddend);
            tmp.put(type, inaccuracy);
        });
        return new CacheValue<>(tmp);
    }

    @Override
    public void eval(List<Map<InaccuracyType, Modifier>> modifiedValues, CacheValue<Map<InaccuracyType, Float>> cache) {
        Map<InaccuracyType, Float> result = Maps.newHashMap();
        Map<InaccuracyType, List<Modifier>> tmpModified = Maps.newHashMap();
        // 先遍历，把配件的数据集中在一起
        for (InaccuracyType type : InaccuracyType.values()) {
            List<Modifier> tmp = Lists.newArrayList();
            for (Map<InaccuracyType, Modifier> value : modifiedValues) {
                if (value.get(type) == null) {
                    continue;
                }
                tmp.add(value.get(type));
            }
            tmpModified.put(type, tmp);
        }
        // 一次性把配件的数据计算完
        cache.getValue().forEach((type, value) -> {
            double eval = AttachmentPropertyManager.eval(tmpModified.get(type), cache.getValue().get(type));
            result.put(type, (float) eval);
        });
        // 写入缓存
        cache.setValue(result);
    }

    public static class Data {
        @Nullable
        @SerializedName("inaccuracy")
        private Modifier inaccuracy;

        @Nullable
        @SerializedName("aim_inaccuracy")
        private Modifier aimInaccuracy;

        @Nullable
        @SerializedName("sneak_inaccuracy")
        private Modifier sneakInaccuracy;

        @Nullable
        @SerializedName("lie_inaccuracy")
        private Modifier lieInaccuracy;

        @SerializedName("inaccuracy_addend")
        @Deprecated
        private float adsAddendTime = 0;

        @Nullable
        public Modifier getInaccuracy() {
            return inaccuracy;
        }

        @Nullable
        public Modifier getAimInaccuracy() {
            return aimInaccuracy;
        }

        @Nullable
        public Modifier getSneakInaccuracy() {
            return sneakInaccuracy;
        }

        @Nullable
        public Modifier getLieInaccuracy() {
            return lieInaccuracy;
        }

        @Deprecated
        public float getInaccuracyAddendTime() {
            return adsAddendTime;
        }
    }
}
