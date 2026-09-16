package dev.tacticaltacz.assembled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssemblyWorkbenchLifecycleTest {
    @Test void closedOrReplacedOpeningCannotReopenFromLateReply(){
        var gate=new AssemblyWorkbenchLifecycle();var origin=new Object();var id=gate.open(origin);
        assertFalse(gate.watch(new Object()));assertFalse(gate.receive(id,origin));
        var first=gate.open(origin);var second=gate.open(origin);
        assertFalse(gate.receive(first,origin));assertTrue(gate.receive(second,origin));
        assertFalse(gate.receive(second,origin));
    }
    @Test void pendingExchangeCannotEnterPresetOrSendAnotherExchange(){
        var gate=new AssemblyWorkbenchLifecycle();var real=new Object();gate.real(real);
        var request=gate.exchange(real).orElseThrow();assertTrue(gate.pending());
        assertFalse(gate.preset(real,new Object()));assertTrue(gate.exchange(real).isEmpty());
        assertTrue(gate.receive(request,real));assertFalse(gate.pending());
        assertTrue(gate.preset(real,new Object()));
    }
    @Test void presetRejectsRealRepliesRefreshAndMutation(){
        var gate=new AssemblyWorkbenchLifecycle();var real=new Object();var draft=new Object();
        gate.real(real);var old=gate.exchange(real).orElseThrow();assertTrue(gate.receive(old,real));
        assertTrue(gate.preset(real,draft));assertTrue(gate.temporary());
        assertFalse(gate.receive(old,draft));assertFalse(gate.receive(old,real));
        assertTrue(gate.exchange(draft).isEmpty());assertTrue(gate.watch(draft));
        var newRequest=gate.open(draft);assertFalse(gate.temporary());
        assertFalse(gate.receive(old,draft));assertTrue(gate.receive(newRequest,draft));
    }
    @Test void leavingScreenRejectsResponseEvenBeforeWatchTick(){
        var gate=new AssemblyWorkbenchLifecycle();var real=new Object();gate.real(real);
        var request=gate.exchange(real).orElseThrow();
        assertFalse(gate.receive(request,new Object()));
        gate.close();assertFalse(gate.receive(request,real));assertTrue(gate.exchange(real).isEmpty());
    }
}
