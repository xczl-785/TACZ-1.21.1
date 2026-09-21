package dev.tacticaltacz.assembled;

import dev.firearms.assembly.AssemblyEngine;
import dev.firearms.assembly.AssemblyJson;
import dev.firearms.assembly.AssemblyNode;
import dev.firearms.presentation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Bakes the production M4A1 content through the public raster. The pure raster contract lives in firearms. */
class NativeAssemblyIconImageTest {
    @Test void productionPresetRemovedStockAndBareReceiver() throws Exception {
        var base=java.nio.file.Path.of("weapon-content/resources");
        var data=base.resolve("data/tacz_fork_tarkov/m4a1");
        var engine=new AssemblyEngine(AssemblyJson.readCatalog(java.nio.file.Files.readString(data.resolve("catalog.json"))));
        var full=AssemblyJson.readSnapshot(java.nio.file.Files.readString(data.resolve("scene.json")),engine);
        Map<String,ModelGeometry> models;AssemblyMaterials materials;
        try(var geometry=java.nio.file.Files.newBufferedReader(data.resolve("preview.json"));var library=java.nio.file.Files.newBufferedReader(data.resolve("library.json"));var bindings=java.nio.file.Files.newBufferedReader(data.resolve("materials.json"))){models=ModelGeometry.load(geometry);materials=AssemblyMaterials.load(library,bindings,models);}
        var textures=new HashMap<String,AssemblyIconRaster.Texture>();
        for(var material:materials.all())if(!material.texture().isEmpty()){
            var image=javax.imageio.ImageIO.read(base.resolve("assets/"+material.texture().replace(':','/')).toFile());
            textures.put(material.texture(),new AssemblyIconRaster.Texture(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth())));
        }
        var scenes=List.of(full,engine.remove(full,List.of("buffer","stock")).after(),new AssemblyNode(java.util.UUID.randomUUID(),"lower_receiver",Map.of()));
        var names=List.of("full","no-stock","bare-receiver");var results=new ArrayList<int[]>();
        var output=java.nio.file.Path.of("../../build/reports/assembly-icons");java.nio.file.Files.createDirectories(output);
        for(int i=0;i<scenes.size();i++){
            long start=System.nanoTime();var pixels=AssemblyIconRaster.bake(scenes.get(i),models,materials,textures::get,256);
            System.out.println(names.get(i)+" bake ms="+(System.nanoTime()-start)/1_000_000.);
            assertTrue(Arrays.stream(pixels).filter(c->c!=0).count()>100);results.add(pixels);
            var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,256,256,pixels,0,256);javax.imageio.ImageIO.write(image,"png",output.resolve(names.get(i)+".png").toFile());
        }
        long wideStart=System.nanoTime();var wide=AssemblyIconRaster.bake(full,models,materials,textures::get,512,192);
        System.out.println("full wide bake ms="+(System.nanoTime()-wideStart)/1_000_000.);
        var wideImage=new java.awt.image.BufferedImage(512,192,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        wideImage.setRGB(0,0,512,192,wide,0,512);javax.imageio.ImageIO.write(wideImage,"png",output.resolve("full-wide.png").toFile());
        assertTrue(Arrays.stream(wide).filter(c->c!=0).count()>1000);
        assertFalse(Arrays.equals(results.get(0),results.get(1)));assertFalse(Arrays.equals(results.get(0),results.get(2)));
        assertArrayEquals(results.get(0),AssemblyIconRaster.bake(full,models,materials,textures::get,256));
    }
}
