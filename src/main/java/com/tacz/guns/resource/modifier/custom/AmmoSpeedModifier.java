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
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class AmmoSpeedModifier implements IAttachmentModifier<Modifier, Float> {
    public static final String ID = GunProperties.AMMO_SPEED.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Modifier> readJson(String json) {
        AmmoSpeedModifier.Data data = CommonAssetsManager.GSON.fromJson(json, AmmoSpeedModifier.Data.class);
        return new JsonProperty<>(data.getAmmoSpeed());
    }

    @Override
    public CacheValue<Float> initCache(ItemStack gunItem, GunData gunData) {
        IGun iGun = Objects.requireNonNull(IGun.getIGunOrNull(gunItem));
        FireMode fireMode = iGun.getFireMode(gunItem);
        GunFireModeAdjustData fireModeAdjustData = gunData.getFireModeAdjustData(fireMode);
        float speed = gunData.getBulletData().getSpeed();
        if (fireModeAdjustData != null) {
            speed += fireModeAdjustData.getSpeed();
        }
        return new CacheValue<>(speed);
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<Float> cache) {
        double eval = AttachmentPropertyManager.eval(modifiers, cache.getValue());
        cache.setValue((float) eval);
    }

    public static class Data {
        @SerializedName("ammo_speed")
        @Nullable
        private Modifier ammoSpeed = null;

        @Nullable
        public Modifier getAmmoSpeed() {
            return ammoSpeed;
        }
    }
}
