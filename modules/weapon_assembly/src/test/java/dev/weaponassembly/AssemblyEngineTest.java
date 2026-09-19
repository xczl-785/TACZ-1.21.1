package dev.weaponassembly;

import dev.firearms.assembly.*;
import dev.firearms.assembly.PartDefinition.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssemblyEngineTest {
    static PartDefinition part(String id, Slot... slots) {
        return new PartDefinition(id, List.of(slots), Set.of(), Set.of(), Modifiers.ZERO, Optional.empty());
    }
    static AssemblyNode node(String id) { return AssemblyNode.leaf(UUID.randomUUID(), id); }
    static final Slot MOUNT = new Slot("mount", true, Set.of("mount"));
    static AssemblyEngine engine() {
        var gun = new PartDefinition("gun", List.of(MOUNT), Set.of(), Set.of(), Modifiers.ZERO,
                Optional.of(new WeaponBase(100, 150, OptionalDouble.of(.05), OptionalDouble.of(100))));
        return new AssemblyEngine(new AssemblyCatalog(List.of(gun,
                part("mount", new Slot("optic", true, Set.of("optic"))), part("optic"), part("wrong"))));
    }
    @Test void installsRecursivelyAndReportsMissingRequiredWithoutRejectingUnfinishedGun() {
        var engine = engine(); var gun = node("gun");
        assertTrue(engine.validate(gun).valid());
        assertFalse(engine.validate(gun).complete());
        var mount = engine.install(gun, List.of("mount"), node("mount"));
        assertTrue(mount.success()); assertTrue(gun.children().isEmpty());
        assertEquals(List.of("mount", "optic"), engine.validate(mount.after()).missingRequired().getFirst().path());
        var optic = engine.install(mount.after(), List.of("mount", "optic"), node("optic"));
        assertTrue(optic.success()); assertTrue(engine.validate(optic.after()).complete());
    }
    @Test void failedInstallReturnsOriginalAndRemovalReturnsWholeSubtree() {
        var engine = engine(); var gun = node("gun");
        var bad = engine.install(gun, List.of("mount"), node("wrong"));
        assertFalse(bad.success()); assertSame(gun, bad.after());
        assertEquals(AssemblyEngine.Code.INCOMPATIBLE, bad.errors().getFirst().code());
        var mount = new AssemblyNode(UUID.randomUUID(), "mount", Map.of("optic", node("optic")));
        var installed = engine.install(gun, List.of("mount"), mount);
        assertTrue(installed.success());
        var removed = engine.remove(installed.after(), List.of("mount"));
        assertTrue(removed.success()); assertEquals(mount, removed.detached().orElseThrow());
        assertEquals(gun, removed.after());
    }
}
