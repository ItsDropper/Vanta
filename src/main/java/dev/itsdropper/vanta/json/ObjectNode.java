package dev.itsdropper.vanta.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class ObjectNode extends JsonNode {
    ObjectNode(JsonObject value) { super(value); }
    private JsonObject object() { return value.getAsJsonObject(); }

    public ObjectNode put(String name, String child) {
        object().add(name, child == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(child));
        return this;
    }
    public ObjectNode put(String name, int child) { object().addProperty(name, child); return this; }
    public ObjectNode put(String name, long child) { object().addProperty(name, child); return this; }
    public ObjectNode put(String name, boolean child) { object().addProperty(name, child); return this; }
    public ObjectNode put(String name, double child) { object().addProperty(name, child); return this; }
    public ObjectNode set(String name, JsonNode child) {
        object().add(name, child == null || child.isNull() || child.isMissingNode()
                ? JsonNull.INSTANCE : child.element().deepCopy());
        return this;
    }
    public ArrayNode putArray(String name) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        object().add(name, array);
        return new ArrayNode(array);
    }
    public ObjectNode putObject(String name) {
        JsonObject child = new JsonObject();
        object().add(name, child);
        return new ObjectNode(child);
    }
    public JsonNode remove(String name) { return JsonNode.wrap(object().remove(name)); }
    public ArrayNode withArray(String name) {
        JsonElement existing = object().get(name);
        if (existing == null) return putArray(name);
        if (!existing.isJsonArray()) throw new IllegalStateException("JSON property '" + name + "' is not an array.");
        return new ArrayNode(existing.getAsJsonArray());
    }
    public ObjectNode with(String name) {
        JsonElement existing = object().get(name);
        if (existing == null) return putObject(name);
        if (!existing.isJsonObject()) throw new IllegalStateException("JSON property '" + name + "' is not an object.");
        return new ObjectNode(existing.getAsJsonObject());
    }
}
