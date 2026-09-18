package dev.weaponassemblyui.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SlotLayoutTest {
    private static final SlotLayout.Bounds AREA=new SlotLayout.Bounds(28,112,1224,472);
    private static SlotLayout.Anchor anchor(int id,double x,double y) {
        return new SlotLayout.Anchor(List.of("receiver","slot"+id),new SlotLayout.Point(x,y));
    }
    private static Map<List<String>,SlotLayout.Point> place(List<SlotLayout.Anchor> anchors,double size) {
        return new SlotLayout.State().update(anchors,AREA,size,size);
    }
    private static void check(Map<List<String>,SlotLayout.Point> result,int count,double size) {
        assertEquals(count,result.size());
        var positions=new ArrayList<>(result.values());
        for(int i=0;i<positions.size();i++) {
            var p=positions.get(i);
            assertTrue(p.x()>=AREA.x()-.001&&p.y()>=AREA.y()-.001);
            assertTrue(p.x()+size<=AREA.x()+AREA.width()+.001&&p.y()+size<=AREA.y()+AREA.height()+.001);
            for(int j=0;j<i;j++)assertFalse(SlotLayout.overlaps(p,positions.get(j),size,5.99),"overlapping cards");
        }
    }
    @Test void coincidentMountsStillHaveDistinctClickableCards() {
        var anchors=new ArrayList<SlotLayout.Anchor>();
        for(int i=0;i<20;i++)anchors.add(anchor(i,640,348));
        check(place(anchors,WorkbenchSlotMetrics.CARD),20,WorkbenchSlotMetrics.CARD);
    }
    @Test void adjoiningDropdownBarsDoNotCoverNeighboringCards() {
        var anchors=new ArrayList<SlotLayout.Anchor>();
        for(int i=0;i<12;i++)anchors.add(anchor(i,640,348));
        // Each slot reserves the compact card plus its disclosure bar.
        double footprint=WorkbenchSlotMetrics.CARD+WorkbenchSlotMetrics.TOGGLE;
        check(place(anchors,footprint),12,footprint);
    }
    @Test void extremeProjectedAnchorsKeepEveryCardInsideTheWorkbench() {
        var state=new SlotLayout.State();
        for(int step=0;step<72;step++) {
            var anchors=new ArrayList<SlotLayout.Anchor>();
            for(int i=0;i<12;i++) {
                double angle=step*Math.PI/36+i*.22;
                anchors.add(anchor(i,640+900*Math.cos(angle),348+300*Math.sin(angle)));
            }
            var positions=state.update(anchors,AREA,WorkbenchSlotMetrics.CARD,WorkbenchSlotMetrics.CARD);
            check(positions,12,WorkbenchSlotMetrics.CARD);
        }
    }
    @Test void locationComesFromMountProjectionRatherThanListParity() {
        var left=place(List.of(anchor(0,100,348)),WorkbenchSlotMetrics.CARD).get(List.of("receiver","slot0"));
        var right=place(List.of(anchor(0,1180,348)),WorkbenchSlotMetrics.CARD).get(List.of("receiver","slot0"));
        assertTrue(left.x()<640);assertTrue(right.x()>640);
    }
    @Test void sameInputHasDeterministicLayout() {
        var anchors=List.of(anchor(0,400,320),anchor(1,440,320),anchor(2,640,400));
        assertEquals(place(anchors,WorkbenchSlotMetrics.CARD),place(anchors,WorkbenchSlotMetrics.CARD));
    }
}
