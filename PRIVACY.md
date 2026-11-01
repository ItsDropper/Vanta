# Vanta Privacy Policy

**Last updated:** 1 November 2026

This policy describes the intended privacy behavior of Vanta, an independently developed Minecraft launcher. Vanta is under active development, so behavior may change between versions. Review the release notes and source code for the version you use.

## 1. Information Vanta handles

Depending on the features you use, Vanta may process:

- **Minecraft account and authentication data:** account identifiers, profile information returned by authentication services, and session/refresh credentials needed to keep you signed in.
- **Local launcher and game data:** instance settings, installed mods and resource packs, logs, crash reports, screenshots, worlds, and other files you choose to keep in your instances.
- **Diagnostic information:** local logs and error details created to help investigate failures.

Vanta is designed to store account/session data and instance data locally on your device. Treat your device account and its files as private. Anyone with sufficient access to your device or its backups may be able to access local launcher data.

## 2. Optional telemetry and data collection

Telemetry is disabled by default. During onboarding and in Settings → Repair & Diagnostics, users can independently opt in to:

- **Anonymous usage statistics:** allowlisted launcher events (launcher startup; visits to Home, Instances, Mods, and Settings; successful instance creation, mod installation, instance backup, and skin change) plus the Vanta version.
- **Anonymous crash reports:** the Java exception class name plus the Vanta version. Stack traces, exception messages, logs, screenshots, and local paths are not sent.

Vanta does not include Microsoft, Xbox, or Minecraft account identifiers, usernames, access tokens, refresh tokens, session credentials, server addresses, hardware identifiers, or persistent installation identifiers in telemetry payloads. The telemetry client has no access to authentication storage and sends only fixed-schema JSON payloads.

Telemetry is sent to the Vanta project's Cloudflare Worker and stored in its D1 database. Cloudflare necessarily receives ordinary network connection metadata, including the request IP address; the Worker uses the connecting IP as a rate-limit key. Vanta does not include that IP in event payloads or application database rows. Cloudflare's own processing is also subject to its service terms and privacy documentation.

Users can withdraw either permission at any time in Settings. Disabling a category stops future events for that category; there is no persistent client-side event queue. Because Vanta does not attach a user or installation identifier, it cannot locate an individual user's previously received rows for selective deletion. The intended server retention periods are 90 days for usage events and 30 days for crash reports, provided the corresponding Cloudflare scheduled cleanup trigger is deployed and enabled.

The Repair & Diagnostics delivery test sends a synthetic telemetry_test usage event only when usage statistics are enabled. It is a diagnostic event and must be excluded from product usage aggregates.

Do not send logs, crash reports, screenshots, or configuration files to anyone without checking them first. They may contain usernames, local file paths, server addresses, or other identifying information.

## 3. Network connections and third parties

To provide its features, Vanta may connect to third-party services, including:

- **Microsoft and Xbox/Minecraft services** for account authentication, profile/session validation, and Minecraft entitlement or game-related functionality.
- **Modrinth** for supported mod, loader, and project metadata and downloads.
- **Configured project release/update endpoints** to check for and obtain Vanta updates.

Those services receive the information necessary for the relevant request and operate under their own privacy policies and terms. Vanta does not control their data practices. Network requests, service names, and behavior may change as the project evolves.

Vanta does not ask you to give your Microsoft password directly to the launcher. Use only authentic builds and the official sign-in flow.

## 4. Sharing

The project does not intend to sell personal information or share locally stored account data with the developer. Data may be sent to a third-party service when you use a feature that requires that service, as described above. You may also disclose information yourself by publishing logs, screenshots, issue reports, or other files.

## 5. Retention and deletion

Launcher files generally remain on your device until you remove them. Logging out, removing an account, deleting an instance, or uninstalling Vanta may not remove every related file, backup, or diagnostic record. Check the applicable data folders before sharing or disposing of a device, and back up worlds or other files you want to keep before deleting anything.

## 6. Children's privacy

Vanta does not provide a Vanta-operated user account. Optional telemetry is off by default and its application payloads omit direct identifiers, but Cloudflare still processes ordinary network connection metadata as described above. Users should follow the age requirements and terms of Microsoft, Minecraft, and other services they connect. If local law requires parental or guardian involvement for optional data processing, obtain that involvement before enabling telemetry.

## 7. Security

Vanta uses local storage for launcher data, but no application or storage mechanism can be guaranteed completely secure. Keep your operating system updated, protect access to your device, and report suspected vulnerabilities privately using the process in [SECURITY.md](SECURITY.md).

## 8. Changes to this policy

This policy may be revised when Vanta's features, dependencies, or network behavior change. The date at the top indicates the latest policy revision. Material changes should be documented in the repository or release notes.

## 9. Contact

For privacy questions, open a GitHub issue that does not contain private information, or contact the repository owner through their GitHub profile. Do not post passwords, access tokens, or private account data in public issues.
