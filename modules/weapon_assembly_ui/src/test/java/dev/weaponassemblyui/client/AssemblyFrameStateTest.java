package dev.weaponassemblyui.client;

import dev.firearms.presentation.ModelGeometry.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyFrameStateTest {
    private static AssemblyFraming.Frame frame(float center,float scale) {
        return new AssemblyFraming.Frame(new Point(center,0,0),scale);
    }

    @Test void shorterAssembliesDoNotAutomaticallyZoomInOrRecenter() {
        var state=new AssemblyFrameState(frame(0,1));
        state.accept(frame(8,1.4f));
        assertEquals(frame(0,1),state.current());
    }

    @Test void meaningfulOverflowShrinksAndRecentersImmediately() {
        var state=new AssemblyFrameState(frame(0,1));
        state.accept(frame(10,.85f));
        assertEquals(frame(10,.85f),state.current());
        assertEquals(frame(10,.85f),state.current());
    }

    @Test void smallChangesStayInsideHysteresisAndExtremeShrinkIsBounded() {
        var state=new AssemblyFrameState(frame(0,1));
        state.accept(frame(4,.95f));
        assertEquals(frame(0,1),state.current());
        state.accept(frame(20,.1f));
        var bounded=state.current();
        assertEquals(.8f,bounded.fitScale(),1e-6);
        assertEquals(0,bounded.center().x(),1e-6,"invalid attachment bounds must not drag the receiver off-center");
    }

    @Test void explicitResetCanFitAShorterAssemblyAgain() {
        var state=new AssemblyFrameState(frame(0,1));
        state.reset(frame(5,1.5f));
        assertEquals(frame(5,1.5f),state.current());
    }

    @Test void extremeBoundsKeepTheLastTrustedTargetCenterWithoutTransition() {
        var state=new AssemblyFrameState(frame(0,1));
        state.accept(frame(10,.85f));
        assertEquals(10,state.current().center().x(),1e-6);
        state.accept(frame(100,.1f));
        var settled=state.current();
        assertEquals(10,settled.center().x(),1e-6);
        assertEquals(.8f,settled.fitScale(),1e-6);
    }
}
