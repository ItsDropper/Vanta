package dev.itsdropper.vanta.launcher.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

public final class InstanceEngineTest {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String legacyJson = """
                {
                  "id": "legacy",
                  "name": "Old Java profile",
                  "minecraftVersion": "1.21.11",
                  "loader": "Fabric",
                  "loaderVersion": "0.17.2",
                  "directory": "C:/Vanta/instances/legacy",
                  "icon": "SHIELD"
                }
                """;
        Instance legacy = mapper.readValue(legacyJson, Instance.class);
        require(Instance.ENGINE_JAVA.equals(legacy.getEngine()),
                "Legacy metadata without engine must default to JAVA.");

        Instance horizon = new Instance(
                "horizon-test",
                "Horizon test",
                "26.3",
                "Horizon",
                "",
                Path.of("C:/Vanta/instances/horizon-test"),
                "SHIELD",
                Instance.ENGINE_HORIZON
        );

        Instance roundTrip = mapper.readValue(
                mapper.writeValueAsString(horizon),
                Instance.class
        );
        require(roundTrip.isHorizon(), "Horizon engine must survive JSON round-trip.");
        require(roundTrip.withIcon("GRASS").isHorizon(),
                "Changing an icon must preserve the engine.");
        require("Horizon · Rust (experimental)".equals(roundTrip.getDisplayLoader()),
                "Horizon profiles must have an accurate display label.");

        boolean rejected = false;
        try {
            new Instance("bad", "Bad", "26.3", "Unknown", "", Path.of("bad"), "SHIELD", "UNKNOWN");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Unknown engines must be rejected.");

        System.out.println("InstanceEngineTest passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
