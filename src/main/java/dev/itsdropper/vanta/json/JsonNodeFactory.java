package dev.itsdropper.vanta.json;

public final class JsonNodeFactory {
    public static final JsonNodeFactory instance = new JsonNodeFactory();
    private JsonNodeFactory() {}
    public ObjectNode objectNode() { return new ObjectNode(new com.google.gson.JsonObject()); }
    public ArrayNode arrayNode() { return new ArrayNode(new com.google.gson.JsonArray()); }
}
