package dev.itsdropper.vanta.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tokenizes launcher argument templates before substituting values.
 * Substituting first is incorrect because paths may contain spaces.
 */
public final class LegacyArgumentResolver {
    private LegacyArgumentResolver() {
    }

    public static List<String> resolve(String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            return List.of();
        }
        if (placeholders == null) {
            throw new IllegalArgumentException("Argument placeholders cannot be null.");
        }

        List<String> result = new ArrayList<>();
        for (String token : tokenize(template)) {
            String resolved = token;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new IllegalArgumentException("Argument placeholders cannot contain null values.");
                }
                resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue());
            }

            if (resolved.contains("${")) {
                throw new IllegalArgumentException(
                        "Unsupported launcher argument placeholder: " + resolved);
            }
            result.add(resolved);
        }
        return List.copyOf(result);
    }

    public static List<String> tokenize(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;
        char quote = 0;

        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);

            if ((c == '"' || c == '\'')
                    && (i == 0 || arguments.charAt(i - 1) != '\\')) {
                if (insideQuotes) {
                    if (c == quote) {
                        insideQuotes = false;
                    } else {
                        current.append(c);
                    }
                } else {
                    insideQuotes = true;
                    quote = c;
                }
                continue;
            }

            if (Character.isWhitespace(c) && !insideQuotes) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }

        if (insideQuotes) {
            throw new IllegalArgumentException("Unterminated quote in launcher arguments.");
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return List.copyOf(result);
    }
}
