package dev.weaponassemblyui.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyTextureQualityTest {
    @Test void workbenchUsesCrispMipmappedSampling() {
        assertFalse(AssemblyTextureQuality.BLUR);
        assertTrue(AssemblyTextureQuality.MIPMAP);
        assertEquals(9,AssemblyTextureQuality.mipmapLevels(512,512));
        assertEquals(7,AssemblyTextureQuality.mipmapLevels(512,192));
    }

    @Test void invalidTextureDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->AssemblyTextureQuality.mipmapLevels(0,512));
    }
}
