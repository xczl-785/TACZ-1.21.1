package dev.tacticaltacz;

import dev.firearms.runtime.ActionSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaCZActionEventsTest {
    @Test
    void existingTaCZLifecycleMapsToStablePublicVocabulary() {
        assertEquals(ActionSession.Event.DRAW_STARTED,
                TaCZActionEvents.action(TaCZActionEvents.Source.DRAW));
        assertEquals(ActionSession.Event.SHOT,
                TaCZActionEvents.action(TaCZActionEvents.Source.SHOT));
        assertEquals(ActionSession.Event.RELOAD_STARTED,
                TaCZActionEvents.action(TaCZActionEvents.Source.RELOAD_STARTED));
    }
}
