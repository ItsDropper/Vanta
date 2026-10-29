# Experimental Rust runtime integration

Status: early Windows prototype. Java remains the default engine.

The experimental target is [Horizon](https://github.com/horizon-rust/horizon), a separate MIT-licensed native Rust implementation targeting Minecraft Java Edition 26.3.

## Implemented on this branch

- The engine is persisted in instance.json. Older metadata without the field defaults to JAVA.
- Engine metadata is preserved when renaming, changing icons, updating the Minecraft version, or duplicating an instance.
- Instance creation has a separate, explicitly experimental Horizon flow that explains platform, version, mod, account, and separate-data limitations before creation.
- Launch dispatch prevents Horizon profiles from going through Java classpath construction or Java account-argument injection.
- The Windows x64 Horizon launcher archive is resolved from GitHub Releases, downloaded into Vanta's own data directory, checked against GitHub's SHA-256 asset digest and declared size, and extracted with path traversal and expanded-size limits.
- Vanta creates or finds a matching Horizon profile using Horizon's documented CLI, updates its display name, and invokes the documented play command for that profile.
- A focused metadata regression test covers legacy JSON, round-trip serialization, icon changes, display labels and invalid engine IDs.
- The current branch's Java compile check and auto-update process-exit regression workflow both passed on commit `c2319fea8a707479fcc319030e57477da2529e85`.

## Known limitations — not a finished native runtime

- Currently Windows x64 only.
- Horizon 26.3 only; Horizon's launcher controls its own installed game build and update channel.
- Horizon profile data is stored by Horizon in its own game directory. Vanta does not copy or synchronize Vanta saves, settings, resource packs or mods into the Horizon profile.
- Java mods, Fabric and Forge are not supported by Horizon. Do not copy Java mod folders into Horizon or imply they will work.
- A Microsoft account that owns Minecraft: Java Edition may need to be signed into Horizon separately.
- The installer currently trusts the SHA-256 digest and size published by GitHub's release API over HTTPS. Signature verification of the checksum manifest is a potential hardening task.
- CI compilation and regression checks do not prove the game launches successfully on a real Windows x64 machine. A real-device smoke test is still required before treating this as ready for general use.
- No performance claim is made. Any benchmark must compare the same world, settings, render distance, and hardware against Vanta's Java runtime.

## Next implementation sequence

1. Add isolated tests around Horizon CLI profile listing, profile reuse, command failures, and installation cleanup.
2. Improve process-state reporting so launcher/CLI failures produce clearer diagnostics and do not get mistaken for a successfully running game.
3. Provide safe access to Horizon's own profile directory without implying that Vanta's instance directory contains Horizon worlds.
4. Keep the option explicitly experimental until direct launch and compatibility behavior are verified on Windows x64.

## Upstream references

- Project and platform/version support: https://github.com/horizon-rust/horizon
- Releases and checksums: https://github.com/horizon-rust/horizon/releases
- Documentation: https://playhorizon.dev/docs/
