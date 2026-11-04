package dev.itsdropper.vanta.launcher.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges compatible launcher-profile overlays into one standalone profile.
 * Overlay order is significant and must match LoaderStackConfiguration order.
 */
public final class LoaderProfileStackMerger {
    private LoaderProfileStackMerger() {
    }

    public static JsonNode merge(
            JsonNode vanilla,
            List<JsonNode> orderedOverlays,
            LoaderStackConfiguration configuration
    ) {
        if (configuration == null) {
            throw new IllegalArgumentException("Loader stack configuration is required.");
        }
        return merge(vanilla, orderedOverlays, configuration.getOrderedTweakers());
    }

    public static JsonNode merge(
            JsonNode vanilla,
            List<JsonNode> orderedOverlays,
            List<String> explicitlyOrderedTweakers
    ) {
        if (vanilla == null || !vanilla.isObject()) {
            throw new IllegalArgumentException("Base Minecraft profile must be a JSON object.");
        }
        ObjectNode merged = (ObjectNode) vanilla.deepCopy();
        Map<String, JsonNode> libraries = new LinkedHashMap<>();
        collectLibraries(libraries, vanilla);

        String baseMainClass = text(vanilla.get("mainClass"));
        String effectiveMainClass = null;
        List<String> tweakers = new ArrayList<>();

        if (orderedOverlays != null) {
            for (JsonNode overlay : orderedOverlays) {
                validateOverlay(overlay);
                String overlayMainClass = text(overlay.get("mainClass"));
                if (overlayMainClass != null) {
                    if (effectiveMainClass != null && !effectiveMainClass.equals(overlayMainClass)) {
                        throw new IllegalStateException(
                                "Cannot chain profiles with different main classes: "
                                        + effectiveMainClass + " and " + overlayMainClass);
                    }
                    effectiveMainClass = overlayMainClass;
                }
                collectLibraries(libraries, overlay);
                collectTweakers(tweakers, overlay.get("minecraftArguments"));
                mergeModernArguments(merged, overlay.get("arguments"));
            }
        }

        if (explicitlyOrderedTweakers != null) {
            for (String tweaker : explicitlyOrderedTweakers) {
                validateTweaker(tweaker);
                tweakers.add(tweaker);
            }
        }

        if (effectiveMainClass != null) {
            merged.put("mainClass", effectiveMainClass);
        } else if (baseMainClass != null) {
            merged.put("mainClass", baseMainClass);
        }

        ArrayNode mergedLibraries = JsonNodeFactory.instance.arrayNode();
        libraries.values().forEach(value -> mergedLibraries.add(value.deepCopy()));
        merged.set("libraries", mergedLibraries);

        if (!tweakers.isEmpty()) {
            String baseArguments = text(vanilla.get("minecraftArguments"));
            StringBuilder arguments = new StringBuilder(baseArguments == null ? "" : baseArguments);
            for (String tweaker : tweakers) {
                if (!arguments.isEmpty()) {
                    arguments.append(' ');
                }
                arguments.append("--tweakClass ").append(quote(tweaker));
            }
            merged.put("minecraftArguments", arguments.toString());
        }

        merged.remove("inheritsFrom");
        return merged;
    }

    private static void collectLibraries(Map<String, JsonNode> target, JsonNode profile) {
        JsonNode libraries = profile.get("libraries");
        if (libraries == null || libraries.isNull()) {
            return;
        }
        if (!libraries.isArray()) {
            throw new IllegalStateException("Profile libraries must be an array.");
        }
        for (JsonNode library : libraries) {
            if (library == null || !library.isObject()) {
                throw new IllegalStateException("Profile contains an invalid library entry.");
            }
            String name = text(library.get("name"));
            if (name == null) {
                throw new IllegalStateException("Profile library is missing its Maven coordinate.");
            }
            // LinkedHashMap preserves first-seen classpath position while the
            // latest profile definition replaces the metadata for that coordinate.
            target.put(libraryKey(name), library.deepCopy());
        }
    }

    private static String libraryKey(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 3 || parts.length > 4) {
            return coordinate;
        }
        // A loader overlay replaces an earlier version of the same
        // Maven artifact, while classifier variants remain distinct.
        return parts[0] + ":" + parts[1]
                + (parts.length == 4 ? ":" + parts[3] : "");
    }

    private static void collectTweakers(List<String> target, JsonNode argumentTemplate) {
        String template = text(argumentTemplate);
        if (template == null) {
            return;
        }
        List<String> tokens = tokenize(template);
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("--tweakClass".equals(token)) {
                if (++i >= tokens.size()) {
                    throw new IllegalStateException("Profile has --tweakClass without a class name.");
                }
                validateTweaker(tokens.get(i));
                target.add(tokens.get(i));
            } else if (token.startsWith("--tweakClass=")) {
                String value = token.substring("--tweakClass=".length());
                validateTweaker(value);
                target.add(value);
            }
        }
    }

    private static void mergeModernArguments(ObjectNode target, JsonNode overlayArguments) {
        if (overlayArguments == null || overlayArguments.isNull()) {
            return;
        }
        if (!overlayArguments.isObject()) {
            throw new IllegalStateException("Modern profile arguments must be an object.");
        }
        ObjectNode targetArguments;
        JsonNode existing = target.get("arguments");
        if (existing == null || !existing.isObject()) {
            targetArguments = JsonNodeFactory.instance.objectNode();
            target.set("arguments", targetArguments);
        } else {
            targetArguments = (ObjectNode) existing;
        }
        overlayArguments.fields().forEachRemaining(field -> {
            JsonNode incoming = field.getValue();
            if (!incoming.isArray()) {
                throw new IllegalStateException("Profile argument group '" + field.getKey() + "' must be an array.");
            }
            JsonNode current = targetArguments.get(field.getKey());
            ArrayNode combined;
            if (current == null) {
                combined = JsonNodeFactory.instance.arrayNode();
                targetArguments.set(field.getKey(), combined);
            } else if (current.isArray()) {
                combined = (ArrayNode) current;
            } else {
                throw new IllegalStateException("Base argument group '" + field.getKey() + "' must be an array.");
            }
            for (JsonNode value : incoming) {
                combined.add(value.deepCopy());
            }
        });
    }

    private static List<String> tokenize(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        boolean started = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (escaped) {
                token.append(ch);
                escaped = false;
                started = true;
            } else if (ch == '\\' && quoted) {
                escaped = true;
            } else if (ch == '"') {
                quoted = !quoted;
                started = true;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (started) {
                    result.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(ch);
                started = true;
            }
        }
        if (quoted || escaped) {
            throw new IllegalStateException("Profile minecraftArguments contains an unterminated quoted value.");
        }
        if (started) {
            result.add(token.toString());
        }
        return result;
    }

    private static void validateOverlay(JsonNode overlay) {
        if (overlay == null || !overlay.isObject()) {
            throw new IllegalStateException("Loader profile overlay must be a JSON object.");
        }
    }

    private static void validateTweaker(String tweaker) {
        if (tweaker == null || !tweaker.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
            throw new IllegalStateException("Invalid launch tweaker class name.");
        }
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank()
                ? node.asText().trim() : null;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
