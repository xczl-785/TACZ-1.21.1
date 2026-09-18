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
    // GunItemRendererWrapper displays this texture on a square GUI quad. Keep the
    // backing raster square so the model is not geometrically stretched in slots.
    private static final int WIDTH=512,HEIGHT=512;
    private static final long CACHE_BYTES=64L*1024*1024;
    private static final int LIMIT=(int)Math.min(128,CACHE_BYTES/(WIDTH*HEIGHT*4L));
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final NativeIconFailures failures=new NativeIconFailures(LIMIT,30_000_000_000L,System::nanoTime);
    private static final dev.itemfoundation.client.api.ItemModelBounds.Bounds NOMINAL_BOUNDS=new dev.itemfoundation.client.api.ItemModelBounds.Bounds(-8,-8,8,8);
    private static long serial;
    private static final Map<String,Assets> assets=new HashMap<>();
    private static final Map<String,NativeAssemblyIconRaster.Texture> textures=new HashMap<>();
    private static final LinkedHashMap<String,Icon> icons=new LinkedHashMap<>(16,.75f,true);
    static int canvasWidth(){return WIDTH;}
    static int canvasHeight(){return HEIGHT;}
    public static ResourceLocation texture(ItemStack stack,ResourceLocation fallback){
        var icon=safeIcon(stack);return icon==null?fallback:icon.location;
    }
    public static dev.itemfoundation.client.api.ItemModelBounds.Bounds bounds(ItemStack stack){
        var icon=safeIcon(stack);return icon==null?NOMINAL_BOUNDS:icon.bounds;
    }
    private static Icon safeIcon(ItemStack stack){
        if(!(stack.getItem() instanceof AssemblyGunItem item)||!item.weapon().assemblyIcons)return null;
        try{return icon(stack,item.weapon());}
        catch(RuntimeException failure){
            // Invalid physical trees have no appearance key. Do not prevent healthy items of this profile rendering.
            report(item.weapon().PROFILE+"/invalid",failure);return null;
        }
    }
    private static void report(String key,RuntimeException failure){
        if(failures.record(key))LOGGER.warn("Assembly icon unavailable for {}; using the native icon and nominal bounds (warnings limited to one per 30 seconds)",key,failure);
    }
    private static Icon icon(ItemStack stack,AssembledWeapon weapon){
        var tree=weapon.projectEnabled(stack);
        String key=weapon.PROFILE+"/"+NativeAssemblyIconRaster.appearanceKey(tree);
        var cached=icons.get(key);if(cached!=null)return cached;
        if(failures.blocked(key))return null;
        try {
            var art=assets.computeIfAbsent(weapon.PROFILE,k->load(weapon));
            var pixels=NativeAssemblyIconRaster.bake(tree,art.geometry,art.materials,NativeAssemblyIcons::loadTexture,WIDTH,HEIGHT);
            if(Arrays.stream(pixels).noneMatch(c->(c>>>24)!=0))throw new IllegalStateException("Assembly icon has no visible geometry");
            var bounds=NativeAssemblyIconRaster.bounds(pixels,WIDTH,HEIGHT);
            var manager=Minecraft.getInstance().getTextureManager();
            if(icons.size()>=LIMIT){Minecraft.getInstance().renderBuffers().bufferSource().endBatch();var oldest=icons.entrySet().iterator();manager.release(oldest.next().getValue().location);oldest.remove();}
            var location=ResourceLocation.fromNamespaceAndPath("tacz_fork_tarkov","dynamic/assembly_icon_"+(serial++));
            var image=new NativeImage(WIDTH,HEIGHT,false);
            DynamicTexture texture=null;
            try {
                for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int c=pixels[y*WIDTH+x];image.setPixelRGBA(x,y,(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16));}
                texture=new DynamicTexture(image);
                manager.register(location,texture);
                dev.weaponassemblyui.client.AssemblyTextureQuality.prepare(location);
                var icon=new Icon(location,new dev.itemfoundation.client.api.ItemModelBounds.Bounds(bounds[0],bounds[1],bounds[2],bounds[3]));
                icons.put(key,icon);return icon;
            }catch(RuntimeException failure){
                manager.release(location);
                if(texture!=null)texture.close();else image.close();
                throw failure;
            }
        }catch(RuntimeException failure){report(key,failure);return null;}
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
    public static void clear(){var manager=Minecraft.getInstance().getTextureManager();icons.values().forEach(icon->manager.release(icon.location));icons.clear();textures.clear();assets.clear();failures.clear();}
    private NativeAssemblyIcons(){}
}
