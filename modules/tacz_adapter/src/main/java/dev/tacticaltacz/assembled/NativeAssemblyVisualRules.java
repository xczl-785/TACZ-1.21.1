package dev.tacticaltacz.assembled;

import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;

/** Per-weapon visibility policy; installation and native attachment state remain the authorities. */
final class NativeAssemblyVisualRules {
    private static final Set<String> ATTACHMENTS=Set.of("SCOPE","STOCK","GRIP","LASER","EXTENDED_MAG","MUZZLE");
    private final Set<String> alwaysVisibleBones;
    private final Map<String,Map<String,Boolean>> definitions,variants;
    private final Map<String,Set<String>> boneRequirements, boneAllRequirements;
    private NativeAssemblyVisualRules(Set<String> bones,Map<String,Map<String,Boolean>> definitions,Map<String,Map<String,Boolean>> variants,Map<String,Set<String>> boneRequirements,Map<String,Set<String>> boneAllRequirements){
        alwaysVisibleBones=Set.copyOf(bones);this.definitions=definitions;this.variants=variants;this.boneRequirements=Map.copyOf(boneRequirements);this.boneAllRequirements=Map.copyOf(boneAllRequirements);
    }
    static NativeAssemblyVisualRules load(String json,Set<String> validDefinitions,Set<String> rigBones){
        try {
            var data=JsonParser.parseString(json).getAsJsonObject();
            if(data.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported schemaVersion");
            var bones=new HashSet<String>();
            for(var value:data.getAsJsonArray("alwaysVisibleBones")){
                String bone=value.getAsString();
                if(!rigBones.contains(bone)||!bones.add(bone))throw new IllegalArgumentException("Missing or duplicate rig bone: "+bone);
            }
            var definitions=requirements(data.getAsJsonObject("definitionRequirements"));
            for(var definition:definitions.keySet())if(!validDefinitions.contains(definition))throw new IllegalArgumentException("Unknown definition: "+definition);
            var dependencies=new HashMap<String,Set<String>>();
            if(data.has("boneRequirements"))for(var entry:data.getAsJsonObject("boneRequirements").entrySet()){
                if(!rigBones.contains(entry.getKey())||bones.contains(entry.getKey()))throw new IllegalArgumentException("Missing or conflicting dependent bone: "+entry.getKey());
                var allowed=new HashSet<String>();
                for(var value:entry.getValue().getAsJsonArray()){
                    String definition=value.getAsString();
                    if(!validDefinitions.contains(definition)||!allowed.add(definition))throw new IllegalArgumentException("Unknown or duplicate bone dependency: "+definition);
                }
                if(allowed.isEmpty())throw new IllegalArgumentException("Empty bone dependency: "+entry.getKey());
                dependencies.put(entry.getKey(),Set.copyOf(allowed));
            }
            var allDependencies=new HashMap<String,Set<String>>();
            if(data.has("boneAllRequirements"))for(var entry:data.getAsJsonObject("boneAllRequirements").entrySet()){
                if(!rigBones.contains(entry.getKey())||bones.contains(entry.getKey()))throw new IllegalArgumentException("Missing or conflicting dependent bone: "+entry.getKey());
                var required=new HashSet<String>();
                for(var value:entry.getValue().getAsJsonArray()){
                    String definition=value.getAsString();
                    if(!validDefinitions.contains(definition)||!required.add(definition))throw new IllegalArgumentException("Unknown or duplicate bone dependency: "+definition);
                }
                if(required.isEmpty())throw new IllegalArgumentException("Empty bone dependency: "+entry.getKey());
                allDependencies.put(entry.getKey(),Set.copyOf(required));
            }
            return new NativeAssemblyVisualRules(bones,definitions,requirements(data.getAsJsonObject("variantRequirements")),dependencies,allDependencies);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid native-visual-rules.json: "+invalid.getMessage(),invalid);}
    }
    private static Map<String,Map<String,Boolean>> requirements(JsonObject data){
        var result=new HashMap<String,Map<String,Boolean>>();
        for(var entry:data.entrySet()){
            if(entry.getKey().isBlank())throw new IllegalArgumentException("Blank rule target");
            var conditions=new HashMap<String,Boolean>();
            for(var condition:entry.getValue().getAsJsonObject().entrySet()){
                if(!ATTACHMENTS.contains(condition.getKey())||!condition.getValue().isJsonPrimitive()||!condition.getValue().getAsJsonPrimitive().isBoolean())
                    throw new IllegalArgumentException("Invalid attachment condition: "+condition.getKey());
                conditions.put(condition.getKey(),condition.getValue().getAsBoolean());
            }
            result.put(entry.getKey(),Map.copyOf(conditions));
        }
        return Map.copyOf(result);
    }
    Set<String> alwaysVisibleBones(){return alwaysVisibleBones;}
    Set<String> dependentBones(){var bones=new HashSet<>(boneRequirements.keySet());bones.addAll(boneAllRequirements.keySet());return Set.copyOf(bones);}
    boolean boneVisible(String bone,Set<String> installed){
        var required=boneRequirements.get(bone);var all=boneAllRequirements.get(bone);return (required==null||required.stream().anyMatch(installed::contains))&&(all==null||installed.containsAll(all));
    }
    boolean visible(String definition,String variant,Set<String> installed,Predicate<String> attachmentPresent){
        return installed.contains(definition)&&matches(definitions.get(definition),attachmentPresent)&&matches(variants.get(variant),attachmentPresent);
    }
    private static boolean matches(Map<String,Boolean> conditions,Predicate<String> present){
        return conditions==null||conditions.entrySet().stream().allMatch(e->present.test(e.getKey())==e.getValue());
    }
}
