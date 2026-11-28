package dev.itsdropper.vanta.ui.legal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Terms of Service and Privacy Policy that ship inside the launcher.
 *
 * <p>The repository root holds the same files for people reading on GitHub, and a test keeps both copies equal.
 * Each document's version is the date on its "Last updated" line, so editing a document and bumping that date is
 * all it takes to make existing users accept the new text.
 */
public final class LegalDocuments {

    public record Document(String markdown, String version) {
    }

    private static final Pattern UPDATED = Pattern.compile("\\*\\*Last updated:\\*\\*\\s*(.+)");

    private LegalDocuments() {
    }

    public static Document terms() {
        return load("/legal/terms.md");
    }

    public static Document privacy() {
        return load("/legal/privacy.md");
    }

    private static final Pattern RELATIVE_LINK = Pattern.compile("\\[([^\\]]+)\\]\\((?!https?://)([^)]+)\\)");
    private static final String REPOSITORY_FILES = "https://github.com/ItsDropper/Vanta/blob/main/";

    /**
     * Markdown for showing inside the launcher: links between the two documents become plain text, because both
     * are one tab away, and links to other repository files point at their GitHub pages.
     */
    public static String forDisplay(String markdown) {
        Matcher matcher = RELATIVE_LINK.matcher(markdown);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String target = matcher.group(2);
            String replacement = "PRIVACY.md".equals(target) || "TERMS.md".equals(target)
                    ? matcher.group(1)
                    : "[" + matcher.group(1) + "](" + REPOSITORY_FILES + target + ")";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static Document parse(String markdown) {
        Matcher matcher = UPDATED.matcher(markdown);
        if (!matcher.find()) {
            throw new IllegalStateException("A legal document is missing its \"Last updated\" line");
        }
        return new Document(markdown, matcher.group(1).trim());
    }

    private static Document load(String resource) {
        try (InputStream input = LegalDocuments.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled document " + resource);
            }
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read bundled document " + resource, failure);
        }
    }
}
