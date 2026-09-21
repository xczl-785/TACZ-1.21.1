package dev.tacticaltacz.assembled;

import com.google.gson.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import dev.itemfoundation.api.assembly.*;
import dev.firearms.assembly.*;
import dev.firearms.assembly.AssemblyJson;
import dev.firearms.ammunition.AssemblyFeed;
import dev.firearms.profile.FirearmProfiles;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** One immutable content definition. Installed ItemStacks remain the sole state owner. */
public final class AssembledWeapon {
    public final String PROFILE, ROOT, resourceDirectory, modelType, itemType, caliber, authoringSource, developmentCategory;
    public final ResourceLocation GUN;
    private final String partIconDirectory;
    public final AssemblyCatalog CATALOG;
    public final AssemblyEngine ENGINE;
    public final AssemblyNode PRESET;
    public final Map<String,String> ITEMS, DEFINITIONS;
    public final Map<String,String> nativeAttachments;
    public final boolean nativeRig, assemblyIcons;
    public final NativeAssemblyProfile nativeProfile;
    public final AssemblyFeed feed;
    public final List<String> magazinePath; // Compatibility accessor for existing detachable-magazine clients.
    public final Set<String> wearableSlots;
    public final List<List<String>> requiredPaths;
    public final FireMode defaultFireMode;
    public final float meshScale;
    public final WeaponHandling handling;

