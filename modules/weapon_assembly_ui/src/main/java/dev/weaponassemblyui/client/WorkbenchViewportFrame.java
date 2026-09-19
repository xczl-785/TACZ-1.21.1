package dev.weaponassemblyui.client;

import dev.firearms.presentation.ModelGeometry.Point;

/** One immutable camera frame shared by the rendered model and projected slot anchors. */
public record WorkbenchViewportFrame(Point modelCenter,float screenCenterX,float screenCenterY,
                                     double fitScale,double renderScale,double yaw,double pitch) {
    public record Projection(float x,float y,float depth) {}

    public Projection project(Point point) {
        double x=(point.x()-modelCenter.x())*fitScale;
        double y=(point.y()-modelCenter.y())*fitScale;
        double z=(point.z()-modelCenter.z())*fitScale;
        double cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        double rx=x*cy+z*sy,rz=-x*sy+z*cy,ry=y*cp-rz*sp;
        return new Projection(screenCenterX+(float)(rx*renderScale),screenCenterY-(float)(ry*renderScale),(float)(y*sp+rz*cp));
    }
}
