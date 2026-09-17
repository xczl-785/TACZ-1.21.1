package dev.weaponassemblyui.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChooserLayoutTest {
    @Test void adaptsColumnsAndPanelToCandidateCount() {
        assertLayout(0,1,0,72,72,false);
        assertLayout(1,1,1,72,128,false);
        assertLayout(4,2,2,128,184,false);
        assertLayout(9,3,3,184,240,false);
        assertLayout(16,4,4,240,296,false);
        assertLayout(23,5,5,296,352,false);
        assertLayout(25,5,5,296,352,false);
    }

    @Test void capsVisibleCandidateRowsAndEnablesScrollingAfterTwentyFive() {
        var layout=ChooserLayout.forCount(26);
        assertEquals(5,layout.columns());
        assertEquals(6,layout.rows());
        assertEquals(5,layout.visibleRows());
        assertEquals(308,layout.width());
        assertEquals(352,layout.height());
        assertEquals(332,layout.contentHeight());
        assertTrue(layout.scrollable());
    }

    @Test void rejectsNegativeCandidateCounts() {
        assertThrows(IllegalArgumentException.class,()->ChooserLayout.forCount(-1));
    }

    private static void assertLayout(int count,int columns,int rows,int width,int height,boolean scrollable) {
        var layout=ChooserLayout.forCount(count);
        assertEquals(columns,layout.columns());
        assertEquals(rows,layout.rows());
        assertEquals(rows,layout.visibleRows());
        assertEquals(width,layout.width());
        assertEquals(height,layout.height());
        assertEquals(scrollable,layout.scrollable());
    }
}
