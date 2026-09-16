package dev.tacticaltacz.assembled;

import com.mojang.blaze3d.platform.NativeImage;
import dev.weaponmodels.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Render-thread-only, bounded appearance cache. Resources are released on eviction/reload. */
public final class NativeAssemblyIcons {
    private record Assets(Map<String,ModelGeometry> geometry,AssemblyMaterials materials){}
    private record Icon(ResourceLocation location,dev.itemfoundation.client.api.ItemModelBounds.Bounds bounds){}
    private static final int SIZE=256,LIMIT=128;
    private static final Map<String,Assets> assets=new HashMap<>();
    private static final Map<String,NativeAssemblyIconRaster.Texture> textures=new HashMap<>();
    private static final LinkedHashMap<String,Icon> icons=new LinkedHashMap<>(16,.75f,true);
    public static ResourceLocation texture(ItemStack stack,ResourceLocation fallback){
        if(!(stack.getItem() instanceof AssemblyGunItem item)||!item.weapon().nativeRig)return fallback;
        return icon(stack,item.weapon()).location;
    }
    public static dev.itemfoundation.client.api.ItemModelBounds.Bounds bounds(ItemStack stack){return icon(stack,((AssemblyGunItem)stack.getItem()).weapon()).bounds;}
    private static Icon icon(ItemStack stack,AssembledWeapon weapon){
        var tree=weapon.projectEnabled(stack);
        String key=weapon.PROFILE+"/"+NativeAssemblyIconRaster.appearanceKey(tree);
        var cached=icons.get(key);if(cached!=null)return cached;
        var art=assets.computeIfAbsent(weapon.PROFILE,k->load(weapon));
        var pixels=NativeAssemblyIconRaster.bake(tree,art.geometry,art.materials,NativeAssemblyIcons::loadTexture,SIZE);
        var image=new NativeImage(SIZE,SIZE,false);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int c=pixels[y*SIZE+x];image.setPixelRGBA(x,y,(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16));}
        var manager=Minecraft.getInstance().getTextureManager();
        if(icons.size()>=LIMIT){Minecraft.getInstance().renderBuffers().bufferSource().endBatch();var oldest=icons.entrySet().iterator();manager.release(oldest.next().getValue().location);oldest.remove();}
        var location=manager.register("assembly_icon",new DynamicTexture(image));
        var bounds=NativeAssemblyIconRaster.bounds(pixels,SIZE);
        var icon=new Icon(location,new dev.itemfoundation.client.api.ItemModelBounds.Bounds(bounds[0],bounds[1],bounds[2],bounds[3]));
        icons.put(key,icon);return icon;
    }
    private static Assets load(AssembledWeapon weapon){
        var resources=Minecraft.getInstance().getResourceManager();
        try(var geometry=resources.openAsReader(ResourceLocation.parse(weapon.asset("icon_geometry.json")));
            var library=resources.openAsReader(ResourceLocation.parse(weapon.asset("icon_library.json")));
            var bindings=resources.openAsReader(ResourceLocation.parse(weapon.asset("icon_materials.json")))){
            var models=ModelGeometry.load(geometry);return new Assets(models,AssemblyMaterials.load(library,bindings,models));
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static NativeAssemblyIconRaster.Texture loadTexture(String id){return textures.computeIfAbsent(id,key->{
        try(var stream=Minecraft.getInstance().getResourceManager().open(ResourceLocation.parse(key));var image=NativeImage.read(stream)){
            int[] pixels=new int[image.getWidth()*image.getHeight()];
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getPixelRGBA(x,y);pixels[y*image.getWidth()+x]=(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16);}
            return new NativeAssemblyIconRaster.Texture(image.getWidth(),image.getHeight(),pixels);
        }catch(IOException e){throw new UncheckedIOException(e);}
    });}
    public static void clear(){var manager=Minecraft.getInstance().getTextureManager();icons.values().forEach(icon->manager.release(icon.location));icons.clear();textures.clear();assets.clear();}
    private NativeAssemblyIcons(){}
}
