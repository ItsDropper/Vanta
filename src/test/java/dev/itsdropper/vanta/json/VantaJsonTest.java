package dev.itsdropper.vanta.json;

import dev.itsdropper.vanta.json.JsonProcessingException;
import dev.itsdropper.vanta.json.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VantaJsonTest {
    @Test
    void mapperReturnsIndependentInstances() {
        ObjectMapper first = VantaJson.mapper();
        ObjectMapper second = VantaJson.mapper();

        assertNotSame(first, second);
    }

    @Test
    void compatibilityMapperRetainsDefaultDuplicateKeyBehavior() throws Exception {
        assertEquals(2, VantaJson.mapper().readTree("{\"value\":1,\"value\":2}")
                .path("value").asInt());
    }

    @Test
    void strictMapperRejectsDuplicateObjectKeys() {
        assertThrows(JsonProcessingException.class,
                () -> VantaJson.strictMapper().readTree("{\"value\":1,\"value\":2}"));
    }

    @Test
    void strictSchemaMapperRejectsUnknownProperties() {
        assertThrows(JsonProcessingException.class,
                () -> VantaJson.strictSchemaMapper().readValue(
                        "{\"known\":\"ok\",\"unexpected\":true}", TestPayload.class));
    }

    static final class TestPayload {
        public String known;
    }
}
