package dev.tacticaltacz.assembled;

import dev.firearms.client.presentation.AssemblyIconCache;
import dev.firearms.presentation.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** TaCZ host of the shared appearance cache: this file owns content lookup, the cache owns its own lifetime. */
public final class NativeAssemblyIcons {
    private static final AssemblyIconCache CACHE=new AssemblyIconCache("tacz_fork_tarkov",new Host());
    static int canvasWidth(){return AssemblyIconCache.WIDTH;}
    static int canvasHeight(){return AssemblyIconCache.HEIGHT;}
    public static ResourceLocation texture(ItemStack stack,ResourceLocation fallback){return CACHE.texture(stack,fallback);}
    public static dev.itemfoundation.client.api.ItemModelBounds.Bounds bounds(ItemStack stack){return CACHE.bounds(stack);}
    public static void clear(){CACHE.clear();}
    private static final class Host implements AssemblyIconCache.Content {
        public String profile(ItemStack stack){
            if(!(stack.getItem() instanceof AssemblyGunItem item)||!item.weapon().assemblyIcons)return null;
            return item.weapon().PROFILE;
        }
        public dev.firearms.assembly.AssemblyNode visibleTree(ItemStack stack){
            return ((AssemblyGunItem)stack.getItem()).weapon().projectEnabled(stack);
        }
        public AssemblyIconCache.Assets assets(String profile){
            var weapon=AssembledWeapons.byId(ResourceLocation.parse(profile));
            String base="data/"+weapon.GUN.getNamespace()+"/"+weapon.resourceDirectory+"/";
            try(var geometry=AssembledWeapon.resourceReader(base+"preview.json");
                var library=AssembledWeapon.resourceReader(base+"library.json");
                var bindings=AssembledWeapon.resourceReader(base+"materials.json")){
                var models=ModelGeometry.load(geometry);return new AssemblyIconCache.Assets(models,AssemblyMaterials.load(library,bindings,models));
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    private NativeAssemblyIcons(){}
}
