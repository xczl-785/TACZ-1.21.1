package dev.tacticaltacz;

import com.google.gson.JsonParser;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDisplayMetadataTest {
    @Test
    void m870DisplayLoadsWithUnconsumedSourceRumbleMetadata() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(
                "weapon-content/resources/assets/tacz_fork_tarkov/display/guns/m870.json"))).getAsJsonObject();
        assertTrue(json.has("controllable"), "Keep author-generated source metadata intact");
        var display = ClientAssetsManager.GSON.fromJson(json, GunDisplay.class);
        assertEquals("tacz_fork_tarkov:gun/m870", display.getModelLocation().toString());
        assertEquals("tacz:m870", display.getAnimationLocation().toString());
        assertEquals("tacz:m870_state_machine", display.getStateMachineLocation().toString());
        assertNotNull(display.getTransform());
        json.remove("controllable");
        var withoutRumble = ClientAssetsManager.GSON.fromJson(json, GunDisplay.class);
        assertEquals(withoutRumble.getModelLocation(), display.getModelLocation());
        assertEquals(withoutRumble.getModelTexture(), display.getModelTexture());
        assertEquals(withoutRumble.getAnimationLocation(), display.getAnimationLocation());
        assertEquals(withoutRumble.getStateMachineParam(), display.getStateMachineParam());
        assertEquals(withoutRumble.getSounds(), display.getSounds());
    }
}
