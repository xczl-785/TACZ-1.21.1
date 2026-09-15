package dev.weaponassembly.api;

import java.util.*;

/** Immutable, validated catalog. Loading a new catalog never mutates an existing engine. */
public final class AssemblyCatalog {
    private final Map<String, PartDefinition> parts;
    public AssemblyCatalog(Collection<PartDefinition> definitions) {
        var copy = new TreeMap<String, PartDefinition>();
        for (var part : definitions) if (copy.putIfAbsent(part.id(), part) != null)
            throw new IllegalArgumentException("Duplicate part: " + part.id());
        if (copy.isEmpty()) throw new IllegalArgumentException("Empty catalog");
        for (var part : copy.values()) {
            for (var slot : part.slots()) for (var id : slot.allowedParts()) {
                if (!copy.containsKey(id)) throw new IllegalArgumentException("Unknown allowed part: " + id);
                if (copy.get(id).weapon().isPresent()) throw new IllegalArgumentException("Weapon cannot be an attachment: " + id);
            }
            for (var id : part.conflictingParts()) if (!copy.containsKey(id)) throw new IllegalArgumentException("Unknown conflict: " + id);
            for (var key : part.blockedSlots()) if (!copy.containsKey(key.ownerDefinition())
                    || copy.get(key.ownerDefinition()).slot(key.slot()).isEmpty())
                throw new IllegalArgumentException("Unknown blocked slot: " + key);
        }
        parts = Collections.unmodifiableMap(copy);
    }
    public Map<String, PartDefinition> parts() { return parts; }
    public Optional<PartDefinition> find(String id) { return Optional.ofNullable(parts.get(id)); }
    public PartDefinition require(String id) { return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown part: " + id)); }
}
