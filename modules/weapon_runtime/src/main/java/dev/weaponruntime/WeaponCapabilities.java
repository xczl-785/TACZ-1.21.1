package dev.weaponruntime;
import dev.itemfoundation.api.assembly.*;
import dev.weaponassembly.api.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Platform-independent weapon capability rules over the existing physical assembly component. */
public final class WeaponCapabilities {
    public record Profile(String platformWeaponId,AssemblyCatalog catalog,String rootDefinition,Map<String,String> itemDefinitions,List<List<String>> criticalPaths,java.util.function.Function<ItemStack,String> definitionResolver) {
        public Profile(String id,AssemblyCatalog catalog,String root,Map<String,String> definitions,List<List<String>> paths){this(id,catalog,root,definitions,paths,s->definitions.get(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));}
        public Profile { Objects.requireNonNull(platformWeaponId);Objects.requireNonNull(definitionResolver);catalog.require(rootDefinition);itemDefinitions=Map.copyOf(itemDefinitions);criticalPaths=criticalPaths.stream().map(List::copyOf).toList(); }
    }
    public record Readiness(boolean managed,boolean ready,String reason) {}
    private static final Map<String,Profile> PROFILES=new HashMap<>();
    private static final UUID ROOT=new UUID(0,0);
    public static synchronized void register(String id,Profile profile){if(PROFILES.putIfAbsent(id,profile)!=null)throw new IllegalArgumentException("Duplicate weapon profile "+id);}
    public static Optional<Profile> profile(ItemStack gun){return Optional.ofNullable(PROFILES.get(gun.get(WeaponRuntime.PROFILE.get())));}
    public static Readiness firing(ItemStack gun,String platformWeaponId) {
        if(!gun.has(WeaponRuntime.PROFILE.get()))return new Readiness(false,true,"");
        var profile=profile(gun);if(profile.isEmpty())return new Readiness(true,false,"unknown_profile");
        var p=profile.get();if(!p.platformWeaponId.equals(platformWeaponId))return new Readiness(true,false,"wrong_weapon");
        try {
            for(var path:p.criticalPaths){
                var current=gun;
                for(var slot:path){var child=AssemblyTrees.state(current).in(slot);if(child.isEmpty())break;
                    if(!child.get().enabled())return new Readiness(true,false,"disabled_critical");current=child.get().stack();}
            }
            var result=FiringReadiness.evaluate(new AssemblyEngine(p.catalog),project(gun,p,p.rootDefinition,ROOT,0),p.criticalPaths);
            return new Readiness(true,result.ready(),result.issues().isEmpty()?"":result.issues().getFirst().code().name());
        }catch(IllegalArgumentException e){return new Readiness(true,false,"invalid_assembly");}
    }
    private static AssemblyNode project(ItemStack stack,Profile p,String definition,UUID id,int depth) {
        if(depth>AssemblyEngine.MAX_DEPTH)throw new IllegalArgumentException("Assembly too deep");
        var children=new TreeMap<String,AssemblyNode>();
        for(var installed:AssemblyTrees.state(stack).installed()) {

            var part=installed.stack();var childId=p.definitionResolver.apply(part);
            if(childId==null)throw new IllegalArgumentException("Unknown physical attachment");
            children.put(installed.slotId(),project(part,p,childId,installed.instanceId(),depth+1));
        }
        return new AssemblyNode(id,definition,children);
    }
    private WeaponCapabilities() {}
}
