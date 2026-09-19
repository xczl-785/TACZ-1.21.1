package dev.weaponassembly;

import dev.firearms.assembly.*;
import dev.firearms.assembly.PartDefinition.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeaponStatsTest {
    static Modifiers mods(double w, double e, double r, double accuracy, double velocity, OptionalDouble coi,
                          double heat, double cooling, double burn) {
        return new Modifiers(w,e,r,accuracy,velocity,coi,OptionalDouble.empty(),heat,cooling,burn);
    }
    @Test void aggregatesOwnValuesAndOverridesBarrelCoiWithoutDoubleCountingBarrelAccuracy() {
        var gun = new PartDefinition("gun", List.of(new Slot("barrel", true, Set.of("barrel")), new Slot("muzzle", false, Set.of("muzzle"))),
                Set.of(), Set.of(), mods(1,50,0,0,0,OptionalDouble.empty(),1,1,1),
                Optional.of(new WeaponBase(100,150,OptionalDouble.of(.06),OptionalDouble.of(100))));
        var barrel = new PartDefinition("barrel", List.of(),Set.of(),Set.of(),mods(.5,-5,-.2,90,10,OptionalDouble.of(.04),1.2,.9,1.1),Optional.empty());
        var muzzle = new PartDefinition("muzzle",List.of(),Set.of(),Set.of(),mods(.1,-2,-.1,10,5,OptionalDouble.empty(),.5,2,1.2),Optional.empty());
        var engine = new AssemblyEngine(new AssemblyCatalog(List.of(gun,barrel,muzzle)));
        var root = new AssemblyNode(UUID.randomUUID(),"gun",Map.of("barrel",AssemblyEngineTest.node("barrel"),"muzzle",AssemblyEngineTest.node("muzzle")));
        var stats = new WeaponStats(engine).calculate(root, new WeaponStats.Context(10,0));
        assertEquals(1.6, stats.weightKg(),1e-12); assertEquals(43,stats.ergonomics());
        assertEquals(70,stats.recoilVertical(),1e-12); assertEquals(105,stats.recoilHorizontal(),1e-12);
        assertEquals(34.36*.04*.9,stats.accuracyMoa().orElseThrow(),1e-12);
        assertEquals(.6,stats.heatFactor(),1e-12); assertEquals(1.8,stats.coolingFactor(),1e-12);
        assertEquals(1.32,stats.durabilityBurnFactor(),1e-12); assertEquals(15,stats.velocityPercent());
        assertEquals(-15*(1.6-(.0007556*43*43+.02736*43+2.9159)),stats.evoErgoDelta(),1e-12);
        assertEquals(((85.5/2.25)+9.15+.06477*43)/1.04*1.04,stats.armStamina(),1e-12);
        assertFalse(stats.overswing());
    }
    @Test void invalidAndNonWeaponTreesCannotProduceAuthoritativeStats() {
        var stats = new WeaponStats(AssemblyEngineTest.engine());
        assertThrows(IllegalArgumentException.class,()->stats.calculate(AssemblyEngineTest.node("missing"),new WeaponStats.Context(10,0)));
        assertThrows(IllegalArgumentException.class,()->stats.calculate(AssemblyEngineTest.node("optic"),new WeaponStats.Context(10,0)));
        assertThrows(IllegalArgumentException.class,()->new WeaponStats.Context(52,0));
        assertThrows(IllegalArgumentException.class,()->new WeaponStats.Context(10,Double.NaN));
    }
    @Test void repeatedPartModelsCountTwiceAndUnknownMoaStaysUnknown() {
        var root=new PartDefinition("gun",List.of(new Slot("left",false,Set.of("part")),new Slot("right",false,Set.of("part"))),
                Set.of(),Set.of(),Modifiers.ZERO,Optional.of(new WeaponBase(100,100,OptionalDouble.empty(),OptionalDouble.empty())));
        var part=new PartDefinition("part",List.of(),Set.of(),Set.of(),mods(.2,3,-.1,0,0,OptionalDouble.empty(),1,1,1),Optional.empty());
        var engine=new AssemblyEngine(new AssemblyCatalog(List.of(root,part)));
        var gun=new AssemblyNode(UUID.randomUUID(),"gun",Map.of("left",AssemblyEngineTest.node("part"),"right",AssemblyEngineTest.node("part")));
        var result=new WeaponStats(engine).calculate(gun,new WeaponStats.Context(0,0));
        assertEquals(.4,result.weightKg());assertEquals(6,result.ergonomics());assertEquals(80,result.recoilVertical());
        assertTrue(result.accuracyMoa().isEmpty());assertTrue(result.sightingRange().isEmpty());
    }
    @Test void ambiguousBarrelsAndOverflowFailRatherThanDependOnTraversalOrder() {
        var root=new PartDefinition("gun",List.of(new Slot("left",false,Set.of("part")),new Slot("right",false,Set.of("part"))),
                Set.of(),Set.of(),Modifiers.ZERO,Optional.of(new WeaponBase(100,100,OptionalDouble.empty(),OptionalDouble.empty())));
        for(var profile:List.of(mods(.2,3,0,0,0,OptionalDouble.of(.04),1,1,1),
                mods(Double.MAX_VALUE,3,0,0,0,OptionalDouble.empty(),1,1,1))) {
            var part=new PartDefinition("part",List.of(),Set.of(),Set.of(),profile,Optional.empty());
            var engine=new AssemblyEngine(new AssemblyCatalog(List.of(root,part)));
            var gun=new AssemblyNode(UUID.randomUUID(),"gun",Map.of("left",AssemblyEngineTest.node("part"),"right",AssemblyEngineTest.node("part")));
            assertThrows(IllegalArgumentException.class,()->new WeaponStats(engine).calculate(gun,new WeaponStats.Context(0,0)));
        }
    }
}
