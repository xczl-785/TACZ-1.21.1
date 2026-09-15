package dev.weaponassembly;
import dev.weaponassembly.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FiringReadinessTest {
    private PartDefinition part(String id,List<PartDefinition.Slot> slots){return new PartDefinition(id,slots,Set.of(),Set.of(),PartDefinition.Modifiers.ZERO,Optional.empty());}
    @Test void missingBarrelBlocksAndRestoringItRecovers() {
        var barrel=part("barrel",List.of());var receiver=part("receiver",List.of(new PartDefinition.Slot("barrel",true,Set.of("barrel"))));
        var gun=part("gun",List.of(new PartDefinition.Slot("receiver",true,Set.of("receiver")),new PartDefinition.Slot("magazine",true,Set.of())));
        var engine=new AssemblyEngine(new AssemblyCatalog(List.of(gun,receiver,barrel)));
        var root=new AssemblyNode(UUID.randomUUID(),"gun",Map.of("receiver",AssemblyNode.leaf(UUID.randomUUID(),"receiver")));
        var path=List.of("receiver","barrel");
        assertFalse(FiringReadiness.evaluate(engine,root,List.of(path)).ready());
        var installed=engine.install(root,path,AssemblyNode.leaf(UUID.randomUUID(),"barrel")).after();
        assertTrue(FiringReadiness.evaluate(engine,installed,List.of(path)).ready(),"missing magazine is not automatically a firing prohibition");
        assertFalse(FiringReadiness.evaluate(engine,engine.remove(installed,List.of("receiver")).after(),List.of(path)).ready());
    }
    @Test void malformedAssemblyNeverPasses() {
        var e=new AssemblyEngine(new AssemblyCatalog(List.of(part("gun",List.of()))));
        assertFalse(FiringReadiness.evaluate(e,new AssemblyNode(UUID.randomUUID(),"gun",Map.of("unknown",AssemblyNode.leaf(UUID.randomUUID(),"gun"))),List.of()).ready());
    }
}
