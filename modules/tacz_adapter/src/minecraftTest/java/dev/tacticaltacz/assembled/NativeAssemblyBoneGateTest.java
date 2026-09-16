package dev.tacticaltacz.assembled;

import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAssemblyBoneGateTest {
    @Test void detachedAndReinstalledRetainNativeAmmoGate(){
        var present=new AtomicBoolean(true);var loaded=new AtomicBoolean(true);
        var part=new FunctionalBedrockPart(null,"bullet_in_mag");
        var gate=NativeAssemblyBoneGate.wrap(p->{p.visible=loaded.get();return null;},present::get);
        assertNull(gate.apply(part));assertTrue(part.visible);
        present.set(false);assertNotNull(gate.apply(part));
        present.set(true);loaded.set(false);assertNull(gate.apply(part));assertFalse(part.visible);
        loaded.set(true);assertNull(gate.apply(part));assertTrue(part.visible);
    }
    @Test void additionalMagazineRendererAndAnimationVisibilityArePreserved(){
        var present=new AtomicBoolean(false);var calls=new AtomicInteger();
        var part=new FunctionalBedrockPart(null,"additional_magazine");
        IFunctionalRenderer original=(a,b,c,d,e)->calls.incrementAndGet();
        var gate=NativeAssemblyBoneGate.wrap(p->original,present::get);
        part.visible=false;gate.apply(part).render(null,null,null,0,0);assertEquals(0,calls.get());assertFalse(part.visible);
        present.set(true);assertSame(original,gate.apply(part));assertFalse(part.visible);
        part.visible=true;assertSame(original,gate.apply(part));assertTrue(part.visible);
    }
    @Test void sharedModelDoesNotKeepPreviousItemsPresenceAndPlainBonesRecover(){
        var present=new AtomicBoolean(true);var part=new FunctionalBedrockPart(null,"bullet");
        var gate=NativeAssemblyBoneGate.wrap(null,present::get);
        for(boolean installed:new boolean[]{true,false,true,false,true}){
            present.set(installed);assertEquals(installed,gate.apply(part)==null);assertTrue(part.visible);
        }
    }
}
