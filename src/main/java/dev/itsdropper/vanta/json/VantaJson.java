package dev.itsdropper.vanta.json;

/** Central JSON configuration for all Vanta-owned persistence and metadata code. */
public final class VantaJson {
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper(true, false, false);
    private static final ObjectMapper STRICT_SCHEMA_MAPPER = STRICT_MAPPER
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private VantaJson() {}

    public static ObjectMapper mapper() { return DEFAULT_MAPPER.copy(); }
    public static ObjectMapper strictMapper() { return STRICT_MAPPER.copy(); }
    public static ObjectMapper strictSchemaMapper() { return STRICT_SCHEMA_MAPPER.copy(); }
}
