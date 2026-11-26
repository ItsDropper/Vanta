package dev.itsdropper.vanta.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import dev.itsdropper.vanta.json.annotation.JsonProperty;
import dev.itsdropper.vanta.json.annotation.JsonIgnore;
import java.util.Set;

/**
 * Vanta's small JSON mapper. It deliberately exposes only the operations used
 * by the launcher, keeping the parser and serialization policy replaceable.
 */
public final class ObjectMapper {
    private final boolean strictDuplicates;
    private final boolean pretty;
    private final boolean failUnknown;
    private final Gson gson;

    public ObjectMapper() { this(false, false, false); }
    ObjectMapper(boolean strictDuplicates, boolean pretty, boolean failUnknown) {
        this.strictDuplicates = strictDuplicates;
        this.pretty = pretty;
        this.failUnknown = failUnknown;
        GsonBuilder builder = new GsonBuilder()
                .serializeNulls()
                .disableHtmlEscaping()
                .setFieldNamingStrategy(field -> {
                    JsonProperty property = field.getAnnotation(JsonProperty.class);
                    return property == null ? field.getName() : property.value();
                })
                .addSerializationExclusionStrategy(new ExclusionStrategy() {
                    @Override public boolean shouldSkipField(FieldAttributes field) {
                        return field.getAnnotation(JsonIgnore.class) != null;
                    }
                    @Override public boolean shouldSkipClass(Class<?> type) { return false; }
                })
                .addDeserializationExclusionStrategy(new ExclusionStrategy() {
                    @Override public boolean shouldSkipField(FieldAttributes field) {
                        return field.getAnnotation(JsonIgnore.class) != null;
                    }
                    @Override public boolean shouldSkipClass(Class<?> type) { return false; }
                })
                .registerTypeHierarchyAdapter(JsonNode.class, new TypeAdapter<JsonNode>() {
                    @Override public void write(JsonWriter out, JsonNode node) throws IOException {
                        if (node == null || node.isNull() || node.isMissingNode()) {
                            out.nullValue();
                            return;
                        }
                        writeElement(node.element(), out);
                    }
                    @Override public JsonNode read(JsonReader in) throws IOException {
                        return JsonNode.wrap(parseValue(in, false));
                    }
                })
                .registerTypeHierarchyAdapter(Path.class, new TypeAdapter<Path>() {
                    @Override public void write(JsonWriter out, Path path) throws IOException {
                        if (path == null) out.nullValue(); else out.value(path.toString());
                    }
                    @Override public Path read(JsonReader in) throws IOException {
                        if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                        return Path.of(in.nextString());
                    }
                });
        if (pretty) builder.setPrettyPrinting();
        gson = builder.create();
    }

    public ObjectMapper copy() { return new ObjectMapper(strictDuplicates, pretty, failUnknown); }
    public ObjectMapper enable(SerializationFeature feature) {
        return feature == SerializationFeature.INDENT_OUTPUT
                ? new ObjectMapper(strictDuplicates, true, failUnknown) : this;
    }
    public ObjectMapper enable(DeserializationFeature feature) {
        return feature == DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
                ? new ObjectMapper(strictDuplicates, pretty, true) : this;
    }
    public ObjectWriter writerWithDefaultPrettyPrinter() {
        return new ObjectWriter(new ObjectMapper(strictDuplicates, true, failUnknown));
    }

