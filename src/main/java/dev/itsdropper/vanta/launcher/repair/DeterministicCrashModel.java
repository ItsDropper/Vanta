package dev.itsdropper.vanta.launcher.repair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small deterministic multinomial Naive Bayes model trained from the
 * versioned, labelled crash-case corpus bundled with Vanta.
 *
 * This model ranks diagnostic hypotheses only. It never authorizes a repair.
 * Explicit signature rules remain authoritative and run before this fallback.
 */
public final class DeterministicCrashModel {
    private static final String CORPUS = "/dev/itsdropper/vanta/launcher/repair/crash-cases.tsv";
    private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9_.$/-]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "this", "that", "when", "while",
            "could", "would", "should", "error", "exception", "failed", "failure",
            "minecraft", "java", "thread", "main", "at", "net", "org", "com"
    );
    private static final DeterministicCrashModel MODEL = load();

    public record Prediction(FailureCategory category, double confidence, String evidence) { }

    private record Example(FailureCategory category, Set<String> tokens) { }

    private static final class ClassStats {
        private int documents;
        private int tokenCount;
        private final Map<String, Integer> frequencies = new HashMap<>();
    }

    private final Map<FailureCategory, ClassStats> classes;
    private final Set<String> vocabulary;
    private final int documentCount;

    private DeterministicCrashModel(List<Example> examples) {
        Map<FailureCategory, ClassStats> trained = new EnumMap<>(FailureCategory.class);
        Set<String> words = new HashSet<>();
        for (Example example : examples) {
            ClassStats stats = trained.computeIfAbsent(example.category(), ignored -> new ClassStats());
            stats.documents++;
            for (String token : example.tokens()) {
                words.add(token);
                stats.frequencies.merge(token, 1, Integer::sum);
                stats.tokenCount++;
            }
        }
        this.classes = Map.copyOf(trained);
        this.vocabulary = Set.copyOf(words);
        this.documentCount = examples.size();
    }

    public static Prediction classify(String log) {
        if (log == null || log.isBlank() || MODEL.vocabulary.isEmpty()) return null;
        Set<String> input = tokens(log);
        input.retainAll(MODEL.vocabulary);
        if (input.size() < 2) return null;

        List<Map.Entry<FailureCategory, Double>> scores = new ArrayList<>();
        double total = MODEL.documentCount;
        int vocabularySize = MODEL.vocabulary.size();
        for (Map.Entry<FailureCategory, ClassStats> entry : MODEL.classes.entrySet()) {
            ClassStats stats = entry.getValue();
            double score = Math.log(stats.documents / total);
            int denominator = stats.tokenCount + vocabularySize;
            for (String token : input) {
                score += Math.log((stats.frequencies.getOrDefault(token, 0) + 1.0) / denominator);
            }
            scores.add(Map.entry(entry.getKey(), score));
        }
        scores.sort(Map.Entry.<FailureCategory, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey));

        double max = scores.get(0).getValue();
        double sum = 0;
        for (Map.Entry<FailureCategory, Double> score : scores) sum += Math.exp(score.getValue() - max);
        double confidence = 1.0 / sum;
        double second = scores.size() > 1 ? Math.exp(scores.get(1).getValue() - max) / sum : 0;
        if (confidence < 0.42 || confidence - second < 0.12) return null;

        FailureCategory category = scores.get(0).getKey();
        List<String> evidence = input.stream().sorted().toList();
        return new Prediction(category, confidence, String.join(", ", evidence));
    }

    static int trainingExampleCount() {
        return MODEL.documentCount;
    }

    private static DeterministicCrashModel load() {
        try (InputStream stream = DeterministicCrashModel.class.getResourceAsStream(CORPUS)) {
            if (stream == null) throw new IllegalStateException("Crash diagnosis training corpus is missing.");
            List<Example> examples = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\t", 2);
                    if (fields.length != 2) throw new IllegalStateException("Invalid crash case at line " + lineNumber);
                    FailureCategory category = FailureCategory.valueOf(fields[0].trim());
                    Set<String> tokens = tokens(fields[1]);
                    if (tokens.size() < 2) throw new IllegalStateException("Crash case has too few features at line " + lineNumber);
                    examples.add(new Example(category, tokens));
                }
            }
            if (examples.isEmpty()) throw new IllegalStateException("Crash diagnosis training corpus is empty.");
            return new DeterministicCrashModel(examples);
        } catch (IOException | IllegalArgumentException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOP_WORDS.contains(token)) result.add(token);
            // Keep the qualified identifier and its semantic components.
            // This lets a held-out fully-qualified exception class match a
            // corpus example that records only the simple class name.
            for (String component : token.split("[.$/_-]+")) {
                if (component.length() >= 3 && !STOP_WORDS.contains(component)) {
                    result.add(component);
                }
            }
        }
        return result;
    }

}
