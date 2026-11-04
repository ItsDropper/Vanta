package dev.itsdropper.vanta.launcher;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyArgumentResolverTest {

    @Test
    void preservesSpacesInsideSubstitutedPaths() {
        String gameDirectory = "C:\\Users\\Test User\\Vanta Instances\\Alpha";
        List<String> actual = LegacyArgumentResolver.resolve(
                "--gameDir ${game_directory} --username ${auth_player_name}",
                Map.of(
                        "game_directory", gameDirectory,
                        "auth_player_name", "Steve"
                )
        );

        assertEquals(List.of("--gameDir", gameDirectory, "--username", "Steve"), actual);
    }

    @Test
    void preservesQuotedArgumentsAsSingleTokens() {
        List<String> actual = LegacyArgumentResolver.resolve(
                '--userProperties "{}" --gameDir "${game_directory}"',
                Map.of("game_directory", "C:\\Users\\Test User\\.vanta")
        );

        assertEquals(List.of("--userProperties", "{}", "--gameDir",
                "C:\\Users\\Test User\\.vanta"), actual);
    }

    @Test
    void rejectsUnknownPlaceholders() {
        assertThrows(IllegalArgumentException.class, () ->
                LegacyArgumentResolver.resolve("--username ${unknown}", Map.of()));
    }

    @Test
    void rejectsUnterminatedQuotes() {
        assertThrows(IllegalArgumentException.class, () ->
                LegacyArgumentResolver.tokenize("--gameDir \"unterminated"));
    }
}
