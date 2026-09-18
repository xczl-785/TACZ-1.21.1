package dev.weaponassemblyui.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StableSlotLayoutTest {
    private static final SlotLayout.Bounds AREA=new SlotLayout.Bounds(28,112,1224,472);
    private static final double WIDTH=WorkbenchSlotMetrics.CARD+WorkbenchSlotMetrics.TOGGLE, HEIGHT=WorkbenchSlotMetrics.CARD;
    private static SlotLayout.Anchor a(String path,double x,double y) {
        return new SlotLayout.Anchor(List.of(path.split("/")),new SlotLayout.Point(x,y));
    }
    private static Map<List<String>,SlotLayout.Point> update(SlotLayout.State state,List<SlotLayout.Anchor> anchors) {
        return state.update(anchors,AREA,WIDTH,HEIGHT);
    }
    private static void usable(Map<List<String>,SlotLayout.Point> map) {
        var points=new ArrayList<>(map.values());
        for(int i=0;i<points.size();i++) {
            var p=points.get(i);
            assertTrue(p.x()>=AREA.x()-.001&&p.x()+WIDTH<=AREA.x()+AREA.width()+.001,"horizontal bounds");
            assertTrue(p.y()>=AREA.y()-.001&&p.y()+HEIGHT<=AREA.y()+AREA.height()+.001,"vertical bounds");
            for(int j=0;j<i;j++)assertFalse(SlotLayout.overlaps(p,points.get(j),WIDTH,HEIGHT,5.99),"card/bar collision");
        }
    }
    @Test void realAdarAndRadianMountsKeepSurvivorsStableAcrossSubtreeChanges() throws Exception {
        // Captured from the formal manifests and presets at old oblique and flat muzzle-left camera poses.
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/layout-mount-samples.json")),java.nio.charset.StandardCharsets.UTF_8)) {
            for(var sample:com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                var anchors=new ArrayList<SlotLayout.Anchor>();
                for(var item:sample.getValue().getAsJsonArray()) {
                    var entry=item.getAsJsonObject();var path=new ArrayList<String>();
                    entry.getAsJsonArray("path").forEach(p->path.add(p.getAsString()));
                    anchors.add(new SlotLayout.Anchor(path,new SlotLayout.Point(entry.get("x").getAsDouble(),entry.get("y").getAsDouble())));
                }
                var state=new SlotLayout.State();var initial=update(state,anchors);usable(initial);
                double left=anchors.stream().mapToDouble(a->a.point().x()).min().orElseThrow()-35;
                double right=anchors.stream().mapToDouble(a->a.point().x()).max().orElseThrow()+35;
                double top=anchors.stream().mapToDouble(a->a.point().y()).min().orElseThrow()-45;
                double bottom=anchors.stream().mapToDouble(a->a.point().y()).max().orElseThrow()+55;
                long above=initial.values().stream().filter(p->p.y()+HEIGHT<=top+.001).count();
                long below=initial.values().stream().filter(p->p.y()>=bottom-.001).count();
                assertTrue(above>=2&&below>=2,sample.getKey()+" must surround the gun above and below");
                assertTrue(initial.values().stream().anyMatch(p->p.x()+WIDTH<=left+.001),sample.getKey()+" muzzle/left exterior");
                assertTrue(initial.values().stream().anyMatch(p->p.x()>=right-.001),sample.getKey()+" stock/right exterior");
                for(var p:initial.values())assertTrue(p.y()+HEIGHT<=top+.001||p.y()>=bottom-.001||p.x()+WIDTH<=left+.001||p.x()>=right-.001,
                        sample.getKey()+" keep the central weapon band clear");
                assertEquals(initial,update(state,anchors),sample.getKey()+" unchanged mount refresh");
                var removed=anchors.stream().filter(a->a.path().size()==1||!a.path().getFirst().equals("mod_reciever")).toList();
                var survivors=update(state,removed);usable(survivors);
                survivors.forEach((path,point)->assertEquals(initial.get(path),point,sample.getKey()+" subtree removal"));
                var restored=update(state,anchors);usable(restored);assertEquals(initial.size(),restored.size());
                survivors.forEach((path,point)->assertEquals(point,restored.get(path),sample.getKey()+" subtree restoration"));
            }
        }
    }
    @Test void shrinkingWindowRehomesExcludedCardsAndKeepsThemClickable() {
        var state=new SlotLayout.State();var anchors=new ArrayList<SlotLayout.Anchor>();
        for(int i=0;i<12;i++)anchors.add(a("slot"+i,700+i*75,350));
        state.update(anchors,new SlotLayout.Bounds(28,112,1800,472),WIDTH,HEIGHT);
        var resized=state.update(anchors,AREA,WIDTH,HEIGHT);
        usable(resized);assertEquals(12,resized.size());
        for(int i=0;i<120;i++)usable(update(state,anchors));
    }
    @Test void sameMountsStayExactlyStillAcrossContentRefreshAndReordering() {
        var state=new SlotLayout.State();
        var anchors=List.of(a("receiver",500,350),a("receiver/barrel/muzzle",260,340),a("stock",950,350));
        var initial=update(state,anchors);
        var reordered=new ArrayList<>(anchors);Collections.reverse(reordered);
        for(int i=0;i<120;i++)assertEquals(initial,update(state,reordered));
    }
    @Test void addingAndRemovingSubtreePreservesEverySurvivor() {
        var state=new SlotLayout.State();
        var base=List.of(a("receiver",540,350),a("stock",1000,350),a("magazine",600,410));
        var initial=update(state,base);
        var expanded=new ArrayList<>(base);
        expanded.add(a("receiver/barrel",490,350));expanded.add(a("receiver/barrel/muzzle",230,345));
        var added=update(state,expanded);usable(added);
        initial.forEach((path,point)->assertEquals(point,added.get(path),"existing card must not move"));
        assertEquals(initial,update(state,base));
        assertEquals(added,update(state,expanded),"reinstalled subtree returns to the same free locations");
    }
    @Test void identicalSlotNamesUnderDifferentParentsKeepDistinctState() {
        var state=new SlotLayout.State();
        var anchors=List.of(a("receiver/scope",500,310),a("handguard/scope",250,340));
        var first=update(state,anchors);assertEquals(2,first.size());usable(first);
        var left=update(state,List.of(anchors.get(1)));
        assertEquals(first.get(anchors.get(1).path()),left.get(anchors.get(1).path()));
    }
    @Test void invalidFrameCannotEraseOrPolluteValidLayout() {
        var state=new SlotLayout.State();var anchors=List.of(a("muzzle",200,350));
        var first=update(state,anchors);
        assertEquals(first,state.update(List.of(),new SlotLayout.Bounds(0,0,0,0),WIDTH,HEIGHT));
        assertEquals(first,update(state,List.of(a("muzzle",Double.NaN,0))));
        assertEquals(first,update(state,anchors));
    }
    @Test void hoveredOrExpandedCardStillFollowsTheCurrentFrame() {
        var state=new SlotLayout.State();var original=a("muzzle",260,350);
        var first=update(state,List.of(original));
        var rotated=List.of(a("muzzle",960,290),a("scope",600,330));
        var positions=state.update(rotated,AREA,WIDTH,HEIGHT);
        usable(positions);
        assertEquals(first.get(original.path()).x()+700,positions.get(original.path()).x(),.001);
        assertEquals(first.get(original.path()).y()-60,positions.get(original.path()).y(),.001);
        assertEquals(positions,state.update(rotated,AREA,WIDTH,HEIGHT));
    }
    @Test void unimpededRotationReachesTheCurrentFrameWithoutTrailing() {
        var state=new SlotLayout.State();var anchors=List.of(a("muzzle",350,340));
        var previous=update(state,anchors);var start=previous.get(anchors.getFirst().path());
        anchors=List.of(a("muzzle",650,350));
        var endFrame=update(state,anchors);usable(endFrame);
        var end=endFrame.get(anchors.getFirst().path());
        assertEquals(300,end.x()-start.x(),.1);assertEquals(10,end.y()-start.y(),.1);
        for(int i=0;i<60;i++)assertEquals(endFrame,update(state,anchors),"stopped camera must not drift");
    }
    @Test void denseRotationIsUsableAndSettledInEveryFrame() {
        var state=new SlotLayout.State();
        List<SlotLayout.Anchor> anchors=List.of();
        for(int frame=0;frame<720;frame++) {
            var moving=new ArrayList<SlotLayout.Anchor>();
            for(int i=0;i<12;i++) {
                double angle=frame*Math.PI/180+i*.15;
                moving.add(a("slot"+i,640+700*Math.cos(angle),350+220*Math.sin(angle)));
            }
            anchors=moving;var next=update(state,anchors);usable(next);
            assertEquals(next,update(state,anchors),"a rendered frame must not leave trailing motion");
        }
    }
    @Test void denseRotationDoesNotJumpBetweenTemporaryCollisionSites() {
        var state=new SlotLayout.State();
        Map<List<String>,SlotLayout.Point> previous=null;
        var previousDelta=new HashMap<List<String>,SlotLayout.Point>();
        for(int frame=0;frame<360;frame++) {
            double angle=Math.toRadians(frame);
            var anchors=new ArrayList<SlotLayout.Anchor>();
            for(int i=0;i<12;i++) {
                double along=(i-5.5)*55;
                double vertical=(i%3-1)*18;
                anchors.add(a("slot"+i,640+along*Math.cos(angle),348+vertical+along*.22*Math.sin(angle)));
            }
            var next=update(state,anchors);usable(next);
            if(previous!=null)for(var entry:next.entrySet()) {
                var old=previous.get(entry.getKey());
                var delta=new SlotLayout.Point(entry.getValue().x()-old.x(),entry.getValue().y()-old.y());
                double distance=Math.hypot(delta.x(),delta.y());
                assertTrue(distance<40,"dense rotation must not teleport "+entry.getKey()+" by "+distance);
                var last=previousDelta.get(entry.getKey());
                if(last!=null&&distance>20&&Math.hypot(last.x(),last.y())>20)
                    assertTrue(delta.x()*last.x()+delta.y()*last.y()>=0,
                            "collision avoidance must not immediately reverse "+entry.getKey());
                previousDelta.put(entry.getKey(),delta);
            }
            previous=next;
        }
    }
    @Test void interactionBoundaryAllowsOneDeterministicFullReflow() {
        var anchors=List.of(a("a",350,340),a("b",900,340),a("c",640,390));
        var state=new SlotLayout.State();update(state,anchors);
        var rotated=List.of(a("a",900,340),a("b",350,340),a("c",640,390));
        var during=update(state,rotated);usable(during);
        state.reflow();
        var after=update(state,rotated);usable(after);
        assertEquals(update(new SlotLayout.State(),rotated),after);
        assertEquals(after,update(state,rotated),"reflow must settle in one frame");
    }
    @Test void touchingCardsCanExchangeOccupiedEndpointsHorizontallyAndVertically() {
        for(boolean horizontal:List.of(true,false)) {
            var state=new SlotLayout.State();
            var anchors=List.of(a("a",350,210),a("b",horizontal?454:350,horizontal?210:296));
            var before=update(state,anchors);
            var a=before.get(anchors.get(0).path());var b=before.get(anchors.get(1).path());
            // Move each mount by the exact delta between the current adjacent cards.
            var exchanged=List.of(a("a",350+b.x()-a.x(),210+b.y()-a.y()),
                    a("b",anchors.get(1).point().x()+a.x()-b.x(),anchors.get(1).point().y()+a.y()-b.y()));
            var positions=before;
            for(int i=0;i<300;i++) {positions=update(state,exchanged);usable(positions);}
            assertEquals(b,positions.get(anchors.get(0).path()),"first reaches the initially occupied endpoint");
            assertEquals(a,positions.get(anchors.get(1).path()),"second reaches the initially occupied endpoint");
            assertEquals(positions,update(state,exchanged));
        }
    }
    @Test void twoMovingCardsCanExchangeSidesWithoutLosingTheirMounts() {
        var state=new SlotLayout.State();
        var anchors=List.of(a("a",350,340),a("b",900,340));
        var before=update(state,anchors);
        var exchanged=List.of(a("a",900,340),a("b",350,340));
        var positions=before;
        for(int i=0;i<240;i++) {positions=update(state,exchanged);usable(positions);}
        assertEquals(before.get(anchors.get(0).path()).x()+550,positions.get(anchors.get(0).path()).x(),.1);
        assertEquals(before.get(anchors.get(1).path()).x()-550,positions.get(anchors.get(1).path()).x(),.1);
        assertEquals(positions,update(state,exchanged));
    }
    @Test void rotatingPastANeighborResolvesInTheCurrentFrame() {
        var state=new SlotLayout.State();
        var initial=List.of(a("moving",350,320),a("pinned",650,320));
        var before=update(state,initial);
        var moved=List.of(a("moving",950,320),initial.get(1));
        var positions=before;
        for(int i=0;i<240;i++) {
            var next=state.update(moved,AREA,WIDTH,HEIGHT);
            usable(next);
            positions=next;
        }
        assertEquals(before.get(initial.get(0).path()).x()+600,positions.get(initial.get(0).path()).x(),.1);
        assertEquals(before.get(initial.get(0).path()).y(),positions.get(initial.get(0).path()).y(),.1);
        assertEquals(positions,state.update(moved,AREA,WIDTH,HEIGHT));
    }
    @Test void coincidentMountsReserveRectangularFootprintsWithoutResizingCards() {
        var state=new SlotLayout.State();var anchors=new ArrayList<SlotLayout.Anchor>();
        for(int i=0;i<24;i++)anchors.add(a("slot"+i,640,350));
        var positions=update(state,anchors);assertEquals(24,positions.size());usable(positions);
    }
}
