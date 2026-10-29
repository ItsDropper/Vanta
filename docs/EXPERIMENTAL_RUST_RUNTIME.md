# Experimental Rust runtime integration

Status: early Windows prototype. Java remains the default engine.

The experimental target is [Horizon](https://github.com/horizon-rust/horizon), a separate MIT-licensed native Rust implementation targeting Minecraft Java Edition 26.3.

## Implemented on this branch

- The engine is persisted in instance.json. Older metadata without the field defaults to JAVA.
- Engine metadata is preserved when renaming, changing icons, updating the Minecraft version, or duplicating an instance.
- Instance creation has a separate, explicitly experimental Horizon flow.
- Launch dispatch prevents Horizon profiles from going through Java classpath construction or Java account-argument injection.
- The Windows x64 Horizon launcher archive is resolved from GitHub Releases, downloaded into Vanta's own data directory, checked against GitHub's SHA-256 asset digest and declared size, and extracted with path traversal and expanded-size limits.
- A focused metadata regression test covers legacy JSON, round-trip serialization, icon changes, display labels and invalid engine IDs.

## Known limitations — not a finished native runtime

- Currently Windows x64 only.
- Horizon 26.3 only.
- The current bridge opens the upstream Horizon launcher. It does not yet launch the exact Vanta profile directly or synchronize the selected Vanta instance with a Horizon profile. Choose and launch the game inside Horizon.
- Vanta's process tracking currently sees the Horizon launcher process, not necessarily the game process. Playtime/status should therefore be treated as approximate for this prototype.
- Java mods, Fabric and Forge are not supported by Horizon. Do not copy Java mod folders into Horizon or imply they will work.
- The installer currently trusts the SHA-256 digest and size published by GitHub's release API over HTTPS. Signature verification of the checksum manifest is a potential hardening task.

## Next implementation sequence

1. Verify the metadata and installation code through CI and fix any regressions.
2. Add robust Horizon process ownership and state reporting, distinct from Java Minecraft process tracking.
3. Integrate documented Horizon instance/CLI interfaces, so Vanta can select or create the corresponding Horizon profile rather than merely opening the launcher.
4. Add migration/duplicate behavior tests and installation failure cleanup tests.
5. Keep the option explicitly experimental until direct profile launch and compatibility checks are verified.

## Upstream references

- Project and platform/version support: https://github.com/horizon-rust/horizon
- Releases and checksums: https://github.com/horizon-rust/horizon/releases
- Documentation: https://playhorizon.dev/docs/

Do not claim a performance improvement until repeatable benchmarks compare the same world, settings, render distance, and hardware against Vanta's Java runtime.
