package dev.tacticaltacz.assembled;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeIconFailuresTest {
    @Test void failedAppearanceDoesNotBlockAnotherAndRetriesAfterCooldown(){
        var clock=new AtomicLong();var failures=new NativeIconFailures(2,30,clock::get);
        assertTrue(failures.record("m4/bad"));assertTrue(failures.blocked("m4/bad"));
        assertFalse(failures.blocked("m4/healthy"));
        clock.set(29);assertTrue(failures.blocked("m4/bad"));
        clock.set(30);assertFalse(failures.blocked("m4/bad"));
    }
    @Test void distinctFailuresCannotFloodWarningsAndNegativeCacheIsBounded(){
        var clock=new AtomicLong();var failures=new NativeIconFailures(2,30,clock::get);
        assertTrue(failures.record("one"));assertFalse(failures.record("two"));assertFalse(failures.record("three"));
        assertFalse(failures.blocked("one"));assertTrue(failures.blocked("two"));assertTrue(failures.blocked("three"));
        clock.set(30);assertTrue(failures.record("four"));
    }
    @Test void resourceReloadAllowsImmediateRetryAndNewDiagnostic(){
        var failures=new NativeIconFailures(2,30,()->0L);
        failures.record("missing-resource");failures.clear();
        assertFalse(failures.blocked("missing-resource"));assertTrue(failures.record("missing-resource"));
    }
}
