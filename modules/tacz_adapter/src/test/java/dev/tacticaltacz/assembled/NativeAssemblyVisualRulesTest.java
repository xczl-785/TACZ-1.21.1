package dev.tacticaltacz.assembled;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAssemblyVisualRulesTest {
    private static final String EMPTY="{\"schemaVersion\":1,\"alwaysVisibleBones\":[],\"definitionRequirements\":{},\"variantRequirements\":{}}";
    @Test void neutralGunNeedsNoM4NamesOrOpticPolicy(){
        var rules=NativeAssemblyVisualRules.load(EMPTY,Set.of("slide"),Set.of());
        assertTrue(rules.visible("slide","upright",Set.of("slide"),type->true));
        assertTrue(rules.visible("slide","folded",Set.of("slide"),type->false));
        assertFalse(rules.visible("slide","always",Set.of(),type->true));
    }
    @Test void productionM4RetainsInstalledAndFoldedVisibility() throws Exception {
        String json=Files.readString(Path.of("weapon-content/resources/data/tacz_assembly/m4a1/native-visual-rules.json"));
        var bones=new HashSet<String>();for(var value:JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("alwaysVisibleBones"))bones.add(value.getAsString());
        var rules=NativeAssemblyVisualRules.load(json,Set.of("rear_sight","front_sight","barrel"),bones);
        var installed=Set.of("rear_sight","front_sight","barrel");
        for(boolean optic:List.of(false,true)){
            assertEquals(!optic,rules.visible("rear_sight","always",installed,type->optic));
            assertEquals(!optic,rules.visible("front_sight","upright",installed,type->optic));
            assertEquals(optic,rules.visible("front_sight","folded",installed,type->optic));
            assertTrue(rules.visible("barrel","always",installed,type->optic));
            assertFalse(rules.visible("front_sight","folded",Set.of("barrel"),type->optic));
        }
        assertEquals(bones,rules.alwaysVisibleBones());
    }
    @Test void conditionsComposeWithoutNamedGunOrPart(){
        String json=EMPTY.replace("\"definitionRequirements\":{}","\"definitionRequirements\":{\"housing\":{\"LASER\":true,\"GRIP\":false}}");
        var rules=NativeAssemblyVisualRules.load(json,Set.of("housing"),Set.of());
        assertTrue(rules.visible("housing","always",Set.of("housing"),Set.of("LASER")::contains));
        assertFalse(rules.visible("housing","always",Set.of("housing"),Set.of("LASER","GRIP")::contains));
    }
    @Test void invalidConfigurationFailsBeforeRendering(){
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load("{}",Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(EMPTY.replace("\"alwaysVisibleBones\":[]","\"alwaysVisibleBones\":[\"missing\"]"),Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(EMPTY.replace("\"definitionRequirements\":{}","\"definitionRequirements\":{\"missing\":{}}"),Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(EMPTY.replace("\"variantRequirements\":{}","\"variantRequirements\":{\"mode\":{\"TYPO\":true}}"),Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(EMPTY.replace("\"variantRequirements\":{}","\"variantRequirements\":{\"mode\":{\"SCOPE\":\"false\"}}"),Set.of(),Set.of()));
    }
    @Test void boneDependenciesAcceptAnyInstalledVariantAndRejectBadReferences(){
        String json=EMPTY.substring(0,EMPTY.length()-1)+",\"boneRequirements\":{\"bullet\":[\"standard\",\"extended\"]}}";
        var rules=NativeAssemblyVisualRules.load(json,Set.of("standard","extended"),Set.of("bullet"));
        assertFalse(rules.boneVisible("bullet",Set.of()));
        assertTrue(rules.boneVisible("bullet",Set.of("standard")));
        assertTrue(rules.boneVisible("bullet",Set.of("extended")));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(json,Set.of("standard"),Set.of("bullet")));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(json,Set.of("standard","extended"),Set.of()));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(json.replace("[\"standard\",\"extended\"]","[]"),Set.of("standard","extended"),Set.of("bullet")));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(json.replace("\"alwaysVisibleBones\":[]","\"alwaysVisibleBones\":[\"bullet\"]"),Set.of("standard","extended"),Set.of("bullet")));
    }

    @Test void chamberRequiresBothPhysicalPartsWhileMagazineVariantsRemainAlternatives(){
        String json="""
            {"schemaVersion":1,"alwaysVisibleBones":[],"definitionRequirements":{},"variantRequirements":{},
             "boneRequirements":{"feed":["standard","extended"]},"boneAllRequirements":{"chamber":["barrel","bolt"]}}
            """;
        var rules=NativeAssemblyVisualRules.load(json,Set.of("barrel","bolt","standard","extended"),Set.of("feed","chamber"));
        assertEquals(Set.of("feed","chamber"),rules.dependentBones());
        assertFalse(rules.boneVisible("chamber",Set.of("barrel")));
        assertFalse(rules.boneVisible("chamber",Set.of("bolt")));
        assertTrue(rules.boneVisible("chamber",Set.of("barrel","bolt")));
        assertTrue(rules.boneVisible("feed",Set.of("extended")));
        assertThrows(IllegalArgumentException.class,()->NativeAssemblyVisualRules.load(json,Set.of("barrel","standard","extended"),Set.of("feed","chamber")));
    }

    @Test void sameBoneCombinesCapacityAlternativesWithRequiredMechanism(){
        String json="""
            {"schemaVersion":1,"alwaysVisibleBones":[],"definitionRequirements":{},"variantRequirements":{},
             "boneRequirements":{"round":["standard","extended"]},"boneAllRequirements":{"round":["barrel","bolt"]}}
            """;
        var rules=NativeAssemblyVisualRules.load(json,Set.of("barrel","bolt","standard","extended"),Set.of("round"));
        assertTrue(rules.boneVisible("round",Set.of("barrel","bolt","extended")));
        assertFalse(rules.boneVisible("round",Set.of("barrel","bolt")));
        assertFalse(rules.boneVisible("round",Set.of("barrel","extended")));
    }

}
