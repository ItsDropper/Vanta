# Experimental Rust runtime integration

Status: early Windows prototype. Java remains the default engine.

The experimental target is [Horizon](https://github.com/horizon-rust/horizon), a separate MIT-licensed native Rust implementation targeting Minecraft Java Edition 26.3.

## Implemented on this branch

- The engine is persisted in instance.json. Older metadata without the field defaults to JAVA.
- Engine metadata is preserved when renaming, changing icons, updating the Minecraft version, or duplicating an instance.
- Instance creation has a separate, explicitly experimental Horizon flow.
- Launch dispatch prevents Horizon profiles from going through Java classpath construction or Java account-argument injection.
- The Windows x64 Horizon launcher archive is resolved from GitHub Releases, downloaded into Vanta's own data directory, checked against GitHub's SHA-256 asset digest and declared size, and extracted with path traversal and expanded-size limits.
- Vanta creates or finds a matching Horizon profile using Horizon's documented CLI, updates its display name, and starts it using the documented play command.
- A focused metadata regression test covers legacy JSON, round-trip serialization, icon changes, display labels and invalid engine IDs.

## Known limitations — not a finished native runtime

- Currently Windows x64 only.
- Horizon 26.3 only; Horizon's launcher controls its own installed game build and update channel.
- Horizon profile data is stored by Horizon in its own game directory. Vanta does not copy or synchronize Vanta saves, settings, resource packs or mods into the Horizon profile.
- Java mods, Fabric and Forge are not supported by Horizon. Do not copy Java mod folders into Horizon or imply they will work.
- A Microsoft account that owns Minecraft: Java Edition may need to be signed into Horizon separately.
- The installer currently trusts the SHA-256 digest and size published by GitHub's release API over HTTPS. Signature verification of the checksum manifest is a potential hardening task.

## Next implementation sequence

1. Verify the full compile and regression suite through CI and fix any failures.
2. Add tests for the Horizon CLI adapter, profile reuse, process failures, and installation cleanup.
3. Improve process-state reporting so launcher/CLI failures produce clearer diagnostics and do not get mistaken for a successfully running game.
4. Decide how to make the separate Horizon data directory clear in the UI, and provide safe access to Horizon's profile folder.
5. Keep the option explicitly experimental until direct launch and compatibility behavior are verified on Windows x64.

## Upstream references

- Project and platform/version support: https://github.com/horizon-rust/horizon
- Releases and checksums: https://github.com/horizon-rust/horizon/releases
- Documentation: https://playhorizon.dev/docs/

Do not claim a performance improvement until repeatable benchmarks compare the same world, settings, render distance, and hardware against Vanta's Java runtime.
