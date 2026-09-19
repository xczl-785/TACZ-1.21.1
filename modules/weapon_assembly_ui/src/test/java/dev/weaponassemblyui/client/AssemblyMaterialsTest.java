package dev.weaponassemblyui.client;

import dev.firearms.presentation.*;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class AssemblyMaterialsTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/weapon_assembly_ui");
    private static final Path TEST=Path.of("src/test/resources/adar-regression");
    private Map<String,ModelGeometry> models() throws Exception {
        try(var reader=Files.newBufferedReader(TEST.resolve("manifest.json"))){return ModelGeometry.load(reader);}
    }
    private String bindings() throws Exception { return Files.readString(TEST.resolve("materials.json")); }
    private AssemblyMaterials load(String bindings,Map<String,ModelGeometry> models) throws Exception {
        try(var reader=Files.newBufferedReader(ASSETS.resolve("materials/basic.json"))){return AssemblyMaterials.load(reader,new StringReader(bindings),models);}
    }
    @Test void namedRegionsOverridePartDefaultsWithoutChangingGeometry() throws Exception {
        var geometry=models();var set=load(bindings(),geometry);String stock="5c0e2ff6d174af02a1659d4a";
        assertEquals("walnut",set.resolve(stock,"body").id());assertEquals("rubber",set.resolve(stock,"butt_pad").id());
        assertEquals("metal_dark",set.resolve("5c0e2f5cd174af02a012cfc9","front_collar").id());
        assertEquals("sand_metal",set.resolve("558022b54bdc2dac148b458d","body").id());
        assertEquals("polymer",set.resolve("558022b54bdc2dac148b458d","inner_frame").id());
        var changed=load(bindings().replace("\"butt_pad\":\"rubber\"","\"butt_pad\":\"steel\""),geometry);
        assertEquals("steel",changed.resolve(stock,"butt_pad").id());assertEquals("walnut",changed.resolve(stock,"body").id());
        assertEquals("rubber",set.resolve(stock,"butt_pad").id());
        assertEquals(7260,geometry.values().stream().flatMap(m->m.meshes().stream()).mapToInt(m->m.triangles().size()).sum());
    }
    @Test void materialTyposAndInvalidParametersFailExplicitly() throws Exception {
        var geometry=models();String b=bindings();
        assertThrows(IllegalArgumentException.class,()->load(b.replace("butt_pad","missing_region"),geometry));
        assertThrows(IllegalArgumentException.class,()->load(b.replace("rubber","missing_material"),geometry));
        assertThrows(IllegalArgumentException.class,()->new AssemblyMaterials.Material("bad",0,"",Float.NaN,0,1));
        assertThrows(IllegalArgumentException.class,()->new AssemblyMaterials.Material("bad",0,"",.5f,0,0));
    }
    @Test void originalTexturesExistAndRegionsHaveUsableUvs() throws Exception {
        var geometry=models();var set=load(bindings(),geometry);
        assertEquals(6,set.all().size());
        for(var material:set.all()) {
            var file=ASSETS.resolve(material.texture().split(":",2)[1]);assertTrue(Files.isRegularFile(file));
            byte[] png=Files.readAllBytes(file);assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},Arrays.copyOf(png,8));
        }
        var stock=geometry.get("5c0e2ff6d174af02a1659d4a");
        var counts=new HashMap<String,Integer>();
        stock.meshes().forEach(m->m.triangles().forEach(t->counts.merge(t.region(),1,Integer::sum)));
        assertTrue(counts.get("body")>0&&counts.get("butt_pad")>0);
        assertTrue(stock.meshes().stream().flatMap(m->m.triangles().stream()).anyMatch(t->new HashSet<>(t.uv()).size()==3));
    }
}
