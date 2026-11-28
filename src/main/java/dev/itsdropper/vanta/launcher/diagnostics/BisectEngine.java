package dev.itsdropper.vanta.launcher.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds the mods that cause a problem by testing smaller and smaller sets of mods, using delta debugging.
 *
 * <p>The engine is a pure state machine: it asks for a set of mods to be enabled, the caller launches the game and
 * reports whether the problem happened, and the engine decides what to test next. It never touches files, so it
 * can be tested exhaustively and its whole state can be saved and resumed after the launcher is closed.
 *
 * <p>Mods depend on each other, so every set it asks for is closed over required dependencies: a mod is never
 * enabled without what it needs. The search itself only narrows down the mods you chose to test. Answers are
 * cached by enabled set, so the same combination is never asked twice, and each answer can be undone.
 */
public final class BisectEngine {

    /** The user's observation after launching with the requested mods. */
    public enum Outcome { PROBLEM, FINE, UNSURE }

    public enum Status {
        /** Testing with no mods at all, to rule out the game itself. */
        BASELINE,
        SEARCHING,
        /** Confirming that the suspected mods cause the problem on their own. */
        VERIFYING,
        FOUND,
        /** The problem happens with no mods enabled. */
        NOT_A_MOD,
        /** The suspects could not reproduce the problem when tested alone, so it may be intermittent. */
        INCONCLUSIVE
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Fixed for the life of a session. */
    private final List<String> universe;
    private final Map<String, List<String>> requires;

    private Snapshot current;
    private final List<Snapshot> history = new ArrayList<>();

    /** Everything that changes as answers arrive. */
    private record Snapshot(
            List<String> culprits,
            int granularity,
            int phase,
            int index,
            Status status,
            List<String> pending,
            Map<String, Outcome> results,
            int tests,
            boolean unsure) {
    }

    private record Saved(List<String> universe, Map<String, List<String>> requires,
                         Snapshot current, List<Snapshot> history) {
    }

    private BisectEngine(List<String> universe, Map<String, List<String>> requires, Snapshot current) {
        this.universe = universe;
        this.requires = requires;
        this.current = current;
    }

    /**
     * Starts a session.
     *
     * @param mods     the mods that were enabled and are being tested
     * @param requires for each mod, the other mods it cannot load without; unknown ids are ignored
     */
    public static BisectEngine start(Collection<String> mods, Map<String, ? extends Collection<String>> requires) {
        List<String> sorted = new ArrayList<>(new TreeSet<>(mods));
        Set<String> known = new HashSet<>(sorted);
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (String mod : sorted) {
            Collection<String> needed = requires.get(mod) == null ? List.<String>of() : requires.get(mod);
            graph.put(mod, needed.stream().filter(known::contains).filter(id -> !id.equals(mod))
                    .distinct().sorted().toList());
        }
        Map<String, Outcome> results = new LinkedHashMap<>();
        // The user starts a session because the problem happens with everything enabled.
        results.put(key(closureOf(sorted, graph)), Outcome.PROBLEM);
        Snapshot first = new Snapshot(sorted, 2, 0, 0, Status.BASELINE, List.of(), results, 0, false);
        return new BisectEngine(List.copyOf(sorted), Map.copyOf(graph), first);
    }

    /** The mods to enable for the next launch, or an empty set while testing with none. Null once finished. */
    public Set<String> currentTest() {
        return isFinished() ? null : new LinkedHashSet<>(current.pending());
    }

    public boolean isFinished() {
        return switch (current.status()) {
            case FOUND, NOT_A_MOD, INCONCLUSIVE -> true;
            default -> false;
        };
    }

    public Status status() {
        return current.status();
    }

    /** The suspected mods: the full set while searching, the minimal set once found. */
    public Set<String> culprits() {
        return new LinkedHashSet<>(current.culprits());
    }

    public int testsDone() {
        return current.tests();
    }

    /** True when an unsure answer may have hidden part of the search, so the result is less certain. */
    public boolean isLessCertain() {
        return current.unsure();
    }

