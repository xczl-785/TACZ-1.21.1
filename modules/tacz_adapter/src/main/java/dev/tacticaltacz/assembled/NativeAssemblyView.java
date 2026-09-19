package dev.tacticaltacz.assembled;

import dev.firearms.presentation.ModelGeometry;
import dev.firearms.presentation.AssemblyMaterials;
import java.io.StringReader;
import java.util.Map;

/** Workbench geometry projection only. Held rendering remains entirely on the native rig. */
public final class NativeAssemblyView {
    public static Map<String,ModelGeometry> geometry(AssembledWeapon weapon){
        return ModelGeometry.load(new StringReader(AssembledWeapon.resource("data/"+weapon.GUN.getNamespace()+"/"+weapon.resourceDirectory+"/preview.json")));
    }
    public static AssemblyMaterials materials(AssembledWeapon weapon,Map<String,ModelGeometry> geometry){
        var base="data/"+weapon.GUN.getNamespace()+"/"+weapon.resourceDirectory+"/";
        return AssemblyMaterials.load(new StringReader(AssembledWeapon.resource(base+"library.json")),
            new StringReader(AssembledWeapon.resource(base+"materials.json")),geometry);
    }
    private NativeAssemblyView(){}
}
