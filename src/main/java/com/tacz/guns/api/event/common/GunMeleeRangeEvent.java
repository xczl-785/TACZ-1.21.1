package com.tacz.guns.api.event.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

/** The resolved melee volume after damage evaluation. Observers cannot change the attack. */
public final class GunMeleeRangeEvent extends Event {
    private final LivingEntity shooter;
    private final int distance;
    private final Vec3 centre;
    private final Vec3 direction;
    private final float angle;

    public GunMeleeRangeEvent(LivingEntity shooter, int distance, Vec3 centre, Vec3 direction, float angle) {
        this.shooter = shooter;
        this.distance = distance;
        this.centre = centre;
        this.direction = direction;
        this.angle = angle;
    }

    public LivingEntity shooter() { return shooter; }
    public int distance() { return distance; }
    public Vec3 centre() { return centre; }
    public Vec3 direction() { return direction; }
    public float angle() { return angle; }
}
