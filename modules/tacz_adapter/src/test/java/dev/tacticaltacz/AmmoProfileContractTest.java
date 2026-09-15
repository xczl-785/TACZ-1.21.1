package dev.tacticaltacz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Retired development items must not leak through stale packaged resources. */
class AmmoProfileContractTest {
    @Test void retiredRoundsHaveNoPackagedResources() {
        for(String id:new String[]{"test_flesh_9x19","test_ap_9x19"}) {
            assertNull(getClass().getResource("/data/tarkov_content/item_foundation/items/"+id+".json"));
            assertNull(getClass().getResource("/assets/tarkov_content/models/item/"+id+".json"));
        }
        assertNull(getClass().getResource("/data/tarkov_content/item_foundation/categories/test_ammunition.json"));
        assertNull(getClass().getResource("/data/tarkov_content/item_foundation/identities/test_ammunition.json"));
    }
}
