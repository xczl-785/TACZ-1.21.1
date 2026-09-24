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
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class RpmModifier implements IAttachmentModifier<Modifier, Integer> {
    public static final String ID = GunProperties.ROUNDS_PER_MINUTE.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Modifier> readJson(String json) {
        RpmModifier.Data data = CommonAssetsManager.GSON.fromJson(json, RpmModifier.Data.class);
        return new JsonProperty<>(data.getRpm());
    }

    @Override
    public CacheValue<Integer> initCache(ItemStack gunItem, GunData gunData) {
        IGun iGun = Objects.requireNonNull(IGun.getIGunOrNull(gunItem));
        FireMode fireMode = iGun.getFireMode(gunItem);
        int roundsPerMinute = gunData.getRoundsPerMinute(fireMode);
        return new CacheValue<>(roundsPerMinute);
    }

    @Override
    public void eval(List<Modifier> modifiers, CacheValue<Integer> cache) {
        double eval = AttachmentPropertyManager.eval(modifiers, cache.getValue());
        cache.setValue((int) Math.round(eval));
    }

    public static class Data {
        @SerializedName("rpm")
        @Nullable
        private Modifier rpm = null;

        @Nullable
        public Modifier getRpm() {
            return rpm;
        }
    }
}
