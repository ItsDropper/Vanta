package dev.itsdropper.vanta.launcher.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.itsdropper.vanta.launcher.diagnostics.BisectEngine.Outcome;
import dev.itsdropper.vanta.launcher.diagnostics.BisectEngine.Status;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class BisectEngineTest {

    private static List<String> mods(int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(String.format("mod%02d", i));
        }
        return result;
    }

    /** Plays the user: reports PROBLEM exactly when the oracle says the enabled set reproduces the problem. */
    private static BisectEngine run(BisectEngine engine, Predicate<Set<String>> problem, Set<Set<String>> asked) {
        int guard = 0;
        while (!engine.isFinished()) {
            Set<String> test = engine.currentTest();
            assertTrue(asked.add(new TreeSet<>(test)), "asked the same combination twice: " + test);
            engine.answer(problem.test(test) ? Outcome.PROBLEM : Outcome.FINE);
            assertTrue(++guard < 500, "search did not terminate");
        }
        return engine;
    }

    @Test
    void findsASingleCulpritAmongManyMods() {
        List<String> all = mods(16);
        BisectEngine engine = BisectEngine.start(all, Map.of());

        run(engine, enabled -> enabled.contains("mod11"), new HashSet<>());

        assertEquals(Status.FOUND, engine.status());
        assertEquals(Set.of("mod11"), engine.culprits());
        assertTrue(engine.testsDone() <= 12, "took " + engine.testsDone() + " launches");
    }

    @Test
    void findsTwoModsThatOnlyFailTogether() {
        BisectEngine engine = BisectEngine.start(mods(20), Map.of());

        run(engine, enabled -> enabled.contains("mod03") && enabled.contains("mod17"), new HashSet<>());

        assertEquals(Status.FOUND, engine.status());
        assertEquals(Set.of("mod03", "mod17"), engine.culprits());
    }

    @Test
    void neverEnablesAModWithoutWhatItRequires() {
        Map<String, List<String>> requires = new LinkedHashMap<>();
        requires.put("mod05", List.of("mod01", "mod02"));
        requires.put("mod01", List.of("mod02"));
        BisectEngine engine = BisectEngine.start(mods(8), requires);

        while (!engine.isFinished()) {
            Set<String> test = engine.currentTest();
            if (test.contains("mod05")) {
                assertTrue(test.containsAll(Set.of("mod01", "mod02")), "mod05 enabled without its libraries");
            }
            if (test.contains("mod01")) {
                assertTrue(test.contains("mod02"));
            }
            engine.answer(test.contains("mod05") ? Outcome.PROBLEM : Outcome.FINE);
        }

        assertEquals(Set.of("mod05"), engine.culprits());
    }

    @Test
    void aProblemWithNoModsIsNotAModProblem() {
        BisectEngine engine = BisectEngine.start(mods(5), Map.of());

        assertEquals(Status.BASELINE, engine.status());
        assertEquals(Set.of(), engine.currentTest());
        engine.answer(Outcome.PROBLEM);

        assertEquals(Status.NOT_A_MOD, engine.status());
        assertTrue(engine.isFinished());
        assertNull(engine.currentTest());
    }

    @Test
    void answeringAFinishedSearchIsRejected() {
        BisectEngine engine = BisectEngine.start(mods(2), Map.of());
        engine.answer(Outcome.PROBLEM);

        assertThrows(IllegalStateException.class, () -> engine.answer(Outcome.FINE));
    }

    @Test
    void whenNothingSmallerReproducesTheProblemEveryModIsReported() {
        BisectEngine engine = BisectEngine.start(mods(4), Map.of());

        run(engine, enabled -> enabled.size() == 4, new HashSet<>());

        assertEquals(Status.FOUND, engine.status());
        assertEquals(4, engine.culprits().size());
    }

    @Test
    void anUnsureAnswerMarksTheResultAsLessCertain() {
        BisectEngine engine = BisectEngine.start(mods(4), Map.of());
        engine.answer(Outcome.FINE);
        assertFalse(engine.isLessCertain());

        engine.answer(Outcome.UNSURE);

        assertTrue(engine.isLessCertain());
    }

    @Test
    void undoGoesBackOneLaunch() {
        BisectEngine engine = BisectEngine.start(mods(8), Map.of());
        engine.answer(Outcome.FINE);
        Set<String> second = engine.currentTest();
        engine.answer(Outcome.FINE);
        assertTrue(engine.canUndo());

        engine.undo();

        assertEquals(second, engine.currentTest());
        engine.undo();
        assertEquals(Status.BASELINE, engine.status());
        assertFalse(engine.canUndo());
    }

    @Test
    void aSavedSessionResumesWithTheSameQuestions() {
        List<String> all = mods(12);
        Predicate<Set<String>> problem = enabled -> enabled.contains("mod07");
        BisectEngine original = BisectEngine.start(all, Map.of("mod07", List.of("mod02")));
        for (int launch = 0; launch < 2; launch++) {
            original.answer(problem.test(original.currentTest()) ? Outcome.PROBLEM : Outcome.FINE);
        }

        BisectEngine resumed = BisectEngine.fromJson(original.toJson());

        assertEquals(original.currentTest(), resumed.currentTest());
        assertEquals(original.testsDone(), resumed.testsDone());
        assertTrue(resumed.canUndo());
        while (!original.isFinished()) {
            assertEquals(original.currentTest(), resumed.currentTest());
            Outcome outcome = problem.test(original.currentTest()) ? Outcome.PROBLEM : Outcome.FINE;
            original.answer(outcome);
            resumed.answer(outcome);
        }
        assertEquals(original.culprits(), resumed.culprits());
        assertEquals(Set.of("mod07"), resumed.culprits());
    }

    @Test
    void rejectsGarbageWhenLoading() {
        assertThrows(RuntimeException.class, () -> BisectEngine.fromJson("{}"));
    }

    @Test
    void randomProblemsAreIsolatedToAMinimalSet() {
        Random random = new Random(20261128);
        for (int round = 0; round < 300; round++) {
            int count = 1 + random.nextInt(40);
            List<String> all = mods(count);

            // Dependencies only point at lower-numbered mods, so there are no cycles.
            Map<String, List<String>> requires = new LinkedHashMap<>();
            for (int i = 1; i < count; i++) {
                List<String> needed = new ArrayList<>();
                for (int tries = random.nextInt(3); tries > 0; tries--) {
                    needed.add(all.get(random.nextInt(i)));
                }
                requires.put(all.get(i), needed);
            }

            Set<String> trigger = new HashSet<>();
            for (int size = 1 + random.nextInt(3); size > 0; size--) {
                trigger.add(all.get(random.nextInt(count)));
            }
            Predicate<Set<String>> problem = enabled -> enabled.containsAll(trigger);

            BisectEngine engine = BisectEngine.start(all, requires);
            run(engine, problem, new HashSet<>());

            assertEquals(Status.FOUND, engine.status(), "round " + round);
            Set<String> found = engine.culprits();
            Set<String> closure = closureOf(found, requires);
            assertTrue(closure.containsAll(trigger), "round " + round + " found a set that does not reproduce it");
            for (String mod : found) {
                Set<String> smaller = new HashSet<>(found);
                smaller.remove(mod);
                assertFalse(closureOf(smaller, requires).containsAll(trigger),
                        "round " + round + " could drop " + mod);
            }
            assertTrue(engine.testsDone() <= 4 * count + 4, "round " + round + " took " + engine.testsDone());
        }
    }

    private static Set<String> closureOf(Set<String> mods, Map<String, List<String>> requires) {
        Set<String> result = new HashSet<>();
        List<String> queue = new ArrayList<>(mods);
        while (!queue.isEmpty()) {
            String mod = queue.remove(queue.size() - 1);
            if (result.add(mod)) {
                queue.addAll(requires.getOrDefault(mod, List.of()));
            }
        }
        return result;
    }
}
