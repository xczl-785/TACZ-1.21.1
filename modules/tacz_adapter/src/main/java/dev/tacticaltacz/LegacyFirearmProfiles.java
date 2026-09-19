package dev.tacticaltacz;

import dev.firearms.profile.FirearmProfiles;
import dev.weaponruntime.WeaponRuntime;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Reads the retired TaCZ profile reference once, then promotes it to the public firearms owner. */
public final class LegacyFirearmProfiles {
    public static Optional<String> profileId(ItemStack firearm) {
        var current = FirearmProfiles.profileId(firearm);
        if (current.isPresent()) return current;
        var legacy = firearm.get(WeaponRuntime.PROFILE.get());
        if (legacy == null || legacy.isBlank()) return Optional.empty();
        FirearmProfiles.assign(firearm, legacy);
        return Optional.of(legacy);
    }

    public static FirearmProfiles.Readiness firing(ItemStack firearm, String platformWeaponId) {
        profileId(firearm);
        return FirearmProfiles.firing(firearm, platformWeaponId);
    }

    private LegacyFirearmProfiles() {}
}
