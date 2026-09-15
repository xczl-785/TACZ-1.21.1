package dev.weaponassemblyui.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkbenchCameraTest {
    @Test void zoomKeepsThePointUnderTheMouseAndReversesWithoutDrift() {
        var camera=new WorkbenchCamera();
        double centerX=640,centerY=350,mouseX=920,mouseY=420;
        double localX=mouseX-centerX,localY=mouseY-centerY;
        camera.zoomAt(.125,mouseX,mouseY,centerX,centerY);
        assertTrue(camera.zoom>1);assertTrue(camera.zoom<1.1);
        assertEquals(mouseX,centerX+camera.panX+localX*camera.zoom,1e-9);
        assertEquals(mouseY,centerY+camera.panY+localY*camera.zoom,1e-9);
        camera.zoomAt(-.125,mouseX,mouseY,centerX,centerY);
        assertEquals(1,camera.zoom,1e-12);assertEquals(0,camera.panX,1e-9);assertEquals(0,camera.panY,1e-9);
    }
    @Test void limitsDoNotIntroducePanAndResetRestoresFlatLeftView() {
        var camera=new WorkbenchCamera();
        camera.zoomAt(100000,900,400,640,350);assertEquals(6,camera.zoom);
        double pan=camera.panX;camera.zoomAt(1,300,200,640,350);assertEquals(pan,camera.panX,1e-9);
        camera.zoomAt(-100000,900,400,640,350);assertEquals(.25,camera.zoom);
        camera.rotate(10,10);camera.reset();
        assertEquals(-Math.PI/2,camera.yaw);assertEquals(0,camera.pitch);assertEquals(1,camera.zoom);
        assertEquals(0,camera.panX);assertEquals(0,camera.panY);
    }
}
