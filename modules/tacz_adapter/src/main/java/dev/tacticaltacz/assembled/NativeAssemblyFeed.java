package dev.tacticaltacz.assembled;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.firearms.assembly.AssemblyEngine;
import dev.firearms.assembly.AssemblyCatalog;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Assembly-side container identity; TaCZ still owns counts and the reload script. */
public record NativeAssemblyFeed(Kind kind, List<String> containerPath, List<List<String>> capacityPaths) {
    public enum Kind { DETACHABLE_MAGAZINE, INTERNAL_TUBE }
    public NativeAssemblyFeed {
        containerPath=checked(containerPath);
        capacityPaths=capacityPaths.stream().map(NativeAssemblyFeed::checked).toList();
    }
    public static NativeAssemblyFeed load(JsonObject config) {
        Kind kind=switch(config.get("feed").getAsString()) {
            case "detachable_magazine" -> Kind.DETACHABLE_MAGAZINE;
            case "internal_tube" -> Kind.INTERNAL_TUBE;
            default -> throw new IllegalArgumentException("Unsupported feed strategy");
        };
        var container=path(config.getAsJsonArray(config.has("feedPath")?"feedPath":"magazinePath"));
        var capacities=new ArrayList<List<String>>();
        if(config.has("capacityPaths"))for(var value:config.getAsJsonArray("capacityPaths"))capacities.add(path(value.getAsJsonArray()));
        return new NativeAssemblyFeed(kind,container,capacities);
    }
    public void validate(AssemblyCatalog catalog,String root) {
        var paths=new ArrayList<List<String>>(capacityPaths);paths.add(containerPath);
        for(var path:paths){
            Set<String> owners=Set.of(root);
            for(String slot:path){
                var children=new HashSet<String>();
                for(String owner:owners)catalog.require(owner).slot(slot).ifPresent(s->children.addAll(s.allowedParts()));
                if(children.isEmpty())throw new IllegalArgumentException("Unreachable feed path: "+path);
                owners=children;
            }
        }
    }
    /** Removing a container ancestor or replacing its capacity component refunds its stored rounds. */
    public boolean affectedBy(List<String> exchangePath) {
        if(exchangePath.isEmpty())return false;
        return ancestor(exchangePath,containerPath)||capacityPaths.stream().anyMatch(p->ancestor(exchangePath,p));
    }
    private static boolean ancestor(List<String> parent,List<String> child) {
        return parent.size()<=child.size()&&child.subList(0,parent.size()).equals(parent);
    }
    private static List<String> path(JsonArray array) {
        if(array==null)throw new IllegalArgumentException("Missing feed container path");
        var out=new ArrayList<String>();for(var value:array)out.add(value.getAsString());return checked(out);
    }
    private static List<String> checked(List<String> path) {
        if(path.isEmpty()||path.size()>AssemblyEngine.MAX_DEPTH||path.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Invalid feed path");
        return List.copyOf(path);
    }
}
