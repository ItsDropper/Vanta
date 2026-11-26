package dev.itsdropper.vanta.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Central JSON configuration for Vanta.
 *
 * <p>Callers receive a configured copy rather than a shared mutable mapper.
 * This keeps serializer caches local to each mapper while ensuring that
 * security-sensitive metadata uses duplicate-key rejection consistently.</p>
 */
public final class VantaJson {
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
    );

    private static final ObjectMapper STRICT_SCHEMA_MAPPER = STRICT_MAPPER.copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private VantaJson() {
    }

    /**
     * Returns the compatibility mapper for ordinary Minecraft and launcher metadata.
     */
    public static ObjectMapper mapper() {
        return DEFAULT_MAPPER.copy();
    }

    /**
     * Returns a mapper that rejects duplicate object keys while parsing.
     */
    public static ObjectMapper strictMapper() {
        return STRICT_MAPPER.copy();
    }

    /**
     * Returns a strict mapper that also rejects unknown POJO properties.
     */
    public static ObjectMapper strictSchemaMapper() {
        return STRICT_SCHEMA_MAPPER.copy();
    }
}
