package dev.tacticaltacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Slim4BehaviorTest {
    @Test void legacyIgnitionFieldsCannotBecomeBulletOrAttachmentPropertiesButExplosionSurvives() {
        var gson = CommonAssetsManager.GSON;
        var data = gson.fromJson("""
                {"damage":12,"pierce":3,"ignite":{"entity":true,"block":true},"ignite_entity_time":200,
                 "explosion":{"explode":true,"radius":2,"damage":20,"delay":1}}
                """, BulletData.class);
        assertEquals(12, data.getDamageAmount());
        assertEquals(3, data.getPierce());
        assertTrue(data.getExplosionData().isExplode());
        assertEquals(20, data.getExplosionData().getDamage());
        var serialized = gson.toJsonTree(data).getAsJsonObject();
        assertFalse(serialized.has("ignite"));
        assertFalse(serialized.has("ignite_entity_time"));
        AttachmentPropertyManager.registerModifier();
        assertFalse(AttachmentPropertyManager.getModifiers().containsKey("ignite"));
        assertFalse(GunProperties.all().containsKey("ignite"));
        assertTrue(AttachmentPropertyManager.getModifiers().containsKey("explosion"));
    }

    @Test void attachmentValuesStillParseAndEvaluateWithoutGeneratingTooltipText() {
        var modifier = new AdsModifier();
        var value = modifier.readJson("{\"ads\":{\"addend\":0.25}}").getValue();
        assertNotNull(value);
        var cache = new CacheValue<>(0.5f);
        modifier.eval(List.of(value), cache);
        assertEquals(0.75f, cache.getValue(), 0.00001f);
        var legacy = modifier.readJson("{\"ads_addend\":0.1}").getValue();
        modifier.eval(List.of(legacy), cache);
        assertEquals(0.85f, cache.getValue(), 0.00001f);
    }
}
