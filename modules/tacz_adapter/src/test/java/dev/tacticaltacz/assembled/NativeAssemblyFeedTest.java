package dev.tacticaltacz.assembled;

import com.google.gson.JsonParser;
import dev.firearms.ammunition.AssemblyFeed;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Legacy TaCZ weapon.json feed keys; the path rules themselves live with the public value object. */
class NativeAssemblyFeedTest {
    @Test void legacyDetachableKeyStillLoadsThePublicPolicy(){
        var policy=NativeAssemblyFeed.load(JsonParser.parseString("""
            {"feed":"detachable_magazine","magazinePath":["receiver","magazine"]}
            """).getAsJsonObject());
        assertEquals(AssemblyFeed.Kind.DETACHABLE_MAGAZINE,policy.kind());
        assertEquals(List.of("receiver","magazine"),policy.containerPath());
        assertTrue(policy.capacityPaths().isEmpty());
    }
    @Test void internalTubeReadsContainerAndCapacityKeys(){
        var policy=NativeAssemblyFeed.load(JsonParser.parseString("""
            {"feed":"internal_tube","feedPath":["body","tube"],"capacityPaths":[["body","tube","extension"]]}
            """).getAsJsonObject());
        assertEquals(AssemblyFeed.Kind.INTERNAL_TUBE,policy.kind());
        assertEquals(List.of("body","tube"),policy.containerPath());
        assertEquals(List.of(List.of("body","tube","extension")),policy.capacityPaths());
    }
    @Test void unknownAndEmptyContainerPoliciesFailAtLoad(){
        for(String config:List.of("{\"feed\":\"invented\",\"feedPath\":[\"tube\"]}","{\"feed\":\"internal_tube\",\"feedPath\":[]}","{\"feed\":\"internal_tube\"}"))
            assertThrows(IllegalArgumentException.class,()->NativeAssemblyFeed.load(JsonParser.parseString(config).getAsJsonObject()));
    }
}
