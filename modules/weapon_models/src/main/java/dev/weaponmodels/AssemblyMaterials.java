package dev.weaponmodels;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Immutable appearance library. Region binding is separate from geometry and gameplay definitions. */
public final class AssemblyMaterials {
    public record Material(String id,int color,String texture,float roughness,float specular,float textureScale) {
        public Material {
            Objects.requireNonNull(id);Objects.requireNonNull(texture);
            if(!texture.isEmpty()&&!texture.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Invalid texture resource: "+texture);
            if(!Float.isFinite(roughness)||roughness<0||roughness>1||!Float.isFinite(specular)||specular<0||specular>1
                ||!Float.isFinite(textureScale)||textureScale<=0||textureScale>64)throw new IllegalArgumentException("Invalid material parameters: "+id);
        }
    }
    public record Binding(String defaultMaterial,Map<String,String> regions) {
        public Binding { Objects.requireNonNull(defaultMaterial);regions=Map.copyOf(regions); }
    }
    public static final Material WHITE=new Material("white",0xffffff,"",1,0,1);
    private final Map<String,Material> materials;
    private final Map<String,Binding> bindings;
    private final String fallback;
    private AssemblyMaterials(Map<String,Material> materials,Map<String,Binding> bindings,String fallback) {
        this.materials=Map.copyOf(materials);this.bindings=Map.copyOf(bindings);this.fallback=fallback;
    }
    public static AssemblyMaterials white() { return new AssemblyMaterials(Map.of("white",WHITE),Map.of(),"white"); }
    public Material resolve(String part,String region) {
        var binding=bindings.get(part);
        return materials.get(binding==null?fallback:binding.regions().getOrDefault(region,binding.defaultMaterial()));
    }
    public Collection<Material> all() { return materials.values(); }
    public static AssemblyMaterials load(Reader libraryReader,Reader bindingReader,Map<String,ModelGeometry> models) {
        var library=JsonParser.parseReader(libraryReader).getAsJsonObject();
        fields(library,"schemaVersion","materials");version(library);
        var materials=new HashMap<String,Material>();
        for(var entry:library.getAsJsonObject("materials").entrySet()) {
            var m=entry.getValue().getAsJsonObject();fields(m,"baseColor","texture","roughness","specular","textureScale");
            var color=m.get("baseColor").getAsString();
            if(!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Expected #RRGGBB: "+entry.getKey());
            materials.put(entry.getKey(),new Material(entry.getKey(),Integer.parseInt(color.substring(1),16),
                m.has("texture")?m.get("texture").getAsString():"",m.get("roughness").getAsFloat(),m.get("specular").getAsFloat(),
                m.has("textureScale")?m.get("textureScale").getAsFloat():1));
        }
        var root=JsonParser.parseReader(bindingReader).getAsJsonObject();fields(root,"schemaVersion","defaultMaterial","parts");version(root);
        String fallback=root.get("defaultMaterial").getAsString();requireMaterial(materials,fallback);
        var bindings=new HashMap<String,Binding>();
        for(var entry:root.getAsJsonObject("parts").entrySet()) {
            var model=models.get(entry.getKey());if(model==null)throw new IllegalArgumentException("Unknown model binding: "+entry.getKey());
            var b=entry.getValue().getAsJsonObject();fields(b,"defaultMaterial","regions");
            String def=b.get("defaultMaterial").getAsString();requireMaterial(materials,def);
            var known=new HashSet<String>();model.meshes().forEach(m->m.triangles().forEach(t->known.add(t.region())));
            var regions=new HashMap<String,String>();
            for(var region:b.getAsJsonObject("regions").entrySet()) {
                if(!known.contains(region.getKey()))throw new IllegalArgumentException("Unknown material region: "+entry.getKey()+"/"+region.getKey());
                String material=region.getValue().getAsString();requireMaterial(materials,material);regions.put(region.getKey(),material);
            }
            bindings.put(entry.getKey(),new Binding(def,regions));
        }
        return new AssemblyMaterials(materials,bindings,fallback);
    }
    private static void fields(JsonObject value,String... allowed) {
        var keys=Set.of(allowed);for(String key:value.keySet())if(!keys.contains(key))throw new IllegalArgumentException("Unknown appearance field: "+key);
    }
    private static void version(JsonObject value) {
        if(value.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported material schema");
    }
    private static void requireMaterial(Map<String,Material> materials,String id) {
        if(!materials.containsKey(id))throw new IllegalArgumentException("Unknown material: "+id);
    }
}
