package dev.weaponassembly.api;

import java.util.*;

/** Stateless planning API. A successful plan is not an inventory commit. */
public final class AssemblyEngine {
    public static final int MAX_DEPTH = 8, MAX_NODES = 129;
    public enum Code { UNKNOWN_PART, UNKNOWN_SLOT, INCOMPATIBLE, DUPLICATE_INSTANCE, LIMIT_EXCEEDED,
        PART_CONFLICT, SLOT_CONFLICT, MISSING_REQUIRED, INVALID_PATH, EMPTY_SLOT, OCCUPIED_SLOT,
        ROOT_NOT_WEAPON, INVALID_STATS }
    public record Issue(Code code, List<String> path, String detail) {
        public Issue { path = List.copyOf(path); Objects.requireNonNull(code); Objects.requireNonNull(detail); }
    }
    public record Validation(List<Issue> errors, List<Issue> missingRequired) {
        public Validation { errors = List.copyOf(errors); missingRequired = List.copyOf(missingRequired); }
        public boolean valid() { return errors.isEmpty(); }
        public boolean complete() { return valid() && missingRequired.isEmpty(); }
    }
    public record Result(AssemblyNode before, AssemblyNode after, Optional<AssemblyNode> detached, List<Issue> errors) {
        public Result { Objects.requireNonNull(before); Objects.requireNonNull(after); Objects.requireNonNull(detached); errors = List.copyOf(errors); }
        public boolean success() { return errors.isEmpty(); }
    }
    private final AssemblyCatalog catalog;
    public AssemblyEngine(AssemblyCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }
    public AssemblyCatalog catalog() { return catalog; }
    public Validation validate(AssemblyNode root) {
        Objects.requireNonNull(root);
        var errors = new ArrayList<Issue>(); var missing = new ArrayList<Issue>();
        var nodes = scan(root, errors);
        if (!errors.isEmpty()) return new Validation(errors, missing);
        for (var entry : nodes) {
            var part = catalog.find(entry.node.definitionId());
            if (part.isEmpty()) { errors.add(issue(Code.UNKNOWN_PART, entry.path, entry.node.definitionId())); continue; }
            var definition = part.orElseThrow();
            for (var slot : definition.slots()) if (slot.required() && !entry.node.children().containsKey(slot.id()))
                missing.add(issue(Code.MISSING_REQUIRED, append(entry.path, slot.id()), definition.id()));
            for (var child : entry.node.children().entrySet()) {
                var path = append(entry.path, child.getKey()); var slot = definition.slot(child.getKey());
                if (slot.isEmpty()) errors.add(issue(Code.UNKNOWN_SLOT, path, definition.id()));
                else if (!slot.orElseThrow().allowedParts().contains(child.getValue().definitionId()))
                    errors.add(issue(Code.INCOMPATIBLE, path, child.getValue().definitionId()));
            }
        }
        // Compare occurrences, not a set of definition IDs. Conflicts are symmetric even if declared once.
        for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++) {
            var a = nodes.get(i); var b = nodes.get(j);
            var da = catalog.find(a.node.definitionId()); var db = catalog.find(b.node.definitionId());
            if (da.isPresent() && db.isPresent() && (da.orElseThrow().conflictingParts().contains(b.node.definitionId())
                    || db.orElseThrow().conflictingParts().contains(a.node.definitionId())))
                errors.add(issue(Code.PART_CONFLICT, b.path, a.node.instanceId().toString()));
        }
        for (var blocker : nodes) {
            var definition = catalog.find(blocker.node.definitionId());
            if (definition.isEmpty()) continue;
            for (var owner : nodes) for (var slot : owner.node.children().keySet())
                if (definition.orElseThrow().blockedSlots().contains(new PartDefinition.SlotKey(owner.node.definitionId(), slot)))
                    errors.add(issue(Code.SLOT_CONFLICT, append(owner.path, slot), blocker.node.instanceId().toString()));
        }
        return new Validation(errors, missing);
    }
    public Result install(AssemblyNode root, List<String> path, AssemblyNode source) { return edit(root, path, Objects.requireNonNull(source), Edit.INSTALL); }
    public Result replace(AssemblyNode root, List<String> path, AssemblyNode source) { return edit(root, path, Objects.requireNonNull(source), Edit.REPLACE); }
    public Result remove(AssemblyNode root, List<String> path) { return edit(root, path, null, Edit.REMOVE); }
    private enum Edit { INSTALL, REPLACE, REMOVE }
    private Result edit(AssemblyNode root, List<String> inputPath, AssemblyNode source, Edit edit) {
        var original = validate(root);
        if (!original.valid()) return failure(root, original.errors());
        if (inputPath == null || inputPath.isEmpty() || inputPath.size() > MAX_DEPTH || inputPath.stream().anyMatch(Objects::isNull))
            return failure(root, List.of(issue(Code.INVALID_PATH, List.of(), "Expected non-root slot path")));
        var path = List.copyOf(inputPath);
        var parent = root;
        for (int i = 0; i < path.size() - 1; i++) {
            parent = parent.children().get(path.get(i));
            if (parent == null) return failure(root, List.of(issue(Code.INVALID_PATH, path, "Missing parent")));
        }
        var slot = path.getLast();
        if (catalog.require(parent.definitionId()).slot(slot).isEmpty())
            return failure(root, List.of(issue(Code.UNKNOWN_SLOT, path, parent.definitionId())));
        var old = parent.children().get(slot);
        if (edit == Edit.INSTALL && old != null) return failure(root, List.of(issue(Code.OCCUPIED_SLOT, path, slot)));
        if (edit != Edit.INSTALL && old == null) return failure(root, List.of(issue(Code.EMPTY_SLOT, path, slot)));
        if (source != null) {
            var checked = validate(source);
            if (!checked.valid()) return failure(root, checked.errors());
            var identities = new HashSet<UUID>();
            scan(root, new ArrayList<>()).forEach(n -> identities.add(n.node.instanceId()));
            if (scan(source, new ArrayList<>()).stream().anyMatch(n -> identities.contains(n.node.instanceId())))
                return failure(root, List.of(issue(Code.DUPLICATE_INSTANCE, path, "Source overlaps host tree")));
        }
        var updated = change(root, path, 0, source);
        var checked = validate(updated);
        if (!checked.valid()) return failure(root, checked.errors());
        return new Result(root, updated, Optional.ofNullable(old), List.of());
    }
    private static AssemblyNode change(AssemblyNode node, List<String> path, int index, AssemblyNode source) {
        var children = new TreeMap<>(node.children()); var slot = path.get(index);
        if (index == path.size() - 1) { if (source == null) children.remove(slot); else children.put(slot, source); }
        else children.put(slot, change(children.get(slot), path, index + 1, source));
        return new AssemblyNode(node.instanceId(), node.definitionId(), children);
    }
    private static Result failure(AssemblyNode root, List<Issue> errors) { return new Result(root, root, Optional.empty(), errors); }
    static Issue issue(Code code, List<String> path, String detail) { return new Issue(code, path, detail); }
    static List<String> append(List<String> path, String slot) { var next = new ArrayList<>(path); next.add(slot); return List.copyOf(next); }
    record Located(AssemblyNode node, List<String> path) {}
    static List<Located> scan(AssemblyNode root, List<Issue> errors) {
        var queue = new ArrayDeque<Located>(); queue.add(new Located(root, List.of()));
        var result = new ArrayList<Located>(); var ids = new HashSet<UUID>();
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (current.path.size() > MAX_DEPTH || result.size() + queue.size() + 1 + current.node.children().size() > MAX_NODES) {
                errors.add(issue(Code.LIMIT_EXCEEDED, current.path, "Assembly limit exceeded")); return result;
            }
            if (!ids.add(current.node.instanceId())) {
                errors.add(issue(Code.DUPLICATE_INSTANCE, current.path, current.node.instanceId().toString())); return result;
            }
            result.add(current);
            current.node.children().forEach((slot, child) -> queue.addLast(new Located(child, append(current.path, slot))));
        }
        return result;
    }
}
