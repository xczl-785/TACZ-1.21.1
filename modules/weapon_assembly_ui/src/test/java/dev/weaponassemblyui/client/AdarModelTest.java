package dev.weaponassemblyui.client;

import dev.firearms.presentation.*;
import dev.firearms.assembly.*;
import dev.firearms.assembly.AssemblyJson;
import dev.firearms.workbench.AssemblySession;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class AdarModelTest {
    private static final Path RES=Path.of("src/test/resources/adar-regression");
    @Test void importedAssemblyPreservesPositionsAndSubtrees() throws Exception {
        var catalog=AssemblyJson.readCatalog(Files.readString(RES.resolve("catalog.json")));
        var engine=new AssemblyEngine(catalog);
        var tree=AssemblyJson.readSnapshot(Files.readString(RES.resolve("scene.json")),engine);
        Map<String,ModelGeometry> models;
        try(var reader=Files.newBufferedReader(RES.resolve("manifest.json"))){models=ModelGeometry.load(reader);}
        assertEquals(11,models.size());assertTrue(engine.validate(tree).complete());
        assertEquals(7260,models.values().stream().flatMap(m->m.meshes().stream()).mapToInt(m->m.triangles().size()).sum());
        // The supplied white model hides the optic lens: 26 visible meshes, 4 fewer triangles.
        assertEquals(26,models.values().stream().mapToInt(m->m.meshes().size()).sum());
        assertTrue(models.values().stream().flatMap(m->m.meshes().stream()).noneMatch(m->m.name().equals("lens")));
        // Known source-art bounds: muzzle Z = 78.62..83.04; root art anchor Z = 30.
        var path=List.of("mod_reciever","mod_barrel","mod_muzzle");
        var origin=ModelGeometry.origin(tree,models,path).orElseThrow();
        assertEquals(new ModelGeometry.Point(0,0,49),origin);
        var muzzle=models.get("5c0fafb6d174af02a96260ba");
        var zs=muzzle.meshes().stream().flatMap(m->m.triangles().stream()).flatMap(t->t.vertices().stream()).mapToDouble(v->v.z()+origin.z()).summaryStatistics();
        assertEquals(48.62,zs.getMin(),.001);assertEquals(53.04,zs.getMax(),.001);
        var session=new AssemblySession(catalog,tree,List.of(),new WeaponStats.Context(0,0));
        assertEquals(3.584,session.stats().weightKg(),.0001);
        session.select(List.of("mod_reciever"));
        assertTrue(session.remove().success());assertFalse(session.validation().complete());
        var receiver=session.detached().getFirst();assertEquals(3,receiver.children().size());
        assertEquals(2,receiver.children().get("mod_barrel").children().size());
        assertTrue(session.install(receiver.instanceId()).success());assertEquals(tree,session.tree());
        assertEquals(origin,ModelGeometry.origin(session.tree(),models,path).orElseThrow());
        assertTrue(session.detached().isEmpty());
    }
}
