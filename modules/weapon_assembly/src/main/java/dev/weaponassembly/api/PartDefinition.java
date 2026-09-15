package dev.weaponassembly.api;

import java.util.*;

/** Own-part values only: a root must never include its installed factory parts again. */
public record PartDefinition(String id, List<Slot> slots, Set<String> conflictingParts,
                             Set<SlotKey> blockedSlots, Modifiers modifiers, Optional<WeaponBase> weapon) {
    public PartDefinition {
        requireId(id); slots = List.copyOf(slots); conflictingParts = Set.copyOf(conflictingParts);
        blockedSlots = Set.copyOf(blockedSlots); Objects.requireNonNull(modifiers); Objects.requireNonNull(weapon);
        var names = new HashSet<String>();
        for (var slot : slots) if (!names.add(slot.id())) throw new IllegalArgumentException("Duplicate slot: " + slot.id());
        conflictingParts.forEach(PartDefinition::requireId);
    }
    public Optional<Slot> slot(String id) { return slots.stream().filter(s -> s.id().equals(id)).findFirst(); }
    public record Slot(String id, boolean required, Set<String> allowedParts) {
        public Slot { requireId(id); allowedParts = Set.copyOf(allowedParts); allowedParts.forEach(PartDefinition::requireId); }
    }
    /** Qualified by definition, never a bare slot name shared by unrelated parents. */
    public record SlotKey(String ownerDefinition, String slot) {
        public SlotKey { requireId(ownerDefinition); requireId(slot); }
    }
    public record WeaponBase(double recoilVertical, double recoilHorizontal,
                             OptionalDouble centerOfImpact, OptionalDouble sightingRange) {
        public WeaponBase {
            nonnegative(recoilVertical); nonnegative(recoilHorizontal);
            optionalNonnegative(centerOfImpact); optionalNonnegative(sightingRange);
        }
    }
    public record Modifiers(double weightKg, double ergonomics, double recoilFraction,
                            double accuracyPercent, double velocityPercent, OptionalDouble centerOfImpact,
                            OptionalDouble sightingRange, double heatFactor, double coolingFactor,
                            double durabilityBurnFactor) {
        public static final Modifiers ZERO = new Modifiers(0, 0, 0, 0, 0,
                OptionalDouble.empty(), OptionalDouble.empty(), 1, 1, 1);
        public Modifiers {
            nonnegative(weightKg); finite(ergonomics); finite(recoilFraction);
            finite(accuracyPercent); finite(velocityPercent); optionalNonnegative(centerOfImpact);
            optionalNonnegative(sightingRange); nonnegative(heatFactor); nonnegative(coolingFactor);
            nonnegative(durabilityBurnFactor);
        }
    }
    static void requireId(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || !value.matches("[a-zA-Z0-9_.:/-]+"))
            throw new IllegalArgumentException("Invalid identifier: " + value);
    }
    static void finite(double value) { if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite statistic"); }
    static void nonnegative(double value) { finite(value); if (value < 0) throw new IllegalArgumentException("Negative statistic"); }
    static void optionalNonnegative(OptionalDouble value) { Objects.requireNonNull(value); value.ifPresent(PartDefinition::nonnegative); }
}
