package dev.weaponassembly.api;

import java.util.*;

/** One physical occurrence; two parts of the same definition must have distinct instance IDs. */
public record AssemblyNode(UUID instanceId, String definitionId, Map<String, AssemblyNode> children) {
    public AssemblyNode {
        Objects.requireNonNull(instanceId); PartDefinition.requireId(definitionId);
        var copy = new TreeMap<String, AssemblyNode>();
        children.forEach((slot, child) -> { PartDefinition.requireId(slot); copy.put(slot, Objects.requireNonNull(child)); });
        children = Collections.unmodifiableMap(copy);
    }
    public static AssemblyNode leaf(UUID instance, String definition) { return new AssemblyNode(instance, definition, Map.of()); }
}
