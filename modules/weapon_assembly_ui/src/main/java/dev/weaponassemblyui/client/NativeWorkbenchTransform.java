package dev.weaponassemblyui.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.firearms.presentation.ModelGeometry.Point;

/** Canonical bridge from TaCZ's Java Bedrock frame into assembly art space. */
public final class NativeWorkbenchTransform {
    private static final float UNITS_PER_BLOCK=16;
    private static final float BEDROCK_GROUND_Y=1.5f;

    public static Point toAssembly(Point nativePoint) {
        return new Point(-nativePoint.x()*UNITS_PER_BLOCK,
                (BEDROCK_GROUND_Y-nativePoint.y())*UNITS_PER_BLOCK,
                -nativePoint.z()*UNITS_PER_BLOCK);
    }

    public static void apply(PoseStack poses) {
        poses.translate(0,BEDROCK_GROUND_Y*UNITS_PER_BLOCK,0);
        poses.scale(-UNITS_PER_BLOCK,-UNITS_PER_BLOCK,-UNITS_PER_BLOCK);
    }

    private NativeWorkbenchTransform() {}
}
