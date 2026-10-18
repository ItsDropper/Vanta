# Contributing to Vanta

Thanks for taking the time to improve Vanta. Changes should prioritize correctness, security, maintainability, and a predictable user experience.

## Before changing code

1. Check existing services and conventions before introducing another abstraction.
2. Keep changes scoped to a clear problem; avoid unrelated formatting churn.
3. Preserve per-instance isolation and existing user data.
4. Never commit credentials, session tokens, private account data, generated installers, or local configuration.
5. Do not describe behavior as supported unless it has been verified.

For large architectural changes, open an issue or discussion first so the proposed direction can be reviewed.

## Development checks

Use the checked-in Gradle wrapper rather than requiring a separately installed Gradle version.

On Windows:

```powershell
.gradlew.bat clean test
.gradlew.bat build
```

If a task is unavailable or the repository does not yet have tests for the affected behavior, state that limitation explicitly. For packaging changes, also verify the relevant packaging tasks in an environment that has the required Windows tooling.

## Pull requests

A useful pull request description includes:

- **Problem:** what is broken or missing.
- **Approach:** the design and why it is appropriate.
- **Compatibility:** effects on existing instances, settings, accounts, and installations.
- **Validation:** commands run and their results.
- **Limitations:** anything not tested or still incomplete.

Keep changes reviewable. A broad refactor should include all package references, build configuration, resource paths, tests, and documentation updates in the same change.

## Code quality

- Prefer descriptive names and small, cohesive classes.
- Keep UI work on the JavaFX Application Thread; move blocking network and filesystem work off it.
- Bound concurrency and ensure executors have a clear lifecycle.
- Close streams and other resources deterministically.
- Validate downloaded content before treating it as installed.
- Avoid logging tokens, authorization headers, or sensitive account data.
- Preserve actionable errors; do not silently swallow failures.
- Add regression tests when changing parsing, installation, repair, update, or account behavior.

## Security reports

Do not publish exploitable details or secrets in a public issue. Follow [SECURITY.md](SECURITY.md).
