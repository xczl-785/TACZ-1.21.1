package dev.weaponassemblyui.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkbenchSlotMetricsTest {
    @Test void mainCardsAndChooserUseOneCompactReferenceScale() {
        assertEquals(52,WorkbenchSlotMetrics.CARD);
        assertEquals(WorkbenchSlotMetrics.CARD,WorkbenchSlotMetrics.CHOOSER_CELL);
        assertEquals(12,WorkbenchSlotMetrics.TOGGLE);
        assertTrue(WorkbenchSlotMetrics.TOGGLE<=WorkbenchSlotMetrics.CARD*.25f);
        assertEquals(9,WorkbenchSlotMetrics.LABEL_HEIGHT);
    }
}
