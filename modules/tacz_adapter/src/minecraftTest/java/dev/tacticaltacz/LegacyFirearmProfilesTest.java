package dev.tacticaltacz.assembled;

import dev.firearms.profile.FirearmComponents;
import dev.tacticaltacz.LegacyFirearmProfiles;
import dev.weaponruntime.WeaponRuntime;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyFirearmProfilesTest {
    @BeforeAll
    static void boot() throws Exception {
        NativeAssemblyStateTest.boot();
    }

    @Test
    void oldProfileReferenceIsReadAndMigratedWithoutChangingItsValue() {
        var weapon = AssembledWeapons.byId(ResourceLocation.parse("tacz_fork_tarkov:m4a1"));
        if (dev.firearms.profile.FirearmProfiles.profile(weapon.preset()).isEmpty())
            dev.firearms.profile.FirearmProfiles.register(weapon.PROFILE,
                    new dev.firearms.profile.FirearmProfiles.Profile(weapon.PROFILE, weapon.CATALOG,
                            weapon.ROOT, weapon.DEFINITIONS, weapon.requiredPaths, weapon::definition));
        var old = weapon.preset();
        old.remove(FirearmComponents.PROFILE.get());
        old.set(WeaponRuntime.PROFILE.get(), weapon.PROFILE);

        assertFalse(old.has(FirearmComponents.PROFILE.get()));
        assertTrue(LegacyFirearmProfiles.firing(old, weapon.PROFILE).ready());
        assertEquals(weapon.PROFILE, old.get(FirearmComponents.PROFILE.get()));
        assertEquals(weapon.PROFILE, old.get(WeaponRuntime.PROFILE.get()));

        var unknown = old.copy();
        unknown.remove(FirearmComponents.PROFILE.get());
        unknown.set(WeaponRuntime.PROFILE.get(), "missing:profile");
        assertEquals("unknown_profile", LegacyFirearmProfiles.firing(unknown, weapon.PROFILE).reason());
    }
}
