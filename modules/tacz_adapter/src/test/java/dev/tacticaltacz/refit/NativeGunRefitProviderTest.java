package dev.tacticaltacz.refit;

import com.tacz.guns.api.item.attachment.AttachmentType;
import dev.firearms.workbench.WorkbenchOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The native family keeps its own screen, so its wording, limit and target descriptor must not drift. */
class NativeGunRefitProviderTest {
    private final NativeGunRefitProvider provider = new NativeGunRefitProvider();

    @Test void keepsTheRefitWordingItsScreenExpects() {
        assertEquals("installed", provider.resultText(WorkbenchOutcome.ACCEPTED, 1));
        assertEquals("unloaded", provider.resultText(WorkbenchOutcome.ACCEPTED, 2));
        assertEquals("stale", provider.resultText(WorkbenchOutcome.STALE, 1));
        assertEquals("rejected", provider.resultText(WorkbenchOutcome.REJECTED, 2));
        assertEquals("", provider.resultText(WorkbenchOutcome.NONE, 0));
        assertEquals("unavailable", provider.unavailableText(WorkbenchOutcome.NONE, 0));
        // An exchange that already produced a reason keeps it instead of reporting a missing catalogue.
        assertEquals("stale", provider.unavailableText(WorkbenchOutcome.STALE, 1));
        assertEquals("rejected", provider.unavailableText(WorkbenchOutcome.REJECTED, 2));
    }

    @Test void keepsTheNativeCandidateLimit() {
        assertEquals(4096, provider.candidateLimit());
    }

    @Test void targetDescriptorRoundTripsAndRefusesAnythingElse() {
        assertEquals(List.of("SCOPE"), NativeGunRefitProvider.target(AttachmentType.SCOPE));
        assertEquals(AttachmentType.SCOPE, NativeGunRefitProvider.typeOf(List.of("SCOPE")));
        assertEquals(List.of(), NativeGunRefitProvider.target(AttachmentType.NONE));
        assertEquals(List.of(), NativeGunRefitProvider.target(null));
        assertEquals(AttachmentType.NONE, NativeGunRefitProvider.typeOf(List.of()));
        assertEquals(AttachmentType.NONE, NativeGunRefitProvider.typeOf(List.of("not_a_type")));
        assertEquals(AttachmentType.NONE, NativeGunRefitProvider.typeOf(List.of("SCOPE", "EXTRA")));
        assertEquals(AttachmentType.NONE, NativeGunRefitProvider.typeOf(List.of("scope")));
    }
}
