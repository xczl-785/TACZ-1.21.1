package dev.weaponassemblyui.client;

import dev.weaponmodels.ModelGeometry.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchViewportFrameTest {
    @Test void nativeAndOverlayProjectionShareTheSameCameraFrame() {
        var frame=new WorkbenchViewportFrame(new Point(10,20,30),640,360,2,8,-Math.PI/2,0);
        var center=frame.project(new Point(10,20,30));
        assertEquals(640,center.x(),1e-5);assertEquals(360,center.y(),1e-5);
        var muzzle=frame.project(new Point(10,20,40));
        assertEquals(480,muzzle.x(),1e-5);assertEquals(360,muzzle.y(),1e-5);
    }

    @Test void pitchAffectsModelAndSlotAnchorsTogether() {
        var frame=new WorkbenchViewportFrame(new Point(0,0,0),100,80,1,10,0,Math.PI/2);
        var projected=frame.project(new Point(0,0,2));
        assertEquals(100,projected.x(),1e-5);assertEquals(100,projected.y(),1e-5);
    }

    @Test void nativeBedrockMuzzleIsBridgedIntoPositiveAssemblyZ() {
        var origin=NativeWorkbenchTransform.toAssembly(new Point(0,1.5f,0));
        var muzzle=NativeWorkbenchTransform.toAssembly(new Point(0,1.5f,-1));
        assertEquals(0,origin.x(),1e-5);assertEquals(0,origin.y(),1e-5);assertEquals(0,origin.z(),1e-5);
        assertEquals(0,muzzle.x(),1e-5);assertEquals(0,muzzle.y(),1e-5);assertEquals(16,muzzle.z(),1e-5);
    }
}
