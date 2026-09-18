package dev.weaponassemblyui.client;

import dev.weaponmodels.ModelGeometry.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyFrameStateTest {
    private static AssemblyFraming.Frame frame(float center,float scale) {
        return new AssemblyFraming.Frame(new Point(center,0,0),scale);
    }

    @Test void shorterAssembliesDoNotAutomaticallyZoomInOrRecenter() {
        var state=new AssemblyFrameState(frame(0,1),0);
        state.accept(frame(8,1.4f),1);
        assertEquals(frame(0,1),state.current(AssemblyFrameState.TRANSITION_NANOS+1));
    }

    @Test void meaningfulOverflowShrinksAndRecentersSmoothly() {
        var state=new AssemblyFrameState(frame(0,1),0);
        state.accept(frame(10,.85f),10);
        var halfway=state.current(10+AssemblyFrameState.TRANSITION_NANOS/2);
        assertTrue(halfway.fitScale()<1&&halfway.fitScale()>.85f);
        assertTrue(halfway.center().x()>0&&halfway.center().x()<10);
        assertEquals(frame(10,.85f),state.current(10+AssemblyFrameState.TRANSITION_NANOS));
    }

    @Test void smallChangesStayInsideHysteresisAndExtremeShrinkIsBounded() {
        var state=new AssemblyFrameState(frame(0,1),0);
        state.accept(frame(4,.95f),1);
        assertEquals(frame(0,1),state.current(AssemblyFrameState.TRANSITION_NANOS+1));
        state.accept(frame(20,.1f),AssemblyFrameState.TRANSITION_NANOS+2);
        var bounded=state.current(2*AssemblyFrameState.TRANSITION_NANOS+2);
        assertEquals(.8f,bounded.fitScale(),1e-6);
        assertEquals(0,bounded.center().x(),1e-6,"invalid attachment bounds must not drag the receiver off-center");
    }

    @Test void explicitResetCanFitAShorterAssemblyAgain() {
        var state=new AssemblyFrameState(frame(0,1),0);
        state.reset(frame(5,1.5f),10);
        assertEquals(frame(5,1.5f),state.current(10));
    }

    @Test void extremeBoundsDuringATransitionKeepTheLastTrustedTargetCenter() {
        var state=new AssemblyFrameState(frame(0,1),0);
        state.accept(frame(10,.85f),10);
        long halfway=10+AssemblyFrameState.TRANSITION_NANOS/2;
        assertEquals(5,state.current(halfway).center().x(),1e-6);
        state.accept(frame(100,.1f),halfway);
        var settled=state.current(halfway+AssemblyFrameState.TRANSITION_NANOS);
        assertEquals(10,settled.center().x(),1e-6);
        assertEquals(.8f,settled.fitScale(),1e-6);
    }
}
