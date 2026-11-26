package dev.itsdropper.vanta.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;

public final class ArrayNode extends JsonNode {
    ArrayNode(JsonArray value) { super(value); }
    private JsonArray array() { return value.getAsJsonArray(); }

    public ArrayNode add(JsonNode child) {
        array().add(child == null || child.isNull() || child.isMissingNode()
                ? JsonNull.INSTANCE : child.element().deepCopy());
        return this;
    }
    public ArrayNode add(String child) { array().add(child); return this; }
    public ArrayNode add(int child) { array().add(child); return this; }
    public ArrayNode add(long child) { array().add(child); return this; }
    public ArrayNode add(boolean child) { array().add(child); return this; }
    public ObjectNode addObject() {
        com.google.gson.JsonObject child = new com.google.gson.JsonObject();
        array().add(child);
        return new ObjectNode(child);
    }
    public JsonNode remove(int index) {
        return index < 0 || index >= array().size() ? null : JsonNode.wrap(array().remove(index));
    }
    @Override public ArrayNode deepCopy() { return new ArrayNode(array().deepCopy()); }
}
