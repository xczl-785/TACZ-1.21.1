package com.tacz.guns.resource.modifier.custom;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class DamageModifier implements IAttachmentModifier<Modifier, LinkedList<DistanceDamagePair>> {
    public static final String ID = GunProperties.DAMAGE.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Modifier> readJson(String json) {
        Data data = CommonAssetsManager.GSON.fromJson(json, Data.class);
        return new JsonProperty<>(data.getDamage());
    }

    @Override
    public CacheValue<LinkedList<DistanceDamagePair>> initCache(ItemStack gunItem, GunData gunData) {
        // 必要数据获取
        IGun iGun = Objects.requireNonNull(IGun.getIGunOrNull(gunItem));
        FireMode fireMode = iGun.getFireMode(gunItem);
        BulletData bulletData = gunData.getBulletData();
        GunFireModeAdjustData fireModeAdjustData = gunData.getFireModeAdjustData(fireMode);

        // 获取最原始的数值
        float rawDamage = bulletData.getDamageAmount();
        // 额外伤害
        ExtraDamage extraDamage = bulletData.getExtraDamage();
        // 开火模式调整
        float fireAdjustDamageAmount = fireModeAdjustData != null ? fireModeAdjustData.getDamageAmount() : 0;

        // 开始存入我们的数据
        LinkedList<DistanceDamagePair> cacheValue = Lists.newLinkedList();
        if (extraDamage != null && extraDamage.getDamageAdjust() != null) {
            for (DistanceDamagePair pair : extraDamage.getDamageAdjust()) {
                float finalBaseDamage = pair.getDamage() + fireAdjustDamageAmount;
                cacheValue.add(new DistanceDamagePair(pair.getDistance(), (float) (finalBaseDamage * SyncConfig.DAMAGE_BASE_MULTIPLIER.get())));
            }
        } else {
            float finalBaseDamage = rawDamage + fireAdjustDamageAmount;
            cacheValue.add(new DistanceDamagePair(Integer.MAX_VALUE, (float) (finalBaseDamage * SyncConfig.DAMAGE_BASE_MULTIPLIER.get())));
        }
        return new CacheValue<>(cacheValue);
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<LinkedList<DistanceDamagePair>> cache) {
        LinkedList<DistanceDamagePair> cacheValue = cache.getValue();
        LinkedList<DistanceDamagePair> modifiedValue = new LinkedList<>();
        for (DistanceDamagePair pair : cacheValue) {
            float base = pair.getDamage();
            float eval = (float) AttachmentPropertyManager.eval(modifiers, base);
            modifiedValue.add(new DistanceDamagePair(pair.getDistance(), eval));
        }
        cache.setValue(modifiedValue);
    }

    public static class Data {
        @SerializedName("damage")
        @Nullable
        private Modifier damage = null;

        @Nullable
        public Modifier getDamage() {
            return damage;
        }
    }
}
