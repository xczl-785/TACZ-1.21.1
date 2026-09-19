package dev.tacticaltacz.assembled;

import com.google.gson.*;
import dev.firearms.assembly.*;
import java.util.*;
import java.util.function.Predicate;

/** Per-gun native slot routing. No inventory copies and no built-in M4 slot names. */
public final class NativeAssemblyProfile {
    private static final Set<String> TYPES=Set.of("SCOPE","STOCK","GRIP","LASER","EXTENDED_MAG","MUZZLE");
    public record Route(List<String> path,Set<String> attachmentIds){
        public Route { path=List.copyOf(path);attachmentIds=Set.copyOf(attachmentIds); }
    }
    private final Map<String,List<String>> paths;
    private final Map<String,List<Route>> overrides;
    private final List<List<List<String>>> sightAlternatives;
    private NativeAssemblyProfile(Map<String,List<String>> paths,Map<String,List<Route>> overrides,List<List<List<String>>> sights){
        this.paths=Map.copyOf(paths);this.overrides=Map.copyOf(overrides);sightAlternatives=List.copyOf(sights);
    }
    public static NativeAssemblyProfile load(String json,AssemblyCatalog catalog,String root){
        var data=JsonParser.parseString(json).getAsJsonObject();
        if(data.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported native assembly profile");
        var paths=new HashMap<String,List<String>>();
        data.getAsJsonObject("attachmentPaths").entrySet().forEach(e->{requireType(e.getKey());paths.put(e.getKey(),path(e.getValue(),catalog,root));});
        var overrides=new HashMap<String,List<Route>>();
        data.getAsJsonObject("attachmentOverrides").entrySet().forEach(e->{
            if(!paths.containsKey(e.getKey()))throw new IllegalArgumentException("Override without base route: "+e.getKey());
            var routes=new ArrayList<Route>();var seen=new HashSet<String>();
            for(var value:e.getValue().getAsJsonArray()){
                var row=value.getAsJsonObject();var ids=new HashSet<String>();
                for(var id:row.getAsJsonArray("attachmentIds")){String key=id.getAsString();if(!key.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||!seen.add(key))throw new IllegalArgumentException("Invalid or duplicate attachment override: "+key);ids.add(key);}
                if(ids.isEmpty())throw new IllegalArgumentException("Empty attachment override");
                routes.add(new Route(path(row.get("path"),catalog,root),ids));
            }
            overrides.put(e.getKey(),List.copyOf(routes));
        });
        var sights=new ArrayList<List<List<String>>>();
        for(var alternative:data.getAsJsonArray("sightAlternatives")){
            var required=new ArrayList<List<String>>();for(var p:alternative.getAsJsonArray())required.add(path(p,catalog,root));
            if(required.isEmpty())throw new IllegalArgumentException("Empty sight alternative");sights.add(List.copyOf(required));
        }
        if(sights.isEmpty())throw new IllegalArgumentException("Declare at least one sight alternative");
        return new NativeAssemblyProfile(paths,overrides,sights);
    }
    private static void requireType(String name){if(!TYPES.contains(name))throw new IllegalArgumentException("Unsupported native attachment type: "+name);}
    private static List<String> path(JsonElement value,AssemblyCatalog catalog,String root){
        var result=new ArrayList<String>();var owners=Set.of(root);
        for(var slot:value.getAsJsonArray()){
            String name=slot.getAsString();if(name.isBlank())throw new IllegalArgumentException("Blank slot");
            var children=new HashSet<String>();for(var owner:owners)catalog.require(owner).slot(name).ifPresent(s->children.addAll(s.allowedParts()));
            if(children.isEmpty())throw new IllegalArgumentException("Unreachable configured slot: "+name);
            result.add(name);owners=children;
        }
        if(result.isEmpty()||result.size()>AssemblyEngine.MAX_DEPTH)throw new IllegalArgumentException("Invalid native slot path");return List.copyOf(result);
    }
    /** With no replacement, prefer a present nested attachment before its parent adapter. */
    public List<String> path(String type,String replacement,Predicate<List<String>> present){
        for(var route:overrides.getOrDefault(type,List.of()))if(replacement==null?present.test(route.path):route.attachmentIds.contains(replacement))return route.path;
        return paths.getOrDefault(type,List.of());
    }
    public boolean hasSight(Predicate<List<String>> present){return sightAlternatives.stream().anyMatch(option->option.stream().allMatch(present));}
}
