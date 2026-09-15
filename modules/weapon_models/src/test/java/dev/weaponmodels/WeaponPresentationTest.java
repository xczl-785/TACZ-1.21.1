package dev.weaponmodels;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.weaponmodels.WeaponPresentation.*;

class WeaponPresentationTest {
    @Test void eyeFollowsNestedMountAndKeepsEyeRelief() {
        var mount = Frame.at(new Vec(2, 4, 8), new Vec(0, 0, 45));
        var optic = mount.then(Frame.at(new Vec(0, 3, 5), Vec.ZERO));
        var sight = new Aim("scope", optic, 10, 1, 55, 10);
        var expected = mount.transform(new Vec(0, 3, -5));
        assertVec(expected, sight.eye().position());
        assertEquals(10, sight.eye().position().distance(optic.position()), 1e-5);
    }
    @Test void duplicateDefinitionsRemainSeparateAndRemovalFallsBack() {
        var spec = fixture();
        var occurrences = List.of(new Occurrence("a", "optic", Frame.IDENTITY),
                new Occurrence("b", "optic", Frame.at(new Vec(0, 5, 0), Vec.ZERO)));
        assertEquals(List.of("a/scope", "b/scope"), spec.resolve(occurrences).aims().stream().map(Aim::id).toList());
        assertEquals("b/scope", spec.resolve(occurrences).select("b/scope").orElseThrow().id());
        assertEquals(1, spec.resolve(occurrences.subList(0, 1)).aims().size());
        assertEquals("a/scope", spec.resolve(occurrences.subList(0, 1)).select("b/scope").orElseThrow().id());
    }
    @Test void missingFrontSightDoesNotInventIronSight() {
        var rear = new Marker("rear", "rear", "mbus", new Vec(0, 2, 0), Vec.ZERO, 10, 1, 55, 1);
        var front = new Marker("front", "front", "mbus", new Vec(0, 2, 20), Vec.ZERO, 0, 1, 55, 1);
        var spec = new WeaponPresentation(Map.of("rear",new Part(List.of(rear), List.of()), "front",new Part(List.of(front), List.of())));
        assertTrue(spec.resolve(List.of(new Occurrence("r","rear",Frame.IDENTITY))).aims().isEmpty());
        var result = spec.resolve(List.of(new Occurrence("r","rear",Frame.IDENTITY),new Occurrence("f","front",Frame.IDENTITY)));
        assertEquals(1,result.aims().size());
        assertVec(new Vec(0,2,-10), result.aims().getFirst().eye().position());
    }
    @Test void continuousRecoveryIsIndependentOfFrameRateAndBoundedUnderAutoFire() {
        var a = new ShotSpring(18, 2); var b = new ShotSpring(18, 2);
        a.kick(1); b.kick(1);
        for(int i=0;i<30;i++) a.advance(1.0/30);
        for(int i=0;i<144;i++) b.advance(1.0/144);
        assertEquals(a.value(),b.value(),1e-8);
        for(int i=0;i<1000;i++){a.kick(1); a.advance(.075); assertTrue(Math.abs(a.value())<=2);}
        a.advance(10); assertEquals(0,a.value(),1e-8);
    }
    @Test void palmStaysOnGripWithAnimatedRotationAndNonUniformArmScale() {
        var target=new Vec(1,-3,-8);var palm=new Vec(-6,-10,0);var scale=new Vec(1,1.5,1);
        var rotation=new org.joml.Quaternionf().rotationZYX(-2.56f,-.51f,1.69f);
        var offset=HandPlacement.translation(target,palm,scale,rotation);
        var actual=rotation.transform(palm.vector().mul(scale.vector())).add(offset.vector());
        assertVec(target,Vec.of(actual));
    }
    @Test void packagedPresetsResolveFromTheirRealGeometryAndRemovedSightsDisappear() throws Exception {
        for(String gun:List.of("adar","radian","m4a1")) {
            var resources=java.nio.file.Path.of("src/test/fixtures");
            var assets=resources.resolve("assets/newmod_"+gun+"/"+gun);
            var data=resources.resolve("data/newmod_"+gun+"/"+gun);
            var catalog=dev.weaponassembly.io.AssemblyJson.readCatalog(java.nio.file.Files.readString(data.resolve("catalog.json")));
            var engine=new dev.weaponassembly.api.AssemblyEngine(catalog);
            var tree=dev.weaponassembly.io.AssemblyJson.readSnapshot(java.nio.file.Files.readString(data.resolve("scene.json")),engine);
            try(var g=java.nio.file.Files.newBufferedReader(assets.resolve("manifest.json"));var m=java.nio.file.Files.newBufferedReader(assets.resolve("markers.json"))) {
                var geometry=ModelGeometry.load(g);var spec=WeaponPresentation.load(m);
                var occurrences=WeaponPresentation.occurrences(tree,geometry);var result=spec.resolve(occurrences);
                assertEquals(1,result.aims().size(),gun);assertEquals(Set.of("left","right"),result.contacts().keySet());
                var aim=result.aims().getFirst();assertTrue(aim.eyeDistance()*.38>=8,gun+" rear sight must not touch camera");
                var receiverRemoved=occurrences.stream().filter(o->!o.path().startsWith("mod_reciever")).toList();
                assertTrue(spec.resolve(receiverRemoved).aims().isEmpty(),gun+" detached receiver removes descendant sights");
                assertFalse(spec.resolve(receiverRemoved).contacts().containsKey("left"));
            }
        }
    }
    @Test void assembledTranslationsUseAttachmentOriginsExactlyOnce() {
        var child=new dev.weaponassembly.api.AssemblyNode(UUID.randomUUID(),"optic",Map.of());
        var mount=new dev.weaponassembly.api.AssemblyNode(UUID.randomUUID(),"mount",Map.of("scope",child));
        var root=new dev.weaponassembly.api.AssemblyNode(UUID.randomUUID(),"gun",Map.of("rail",mount));
        var zero=new ModelGeometry.Point(0,0,0);
        var models=Map.of("gun",new ModelGeometry(zero,Map.of("rail",new ModelGeometry.Point(0,4,8)),List.of()),
            "mount",new ModelGeometry(new ModelGeometry.Point(0,1,0),Map.of("scope",new ModelGeometry.Point(0,3,5)),List.of()),
            "optic",new ModelGeometry(new ModelGeometry.Point(0,2,1),Map.of(),List.of()));
        var result=fixture().resolve(WeaponPresentation.occurrences(root,models));
        assertVec(new Vec(0,4,2),result.aims().getFirst().eye().position());
    }
    @Test void attachmentAndTuningShareOneReferenceAndZeroMeansZero() {
        assertEquals(1,RecoilResponse.factor(112,-.481,58.128,1),1e-6);
        assertTrue(RecoilResponse.factor(112,-.3,58.128,1)>1);
        assertEquals(.5,RecoilResponse.factor(112,-.481,58.128,.5),1e-6);
        assertEquals(0,RecoilResponse.factor(112,-.481,58.128,0));
        assertThrows(IllegalArgumentException.class,()->RecoilResponse.factor(112,-1.1,58.128,1));
        assertThrows(IllegalArgumentException.class,()->RecoilResponse.factor(112,0,0,1));
    }
    @Test void invalidMarkersFailAtAuthoringBoundary() {
        assertThrows(IllegalArgumentException.class,()->new Marker("x","optic","",Vec.ZERO,Vec.ZERO,0,1,55,0));
        assertThrows(IllegalArgumentException.class,()->new Marker("x","rear","",Vec.ZERO,Vec.ZERO,10,1,55,0));
        assertThrows(IllegalArgumentException.class,()->new Vec(Double.NaN,0,0));
    }
    private WeaponPresentation fixture() {
        return new WeaponPresentation(Map.of("optic",new Part(List.of(new Marker("scope","optic","",Vec.ZERO,Vec.ZERO,10,1,55,10)),List.of())));
    }
    private void assertVec(Vec a,Vec b){assertEquals(a.x(),b.x(),1e-5);assertEquals(a.y(),b.y(),1e-5);assertEquals(a.z(),b.z(),1e-5);}
}
