package dev.tacticaltacz.assembled;

import dev.firearms.assembly.*;
import dev.firearms.assembly.AssemblyJson;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the production catalog with the production engine; no mock Minecraft inventory. */
class NativeAssemblyRulesTest {
    private static final Path BASE=Path.of("weapon-content/resources/data/tacz_fork_tarkov/m4a1");
    private static final AssemblyCatalog CATALOG;
    private static final AssemblyEngine ENGINE;
    private static final AssemblyNode PRESET;
    private static final List<List<String>> REQUIRED;
    static {try{
        CATALOG=AssemblyJson.readCatalog(Files.readString(BASE.resolve("catalog.json")));ENGINE=new AssemblyEngine(CATALOG);
        PRESET=AssemblyJson.readSnapshot(Files.readString(BASE.resolve("scene.json")),ENGINE);
        var rows=JsonParser.parseString(Files.readString(BASE.resolve("weapon.json"))).getAsJsonObject().getAsJsonArray("requiredPaths");
        var required=new ArrayList<List<String>>();for(var row:rows){var path=new ArrayList<String>();for(var slot:row.getAsJsonArray())path.add(slot.getAsString());required.add(List.copyOf(path));}REQUIRED=List.copyOf(required);
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static AssemblyNode part(String id){return new AssemblyNode(UUID.randomUUID(),id,Map.of());}
    private static List<String> path(String s){return List.of(s.split("/"));}
    private static int count(AssemblyNode n){return 1+n.children().values().stream().mapToInt(NativeAssemblyRulesTest::count).sum();}
    @Test void approvedPresetAndRequiredMechanisms(){
        assertEquals(15,count(PRESET));assertTrue(ENGINE.validate(PRESET).complete());
        assertTrue(FiringReadiness.evaluate(ENGINE,PRESET,REQUIRED).ready());
        for(var path:REQUIRED){var removed=ENGINE.remove(PRESET,path);assertTrue(removed.success());assertFalse(FiringReadiness.evaluate(ENGINE,removed.after(),REQUIRED).ready(),path.toString());}
        for(var p:List.of("magazine","pistol_grip","buffer/stock","upper/rear_sight","upper/barrel_mount/handguard","upper/barrel_mount/barrel/muzzle","upper/barrel_mount/barrel/gas/front_sight"))
            assertTrue(FiringReadiness.evaluate(ENGINE,ENGINE.remove(PRESET,path(p)).after(),REQUIRED).ready(),p);
    }
    @Test void railHandguardRequiresRealExchangeAndReturnsItsCompleteSubtree(){
        var hg=path("upper/barrel_mount/handguard");var grip=path("upper/barrel_mount/handguard/grip");
        assertFalse(ENGINE.install(PRESET,grip,part("tacz_grip_cobra")).success());
        var replaced=ENGINE.replace(PRESET,hg,part("handguard_tactical"));assertTrue(replaced.success());assertEquals("handguard_default",replaced.detached().orElseThrow().definitionId());
        var installed=ENGINE.install(replaced.after(),grip,part("tacz_grip_cobra"));assertTrue(installed.success());
        var removed=ENGINE.remove(installed.after(),hg);assertTrue(removed.success());assertEquals(2,count(removed.detached().orElseThrow()));
        assertEquals(count(installed.after()),count(removed.after())+count(removed.detached().orElseThrow()));
    }
    @Test void bayonetNeedsRealDefaultMuzzleAndReplacementRefundsBoth(){
        var muzzle=path("upper/barrel_mount/barrel/muzzle");var bayonet=path("upper/barrel_mount/barrel/muzzle/bayonet");
        var installed=ENGINE.install(PRESET,bayonet,part("tacz_bayonet_m9"));assertTrue(installed.success());
        var replaced=ENGINE.replace(installed.after(),muzzle,part("tacz_muzzle_silencer_knight_qd"));assertTrue(replaced.success());assertEquals(2,count(replaced.detached().orElseThrow()));
        assertFalse(ENGINE.install(replaced.after(),bayonet,part("tacz_bayonet_m9")).success());
        var empty=ENGINE.remove(PRESET,muzzle);assertFalse(ENGINE.install(empty.after(),bayonet,part("tacz_bayonet_m9")).success());
        assertFalse(ENGINE.remove(empty.after(),muzzle).success()); // no free default regenerated
    }
    @Test void effectAttachmentsRejectedAndMagazineVariantsExchange(){
        for(var id:List.of("fmj","hp","i"))assertFalse(ENGINE.replace(PRESET,path("magazine"),part("tacz_ammo_mod_"+id)).success());
        for(int i=1;i<=3;i++){var changed=ENGINE.replace(PRESET,path("magazine"),part("tacz_extended_mag_"+i));assertTrue(changed.success());assertEquals("magazine_standard",changed.detached().orElseThrow().definitionId());assertEquals(15,count(changed.after()));}
    }
    @Test void definitionLoadsWithoutLegacyPresentationCalibration(){
        var weapon=new AssembledWeapon("data/tacz_fork_tarkov/m4a1/weapon.json");assertTrue(weapon.nativeRig);assertEquals(67,weapon.nativeAttachments.size());assertNull(weapon.handling);assertEquals(PRESET,weapon.PRESET);
    }
}
