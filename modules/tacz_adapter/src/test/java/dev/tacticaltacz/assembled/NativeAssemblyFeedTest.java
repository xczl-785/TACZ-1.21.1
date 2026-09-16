package dev.tacticaltacz.assembled;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAssemblyFeedTest {
    @Test void legacyDetachablePathStillRefundsOnlyContainerOrAncestors(){
        var policy=NativeAssemblyFeed.load(JsonParser.parseString("""
            {"feed":"detachable_magazine","magazinePath":["receiver","magazine"]}
            """).getAsJsonObject());
        assertEquals(NativeAssemblyFeed.Kind.DETACHABLE_MAGAZINE,policy.kind());
        assertTrue(policy.affectedBy(List.of("receiver")));
        assertTrue(policy.affectedBy(List.of("receiver","magazine")));
        assertFalse(policy.affectedBy(List.of("stock")));
        assertFalse(policy.affectedBy(List.of("receiver","magazine","cosmetic")));
    }
    @Test void internalTubeSeparatesContainerFromCapacityExtension(){
        var policy=NativeAssemblyFeed.load(JsonParser.parseString("""
            {"feed":"internal_tube","feedPath":["body","tube"],"capacityPaths":[["body","tube","extension"]]}
            """).getAsJsonObject());
        assertEquals(NativeAssemblyFeed.Kind.INTERNAL_TUBE,policy.kind());
        assertTrue(policy.affectedBy(List.of("body")));
        assertTrue(policy.affectedBy(List.of("body","tube")));
        assertTrue(policy.affectedBy(List.of("body","tube","extension")));
        assertFalse(policy.affectedBy(List.of("body","barrel")));
        assertFalse(policy.affectedBy(List.of()));
        assertThrows(UnsupportedOperationException.class,()->policy.containerPath().add("other"));
    }
    @Test void unknownAndEmptyContainerPoliciesFailAtLoad(){
        for(String config:List.of("{\"feed\":\"invented\",\"feedPath\":[\"tube\"]}","{\"feed\":\"internal_tube\",\"feedPath\":[]}","{\"feed\":\"internal_tube\"}"))
            assertThrows(IllegalArgumentException.class,()->NativeAssemblyFeed.load(JsonParser.parseString(config).getAsJsonObject()));
    }
}
