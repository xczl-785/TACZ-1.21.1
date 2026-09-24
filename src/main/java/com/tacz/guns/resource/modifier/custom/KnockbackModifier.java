package com.tacz.guns.resource.modifier.custom;

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
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class KnockbackModifier implements IAttachmentModifier<Modifier, Float> {
    public static final String ID = GunProperties.KNOCKBACK.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Modifier> readJson(String json) {
        Data data = CommonAssetsManager.GSON.fromJson(json, Data.class);
        return new JsonProperty<>(data.getKnockback());
    }

    @Override
    public CacheValue<Float> initCache(ItemStack gunItem, GunData gunData) {
        // 必要数据获取
        IGun iGun = Objects.requireNonNull(IGun.getIGunOrNull(gunItem));
        FireMode fireMode = iGun.getFireMode(gunItem);
        BulletData bulletData = gunData.getBulletData();
        GunFireModeAdjustData fireModeAdjustData = gunData.getFireModeAdjustData(fireMode);

        // 开火模式调整
        // 最终的 base
        float finalBase = bulletData.getKnockback();
        finalBase = fireModeAdjustData != null ? finalBase + fireModeAdjustData.getKnockback() : finalBase;
        return new CacheValue<>(finalBase);
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<Float> cache) {
        double eval = AttachmentPropertyManager.eval(modifiers, cache.getValue());
        cache.setValue((float) eval);
    }

    public static class Data {
        @SerializedName("knockback")
        @Nullable
        private Modifier knockback = null;

        @Nullable
        public Modifier getKnockback() {
            return knockback;
        }
    }
}
