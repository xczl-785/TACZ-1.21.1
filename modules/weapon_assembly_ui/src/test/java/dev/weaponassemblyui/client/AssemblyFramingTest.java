package dev.weaponassemblyui.client;

import dev.firearms.assembly.AssemblyNode;
import dev.firearms.presentation.ModelGeometry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssemblyFramingTest {
    private static ModelGeometry box(float min,float max) {
        return new ModelGeometry(new ModelGeometry.Point(0,0,0),Map.of(),
                List.of(new ModelGeometry.Box(new ModelGeometry.Point(min,0,0),new ModelGeometry.Point(max,2,2))),List.of());
    }
    @Test void currentAssemblyOwnsTheAutomaticCenterAndFit() {
        var root=new AssemblyNode(UUID.randomUUID(),"root",Map.of());
        var wide=new AssemblyNode(UUID.randomUUID(),"wide",Map.of());
        var first=AssemblyFraming.fit(root,Map.of("root",box(-2,2)));
        var replacement=AssemblyFraming.fit(wide,Map.of("wide",box(10,20)));
        assertEquals(0,first.center().x(),1e-6);assertEquals(15,replacement.center().x(),1e-6);
        assertTrue(replacement.fitScale()<first.fitScale());
    }
}
