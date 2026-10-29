# Experimental Rust runtime integration

Status: prototype planning and UI entry only. This is not a playable runtime integration yet.

Vanta's default engine remains the normal Java client. The experimental target is [Horizon](https://github.com/horizon-rust/horizon), a separate MIT-licensed Rust implementation of Minecraft Java Edition 26.3.

## Compatibility boundary

- Horizon currently targets Minecraft 26.3; do not silently map arbitrary Java Edition versions to it.
- Ordinary Java mods, including Fabric and Forge mods, do not run in Horizon. Horizon has its own native mod system and supports only the compatible content it documents.
- Worlds and resource/data packs need to be validated against Horizon's compatibility notes before Vanta advertises them as supported.
- Vanta must never route a Horizon profile through the existing Java classpath launcher. The engine choice must be persisted per instance and dispatch to an engine-specific launch adapter.
- Java must remain the default, existing instances must remain Java instances when reading older metadata, and the experimental path must not mutate existing worlds or mod directories.

## Implementation sequence

1. Add a clearly labelled, disabled Horizon entry in the instance creation type picker. This makes the direction visible without allowing users to create profiles that Vanta cannot launch.
2. Add a persisted engine identifier with a backward-compatible default of JAVA, plus tests for old instance metadata and engine selection.
3. Implement a Horizon installation adapter that obtains the upstream Windows x64 release, verifies its published SHA-256 digest, constrains extraction to the dedicated Horizon data directory, and records the installed version.
4. Implement engine-specific profile preparation and launch integration based on upstream-supported interfaces; do not assume the Horizon GUI launcher accepts Minecraft arguments that it does not document.
5. Add explicit compatibility checks, diagnostics, opt-in warnings, and tests. Only enable the UI option when the Windows launch path is supported and validated.

## Upstream references

- Project and platform/version support: https://github.com/horizon-rust/horizon
- Releases and checksums: https://github.com/horizon-rust/horizon/releases
- Documentation: https://playhorizon.dev/docs/

Do not claim a performance improvement until repeatable benchmarks compare the same world, settings, render distance, and hardware against Vanta's Java runtime.
