package dev.weaponassemblyui.client;

import dev.weaponmodels.ModelGeometry.Point;

/** Stable workbench framing: keep shorter builds steady and ease only meaningful zoom-outs. */
final class AssemblyFrameState {
    static final long TRANSITION_NANOS=220_000_000L;
    private static final float SHRINK_THRESHOLD=.92f;
    private static final float MIN_RELATIVE_SCALE=.8f;
    private AssemblyFraming.Frame start,target;
    private Point trustedCenter;
    private float scaleFloor;
    private long transitionStart;

    AssemblyFrameState(AssemblyFraming.Frame initial,long now) { reset(initial,now); }

    void accept(AssemblyFraming.Frame next,long now) {
        if(next.fitScale()>=target.fitScale()*SHRINK_THRESHOLD)return;
        start=current(now);
        boolean boundsAreExtreme=next.fitScale()<scaleFloor;
        float scale=boundsAreExtreme?scaleFloor:next.fitScale();
        if(!boundsAreExtreme)trustedCenter=next.center();
        target=new AssemblyFraming.Frame(trustedCenter,scale);
        transitionStart=now;
    }

    void reset(AssemblyFraming.Frame frame,long now) {
        start=target=frame;trustedCenter=frame.center();scaleFloor=frame.fitScale()*MIN_RELATIVE_SCALE;transitionStart=now-TRANSITION_NANOS;
    }

    AssemblyFraming.Frame current(long now) {
        double linear=Math.clamp((double)(now-transitionStart)/TRANSITION_NANOS,0,1);
        float t=(float)(linear*linear*(3-2*linear));
        return new AssemblyFraming.Frame(lerp(start.center(),target.center(),t),
                start.fitScale()+(target.fitScale()-start.fitScale())*t);
    }

    private static Point lerp(Point a,Point b,float t) {
        return new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);
    }
}
