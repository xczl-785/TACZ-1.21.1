package dev.weaponmodels;

import org.joml.Quaternionfc;
import org.joml.Vector3f;
import dev.weaponmodels.WeaponPresentation.Vec;

/** Solve a palm contact without moving/scaling the gun or altering animation rotations. */
public final class HandPlacement {
    public static Vec translation(Vec target, Vec palm, Vec scale, Quaternionfc rotation) {
        var transformed=palm.vector().mul(scale.vector());
        rotation.transform(transformed);
        return target.subtract(Vec.of(transformed));
    }
    private HandPlacement(){}
}
