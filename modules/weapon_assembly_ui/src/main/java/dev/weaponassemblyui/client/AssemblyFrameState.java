package dev.weaponassemblyui.client;

import dev.weaponmodels.ModelGeometry.Point;

/** Stable workbench framing: keep shorter builds steady and apply meaningful zoom-outs immediately. */
final class AssemblyFrameState {
    private static final float SHRINK_THRESHOLD=.92f;
    private static final float MIN_RELATIVE_SCALE=.8f;
    private AssemblyFraming.Frame target;
    private Point trustedCenter;
    private float scaleFloor;

    AssemblyFrameState(AssemblyFraming.Frame initial) { reset(initial); }

    void accept(AssemblyFraming.Frame next) {
        if(next.fitScale()>=target.fitScale()*SHRINK_THRESHOLD)return;
        boolean boundsAreExtreme=next.fitScale()<scaleFloor;
        float scale=boundsAreExtreme?scaleFloor:next.fitScale();
        if(!boundsAreExtreme)trustedCenter=next.center();
        target=new AssemblyFraming.Frame(trustedCenter,scale);
    }

    void reset(AssemblyFraming.Frame frame) {
        target=frame;trustedCenter=frame.center();scaleFloor=frame.fitScale()*MIN_RELATIVE_SCALE;
    }

    AssemblyFraming.Frame current() {
        return target;
    }
}
