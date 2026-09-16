package dev.weaponassemblyui.session;

import dev.weaponassembly.api.*;
import java.util.*;

/** UI-facing host boundary. A real inventory host must authorize and atomically commit its own exchanges. */
public interface WorkbenchAccess {
    AssemblyCatalog catalog();
    AssemblyNode tree();
    List<AssemblyNode> stock();
    List<AssemblyNode> detached();
    List<String> selectedPath();
    Optional<AssemblySession.Preview> preview();
    List<AssemblyEngine.Issue> feedback();
    WeaponStats.Values stats();
    /** Explain when the host uses a different stat system instead of showing fabricated zero values. */
    default Optional<String> statsExplanation(){return Optional.empty();}
    /** Temporary draft backed by a virtual catalog rather than player inventory. */
    default boolean temporaryPreset(){return false;}
    AssemblyEngine.Validation validation();
    boolean canUndo();
    boolean canReset();
    Optional<AssemblyNode> nodeAt(List<String> path);
    List<AssemblySession.SlotView> slots(List<String> parentPath);
    /** All currently exposed physical slots. Paths remain private operation identities, not navigation. */
    default List<AssemblySession.SlotView> visibleSlots() {
        var result = new ArrayList<AssemblySession.SlotView>();
        var parents = new ArrayDeque<List<String>>();
        parents.add(List.of());
        while (!parents.isEmpty()) {
            for (var slot : slots(parents.removeFirst())) {
                // Import catalogs also preserve unsupported source slots with placeholder anchors.
                // They are not empty usable slots until compatible content has been authored.
                if (slot.installed().isPresent() || !slot.definition().allowedParts().isEmpty()) result.add(slot);
                if (slot.installed().isPresent()) parents.addLast(slot.path());
            }
        }
        return List.copyOf(result);
    }
    default boolean busy() { return false; }
    boolean select(List<String> path);
    List<AssemblyNode> candidates();
    void clearPreview();
    AssemblySession.Preview preview(UUID sourceId);
    AssemblyEngine.Result install(UUID sourceId);
    AssemblyEngine.Result remove();
    boolean undo();
    void reset();
}
