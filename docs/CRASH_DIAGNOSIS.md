# Crash diagnosis and case review

Vanta's crash diagnosis is deliberately deterministic and local-first. It does not use an LLM, call a hosted model, or upload crash logs.

## Runtime diagnosis

`RepairDiagnosticEngine` runs explicit, high-specificity signatures first. If those do not identify the failure, `DeterministicCrashModel` applies a small multinomial Naive Bayes classifier trained from the bundled `crash-cases.tsv` resource.

The model:
- is trained in Java from labelled, version-controlled examples;
- tokenizes exception names and qualified identifiers into their semantic components;
- returns a category, confidence, and feature evidence;
- abstains when the prediction is weak or ambiguous;
- is advisory only and cannot authorize or execute a repair.

The bundled corpus currently contains 63 synthetic, labelled examples across 21 failure categories. It is a starting dataset, not a claim of broad real-world accuracy. Add new examples only after reviewing their label and checking the case for private data.

## Local case collection

The queue is stored at `%AppData%/Vanta/crash-case-review/cases.json` on Windows, and under the platform-specific Vanta data directory on macOS/Linux.

Cases enter the queue only after a user explicitly saves the current failure or imports a crash log from Experimental → Local crash-case review. The repository:
- never modifies or deletes the source log;
- bounds imports to 2 MiB and stored log text to 96 KiB;
- redacts common credentials, usernames, UUIDs, email addresses, IP/MAC addresses and home-directory paths before persistence;
- groups matching sanitized reports by a normalized SHA-256 fingerprint;
- keeps up to 256 cases, evicting rejected cases before refusing new data;
- uses atomic replacement, process/thread locking, and owner-only POSIX permissions where supported;
- stores no raw source path.

Redaction is best-effort, not a guarantee of anonymity. Inspect the sanitized evidence and every export before sharing it.

## Human review and verified fixes

A proposed model category is only a suggestion. A reviewer must explicitly confirm a category or reject the case. Confirmed labels and fix notes can be corrected later; rejected cases can be reopened for another review. Confirmed cases may include a short, sanitized note describing the fix that actually resolved the failure.

Previously confirmed notes can appear in repair diagnostics for the same failure category. They are references only: Vanta does not run them, assume they apply to every mod/loader version, or perform destructive actions based on them.

## Export and CI process

The review screen has two separate, user-initiated exports:

- `vanta-reviewed-crash-cases.tsv`: confirmed log/category pairs for model training.
- `vanta-verified-crash-fixes.json`: confirmed fix notes with their sanitized case context.

Exports remain on the chosen local destination. Vanta does not submit them to GitHub or any service.

To update the bundled model corpus:
1. Export confirmed cases locally.
2. Review every case, category, and redacted field before adding it to source control.
3. Merge only approved rows into `src/main/resources/dev/itsdropper/vanta/launcher/repair/crash-cases.tsv`.
4. Add held-out examples and expected categories to the regression tests.
5. Let the crash-diagnosis workflow train the model from the updated corpus and run the full regression suite on Ubuntu, Windows, and macOS.

The local queue is never silently merged into the repository corpus. This keeps personal crash data out of CI and makes every shipped training example reviewable.
