package dev.weaponassembly.io;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.weaponassembly.api.*;
import dev.weaponassembly.api.PartDefinition.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Versioned exchange format. No global registry, file IO, ItemStack serialization or world writes. */
public final class AssemblyJson {
    public static final int SCHEMA_VERSION = 1, MAX_JSON_CHARS = 1_048_576;
    private static final Gson GSON = new Gson();
    private AssemblyJson() {}

    public static AssemblyCatalog readCatalog(String json) {
        var top = object(parse(json)); keys(top, "schemaVersion", "parts"); schema(top);
        var parts = new ArrayList<PartDefinition>();
        for (var value : array(top, "parts")) {
            var part = object(value); keys(part, "id", "slots", "conflictingParts", "blockedSlots", "stats", "weapon");
            var slots = new ArrayList<Slot>();
            for (var entry : optionalArray(part, "slots")) {
                var slot = object(entry); keys(slot, "id", "required", "allowedParts");
                slots.add(new Slot(string(slot, "id"), bool(slot, "required", false), strings(array(slot, "allowedParts"))));
            }
            var blocked = new HashSet<SlotKey>();
            for (var entry : optionalArray(part, "blockedSlots")) {
                var key = object(entry); keys(key, "ownerDefinition", "slot");
                if (!blocked.add(new SlotKey(string(key, "ownerDefinition"), string(key, "slot")))) throw invalid("Duplicate blocked slot");
            }
            var stats = part.has("stats") ? object(part.get("stats")) : new JsonObject();
            keys(stats, "weightKg", "ergonomics", "recoilFraction", "accuracyPercent", "velocityPercent", "centerOfImpact",
                    "sightingRange", "heatFactor", "coolingFactor", "durabilityBurnFactor");
            var modifiers = new Modifiers(number(stats,"weightKg",0), number(stats,"ergonomics",0), number(stats,"recoilFraction",0),
                    number(stats,"accuracyPercent",0), number(stats,"velocityPercent",0), optionalNumber(stats,"centerOfImpact"),
                    optionalNumber(stats,"sightingRange"), number(stats,"heatFactor",1), number(stats,"coolingFactor",1), number(stats,"durabilityBurnFactor",1));
            Optional<WeaponBase> weapon = Optional.empty();
            if (part.has("weapon")) {
                var base = object(part.get("weapon")); keys(base,"recoilVertical","recoilHorizontal","centerOfImpact","sightingRange");
                required(base,"recoilVertical"); required(base,"recoilHorizontal");
                weapon = Optional.of(new WeaponBase(number(base,"recoilVertical",0), number(base,"recoilHorizontal",0),
                        optionalNumber(base,"centerOfImpact"), optionalNumber(base,"sightingRange")));
            }
            parts.add(new PartDefinition(string(part,"id"), slots, strings(optionalArray(part,"conflictingParts")), blocked, modifiers, weapon));
        }
        return new AssemblyCatalog(parts);
    }

    /** Canonical parent-before-child rows; child slots sorted by AssemblyNode. */
    public static String writeSnapshot(AssemblyNode root, AssemblyEngine engine) {
        requireValid(root, engine);
        var top = new JsonObject(); top.addProperty("schemaVersion", SCHEMA_VERSION);
        var rows = new JsonArray(); top.add("nodes", rows);
        record Pending(AssemblyNode node, UUID parent, String slot) {}
        var queue = new ArrayDeque<Pending>(); queue.add(new Pending(root,null,null));
        while (!queue.isEmpty()) {
            var pending = queue.removeFirst(); var node = pending.node();
            var row = new JsonObject(); row.addProperty("instanceId",node.instanceId().toString()); row.addProperty("definitionId",node.definitionId());
            if (pending.parent() != null) { row.addProperty("parentId",pending.parent().toString()); row.addProperty("slot",pending.slot()); }
            rows.add(row);
            node.children().forEach((slot,child)->queue.addLast(new Pending(child,node.instanceId(),slot)));
        }
        return GSON.toJson(top);
    }

