package dev.weaponassembly;

import dev.weaponassembly.api.*;
import dev.weaponassembly.api.PartDefinition.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.weaponassembly.AssemblyEngineTest.*;
import static org.junit.jupiter.api.Assertions.*;

class AssemblyRulesTest {
    static AssemblyEngine branches() {
        return new AssemblyEngine(new AssemblyCatalog(List.of(
                part("root",new Slot("left",true,Set.of("rail")),new Slot("right",true,Set.of("rail")),new Slot("blocker",false,Set.of("blocker"))),
                part("rail",new Slot("optic",true,Set.of("optic","enemy"))),part("optic"),
                new PartDefinition("enemy",List.of(),Set.of("optic"),Set.of(),Modifiers.ZERO,Optional.empty()),
                new PartDefinition("blocker",List.of(),Set.of(),Set.of(new SlotKey("rail","optic")),Modifiers.ZERO,Optional.empty()))));
    }
    @Test void sameDefinitionInTwoSlotsCountsAsTwoInstancesAndIndependentRequiredSlots() {
        var engine=branches(); var root=node("root");
        var one=engine.install(root,List.of("left"),node("rail"));
        assertTrue(one.success());
        assertTrue(engine.validate(one.after()).missingRequired().stream().anyMatch(i->i.path().equals(List.of("right"))));
        var two=engine.install(one.after(),List.of("right"),node("rail")); assertTrue(two.success());
        var l=engine.install(two.after(),List.of("left","optic"),node("optic"));
        var r=engine.install(l.after(),List.of("right","optic"),node("optic"));
        assertTrue(r.success()); assertTrue(engine.validate(r.after()).complete());
    }
    @Test void sharedIdentityIsRejectedEvenWhenReplacingItsOldSlot() {
        var engine=branches(); var rail=node("rail");
        var root=engine.install(node("root"),List.of("left"),rail).after();
        assertEquals(AssemblyEngine.Code.DUPLICATE_INSTANCE,engine.install(root,List.of("right"),rail).errors().getFirst().code());
        assertEquals(AssemblyEngine.Code.DUPLICATE_INSTANCE,engine.replace(root,List.of("left"),rail).errors().getFirst().code());
        var corrupt=new AssemblyNode(UUID.randomUUID(),"root",Map.of("left",rail,"right",rail));
        assertFalse(engine.validate(corrupt).valid());
    }
    @Test void oneSidedConflictsAreSymmetricAndApplyAcrossBranches() {
        var engine=branches();
        for(boolean reverse:List.of(false,true)) {
            var left=new AssemblyNode(UUID.randomUUID(),"rail",Map.of("optic",node(reverse?"enemy":"optic")));
            var right=new AssemblyNode(UUID.randomUUID(),"rail",Map.of("optic",node(reverse?"optic":"enemy")));
            var first=engine.install(node("root"),List.of("left"),left);
            var second=engine.install(first.after(),List.of("right"),right);
            assertFalse(second.success()); assertSame(first.after(),second.after());
            assertEquals(AssemblyEngine.Code.PART_CONFLICT,second.errors().getFirst().code());
        }
    }
    @Test void slotBlockerChecksOccupiedQualifiedSlotsInBothInstallationOrders() {
        var engine=branches(); var rail=new AssemblyNode(UUID.randomUUID(),"rail",Map.of("optic",node("optic")));
        var withRail=engine.install(node("root"),List.of("left"),rail).after();
        assertEquals(AssemblyEngine.Code.SLOT_CONFLICT,engine.install(withRail,List.of("blocker"),node("blocker")).errors().getFirst().code());
        var blocked=engine.install(node("root"),List.of("blocker"),node("blocker")).after();
        assertEquals(AssemblyEngine.Code.SLOT_CONFLICT,engine.install(blocked,List.of("left"),rail).errors().getFirst().code());
        assertTrue(engine.install(blocked,List.of("left"),node("rail")).success());
    }
    @Test void replacementReturnsOldTreeAndBadRequestsLeaveOriginal() {
        var engine=engine(); var mount=new AssemblyNode(UUID.randomUUID(),"mount",Map.of("optic",node("optic")));
        var root=engine.install(node("gun"),List.of("mount"),mount).after();
        var replacement=engine.replace(root,List.of("mount"),node("mount"));
        assertTrue(replacement.success()); assertEquals(mount,replacement.detached().orElseThrow());
        assertFalse(engine.validate(replacement.after()).complete());
        assertEquals(AssemblyEngine.Code.OCCUPIED_SLOT,engine.install(root,List.of("mount"),node("mount")).errors().getFirst().code());
        assertEquals(AssemblyEngine.Code.INVALID_PATH,engine.remove(root,List.of()).errors().getFirst().code());
        assertEquals(AssemblyEngine.Code.UNKNOWN_SLOT,engine.remove(root,List.of("bad")).errors().getFirst().code());
        assertEquals(AssemblyEngine.Code.EMPTY_SLOT,engine.remove(node("gun"),List.of("mount")).errors().getFirst().code());
        assertEquals(AssemblyEngine.Code.INVALID_PATH,engine.install(node("gun"),List.of("mount","optic"),node("optic")).errors().getFirst().code());
    }
    @Test void boundedTraversalRejectsOversizeAndDeepTreesAndAllowsRepeatedDefinitionsWithoutCycles() {
        var engine=new AssemblyEngine(new AssemblyCatalog(List.of(part("loop",new Slot("next",false,Set.of("loop"))))));
        var tree=node("loop");
        for(int i=0;i<8;i++) tree=new AssemblyNode(UUID.randomUUID(),"loop",Map.of("next",tree));
        assertTrue(engine.validate(tree).valid());
        tree=new AssemblyNode(UUID.randomUUID(),"loop",Map.of("next",tree));
        assertEquals(AssemblyEngine.Code.LIMIT_EXCEEDED,engine.validate(tree).errors().getFirst().code());
        var children=new TreeMap<String,AssemblyNode>(); for(int i=0;i<129;i++) children.put("s"+i,node("loop"));
        assertEquals(AssemblyEngine.Code.LIMIT_EXCEEDED,engine.validate(new AssemblyNode(UUID.randomUUID(),"loop",children)).errors().getFirst().code());
    }
    @Test void catalogRejectsDanglingReferencesAndNodesDefensivelyCopyMaps() {
        assertThrows(IllegalArgumentException.class,()->new AssemblyCatalog(List.of(part("root",new Slot("a",true,Set.of("missing"))))));
        assertThrows(IllegalArgumentException.class,()->new AssemblyCatalog(List.of(part("same"),part("same"))));
        var children=new HashMap<String,AssemblyNode>();var n=new AssemblyNode(UUID.randomUUID(),"root",children);
        children.put("left",node("rail"));assertTrue(n.children().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->n.children().put("left",node("rail")));
    }
}
