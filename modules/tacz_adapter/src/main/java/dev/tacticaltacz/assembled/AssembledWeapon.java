package dev.tacticaltacz.assembled;

import com.google.gson.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import dev.itemfoundation.api.assembly.*;
import dev.weaponassembly.api.*;
import dev.weaponassembly.io.AssemblyJson;
import dev.weaponruntime.WeaponRuntime;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** One immutable content definition. Installed ItemStacks remain the sole state owner. */
public final class AssembledWeapon {
    public final String PROFILE, ROOT, resourceDirectory, modelType, itemType, caliber, developmentSource;
    public final ResourceLocation GUN;
    public final AssemblyCatalog CATALOG;
    public final AssemblyEngine ENGINE;
    public final AssemblyNode PRESET;
    public final Map<String,String> ITEMS, DEFINITIONS;
    public final List<String> magazinePath;
    public final List<List<String>> requiredPaths;
    public final FireMode defaultFireMode;
    public final float meshScale;
    public final WeaponHandling handling;

    public AssembledWeapon(String resource) {
        var config = JsonParser.parseString(resource(resource)).getAsJsonObject();
        if (config.get("schemaVersion").getAsInt()!=1) throw new IllegalArgumentException("Unsupported weapon definition");
        PROFILE=config.get("gunId").getAsString(); GUN=ResourceLocation.parse(PROFILE);
        ROOT=config.get("rootDefinition").getAsString(); resourceDirectory=config.get("resourceDirectory").getAsString();
        modelType=config.get("modelType").getAsString(); itemType=config.has("itemType")?config.get("itemType").getAsString():PROFILE;
        caliber=config.get("caliber").getAsString(); developmentSource=config.get("developmentSource").getAsString();
        if (!"detachable_magazine".equals(config.get("feed").getAsString())) throw new IllegalArgumentException("Unsupported feed strategy: "+PROFILE);
        meshScale=config.get("meshScale").getAsFloat();
        if (!Float.isFinite(meshScale)||meshScale<=0) throw new IllegalArgumentException("Invalid mesh scale");
        magazinePath=strings(config.getAsJsonArray("magazinePath"));
        var paths=new ArrayList<List<String>>(); for (var path:config.getAsJsonArray("requiredPaths")) paths.add(strings(path.getAsJsonArray()));requiredPaths=List.copyOf(paths);
        defaultFireMode=FireMode.valueOf(config.get("defaultFireMode").getAsString().toUpperCase(Locale.ROOT));
        String base="data/"+GUN.getNamespace()+"/"+resourceDirectory+"/";
        handling=WeaponHandling.load(resource(base+"handling.json"));
        CATALOG=AssemblyJson.readCatalog(resource(base+"catalog.json")); ENGINE=new AssemblyEngine(CATALOG);
        PRESET=AssemblyJson.readSnapshot(resource(base+"scene.json"),ENGINE);
        if(!PRESET.definitionId().equals(ROOT)||!ENGINE.validate(PRESET).complete()) throw new IllegalArgumentException("Invalid preset: "+PROFILE);
        var ids=new LinkedHashMap<String,String>();
        JsonParser.parseString(resource(base+"mapping.json")).getAsJsonObject().entrySet().forEach(e->ids.put(e.getKey(),e.getValue().getAsString()));
        if(!PROFILE.equals(ids.get(ROOT)))throw new IllegalArgumentException("Root item must match gun ID");
        ITEMS=Map.copyOf(ids); var reverse=new HashMap<String,String>();
        ids.forEach((k,v)->{if(reverse.put(v,k)!=null)throw new IllegalArgumentException("Duplicate item identity");}); DEFINITIONS=Map.copyOf(reverse);

    }
    private static List<String> strings(JsonArray array){var out=new ArrayList<String>();for(var v:array)out.add(v.getAsString());if(out.isEmpty()||out.size()>AssemblyEngine.MAX_DEPTH)throw new IllegalArgumentException("Invalid slot path");return List.copyOf(out);}
    public String asset(String name){return GUN.getNamespace()+":"+resourceDirectory+"/"+name;}
    public static String resource(String path){try(var in=AssembledWeapon.class.getResourceAsStream("/"+path)){if(in==null)throw new IllegalStateException("Missing "+path);return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    public boolean isGun(ItemStack stack){return stack.getItem() instanceof AssemblyGunItem item && item.weapon()==this;}
    public boolean isPart(ItemStack stack){return !isGun(stack)&&DEFINITIONS.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());}
    public String definition(ItemStack stack){var id=DEFINITIONS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());if(id==null)throw new IllegalArgumentException("Item outside weapon catalog: "+PROFILE);return id;}
    public static UUID identity(ItemStack stack){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(!tag.hasUUID("newmod_assembly_instance"))throw new IllegalArgumentException("Missing physical identity");return tag.getUUID("newmod_assembly_instance");}
    public static void identify(ItemStack stack){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putUUID("newmod_assembly_instance",UUID.randomUUID());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    public ItemStack createPart(String id){var key=ITEMS.get(id);if(key==null)throw new IllegalArgumentException("Unknown part: "+id);var s=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(key)));identify(s);return s;}
    public ItemStack preset(){return materialize(PRESET);}
    private ItemStack materialize(AssemblyNode node){
        var s=createPart(node.definitionId());
        if(node.definitionId().equals(ROOT)){var gun=(IGun)s.getItem();gun.setGunId(s,GUN);gun.setFireMode(s,defaultFireMode);gun.setCurrentAmmoCount(s,0);gun.setBulletInBarrel(s,false);s.set(WeaponRuntime.PROFILE.get(),PROFILE);}
        var children=new ArrayList<AssemblyState.Installed>();node.children().forEach((slot,n)->{var part=materialize(n);children.add(new AssemblyState.Installed(slot,identity(part),part,true));});
        if(!children.isEmpty())s.set(AssemblyComponents.STATE.get(),AssemblyState.empty().updated(children));return s;
    }
    public AssemblyNode project(ItemStack stack){return project(stack,identity(stack),0);}
    private AssemblyNode project(ItemStack stack,UUID identity,int depth){
        if(depth>AssemblyEngine.MAX_DEPTH)throw new IllegalArgumentException("Too deep");var children=new TreeMap<String,AssemblyNode>();
        for(var part:AssemblyTrees.state(stack).installed())children.put(part.slotId(),project(part.stack(),part.instanceId(),depth+1));
        return new AssemblyNode(identity,definition(stack),children);
    }
    public AssemblyNode projectEnabled(ItemStack stack){return enabled(stack,identity(stack),0);}
    private AssemblyNode enabled(ItemStack stack,UUID id,int depth){
        if(depth>AssemblyEngine.MAX_DEPTH)throw new IllegalArgumentException("Too deep");
        var children=new TreeMap<String,AssemblyNode>();
        for(var part:AssemblyTrees.state(stack).installed())if(part.enabled())children.put(part.slotId(),enabled(part.stack(),part.instanceId(),depth+1));
        return new AssemblyNode(id,definition(stack),children);
    }
    public boolean hasMagazine(ItemStack stack){
        try {var owner=stack;for(var slot:magazinePath){var part=AssemblyTrees.state(owner).in(slot).filter(AssemblyState.Installed::enabled);if(part.isEmpty())return false;owner=part.get().stack();}return true;}
        catch(IllegalArgumentException invalid){return false;}
    }
}
