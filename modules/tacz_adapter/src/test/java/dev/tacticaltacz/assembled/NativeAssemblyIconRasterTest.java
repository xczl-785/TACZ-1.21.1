package dev.tacticaltacz.assembled;

import dev.weaponassembly.api.AssemblyNode;
import dev.weaponmodels.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAssemblyIconRasterTest {
    private static AssemblyNode node(String name,Map<String,AssemblyNode> children){return new AssemblyNode(UUID.randomUUID(),name,children);}
    private static ModelGeometry geometry(float x,float z){
        var points=List.of(new ModelGeometry.Point(x,0,z),new ModelGeometry.Point(x,1,z),new ModelGeometry.Point(x,0,z+1));
        return new ModelGeometry(new ModelGeometry.Point(0,0,0),Map.of("child",new ModelGeometry.Point(0,0,0)),List.of(),List.of(new ModelGeometry.Mesh("body",List.of(new ModelGeometry.Triangle(points)))));
    }
    @Test void identityDoesNotInvalidateButAssemblyChangesDo(){
        var a=node("root",Map.of("child",node("stock",Map.of())));var b=node("root",Map.of("child",node("stock",Map.of())));
        assertEquals(NativeAssemblyIconRaster.appearanceKey(a),NativeAssemblyIconRaster.appearanceKey(b));
        assertNotEquals(NativeAssemblyIconRaster.appearanceKey(a),NativeAssemblyIconRaster.appearanceKey(node("root",Map.of())));
    }
    @Test void wholeAssemblyAndBareRootProduceDifferentImages(){
        var models=Map.of("root",geometry(0,0),"stock",geometry(0,-3));var bare=node("root",Map.of());var full=node("root",Map.of("child",node("stock",Map.of())));
        var a=NativeAssemblyIconRaster.bake(bare,models,AssemblyMaterials.white(),id->{throw new AssertionError();},64);
        var b=NativeAssemblyIconRaster.bake(full,models,AssemblyMaterials.white(),id->{throw new AssertionError();},64);
        assertTrue(Arrays.stream(a).anyMatch(c->c!=0));assertFalse(Arrays.equals(a,b));
        assertArrayEquals(b,NativeAssemblyIconRaster.bake(full,models,AssemblyMaterials.white(),id->{throw new AssertionError();},64));
    }

    @Test void transparentFrontDoesNotOccludeRearAndMuzzleProjectsLeft(){
        var models=Map.of("root",geometry(0,0),"front",geometry(-1,0));
        var library="{\"schemaVersion\":1,\"materials\":{\"back\":{\"baseColor\":\"#ffffff\",\"texture\":\"test:back\",\"roughness\":1,\"specular\":0},\"front\":{\"baseColor\":\"#ffffff\",\"texture\":\"test:front\",\"roughness\":1,\"specular\":0}}}";
        var bindings="{\"schemaVersion\":1,\"defaultMaterial\":\"back\",\"parts\":{\"front\":{\"defaultMaterial\":\"front\",\"regions\":{}}}}";
        var materials=AssemblyMaterials.load(new java.io.StringReader(library),new java.io.StringReader(bindings),models);
        var tree=node("root",Map.of("child",node("front",Map.of())));
        var pixels=NativeAssemblyIconRaster.bake(tree,models,materials,id->new NativeAssemblyIconRaster.Texture(1,1,new int[]{id.equals("test:front")?0x00ffffff:0xffff0000}),64);
        assertTrue(Arrays.stream(pixels).anyMatch(c->(c&0xff0000)!=0));
        assertTrue(Arrays.stream(pixels).allMatch(c->c==0||(c&0xffff)==0));
        // The triangle's +Z tip must be to the left of its vertical edge.
        int bottom=0,top=0;double bottomX=0,topX=0;
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)if(pixels[y*64+x]!=0){if(y>40){bottom++;bottomX+=x;}if(y<25){top++;topX+=x;}}
        assertTrue(bottom>0&&top>0);assertTrue(bottomX/bottom<topX/top);
    }
    @Test void productionPresetRemovedStockAndBareReceiver() throws Exception {
        var base=java.nio.file.Path.of("weapon-content/resources");
        var data=base.resolve("data/tacz_assembly/m4a1");
        var engine=new dev.weaponassembly.api.AssemblyEngine(dev.weaponassembly.io.AssemblyJson.readCatalog(java.nio.file.Files.readString(data.resolve("catalog.json"))));
        var full=dev.weaponassembly.io.AssemblyJson.readSnapshot(java.nio.file.Files.readString(data.resolve("scene.json")),engine);
        Map<String,ModelGeometry> models;AssemblyMaterials materials;
        try(var geometry=java.nio.file.Files.newBufferedReader(base.resolve("assets/tacz_assembly/m4a1/icon_geometry.json"));var library=java.nio.file.Files.newBufferedReader(data.resolve("library.json"));var bindings=java.nio.file.Files.newBufferedReader(data.resolve("materials.json"))){models=ModelGeometry.load(geometry);materials=AssemblyMaterials.load(library,bindings,models);}
        var textures=new HashMap<String,NativeAssemblyIconRaster.Texture>();
        for(var material:materials.all())if(!material.texture().isEmpty()){
            var image=javax.imageio.ImageIO.read(base.resolve("assets/"+material.texture().replace(':','/')).toFile());
            textures.put(material.texture(),new NativeAssemblyIconRaster.Texture(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth())));
        }
        var scenes=List.of(full,engine.remove(full,List.of("buffer","stock")).after(),node("lower_receiver",Map.of()));
        var names=List.of("full","no-stock","bare-receiver");var results=new ArrayList<int[]>();
        var output=java.nio.file.Path.of("../../build/reports/assembly-icons");java.nio.file.Files.createDirectories(output);
        for(int i=0;i<scenes.size();i++){
            long start=System.nanoTime();var pixels=NativeAssemblyIconRaster.bake(scenes.get(i),models,materials,textures::get,256);
            System.out.println(names.get(i)+" bake ms="+(System.nanoTime()-start)/1_000_000.);
            assertTrue(Arrays.stream(pixels).filter(c->c!=0).count()>100);results.add(pixels);
            var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,256,256,pixels,0,256);javax.imageio.ImageIO.write(image,"png",output.resolve(names.get(i)+".png").toFile());
        }
        assertFalse(Arrays.equals(results.get(0),results.get(1)));assertFalse(Arrays.equals(results.get(0),results.get(2)));
        assertArrayEquals(results.get(0),NativeAssemblyIconRaster.bake(full,models,materials,textures::get,256));
    }
    @Test void visibleBoundsExcludeTransparentPadding(){
        int[] pixels=new int[256];pixels[4*16+2]=0xffffffff;pixels[11*16+13]=0xffffffff;
        assertArrayEquals(new float[]{-6,-4,6,4},NativeAssemblyIconRaster.bounds(pixels,16));
        assertArrayEquals(new float[]{-8,-8,8,8},NativeAssemblyIconRaster.bounds(new int[256],16));
    }
    @Test void textureWrappingPreservesChannels(){
        var t=new NativeAssemblyIconRaster.Texture(2,1,new int[]{0xffff0000,0xff0000ff});
        assertEquals(0xffff0000,t.sample(0,0));assertEquals(0xff0000ff,t.sample(-.1,1));
    }
}
