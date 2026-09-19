package dev.weaponassembly;

import dev.firearms.assembly.*;
import dev.firearms.assembly.AssemblyJson;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssemblyJsonTest {
    static String resource(String name) throws Exception {
        try(var in=AssemblyJsonTest.class.getResourceAsStream("/"+name)) { return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8); }
    }
    @Test void catalogLoadsRealGlockChainAndSnapshotsPreserveEveryOccurrence() throws Exception {
        var engine=new AssemblyEngine(AssemblyJson.readCatalog(resource("glock-optics.json")));
        var gun=AssemblyEngineTest.node("5a7ae0c351dfba0017554310");
        var slide=engine.install(gun,List.of("mod_reciever"),AssemblyEngineTest.node("5a71e22f8dc32e00094b97f4"));
        assertTrue(slide.success());
        var optic=engine.install(slide.after(),List.of("mod_reciever","mod_scope"),AssemblyEngineTest.node("5a32aa8bc4a2826c6e06d737"));
        assertTrue(optic.success()); assertFalse(engine.validate(optic.after()).complete());
        var barrel=engine.install(optic.after(),List.of("mod_barrel"),AssemblyEngineTest.node("5a6b5b8a8dc32e001207faf3"));
        assertTrue(barrel.success()); assertTrue(engine.validate(barrel.after()).complete());
        String encoded=AssemblyJson.writeSnapshot(barrel.after(),engine);
        assertEquals(barrel.after(),AssemblyJson.readSnapshot(encoded,engine));
        assertEquals(encoded,AssemblyJson.writeSnapshot(AssemblyJson.readSnapshot(encoded,engine),engine));
        var stats=new WeaponStats(engine).calculate(barrel.after(),new WeaponStats.Context(10,0));
        assertEquals(.626,stats.weightKg(),1e-12); assertEquals(98,stats.ergonomics());
        assertEquals(311*.941,stats.recoilVertical(),1e-10); assertEquals(260*.941,stats.recoilHorizontal(),1e-10);
        assertEquals(34.36*.3,stats.accuracyMoa().orElseThrow(),1e-12); assertEquals(150,stats.sightingRange().orElseThrow());
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(encoded.replace("\"schemaVersion\":1","\"schemaVersion\":2"),engine));
    }
    @Test void malformedSnapshotsAndCatalogsFailClosed() {
        var engine=AssemblyEngineTest.engine();
        for(String json:List.of("{}","null","{\"schemaVersion\":1,\"nodes\":[]}","{\"schemaVersion\":1.5,\"nodes\":[]}","{\"schemaVersion\":1,\"schemaVersion\":1,\"nodes\":[]}"))
            assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(json,engine));
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readCatalog("{\"schemaVersion\":1,\"parts\":[{\"id\":\"a\",\"stats\":{\"weightKg\":\"2\"}}]}"));
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readCatalog("{\"schemaVersion\":1,\"parts\":[{\"id\":\"a\",\"stats\":{\"wieghtKg\":2}}]}"));
    }
    @Test void snapshotRejectsDuplicateIdentityDuplicatePlacementUnknownPartAndOrphan() {
        var engine=AssemblyEngineTest.engine();
        var mount=new AssemblyNode(UUID.randomUUID(),"mount",Map.of("optic",AssemblyEngineTest.node("optic")));
        var root=engine.install(AssemblyEngineTest.node("gun"),List.of("mount"),mount).after();
        var encoded=AssemblyJson.writeSnapshot(root,engine);
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(encoded.replace(mount.instanceId().toString(),root.instanceId().toString()),engine));
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(encoded.replace("\"definitionId\":\"optic\"","\"definitionId\":\"missing\""),engine));
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(encoded.replace("\"parentId\":\""+root.instanceId(),"\"parentId\":\""+UUID.randomUUID()),engine));
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(encoded.replace("\"slot\":\"optic\"","\"slot\":\"wrong\""),engine));
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(" ".repeat(AssemblyJson.MAX_JSON_CHARS+1),engine));
        assertThrows(IllegalArgumentException.class,()->AssemblyJson.readSnapshot(encoded+" {}",engine));
    }

    @Test void seededExchangeSequenceConservesAllInstancesThroughRoundTrips() {
        var engine=AssemblyEngineTest.engine(); var root=AssemblyEngineTest.node("gun");
        var bag=new ArrayList<AssemblyNode>();
        for(int i=0;i<4;i++) bag.add(new AssemblyNode(UUID.randomUUID(),"mount",Map.of("optic",AssemblyEngineTest.node("optic"))));
        var universe=new HashSet<UUID>(); universe.add(root.instanceId());bag.forEach(n->collectIds(n,universe));
        var random=new Random(913);
        for(int i=0;i<200;i++) {
            if(root.children().isEmpty()) {
                var source=bag.remove(random.nextInt(bag.size()));var plan=engine.install(root,List.of("mount"),source);
                assertTrue(plan.success());root=plan.after();
            } else if(random.nextBoolean()) {
                var plan=engine.remove(root,List.of("mount"));assertTrue(plan.success());root=plan.after();bag.add(plan.detached().orElseThrow());
            } else {
                var source=bag.remove(random.nextInt(bag.size()));var plan=engine.replace(root,List.of("mount"),source);
                assertTrue(plan.success());root=plan.after();bag.add(plan.detached().orElseThrow());
            }
            root=AssemblyJson.readSnapshot(AssemblyJson.writeSnapshot(root,engine),engine);
            var actual=new HashSet<UUID>();collectIds(root,actual);bag.forEach(n->collectIds(n,actual));assertEquals(universe,actual);
        }
    }
    private static void collectIds(AssemblyNode node,Set<UUID> ids) {
        assertTrue(ids.add(node.instanceId()),"Duplicate physical occurrence");node.children().values().forEach(n->collectIds(n,ids));
    }
}
