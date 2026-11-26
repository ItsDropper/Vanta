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
    void integralNumberValidationRejectsDecimalAndExponentForms() throws Exception {
        assertEquals(true, VantaJson.mapper().readTree("{\"value\":1}").path("value").isIntegralNumber());
        assertEquals(false, VantaJson.mapper().readTree("{\"value\":1.0}").path("value").isIntegralNumber());
        assertEquals(false, VantaJson.mapper().readTree("{\"value\":1e3}").path("value").isIntegralNumber());
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
