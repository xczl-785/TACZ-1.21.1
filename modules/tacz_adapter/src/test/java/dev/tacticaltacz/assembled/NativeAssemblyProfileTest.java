package dev.tacticaltacz.assembled;

import com.google.gson.*;
import dev.weaponassembly.api.*;
import dev.weaponassembly.io.AssemblyJson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAssemblyProfileTest {
    private static final Path BASE=Path.of("weapon-content/resources/data/tacz_fork_tarkov/m4a1");
    private AssemblyCatalog catalog() throws Exception{return AssemblyJson.readCatalog(Files.readString(BASE.resolve("catalog.json")));}
    private String config() throws Exception{return Files.readString(BASE.resolve("native-profile.json"));}
    @Test void nativeRoutesAndNestedMuzzleSelection() throws Exception {
        var profile=NativeAssemblyProfile.load(config(),catalog(),"lower_receiver");
        var muzzle=List.of("upper","barrel_mount","barrel","muzzle");var bayonet=new ArrayList<>(muzzle);bayonet.add("bayonet");
        assertEquals(List.of("buffer","stock"),profile.path("STOCK",null,p->false));
        assertEquals(muzzle,profile.path("MUZZLE",null,p->false));
        assertEquals(bayonet,profile.path("MUZZLE","tacz:bayonet_m9",p->false));
        assertEquals(bayonet,profile.path("MUZZLE",null,p->p.equals(bayonet)));
        assertEquals(muzzle,profile.path("MUZZLE","tacz:muzzle_brake_cyclone",p->true));
        assertEquals(List.of(),profile.path("NONE",null,p->false));
    }
    @Test void pairedIronOrIndependentOpticAndNoSingleIron() throws Exception {
        var profile=NativeAssemblyProfile.load(config(),catalog(),"lower_receiver");
        var front=List.of("upper","barrel_mount","barrel","gas","front_sight");var rear=List.of("upper","rear_sight");var optic=List.of("upper","scope");
        assertFalse(profile.hasSight(p->false));assertFalse(profile.hasSight(p->p.equals(front)));assertFalse(profile.hasSight(p->p.equals(rear)));
        assertTrue(profile.hasSight(p->p.equals(front)||p.equals(rear)));assertTrue(profile.hasSight(p->p.equals(optic)));
    }
    @Test void differentlyNamedSlotTreeUsesSameResolver() throws Exception {
        // Rename every slot in the production catalog/config; there are no magic M4 names in the resolver.
        var data=JsonParser.parseString(Files.readString(BASE.resolve("catalog.json"))).getAsJsonObject();
        for(var part:data.getAsJsonArray("parts"))for(var slot:part.getAsJsonObject().getAsJsonArray("slots")){
            var s=slot.getAsJsonObject();s.addProperty("id","other_"+s.get("id").getAsString());
        }
        var configuration=JsonParser.parseString(config()).getAsJsonObject();
        java.util.function.Consumer<JsonArray> rename=path->{for(int i=0;i<path.size();i++)path.set(i,new JsonPrimitive("other_"+path.get(i).getAsString()));};
        configuration.getAsJsonObject("attachmentPaths").entrySet().forEach(e->rename.accept(e.getValue().getAsJsonArray()));
        configuration.getAsJsonObject("attachmentOverrides").entrySet().forEach(e->{for(var v:e.getValue().getAsJsonArray())rename.accept(v.getAsJsonObject().getAsJsonArray("path"));});
        for(var option:configuration.getAsJsonArray("sightAlternatives"))for(var path:option.getAsJsonArray())rename.accept(path.getAsJsonArray());
        var profile=NativeAssemblyProfile.load(configuration.toString(),AssemblyJson.readCatalog(data.toString()),"lower_receiver");
        assertEquals(List.of("other_buffer","other_stock"),profile.path("STOCK",null,p->false));
        assertTrue(profile.hasSight(p->p.equals(List.of("other_upper","other_scope"))));
        assertFalse(profile.hasSight(p->p.equals(List.of("upper","scope"))));
    }
    @Test void unreachableAndEmptyPathsRejectedAtLoad() throws Exception {
        var catalog=catalog();var valid=config();
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyProfile.load(valid.replace("\"buffer\"","\"unknown_mount\""),catalog,"lower_receiver"));
        var data=JsonParser.parseString(valid).getAsJsonObject();data.getAsJsonObject("attachmentPaths").add("STOCK",new JsonArray());
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyProfile.load(data.toString(),catalog,"lower_receiver"));
    }
}
