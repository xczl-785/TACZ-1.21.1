package dev.weaponassembly.api;

import java.util.*;

/** EFTForge-derived formula semantics, operating on normalized own-part values. */
public final class WeaponStats {
    public record Context(int strengthLevel, double equipmentErgoModifier) {
        public Context {
            if (strengthLevel < 0 || strengthLevel > 51 || !Double.isFinite(equipmentErgoModifier)
                    || equipmentErgoModifier < -1 || equipmentErgoModifier > 1)
                throw new IllegalArgumentException("Invalid character context");
        }
    }
    public record Values(double weightKg, double ergonomics, double recoilVertical, double recoilHorizontal,
                         OptionalDouble accuracyMoa, OptionalDouble sightingRange, double velocityPercent,
                         double heatFactor, double coolingFactor, double durabilityBurnFactor,
                         double evoErgoDelta, boolean overswing, double armStamina) {}
    private final AssemblyEngine engine;
    public WeaponStats(AssemblyEngine engine) { this.engine = Objects.requireNonNull(engine); }
    public Values calculate(AssemblyNode root, Context context) {
        Objects.requireNonNull(context);
        var validation = engine.validate(root);
        if (!validation.valid()) throw new IllegalArgumentException("Invalid assembly: " + validation.errors());
        var base = engine.catalog().require(root.definitionId()).weapon()
                .orElseThrow(() -> new IllegalArgumentException("Root is not a weapon"));
        double weight = 0, ergo = 0, recoil = 0, accuracy = 0, velocity = 0, heat = 1, cooling = 1, burn = 1;
        OptionalDouble coi = base.centerOfImpact(), range = base.sightingRange();
        boolean barrelSeen = false;
        for (var entry : AssemblyEngine.scan(root, new ArrayList<>())) {
            var mods = engine.catalog().require(entry.node().definitionId()).modifiers();
            weight += mods.weightKg(); ergo += mods.ergonomics(); recoil += mods.recoilFraction();
            velocity += mods.velocityPercent(); heat *= mods.heatFactor(); cooling *= mods.coolingFactor(); burn *= mods.durabilityBurnFactor();
            if (!entry.path().isEmpty() && mods.centerOfImpact().isPresent()) {
                if (barrelSeen) throw new IllegalArgumentException("Ambiguous barrel COI overrides");
                barrelSeen = true; coi = mods.centerOfImpact();
            } else accuracy += mods.accuracyPercent();
            if (mods.sightingRange().isPresent() && (range.isEmpty() || mods.sightingRange().getAsDouble() > range.getAsDouble()))
                range = mods.sightingRange();
        }
        // Invalid physical combinations fail explicitly rather than silently clamping source data.
        if (recoil < -1 || accuracy > 100 || velocity < -100) throw new IllegalArgumentException("Negative derived weapon statistic");
        double e = ergo * (1 + context.equipmentErgoModifier());
        double balanceWeight = .0007556 * e * e + .02736 * e + 2.9159;
        double eed = -15 * (weight - balanceWeight);
        double stamina = ((85.5 / (weight + .65)) + 9.15 + .06477 * ergo * (1 + context.equipmentErgoModifier() / 2))
                / 1.04 * (1 + context.strengthLevel() * .004);
        double rv = base.recoilVertical() * (1 + recoil), rh = base.recoilHorizontal() * (1 + recoil);
        OptionalDouble moa = coi.isPresent() ? OptionalDouble.of(34.36 * coi.getAsDouble() * (1 - accuracy / 100)) : OptionalDouble.empty();
        for (double value : new double[]{weight, ergo, velocity, heat, cooling, burn, eed, stamina, rv, rh}) PartDefinition.finite(value);
        moa.ifPresent(PartDefinition::finite);
        if (stamina < 0) throw new IllegalArgumentException("Negative derived stamina");
        return new Values(weight, ergo, rv, rh, moa, range, velocity, heat, cooling, burn, eed, weight > balanceWeight, stamina);
    }
}
