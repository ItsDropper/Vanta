package org.example.launcher.service;

/**
 * Result of checking whether a candidate can coexist with the
 * currently resolved dependency graph.
 *
 * Compatibility is deliberately represented as a small value object so
 * the resolver can keep the reason for a rejected candidate without
 * coupling the result to the resolver implementation.
 */
record CompatibilityResult(
        boolean compatible,
        String reason
) {
    static CompatibilityResult ok() {
        return new CompatibilityResult(true, null);
    }

    static CompatibilityResult conflict(String reason) {
        return new CompatibilityResult(false, reason);
    }
}
