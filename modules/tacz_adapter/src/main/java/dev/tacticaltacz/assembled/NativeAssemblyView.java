package dev.tacticaltacz.assembled;

import dev.weaponmodels.ModelGeometry;
import java.io.StringReader;
import java.util.Map;

/** Workbench geometry projection only. Held rendering remains entirely on the native rig. */
public final class NativeAssemblyView {
    public static Map<String,ModelGeometry> geometry(AssembledWeapon weapon){
        return ModelGeometry.load(new StringReader(AssembledWeapon.resource("data/"+weapon.GUN.getNamespace()+"/"+weapon.resourceDirectory+"/preview.json")));
    }
    private NativeAssemblyView(){}
}
