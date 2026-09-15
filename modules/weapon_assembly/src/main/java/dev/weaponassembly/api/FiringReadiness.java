package dev.weaponassembly.api;
import java.util.*;
/** Structural firing prerequisites only; ammunition, safety and cycling belong to the weapon runtime. */
public final class FiringReadiness {
    public record Result(boolean ready,List<AssemblyEngine.Issue> issues) { public Result { issues=List.copyOf(issues); } }
    public static Result evaluate(AssemblyEngine engine,AssemblyNode tree,List<List<String>> criticalPaths) {
        var errors=new ArrayList<>(engine.validate(tree).errors());
        for(var path:criticalPaths) {
            if(path.isEmpty())throw new IllegalArgumentException("Empty firing prerequisite");
            var node=tree;
            for(var slot:path){node=node.children().get(slot);if(node==null)break;}
            if(node==null)errors.add(new AssemblyEngine.Issue(AssemblyEngine.Code.MISSING_REQUIRED,path,"Critical firing component missing"));
        }
        return new Result(errors.isEmpty(),errors);
    }
    private FiringReadiness() {}
}
