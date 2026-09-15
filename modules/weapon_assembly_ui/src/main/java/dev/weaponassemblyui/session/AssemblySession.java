package dev.weaponassemblyui.session;

import dev.weaponassembly.api.*;
import java.util.*;

/** In-memory sample workbench. Hosts supply every instance; this never writes a player inventory. */
public final class AssemblySession implements WorkbenchAccess {
    public record SlotView(List<String> path, UUID ownerId, PartDefinition.Slot definition,
                           Optional<AssemblyNode> installed) {
        public SlotView { path = List.copyOf(path); }
    }
    public record Preview(UUID sourceId, AssemblyEngine.Result plan, Optional<WeaponStats.Values> stats) {}
    private record State(AssemblyNode tree, List<AssemblyNode> stock, Set<UUID> detached) {
        State { stock = List.copyOf(stock); detached = Set.copyOf(detached); }
    }
    private final AssemblyEngine engine;
    private final WeaponStats calculator;
    private final WeaponStats.Context context;
    private final State initial;
    private final Deque<State> history = new ArrayDeque<>();
    private State state;
    private List<String> selected = List.of();
    private Preview preview;
    private List<AssemblyEngine.Issue> feedback = List.of();

    public AssemblySession(AssemblyCatalog catalog, AssemblyNode initialTree,
                           Collection<AssemblyNode> suppliedParts, WeaponStats.Context context) {
        engine = new AssemblyEngine(catalog);
        calculator = new WeaponStats(engine);
        this.context = Objects.requireNonNull(context);
        calculator.calculate(initialTree, context);
        var identities = new HashSet<UUID>();
        collectIdentities(initialTree, identities);
        for (var part : suppliedParts) {
            if (!engine.validate(part).valid() || catalog.require(part.definitionId()).weapon().isPresent())
                throw new IllegalArgumentException("Invalid supplied attachment: " + part.definitionId());
            collectIdentities(part, identities);
        }
        initial = state = new State(initialTree, List.copyOf(suppliedParts), Set.of());
    }
    private static void collectIdentities(AssemblyNode node, Set<UUID> identities) {
        if (!identities.add(node.instanceId())) throw new IllegalArgumentException("Duplicate supplied instance: " + node.instanceId());
        node.children().values().forEach(child -> collectIdentities(child, identities));
    }
    public AssemblyCatalog catalog() { return engine.catalog(); }
    public AssemblyNode tree() { return state.tree(); }
    public List<AssemblyNode> stock() { return state.stock(); }
    public List<AssemblyNode> detached() { return stock().stream().filter(n -> state.detached().contains(n.instanceId())).toList(); }
    public List<String> selectedPath() { return selected; }
    public Optional<Preview> preview() { return Optional.ofNullable(preview); }
    public List<AssemblyEngine.Issue> feedback() { return feedback; }
    public WeaponStats.Values stats() { return calculator.calculate(tree(), context); }
    public AssemblyEngine.Validation validation() { return engine.validate(tree()); }
    public boolean canUndo() { return !history.isEmpty(); }
    public boolean canReset() { return !state.equals(initial); }
    public Optional<AssemblyNode> nodeAt(List<String> path) {
        var node = tree();
        for (String slot : path) {
            node = node.children().get(slot);
            if (node == null) return Optional.empty();
        }
        return Optional.of(node);
    }
    public List<SlotView> slots(List<String> parentPath) {
        var parent = nodeAt(parentPath);
        if (parent.isEmpty()) return List.of();
        var node = parent.orElseThrow();
        return catalog().require(node.definitionId()).slots().stream().map(slot -> {
            var path = new ArrayList<>(parentPath); path.add(slot.id());
            return new SlotView(path, node.instanceId(), slot, Optional.ofNullable(node.children().get(slot.id())));
        }).toList();
    }
    /** Empty selection closes the parts panel. Selection is always an entire root-relative slot path. */
    public boolean select(List<String> path) {
        var copy = List.copyOf(path);
        if (!copy.isEmpty() && findSlot(copy).isEmpty()) return false;
        selected = copy; clearPreview(); feedback = List.of(); return true;
    }
    private Optional<SlotView> findSlot(List<String> path) {
        if (path.isEmpty()) return Optional.empty();
        return slots(path.subList(0, path.size() - 1)).stream().filter(s -> s.path().equals(path)).findFirst();
    }
    /** Slot-compatible candidates include globally conflicting choices so the UI can explain rejection. */
    public List<AssemblyNode> candidates() {
        return findSlot(selected).map(slot -> stock().stream()
                .filter(n -> slot.definition().allowedParts().contains(n.definitionId()))
                .sorted(Comparator.comparingInt(n -> state.detached().contains(n.instanceId()) ? 0 : 1))
                .toList()).orElse(List.of());
    }
    public void clearPreview() { preview = null; }
    public Preview preview(UUID sourceId) { preview = plan(sourceId); return preview; }
    private Preview plan(UUID sourceId) {
        var source = stock().stream().filter(n -> n.instanceId().equals(sourceId)).findFirst();
        AssemblyEngine.Result result;
        if (source.isEmpty()) result = failure(AssemblyEngine.Code.UNKNOWN_PART, "Candidate is not supplied by this host");
        else if (nodeAt(selected).isPresent() && !selected.isEmpty()) result = engine.replace(tree(), selected, source.orElseThrow());
        else result = engine.install(tree(), selected, source.orElseThrow());
        Optional<WeaponStats.Values> values = Optional.empty();
        if (result.success()) {
            try { values = Optional.of(calculator.calculate(result.after(), context)); }
            catch (IllegalArgumentException e) { result = failure(AssemblyEngine.Code.INVALID_STATS, e.getMessage()); }
        }
        return new Preview(sourceId, result, values);
    }
    public AssemblyEngine.Result install(UUID sourceId) {
        var plan = plan(sourceId); // Re-plan against the current state, never commit a stale hover snapshot.
        return commit(plan.plan(), Optional.of(sourceId));
    }
    public AssemblyEngine.Result remove() {
        var result = engine.remove(tree(), selected);
        if (result.success()) {
            try { calculator.calculate(result.after(), context); }
            catch (IllegalArgumentException e) { result = failure(AssemblyEngine.Code.INVALID_STATS, e.getMessage()); }
        }
        return commit(result, Optional.empty());
    }
    private AssemblyEngine.Result failure(AssemblyEngine.Code code, String message) {
        return new AssemblyEngine.Result(tree(), tree(), Optional.empty(),
                List.of(new AssemblyEngine.Issue(code, selected, message)));
    }
    private AssemblyEngine.Result commit(AssemblyEngine.Result result, Optional<UUID> consumed) {
        clearPreview(); feedback = result.errors();
        if (!result.success()) return result;
        var stock = new ArrayList<>(state.stock());
        var detached = new HashSet<>(state.detached());
        consumed.ifPresent(id -> { stock.removeIf(n -> n.instanceId().equals(id)); detached.remove(id); });
        result.detached().ifPresent(n -> { stock.add(n); detached.add(n.instanceId()); });
        history.push(state);
        state = new State(result.after(), stock, detached);
        return result;
    }
    public boolean undo() {
        if (history.isEmpty()) return false;
        state = history.pop(); tidySelection(); return true;
    }
    /** Restore the complete original sample session, including its supplied parts and detached tray. */
    public void reset() {
        if (!state.equals(initial)) history.push(state);
        state = initial; tidySelection();
    }
    private void tidySelection() {
        while (!selected.isEmpty() && findSlot(selected).isEmpty()) selected = List.copyOf(selected.subList(0, selected.size() - 1));
        clearPreview(); feedback = List.of();
    }
}
