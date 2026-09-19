package dev.weaponassemblyui.client;

import dev.weaponmodels.*;
import dev.firearms.assembly.AssemblyNode;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static dev.weaponmodels.ModelGeometry.*;

/** Headless geometry checks; does not require a Minecraft instance. */
public final class ModelGeometryTest {
    @Test void geometryAndProjectionContract() throws Exception {
        var child=AssemblyNode.leaf(UUID.randomUUID(),"child");
        var root=new AssemblyNode(UUID.randomUUID(),"root",Map.of("mount",child));
        var geometry=Map.of("root",new ModelGeometry(new Point(2,3,4),Map.of("mount",new Point(5,7,9)),List.of()),
            "child",new ModelGeometry(new Point(1,2,3),Map.of("optic",new Point(4,6,8)),List.of()));
        equal(origin(root,geometry,List.of()).orElseThrow(),new Point(-2,-3,-4));
        // Parent slot (5,7,9) minus parent origin (2,3,4), then minus child origin (1,2,3).
        var childOrigin=origin(root,geometry,List.of("mount")).orElseThrow();
        equal(childOrigin,new Point(2,2,2));
        equal(childOrigin.add(geometry.get("child").slots().get("optic")),new Point(6,8,10));
        check(origin(root,geometry,List.of("missing")).isEmpty(),"Unknown slot must not project");
        equal(project(new Point(0,-1,0),0,0,1,10,20,210,160),new Point(115,100,20));
        equal(project(new Point(1,-1,0),0,0,2,10,20,210,160),new Point(135,100,20));
        check(zoomAfter(1,10000)==2.5,"Maximum zoom");
        check(zoomAfter(1,-10000)==.45,"Minimum zoom");
        check(Math.abs(zoomAfter(zoomAfter(1,2),-2)-1)<1e-12,"Reversible zoom within bounds");
        var rotated=project(new Point(0,-1,1),Math.PI/2,0,1,10,20,210,160);
        check(Math.abs(rotated.x()-125)<.001&&Math.abs(rotated.y()-100)<.001,"Yaw projection");
        boolean rejected=false;try {new Box(new Point(1,1,1),new Point(0,0,0));}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Inverted boxes must fail");
        try(var reader=new FileReader("../weapon_assembly/model-fixtures/manifest.json")) {var models=load(reader);check(models.size()==13,"Expected 13 fixture geometries");check(models.values().stream().allMatch(m->!m.boxes().isEmpty()),"Missing boxes");}

    }
    private static void equal(Point actual,Point expected) {check(actual.equals(expected),actual+" != "+expected);}
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
