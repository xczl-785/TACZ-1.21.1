package dev.tacticaltacz.assembled;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.firearms.ammunition.AssemblyFeed;
import java.util.ArrayList;
import java.util.List;

/** Legacy TaCZ weapon.json feed keys. The value object and its path rules are public. */
public final class NativeAssemblyFeed {
    public static AssemblyFeed load(JsonObject config) {
        var kind=switch(config.get("feed").getAsString()) {
            case "detachable_magazine" -> AssemblyFeed.Kind.DETACHABLE_MAGAZINE;
            case "internal_tube" -> AssemblyFeed.Kind.INTERNAL_TUBE;
            default -> throw new IllegalArgumentException("Unsupported feed strategy");
        };
        var container=path(config.getAsJsonArray(config.has("feedPath")?"feedPath":"magazinePath"));
        var capacities=new ArrayList<List<String>>();
        if(config.has("capacityPaths"))for(var value:config.getAsJsonArray("capacityPaths"))capacities.add(path(value.getAsJsonArray()));
        return new AssemblyFeed(kind,container,capacities);
    }
    private static List<String> path(JsonArray array) {
        if(array==null)throw new IllegalArgumentException("Missing feed container path");
        var out=new ArrayList<String>();for(var value:array)out.add(value.getAsString());return out;
    }
    private NativeAssemblyFeed() {}
}