    public JsonNode readTree(File file) throws IOException {
        try (Reader reader = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return readTree(reader);
        }
    }
    public JsonNode readTree(InputStream input) throws IOException {
        return readTree(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
    public JsonNode readTree(byte[] json) throws IOException {
        return readTree(new ByteArrayInputStream(json));
    }
    public JsonNode readTree(String json) throws IOException {
        return readTree(new StringReader(json));
    }
    public JsonNode readTree(Reader input) throws IOException {
        try {
            JsonReader reader = new JsonReader(input);
            reader.setStrictness(Strictness.STRICT);
            JsonElement parsed = parseValue(reader, strictDuplicates);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonProcessingException("Trailing content after JSON document.");
            }
            return JsonNode.wrap(parsed);
        } catch (JsonProcessingException failure) {
            throw failure;
        } catch (Exception malformed) {
            throw new JsonProcessingException("Invalid JSON: " + malformed.getMessage(), malformed);
        }
    }

    private static JsonElement parseValue(JsonReader reader, boolean rejectDuplicates) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                Set<String> keys = rejectDuplicates ? new HashSet<>() : null;
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (rejectDuplicates && !keys.add(name)) {
                        throw new JsonProcessingException("Duplicate JSON object key: " + name);
                    }
                    object.add(name, parseValue(reader, rejectDuplicates));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(parseValue(reader, rejectDuplicates));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new JsonProcessingException("Unexpected JSON token: " + reader.peek());
        };
    }

    public <T> T readValue(String json, Class<T> type) throws IOException {
        JsonNode tree = readTree(json);
        validateKnownProperties(tree == null ? JsonNull.INSTANCE : tree.element(), type);
        try { return gson.fromJson(tree == null ? JsonNull.INSTANCE : tree.element(), type); }
        catch (RuntimeException malformed) { throw new JsonProcessingException("Invalid JSON value: " + malformed.getMessage(), malformed); }
    }
    public <T> T readValue(String json, TypeReference<T> type) throws IOException {
        JsonNode tree = readTree(json);
        validateKnownProperties(tree == null ? JsonNull.INSTANCE : tree.element(), type.getType());
        try {
            @SuppressWarnings("unchecked")
            T value = (T) gson.fromJson(tree == null ? JsonNull.INSTANCE : tree.element(), type.getType());
            return value;
        } catch (RuntimeException malformed) {
            throw new JsonProcessingException("Invalid JSON value: " + malformed.getMessage(), malformed);
        }
    }
    public <T> List<T> readList(String json, Class<T> elementType) throws IOException {
        java.lang.reflect.Type listType = com.google.gson.reflect.TypeToken
                .getParameterized(List.class, elementType).getType();
        try {
            JsonNode tree = readTree(json);
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) gson.fromJson(tree == null ? JsonNull.INSTANCE : tree.element(), listType);
            return result;
        } catch (RuntimeException malformed) {
            throw new JsonProcessingException("Invalid JSON list: " + malformed.getMessage(), malformed);
        }
    }
    public <T> T readValue(byte[] json, TypeReference<T> type) throws IOException {
        JsonNode tree = readTree(json);
        validateKnownProperties(tree == null ? JsonNull.INSTANCE : tree.element(), type.getType());
        try {
            @SuppressWarnings("unchecked")
            T value = (T) gson.fromJson(tree == null ? JsonNull.INSTANCE : tree.element(), type.getType());
            return value;
        } catch (RuntimeException malformed) {
            throw new JsonProcessingException("Invalid JSON value: " + malformed.getMessage(), malformed);
        }
    }
    public <T> T readValue(byte[] json, Class<T> type) throws IOException {
        JsonNode tree = readTree(json);
        validateKnownProperties(tree == null ? JsonNull.INSTANCE : tree.element(), type);
        try { return gson.fromJson(tree == null ? JsonNull.INSTANCE : tree.element(), type); }
        catch (RuntimeException malformed) { throw new JsonProcessingException("Invalid JSON value: " + malformed.getMessage(), malformed); }
    }
    public <T> T readValue(File file, TypeReference<T> type) throws IOException {
        JsonNode tree = readTree(file);
        validateKnownProperties(tree == null ? JsonNull.INSTANCE : tree.element(), type.getType());
        try {
            @SuppressWarnings("unchecked")
            T value = (T) gson.fromJson(tree == null ? JsonNull.INSTANCE : tree.element(), type.getType());
            return value;
        } catch (RuntimeException malformed) {
            throw new JsonProcessingException("Invalid JSON value: " + malformed.getMessage(), malformed);
        }
    }
    public <T> T readValue(File file, Class<T> type) throws IOException {
        return readValue(java.nio.file.Files.readAllBytes(file.toPath()), type);
    }
    public <T> T treeToValue(JsonNode node, Class<T> type) throws IOException {
        if (node == null || node.isMissingNode()) return null;
        validateKnownProperties(node.element(), type);
        try { return gson.fromJson(node.element(), type); }
        catch (RuntimeException malformed) { throw new JsonProcessingException("Invalid JSON value: " + malformed.getMessage(), malformed); }
    }
    public JsonNode valueToTree(Object value) {
        return JsonNode.wrap(gson.toJsonTree(value));
    }
    public ObjectNode createObjectNode() { return new ObjectNode(new JsonObject()); }
    public JsonNodeFactory getNodeFactory() { return JsonNodeFactory.instance; }
    public ArrayNode createArrayNode() { return new ArrayNode(new JsonArray()); }
    public String writeValueAsString(Object value) throws JsonProcessingException {
        try { return gson.toJson(value); }
        catch (RuntimeException failure) { throw new JsonProcessingException("Could not serialize JSON: " + failure.getMessage(), failure); }
    }
    public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
        return writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    }
    public void writeValue(File file, Object value) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) java.nio.file.Files.createDirectories(parent.toPath());
        try (BufferedWriter writer = java.nio.file.Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(value, writer);
        }
    }

    private static void writeElement(JsonElement element, JsonWriter out) throws IOException {
        if (element == null || element.isJsonNull()) {
            out.nullValue();
        } else if (element.isJsonObject()) {
            out.beginObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                out.name(entry.getKey());
                writeElement(entry.getValue(), out);
            }
            out.endObject();
        } else if (element.isJsonArray()) {
            out.beginArray();
            for (JsonElement child : element.getAsJsonArray()) writeElement(child, out);
            out.endArray();
        } else {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) out.value(primitive.getAsString());
            else if (primitive.isBoolean()) out.value(primitive.getAsBoolean());
            else out.value(new BigDecimal(primitive.getAsString()));
        }
    }

    private void validateKnownProperties(JsonElement json, Type target) throws JsonProcessingException {
        if (!failUnknown || json == null || json.isJsonNull()) return;
        if (target instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass)
                    && json.isJsonArray()) {
                for (JsonElement item : json.getAsJsonArray()) validateKnownProperties(item, parameterized.getActualTypeArguments()[0]);
            } else if (raw instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass)
                    && json.isJsonObject()) {
                for (JsonElement item : json.getAsJsonObject().asMap().values()) validateKnownProperties(item, parameterized.getActualTypeArguments()[1]);
            }
            return;
        }
        if (!(target instanceof Class<?> type) || type.isPrimitive() || type.isEnum()
                || type.getName().startsWith("java.") || type == Object.class
                || type == JsonNode.class || type == ObjectNode.class || type == ArrayNode.class) return;
        if (type.isArray() && json.isJsonArray()) {
            for (JsonElement item : json.getAsJsonArray()) validateKnownProperties(item, type.getComponentType());
            return;
        }
        if (!json.isJsonObject()) return;

        Set<String> allowed = new HashSet<>();
        Map<String, Field> fields = new java.util.LinkedHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                        || field.isSynthetic()) continue;
                JsonProperty annotation = field.getAnnotation(JsonProperty.class);
                String name = annotation == null ? field.getName() : annotation.value();
                if (field.getAnnotation(JsonIgnore.class) != null) continue;
                allowed.add(name);
                fields.put(name, field);
            }
        }
        for (String name : json.getAsJsonObject().keySet()) {
            if (!allowed.contains(name)) throw new JsonProcessingException("Unknown JSON property '" + name + "' for " + type.getSimpleName());
        }
        for (Map.Entry<String, Field> field : fields.entrySet()) {
            JsonElement child = json.getAsJsonObject().get(field.getKey());
            if (child != null) validateKnownProperties(child, field.getValue().getGenericType());
        }
    }
}