    public static AssemblyNode readSnapshot(String json, AssemblyEngine engine) {
        var top = object(parse(json)); keys(top,"schemaVersion","nodes"); schema(top);
        var rows = array(top,"nodes");
        if (rows.isEmpty() || rows.size() > AssemblyEngine.MAX_NODES) throw invalid("Invalid snapshot node count");
        record Row(UUID id, String definition, UUID parent, String slot, int depth) {}
        var seen = new LinkedHashMap<UUID,Row>(); var occupied = new HashMap<UUID,Set<String>>();
        for (var value : rows) {
            var row = object(value); keys(row,"instanceId","definitionId","parentId","slot");
            var id = uuid(string(row,"instanceId")); var definition = string(row,"definitionId");
            UUID parent = null; String slot = null; int depth = 0;
            if (seen.isEmpty()) {
                if (row.has("parentId") || row.has("slot")) throw invalid("Root cannot have a parent slot");
            } else {
                parent = uuid(string(row,"parentId")); slot = string(row,"slot");
                if (!seen.containsKey(parent)) throw invalid("Parent must precede child");
                depth = seen.get(parent).depth()+1;
                if (depth > AssemblyEngine.MAX_DEPTH) throw invalid("Snapshot too deep");
                if (!occupied.computeIfAbsent(parent,k->new HashSet<>()).add(slot)) throw invalid("Duplicate occupied slot");
            }
            if (seen.putIfAbsent(id,new Row(id,definition,parent,slot,depth)) != null) throw invalid("Duplicate instance");
        }
        var children = new HashMap<UUID,Map<String,AssemblyNode>>(); AssemblyNode root = null;
        for (var row : new ArrayList<>(seen.values()).reversed()) {
            var node = new AssemblyNode(row.id(),row.definition(),children.getOrDefault(row.id(),Map.of()));
            if (row.parent() == null) root = node;
            else children.computeIfAbsent(row.parent(),k->new TreeMap<>()).put(row.slot(),node);
        }
        requireValid(Objects.requireNonNull(root),engine); return root;
    }

    private static void requireValid(AssemblyNode node, AssemblyEngine engine) {
        var check = engine.validate(node); if (!check.valid()) throw invalid("Invalid assembly: " + check.errors());
    }
    private static UUID uuid(String value) {
        var id = UUID.fromString(value); if (!id.toString().equals(value)) throw invalid("Noncanonical UUID"); return id;
    }
    private static void schema(JsonObject object) {
        var value = required(object,"schemaVersion");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Invalid schema version");
        try { if (value.getAsBigDecimal().intValueExact() != SCHEMA_VERSION) throw invalid("Unsupported schema version"); }
        catch (ArithmeticException ex) { throw invalid("Nonintegral schema version"); }
    }
    private static JsonElement required(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) throw invalid("Missing field: " + key); return object.get(key);
    }
    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw invalid("Expected object"); return value.getAsJsonObject();
    }
    private static JsonArray array(JsonObject object, String key) {
        var value = required(object,key); if (!value.isJsonArray()) throw invalid("Expected array: " + key); return value.getAsJsonArray();
    }
    private static JsonArray optionalArray(JsonObject object, String key) { return object.has(key) ? array(object,key) : new JsonArray(); }
    private static String string(JsonObject object, String key) {
        var value = required(object,key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string: " + key);
        return value.getAsString();
    }
    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        var value=required(object,key); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid("Expected boolean: " + key);
        return value.getAsBoolean();
    }
    private static double number(JsonObject object, String key, double fallback) {
        if (!object.has(key)) return fallback;
        var value=required(object,key); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Expected number: " + key);
        double result=value.getAsDouble(); if (!Double.isFinite(result)) throw invalid("Non-finite number: " + key); return result;
    }
    private static OptionalDouble optionalNumber(JsonObject object,String key) {
        return object.has(key) ? OptionalDouble.of(number(object,key,0)) : OptionalDouble.empty();
    }
    private static Set<String> strings(JsonArray values) {
        var result = new HashSet<String>();
        for (var value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string list");
            if (!result.add(value.getAsString())) throw invalid("Duplicate list entry");
        }
        return result;
    }
    private static void keys(JsonObject object, String... allowed) {
        var known=Set.of(allowed); for(var key:object.keySet()) if(!known.contains(key)) throw invalid("Unknown field: " + key);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    /** Strict bounded parser additionally rejects duplicate JSON keys (Gson's tree parser overwrites them). */
    private static JsonElement parse(String json) {
        if (json == null || json.length() > MAX_JSON_CHARS) throw invalid("JSON input limit exceeded");
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            var value = read(reader,0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON input");
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException ex) {
            throw new IllegalArgumentException("Malformed assembly JSON",ex);
        }
    }
    private static JsonElement read(JsonReader reader,int depth) throws IOException {
        if (depth > 32) throw invalid("JSON nesting limit exceeded");
        switch(reader.peek()) {
            case BEGIN_OBJECT: {
                var result=new JsonObject(); reader.beginObject();
                while(reader.hasNext()) { String key=reader.nextName(); if(result.has(key)) throw invalid("Duplicate JSON key: " + key); result.add(key,read(reader,depth+1)); }
                reader.endObject(); return result;
            }
            case BEGIN_ARRAY: {
                var result=new JsonArray(); reader.beginArray(); while(reader.hasNext()) result.add(read(reader,depth+1)); reader.endArray(); return result;
            }
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw invalid("Unexpected JSON token");
        }
    }
}
