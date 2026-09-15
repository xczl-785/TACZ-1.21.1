package dev.weaponassembly;

import com.google.gson.*;
import dev.weaponassembly.api.*;
import dev.weaponassembly.io.AssemblyJson;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** The actual model-package scenarios must exercise the shipped engine, not only a Python replica. */
class AssemblyModelFixturesTest {
    static String data(String name) throws Exception { return AssemblyJsonTest.resource("model-fixtures/" + name); }
    static AssemblyEngine engine() throws Exception { return new AssemblyEngine(AssemblyJson.readCatalog(data("catalog.json"))); }

    @TestFactory Stream<DynamicTest> modelScenariosDriveRealInstallOperations() throws Exception {
        var scenarios=JsonParser.parseString(data("scenarios.json")).getAsJsonObject().getAsJsonArray("scenarios");
        var sceneIds=new HashSet<String>();scenarios.forEach(s->sceneIds.add(s.getAsJsonObject().get("id").getAsString()));
        assertEquals(scenarios.size(),sceneIds.size(),"Scenario IDs must be unique");
        assertTrue(sceneIds.containsAll(Set.of("zev_rmr","mos_acro","dual_rmr","mount_conflict","missing_barrel","wrong_optic")),
                "Retain the baseline complex scenarios; additional scenes run automatically");
        var cases=new ArrayList<DynamicTest>();
        for(var value:scenarios) {
            var scenario=value.getAsJsonObject();
            cases.add(DynamicTest.dynamicTest(scenario.get("id").getAsString(),()->{
                var engine=engine();var snapshot=data(scenario.get("snapshot").getAsString());
                var rows=JsonParser.parseString(snapshot).getAsJsonObject().getAsJsonArray("nodes");
                var first=rows.get(0).getAsJsonObject();
                var root=AssemblyNode.leaf(UUID.fromString(first.get("instanceId").getAsString()),first.get("definitionId").getAsString());
                var paths=new HashMap<String,List<String>>();paths.put(root.instanceId().toString(),List.of());
                var actualCodes=new TreeSet<String>();
                for(int i=1;i<rows.size();i++) {
                    var row=rows.get(i).getAsJsonObject();
                    var path=new ArrayList<>(Objects.requireNonNull(paths.get(row.get("parentId").getAsString())));
                    path.add(row.get("slot").getAsString());
                    var source=AssemblyNode.leaf(UUID.fromString(row.get("instanceId").getAsString()),row.get("definitionId").getAsString());
                    var result=engine.install(root,path,source);
                    if(!result.success()) {
                        assertSame(root,result.after()); assertTrue(result.detached().isEmpty());
                        result.errors().forEach(error->actualCodes.add(error.code().name()));break;
                    }
                    paths.put(source.instanceId().toString(),List.copyOf(path));root=result.after();
                }
                var expectedCodes=new TreeSet<String>();scenario.getAsJsonArray("expectedErrorCodes").forEach(c->expectedCodes.add(c.getAsString()));
                assertEquals(expectedCodes,actualCodes);
                boolean valid=scenario.get("expectValid").getAsBoolean();assertEquals(valid,actualCodes.isEmpty());
                if(valid) {
                    assertEquals(root,AssemblyJson.readSnapshot(snapshot,engine));
                    assertEquals(scenario.get("expectComplete").getAsBoolean(),engine.validate(root).complete());
                    assertTrue(Double.isFinite(new WeaponStats(engine).calculate(root,new WeaponStats.Context(10,0)).weightKg()));
                    assertEquals(root,AssemblyJson.readSnapshot(AssemblyJson.writeSnapshot(root,engine),engine));
                } else assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(snapshot,engine));
            }));
        }
        return cases.stream();
    }

    @Test void dualRmrRemoveOnlyOneOccurrenceAndRestoreItsOwnSubtree() throws Exception {
        var engine=engine();var root=AssemblyJson.readSnapshot(data("scenes/dual_rmr.json"),engine);
        var slideOptic=root.children().get("mod_reciever").children().get("mod_scope");
        var secondOptic=root.children().get("mod_mount").children().get("mod_scope").children().get("mod_scope");
        assertEquals(slideOptic.definitionId(),secondOptic.definitionId());assertNotEquals(slideOptic.instanceId(),secondOptic.instanceId());
        var path=List.of("mod_mount","mod_scope","mod_scope");
        var result=engine.remove(root,path);assertTrue(result.success());
        assertEquals(secondOptic,result.detached().orElseThrow());
        assertEquals(slideOptic,result.after().children().get("mod_reciever").children().get("mod_scope"));
        var calc=new WeaponStats(engine);var context=new WeaponStats.Context(10,0);
        assertEquals(engine.catalog().require(secondOptic.definitionId()).modifiers().weightKg(),
                calc.calculate(root,context).weightKg()-calc.calculate(result.after(),context).weightKg(),1e-12);
        assertEquals(root,engine.install(result.after(),path,result.detached().orElseThrow()).after());
    }

    @Test void mosAssemblyDetachesWithAdapterAndAcroStillAttached() throws Exception {
        var engine=engine();var root=AssemblyJson.readSnapshot(data("scenes/mos_acro.json"),engine);
        var path=List.of("mod_reciever");var result=engine.remove(root,path);assertTrue(result.success());
        assertFalse(engine.validate(result.after()).complete());
        assertTrue(result.detached().orElseThrow().children().get("mod_mount").children().containsKey("mod_scope"));
        assertEquals(root,engine.install(result.after(),path,result.detached().orElseThrow()).after());
    }
}