    /** A rough upper bound on launches still needed, for showing progress. */
    public int estimatedRemaining() {
        if (isFinished()) {
            return 0;
        }
        int size = Math.max(current.culprits().size(), 1);
        return 1 + 2 * (int) Math.ceil(Math.log(size + 1) / Math.log(2)) + 1;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public void undo() {
        if (!history.isEmpty()) {
            current = history.remove(history.size() - 1);
        }
    }

    /** Records what happened with the set returned by {@link #currentTest()} and moves to the next test. */
    public void answer(Outcome outcome) {
        if (isFinished()) {
            throw new IllegalStateException("The search is already finished");
        }
        history.add(current);
        Snapshot s = current;
        Map<String, Outcome> results = new LinkedHashMap<>(s.results());
        results.put(key(s.pending()), outcome);
        boolean unsure = s.unsure() || outcome == Outcome.UNSURE;
        int tests = s.tests() + 1;

        switch (s.status()) {
            case BASELINE -> {
                if (outcome == Outcome.PROBLEM) {
                    current = finished(s, Status.NOT_A_MOD, s.culprits(), results, tests, unsure);
                } else {
                    current = advance(new Snapshot(s.culprits(), 2, 0, 0, Status.SEARCHING,
                            List.of(), results, tests, unsure));
                }
            }
            case VERIFYING -> current = finished(s,
                    outcome == Outcome.PROBLEM ? Status.FOUND : Status.INCONCLUSIVE,
                    s.culprits(), results, tests, unsure);
            case SEARCHING -> current = advance(react(s, outcome, results, tests, unsure));
            default -> throw new IllegalStateException("Unexpected status " + s.status());
        }
    }

    // ---- search ----

    /** Applies the answer to the subset or complement that was being tested. */
    private Snapshot react(Snapshot s, Outcome outcome, Map<String, Outcome> results, int tests, boolean unsure) {
        List<List<String>> chunks = split(s.culprits(), s.granularity());
        List<String> chunk = chunks.get(s.index());
        if (outcome == Outcome.PROBLEM) {
            if (s.phase() == 0) {
                return new Snapshot(chunk, 2, 0, 0, Status.SEARCHING, List.of(), results, tests, unsure);
            }
            List<String> complement = minus(s.culprits(), chunk);
            return new Snapshot(complement, Math.max(s.granularity() - 1, 2), 0, 0, Status.SEARCHING,
                    List.of(), results, tests, unsure);
        }
        return new Snapshot(s.culprits(), s.granularity(), s.phase(), s.index() + 1, Status.SEARCHING,
                List.of(), results, tests, unsure);
    }

    /** Moves to the next combination that has not been answered yet, or finishes. */
    private Snapshot advance(Snapshot start) {
        Snapshot s = start;
        while (true) {
            List<String> culprits = s.culprits();
            if (culprits.size() <= 1) {
                return verify(s);
            }
            List<List<String>> chunks = split(culprits, s.granularity());
            if (s.phase() == 0 && s.index() < chunks.size()) {
                List<String> test = closure(chunks.get(s.index()));
                Outcome known = s.results().get(key(test));
                if (known == null) {
                    return withPending(s, test);
                }
                s = react(s, known, s.results(), s.tests(), s.unsure());
                continue;
            }
            if (s.phase() == 0) {
                // With two chunks the complements are the chunks again, so there is nothing new to test.
                s = new Snapshot(culprits, s.granularity(), s.granularity() > 2 ? 1 : 2, 0, Status.SEARCHING,
                        List.of(), s.results(), s.tests(), s.unsure());
                continue;
            }
            if (s.phase() == 1 && s.index() < chunks.size()) {
                List<String> test = closure(minus(culprits, chunks.get(s.index())));
                Outcome known = s.results().get(key(test));
                if (known == null) {
                    return withPending(s, test);
                }
                s = react(s, known, s.results(), s.tests(), s.unsure());
                continue;
            }
            if (s.granularity() >= culprits.size()) {
                return verify(s);
            }
            s = new Snapshot(culprits, Math.min(culprits.size(), s.granularity() * 2), 0, 0, Status.SEARCHING,
                    List.of(), s.results(), s.tests(), s.unsure());
        }
    }

    private Snapshot verify(Snapshot s) {
        List<String> test = closure(s.culprits());
        Outcome known = s.results().get(key(test));
        if (known == Outcome.PROBLEM) {
            return finished(s, Status.FOUND, s.culprits(), s.results(), s.tests(), s.unsure());
        }
        if (known != null) {
            return finished(s, Status.INCONCLUSIVE, s.culprits(), s.results(), s.tests(), s.unsure());
        }
        return new Snapshot(s.culprits(), s.granularity(), s.phase(), s.index(), Status.VERIFYING,
                test, s.results(), s.tests(), s.unsure());
    }

    private static Snapshot withPending(Snapshot s, List<String> test) {
        return new Snapshot(s.culprits(), s.granularity(), s.phase(), s.index(), Status.SEARCHING,
                test, s.results(), s.tests(), s.unsure());
    }

    private static Snapshot finished(Snapshot from, Status status, List<String> culprits,
                                     Map<String, Outcome> results, int tests, boolean unsure) {
        return new Snapshot(culprits, from.granularity(), from.phase(), from.index(), status,
                List.of(), results, tests, unsure);
    }

    // ---- dependency closure and set helpers ----

    private List<String> closure(Collection<String> mods) {
        return closureOf(mods, requires);
    }

    /** The mods plus everything they require, directly or indirectly, in a stable order. */
    private static List<String> closureOf(Collection<String> mods, Map<String, List<String>> requires) {
        Set<String> result = new TreeSet<>();
        List<String> queue = new ArrayList<>(mods);
        while (!queue.isEmpty()) {
            String mod = queue.remove(queue.size() - 1);
            if (result.add(mod)) {
                queue.addAll(requires.getOrDefault(mod, List.of()));
            }
        }
        return new ArrayList<>(result);
    }

    private static List<List<String>> split(List<String> items, int parts) {
        List<List<String>> chunks = new ArrayList<>();
        int size = items.size();
        int start = 0;
        for (int i = 0; i < parts; i++) {
            int end = start + (size - start) / (parts - i);
            if (end > start) {
                chunks.add(items.subList(start, end));
            }
            start = end;
        }
        return chunks;
    }

    private static List<String> minus(List<String> items, List<String> remove) {
        Set<String> drop = new HashSet<>(remove);
        return items.stream().filter(item -> !drop.contains(item)).toList();
    }

    private static String key(Collection<String> mods) {
        return String.join("\n", new TreeSet<>(mods));
    }

    // ---- persistence ----

    public String toJson() {
        return GSON.toJson(new Saved(universe, requires, current, history));
    }

    public static BisectEngine fromJson(String json) {
        Saved saved = GSON.fromJson(json, Saved.class);
        if (saved == null || saved.universe() == null || saved.current() == null) {
            throw new IllegalArgumentException("Not a saved mod search");
        }
        BisectEngine engine = new BisectEngine(
                List.copyOf(saved.universe()), new HashMap<>(saved.requires()), saved.current());
        if (saved.history() != null) {
            engine.history.addAll(saved.history());
        }
        return engine;
    }
}
