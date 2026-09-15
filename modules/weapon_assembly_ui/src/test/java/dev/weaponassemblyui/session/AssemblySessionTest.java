package dev.weaponassemblyui.session;

import dev.weaponassembly.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssemblySessionTest {
    private static PartDefinition part(String id, List<PartDefinition.Slot> slots, Set<String> conflicts, boolean weapon) {
        return new PartDefinition(id, slots, conflicts, Set.of(), PartDefinition.Modifiers.ZERO,
                weapon ? Optional.of(new PartDefinition.WeaponBase(100, 100, OptionalDouble.empty(), OptionalDouble.empty())) : Optional.empty());
    }
    private static AssemblyNode leaf(String id) { return AssemblyNode.leaf(UUID.randomUUID(), id); }
    private static AssemblyCatalog catalog() {
        return new AssemblyCatalog(List.of(
                part("gun", List.of(new PartDefinition.Slot("top", true, Set.of("mount", "blocker")),
                        new PartDefinition.Slot("side", false, Set.of("mount", "blocker")),
                        new PartDefinition.Slot("unsupported", false, Set.of())), Set.of(), true),
                part("mount", List.of(new PartDefinition.Slot("optic", true, Set.of("optic"))), Set.of(), false),
                part("optic", List.of(), Set.of("blocker"), false),
                part("blocker", List.of(), Set.of(), false)));
    }
    private static AssemblySession session(AssemblyNode tree, AssemblyNode... stock) {
        return new AssemblySession(catalog(), tree, List.of(stock), new WeaponStats.Context(0, 0));
    }
    private static AssemblyNode gun(Map<String, AssemblyNode> children) { return new AssemblyNode(UUID.randomUUID(), "gun", children); }
    private static AssemblyNode mount(AssemblyNode optic) { return new AssemblyNode(UUID.randomUUID(), "mount", Map.of("optic", optic)); }

    @Test void hoverIsPureAndCommitConsumesExactlyOneSuppliedInstance() {
        var a = leaf("mount"); var b = leaf("mount"); var root = gun(Map.of()); var s = session(root, a, b);
        assertTrue(s.select(List.of("top")));
        assertEquals(2, s.candidates().size());
        assertTrue(s.preview(a.instanceId()).plan().success());
        assertSame(root, s.tree()); assertEquals(List.of(a, b), s.stock()); assertFalse(s.canUndo());
        s.clearPreview(); assertTrue(s.preview().isEmpty());
        assertTrue(s.install(a.instanceId()).success());
        assertEquals(a, s.tree().children().get("top")); assertEquals(List.of(b), s.stock());
        assertTrue(s.undo()); assertEquals(root, s.tree()); assertEquals(List.of(a, b), s.stock());
    }
    @Test void replacementRetainsEntireSubtreeAndUndoRestoresWholeSession() {
        var optic = leaf("optic"); var original = mount(optic); var replacement = leaf("mount");
        var root = gun(Map.of("top", original)); var s = session(root, replacement);
        s.select(List.of("top")); assertTrue(s.install(replacement.instanceId()).success());
        assertEquals(List.of(original), s.detached()); assertSame(optic, s.detached().getFirst().children().get("optic"));
        assertTrue(s.undo()); assertEquals(root, s.tree()); assertEquals(List.of(replacement), s.stock()); assertTrue(s.detached().isEmpty());
    }
    @Test void duplicateDefinitionsAreSelectedByFullPathAndInstance() {
        var left = mount(leaf("optic")); var right = mount(leaf("optic"));
        var s = session(gun(Map.of("top", left, "side", right)));
        assertTrue(s.select(List.of("side", "optic")));
        assertEquals(right.instanceId(), s.slots(List.of("side")).getFirst().ownerId());
        assertTrue(s.remove().success());
        assertEquals(left, s.tree().children().get("top"));
        assertEquals(right.children().get("optic"), s.detached().getFirst());
        assertTrue(s.validation().valid()); assertFalse(s.validation().complete());
        assertTrue(s.install(s.detached().getFirst().instanceId()).success()); assertTrue(s.detached().isEmpty());
    }
    @Test void conflictPreviewAndCommitLeaveTreeSupplyAndHistoryUnchanged() {
        var candidate = leaf("blocker"); var root = gun(Map.of("top", mount(leaf("optic")))); var s = session(root, candidate);
        s.select(List.of("side")); assertEquals(List.of(candidate), s.candidates());
        assertFalse(s.preview(candidate.instanceId()).plan().success()); assertTrue(s.preview().orElseThrow().stats().isEmpty());
        assertFalse(s.install(candidate.instanceId()).success()); assertSame(root, s.tree());
        assertEquals(List.of(candidate), s.stock()); assertFalse(s.canUndo());
        assertTrue(s.feedback().stream().anyMatch(i -> i.code() == AssemblyEngine.Code.PART_CONFLICT));
    }
    @Test void resetRestoresSupplyAndCanBeUndoneWithoutLosingDetachedParts() {
        var original = mount(leaf("optic")); var root = gun(Map.of("top", original)); var s = session(root);
        s.select(List.of("top")); s.remove(); var removed = s.tree();
        s.reset(); assertEquals(root, s.tree()); assertTrue(s.stock().isEmpty());
        assertTrue(s.undo()); assertEquals(removed, s.tree()); assertEquals(List.of(original), s.detached());
        assertTrue(s.undo()); assertEquals(root, s.tree()); assertTrue(s.stock().isEmpty());
    }
    @Test void staleSelectionAndUnknownSupplyCannotMutateSession() {
        var root = gun(Map.of()); var supplied = leaf("mount"); var s = session(root, supplied);
        assertFalse(s.select(List.of("top", "optic"))); assertTrue(s.selectedPath().isEmpty());
        assertFalse(s.install(supplied.instanceId()).success()); assertSame(root, s.tree());
        s.select(List.of("top")); assertFalse(s.install(UUID.randomUUID()).success()); assertSame(root, s.tree());
        assertThrows(IllegalArgumentException.class, () -> session(gun(Map.of("top", supplied)), supplied));
    }
    @Test void allPhysicalSlotsAreDirectlyAccessibleAndFollowParentRemoval() {
        var left=mount(leaf("optic"));var right=mount(leaf("optic"));
        var s=session(gun(Map.of("top",left,"side",right)));
        assertEquals(Set.of(List.of("top"),List.of("side"),List.of("top","optic"),List.of("side","optic")),
                new HashSet<>(s.visibleSlots().stream().map(AssemblySession.SlotView::path).toList()));
        // No parent selection or navigation is needed to remove the nested optic.
        assertTrue(s.select(List.of("side","optic")));assertTrue(s.remove().success());
        assertEquals(4,s.visibleSlots().size()); // its empty mount remains available
        assertEquals(left,s.nodeAt(List.of("top")).orElseThrow());
        s.reset();s.select(List.of("top"));assertTrue(s.remove().success());
        assertEquals(3,s.visibleSlots().size());
        assertTrue(s.visibleSlots().stream().noneMatch(v->v.path().equals(List.of("top","optic"))));
        assertEquals(left,s.detached().getFirst()); // entire mounted group is preserved
        assertTrue(s.install(left.instanceId()).success());assertEquals(4,s.visibleSlots().size());
    }
    @Test void unsupportedImportSlotsAreNotOfferedButUsableEmptySlotsRemainVisible() {
        var s=session(gun(Map.of()));
        assertEquals(3,s.slots(List.of()).size());
        assertEquals(Set.of(List.of("top"),List.of("side")),
                new HashSet<>(s.visibleSlots().stream().map(AssemblySession.SlotView::path).toList()));
    }
}