    public AssembledWeapon(String resource) {
        var config = JsonParser.parseString(resource(resource)).getAsJsonObject();
        if (config.get("schemaVersion").getAsInt()!=1) throw new IllegalArgumentException("Unsupported weapon definition");
        nativeRig=config.has("nativeRig")&&config.get("nativeRig").getAsBoolean();
        assemblyIcons=config.has("assemblyIcons")&&config.get("assemblyIcons").getAsBoolean();
        PROFILE=config.get("gunId").getAsString(); GUN=ResourceLocation.parse(PROFILE);
        ROOT=config.get("rootDefinition").getAsString(); resourceDirectory=config.get("resourceDirectory").getAsString();
        partIconDirectory=config.has("partIconDirectory")?config.get("partIconDirectory").getAsString():"textures/item";
        ResourceLocation.fromNamespaceAndPath(GUN.getNamespace(),partIconDirectory+"/part.png");
        modelType=config.get("modelType").getAsString(); itemType=config.has("itemType")?config.get("itemType").getAsString():PROFILE;
        caliber=config.get("caliber").getAsString();
        authoringSource=config.get("authoringSource").getAsString();
        developmentCategory=config.get("developmentCategory").getAsString();
        feed=NativeAssemblyFeed.load(config);
        meshScale=nativeRig?1:config.get("meshScale").getAsFloat();
        if (!Float.isFinite(meshScale)||meshScale<=0) throw new IllegalArgumentException("Invalid mesh scale");
        magazinePath=feed.containerPath();
        var equipment=new LinkedHashSet<String>();
        if(config.has("wearableSlots")){
            for(var slot:config.getAsJsonArray("wearableSlots")){
                String id=slot.getAsString();
                if(ResourceLocation.tryParse(id)==null||!equipment.add(id))throw new IllegalArgumentException("Invalid/duplicate wearable slot: "+id);
            }
            if(equipment.isEmpty())throw new IllegalArgumentException("No wearable slots: "+PROFILE);
        }else equipment.addAll(List.of("tactical_inventory:primary_weapon_1","tactical_inventory:primary_weapon_2"));
        wearableSlots=Collections.unmodifiableSet(equipment);
        var paths=new ArrayList<List<String>>(); for (var path:config.getAsJsonArray("requiredPaths")) paths.add(strings(path.getAsJsonArray()));requiredPaths=List.copyOf(paths);
        defaultFireMode=FireMode.valueOf(config.get("defaultFireMode").getAsString().toUpperCase(Locale.ROOT));
        String base="data/"+GUN.getNamespace()+"/"+resourceDirectory+"/";
        handling=nativeRig?null:WeaponHandling.load(resource(base+"handling.json"));
        CATALOG=AssemblyJson.readCatalog(resource(base+"catalog.json")); ENGINE=new AssemblyEngine(CATALOG);
        feed.validate(CATALOG,ROOT);
        PRESET=AssemblyJson.readSnapshot(resource(base+"scene.json"),ENGINE);
        nativeProfile=nativeRig?NativeAssemblyProfile.load(resource(base+"native-profile.json"),CATALOG,ROOT):null;
        if(!PRESET.definitionId().equals(ROOT)||!ENGINE.validate(PRESET).complete()) throw new IllegalArgumentException("Invalid preset: "+PROFILE);
        var ids=new LinkedHashMap<String,String>();
        JsonParser.parseString(resource(base+"mapping.json")).getAsJsonObject().entrySet().forEach(e->ids.put(e.getKey(),e.getValue().getAsString()));
        if(!PROFILE.equals(ids.get(ROOT)))throw new IllegalArgumentException("Root item must match gun ID");
        var attachments=new LinkedHashMap<String,String>();
        if(nativeRig)JsonParser.parseString(resource(base+"native_attachments.json")).getAsJsonObject().entrySet().forEach(e->attachments.put(e.getKey(),e.getValue().getAsString()));
        nativeAttachments=Map.copyOf(attachments);
        ITEMS=Map.copyOf(ids); var reverse=new HashMap<String,String>();
        ids.forEach((k,v)->{if(reverse.put(v,k)!=null)throw new IllegalArgumentException("Duplicate item identity");}); DEFINITIONS=Map.copyOf(reverse);

    }
    private static List<String> strings(JsonArray array){var out=new ArrayList<String>();for(var v:array)out.add(v.getAsString());if(out.isEmpty()||out.size()>AssemblyEngine.MAX_DEPTH)throw new IllegalArgumentException("Invalid slot path");return List.copyOf(out);}
    /** Shared by actual workbench cards and resource-contract tests. */
    public ResourceLocation partIcon(String definition){
        String itemId=ITEMS.get(definition);
        if(itemId==null)throw new IllegalArgumentException("Unknown icon definition: "+definition);
        if(nativeRig)return ResourceLocation.fromNamespaceAndPath(GUN.getNamespace(),partIconDirectory+"/"+definition+".png");
        var item=ResourceLocation.parse(itemId);return item.withPath("textures/item/"+item.getPath()+".png");
    }
    public String asset(String name){return GUN.getNamespace()+":"+resourceDirectory+"/"+name;}
    public static java.io.Reader resourceReader(String path){
        var in=AssembledWeapon.class.getResourceAsStream("/"+path);
        if(in==null)throw new IllegalStateException("Missing "+path);
        return new java.io.BufferedReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
    }
    public static String resource(String path){try(var in=AssembledWeapon.class.getResourceAsStream("/"+path)){if(in==null)throw new IllegalStateException("Missing "+path);return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    public boolean isGun(ItemStack stack){return stack.getItem() instanceof AssemblyGunItem item && item.weapon()==this;}
    public boolean isPart(ItemStack stack){try{return !isGun(stack)&&definition(stack)!=null;}catch(IllegalArgumentException invalid){return false;}}
    public String definition(ItemStack stack){var attachment=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(stack);
        var id=attachment!=null&&nativeRig?nativeAttachments.get(attachment.getAttachmentId(stack).toString()):DEFINITIONS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());if(id==null)throw new IllegalArgumentException("Item outside weapon catalog: "+PROFILE);return id;}
    public static UUID identity(ItemStack stack){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(!tag.hasUUID("newmod_assembly_instance"))throw new IllegalArgumentException("Missing physical identity");return tag.getUUID("newmod_assembly_instance");}
    public static void identify(ItemStack stack){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putUUID("newmod_assembly_instance",UUID.randomUUID());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    public ItemStack createPart(String id){var key=ITEMS.get(id);if(key==null)throw new IllegalArgumentException("Unknown part: "+id);var s=nativeAttachments.containsKey(key)?com.tacz.guns.api.item.builder.AttachmentItemBuilder.create().setId(ResourceLocation.parse(key)).build():new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(key)));identify(s);return s;}
    /** Assign identity only to a proposal copy; tactical still owns the source stack. */
    public ItemStack proposalPart(ItemStack stack,UUID quoteIdentity){
        var copy=stack.copyWithCount(1);definition(copy);
        var tag=copy.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!tag.hasUUID("newmod_assembly_instance")){
            if(!nativeRig||!isPart(copy))throw new IllegalArgumentException("Missing physical identity");
            tag.putUUID("newmod_assembly_instance",quoteIdentity);copy.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        }
        return copy;
    }
    public ItemStack preset(){return materialize(PRESET);}
    private ItemStack materialize(AssemblyNode node){
        var s=createPart(node.definitionId());
        if(node.definitionId().equals(ROOT)){var gun=(IGun)s.getItem();gun.setGunId(s,GUN);gun.setFireMode(s,defaultFireMode);gun.setCurrentAmmoCount(s,0);gun.setBulletInBarrel(s,false);FirearmProfiles.assign(s,PROFILE);}
        var children=new ArrayList<AssemblyState.Installed>();node.children().forEach((slot,n)->{var part=materialize(n);children.add(new AssemblyState.Installed(slot,identity(part),part,true));});
        if(!children.isEmpty())s.set(AssemblyComponents.STATE.get(),AssemblyState.empty().updated(children));return s;
    }
    public AssemblyNode project(ItemStack stack){
        return AssemblyViews.project(stack,identity(stack),part->{
            try{return Optional.of(definition(part));}catch(IllegalArgumentException unknown){return Optional.empty();}
        }).root();
    }
    public AssemblyNode projectEnabled(ItemStack stack){return AssemblyViews.onlyEnabled(project(stack));}
    public boolean hasMagazine(ItemStack stack){return feed.kind()==AssemblyFeed.Kind.DETACHABLE_MAGAZINE&&hasFeedContainer(stack);}
    public boolean hasFeedContainer(ItemStack stack){
        try {var owner=stack;for(var slot:feed.containerPath()){var part=AssemblyTrees.state(owner).in(slot).filter(AssemblyState.Installed::enabled);if(part.isEmpty())return false;owner=part.get().stack();}return true;}
        catch(IllegalArgumentException invalid){return false;}
    }
}
