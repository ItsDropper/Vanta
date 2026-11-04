package dev.itsdropper.vanta.launcher.loader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoaderStackConfigurationTest {

    @Test
    void returnsLayersInConfiguredLaunchOrder() {
        LoaderStackConfiguration configuration = new LoaderStackConfiguration(List.of(
                new LoaderStackConfiguration.Layer(
                        "optifine-bridge", "1.0", LoaderStackConfiguration.LayerType.MOD, 30, List.of()),
                new LoaderStackConfiguration.Layer(
                        "forge", "1.12.2-14.23.5.2860", LoaderStackConfiguration.LayerType.PROFILE_OVERLAY, 10, List.of()),
                new LoaderStackConfiguration.Layer(
                        "liteloader", "1.12.2", LoaderStackConfiguration.LayerType.TWEAKER, 20,
                        List.of("com.mumfrey.liteloader.launch.LiteLoaderTweaker"))
        ));

        assertEquals(List.of("forge", "liteloader", "optifine-bridge"),
                configuration.getOrderedLayers().stream()
                        .map(LoaderStackConfiguration.Layer::id)
                        .toList());
    }

    @Test
    void rejectsDuplicateLayerIds() {
        assertThrows(IllegalStateException.class, () -> new LoaderStackConfiguration(List.of(
                new LoaderStackConfiguration.Layer(
                        "forge", "1.0", LoaderStackConfiguration.LayerType.PROFILE_OVERLAY, 10, List.of()),
                new LoaderStackConfiguration.Layer(
                        "forge", "2.0", LoaderStackConfiguration.LayerType.TWEAKER, 20, List.of())
        )));
    }

    @Test
    void rejectsInvalidTweakerClassNames() {
        assertThrows(IllegalStateException.class, () -> new LoaderStackConfiguration(List.of(
                new LoaderStackConfiguration.Layer(
                        "legacy-loader", "1.0", LoaderStackConfiguration.LayerType.TWEAKER, 10,
                        List.of("../not-a-class"))
        )));
    }
}
