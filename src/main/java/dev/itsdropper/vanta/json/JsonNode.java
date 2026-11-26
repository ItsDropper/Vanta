package dev.itsdropper.vanta.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/** Read-only JSON value used by Vanta's metadata and persistence code. */
public class JsonNode implements Iterable<JsonNode> {
    static final JsonNode MISSING = new JsonNode(null, true);
    protected final JsonElement value;
    private final boolean missing;

    protected JsonNode(JsonElement value) { this(value, false); }
    protected JsonNode(JsonElement value, boolean missing) {
        this.value = value;
        this.missing = missing;
    }

    static JsonNode wrap(JsonElement value) {
        if (value == null) return null;
        if (value.isJsonObject()) return new ObjectNode(value.getAsJsonObject());
        if (value.isJsonArray()) return new ArrayNode(value.getAsJsonArray());
        return new JsonNode(value);
    }

    JsonElement element() { return value; }

    public JsonNode get(String name) {
        return value != null && value.isJsonObject()
                ? wrap(value.getAsJsonObject().get(name)) : null;
    }

    public JsonNode get(int index) {
        return value != null && value.isJsonArray() && index >= 0 && index < value.getAsJsonArray().size()
                ? wrap(value.getAsJsonArray().get(index)) : null;
    }

    public JsonNode path(String name) {
        JsonNode child = get(name);
        return child == null ? MISSING : child;
    }

    public JsonNode path(int index) {
        JsonNode child = get(index);
        return child == null ? MISSING : child;
    }

    public boolean has(String name) { return get(name) != null; }
    public boolean isMissingNode() { return missing; }
    public boolean isNull() { return !missing && (value == null || value.isJsonNull()); }
    public boolean isObject() { return value != null && value.isJsonObject(); }
    public boolean isArray() { return value != null && value.isJsonArray(); }
    public boolean isTextual() { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(); }
    public boolean isBoolean() { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(); }
    public boolean isNumber() { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(); }
    public boolean isIntegralNumber() {
        if (!isNumber()) return false;
        try { return new BigDecimal(value.getAsJsonPrimitive().getAsString()).stripTrailingZeros().scale() <= 0; }
        catch (NumberFormatException ignored) { return false; }
    }
    public boolean isValueNode() { return missing ? false : value == null || value.isJsonNull() || value.isJsonPrimitive(); }
    public boolean isEmpty() {
        if (isNull() || missing) return true;
        if (isObject()) return value.getAsJsonObject().size() == 0;
        if (isArray()) return value.getAsJsonArray().size() == 0;
        return false;
    }
    public int size() {
        if (isObject()) return value.getAsJsonObject().size();
        if (isArray()) return value.getAsJsonArray().size();
        return 0;
    }

    public String asText() { return asText(""); }
    public String asText(String fallback) {
        if (missing || value == null || value.isJsonNull() || !value.isJsonPrimitive()) return fallback;
        return value.getAsJsonPrimitive().getAsString();
    }
    public int asInt() { return asInt(0); }
    public int asInt(int fallback) {
        if (missing || value == null || value.isJsonNull() || !value.isJsonPrimitive()) return fallback;
        try { return value.getAsInt(); } catch (RuntimeException ignored) { return fallback; }
    }
    public long asLong() { return asLong(0L); }
    public long asLong(long fallback) {
        if (missing || value == null || value.isJsonNull() || !value.isJsonPrimitive()) return fallback;
        try { return value.getAsLong(); } catch (RuntimeException ignored) { return fallback; }
    }
    public boolean asBoolean() { return asBoolean(false); }
    public boolean asBoolean(boolean fallback) {
        if (missing || value == null || value.isJsonNull() || !value.isJsonPrimitive()) return fallback;
        try { return value.getAsBoolean(); } catch (RuntimeException ignored) { return fallback; }
    }
    public boolean canConvertToInt() {
        if (!isIntegralNumber()) return false;
        try {
            BigDecimal number = new BigDecimal(value.getAsJsonPrimitive().getAsString());
            return number.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0
                    && number.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        } catch (NumberFormatException ignored) { return false; }
    }
    public boolean canConvertToLong() {
        if (!isIntegralNumber()) return false;
        try {
            BigDecimal number = new BigDecimal(value.getAsJsonPrimitive().getAsString());
            return number.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0
                    && number.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0;
        } catch (NumberFormatException ignored) { return false; }
    }

    public Iterator<Map.Entry<String, JsonNode>> fields() {
        if (!isObject()) return java.util.Collections.emptyIterator();
        Iterator<Map.Entry<String, JsonElement>> source = value.getAsJsonObject().entrySet().iterator();
        return new Iterator<>() {
            public boolean hasNext() { return source.hasNext(); }
            public Map.Entry<String, JsonNode> next() {
                Map.Entry<String, JsonElement> entry = source.next();
                return Map.entry(entry.getKey(), wrap(entry.getValue()));
            }
        };
    }
    public Iterator<String> fieldNames() {
        if (!isObject()) return java.util.Collections.emptyIterator();
        return value.getAsJsonObject().keySet().iterator();
    }
    public Iterator<JsonNode> elements() { return iterator(); }

    @Override public Iterator<JsonNode> iterator() {
        if (isArray()) {
            Iterator<JsonElement> source = value.getAsJsonArray().iterator();
            return new Iterator<>() {
                public boolean hasNext() { return source.hasNext(); }
                public JsonNode next() { return wrap(source.next()); }
            };
        }
        if (isObject()) {
            Iterator<JsonElement> source = value.getAsJsonObject().asMap().values().iterator();
            return new Iterator<>() {
                public boolean hasNext() { return source.hasNext(); }
                public JsonNode next() { return wrap(source.next()); }
            };
        }
        return java.util.Collections.emptyIterator();
    }

    public JsonNode deepCopy() {
        return missing ? MISSING : wrap(value == null ? JsonNull.INSTANCE : value.deepCopy());
    }

    @Override public String toString() {
        return missing || value == null ? "null" : value.toString();
    }
}
