package dev.weaponassemblyui.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkbenchCameraTest {
    @Test void tarkovStyleCameraHasRotationButNoZoomOrPanState() {
        assertThrows(NoSuchFieldException.class,()->WorkbenchCamera.class.getDeclaredField("zoom"));
        assertThrows(NoSuchFieldException.class,()->WorkbenchCamera.class.getDeclaredField("panX"));
        assertThrows(NoSuchFieldException.class,()->WorkbenchCamera.class.getDeclaredField("panY"));
        var camera=new WorkbenchCamera();
        camera.rotate(10,10);camera.reset();
        assertEquals(-Math.PI/2,camera.yaw);assertEquals(0,camera.pitch);
    }
}
