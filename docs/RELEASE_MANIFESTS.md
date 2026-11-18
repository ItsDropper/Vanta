# Release manifests and signing

Vanta's stable updater consumes a versioned JSON manifest and a detached
Ed25519 signature from the same GitHub Release:

- `manifest.json` — release metadata and platform artifact hashes.
- `manifest.sig` — signature envelope identifying the trusted signing key.

The stable endpoints are:

`https://github.com/ItsDropper/Vanta/releases/latest/download/manifest.json`

`https://github.com/ItsDropper/Vanta/releases/latest/download/manifest.sig`

## Signature format

The signature envelope is UTF-8 JSON with exactly these fields:

```json
{
  "schema_version": 1,
  "algorithm": "Ed25519",
  "key_id": "vanta-release-2026-01",
  "signature": "BASE64_ED25519_SIGNATURE"
}
```

The signature is Ed25519 over the exact manifest response bytes prefixed by
the domain-separation string `VANTA-RELEASE-MANIFEST-V1\n`. The manifest is
verified before JSON parsing or field interpretation. The signature envelope
is size-limited, duplicate JSON keys are rejected, and its key ID must resolve
to a public key embedded in `src/main/resources/release-trusted-keys.json`.

The trust store supports multiple independently pinned keys. A key ID is only
a selector; it never grants trust by itself. Unknown keys, missing signatures,
invalid signatures, malformed metadata and unsigned legacy releases are rejected.
The old GitHub Releases API fallback is not used for automatic updates or
rollback packages.

## Provision the initial signing key

The initial key can be provisioned entirely through GitHub Actions; no local
Python installation or private-key file is required. The one manual prerequisite
is a temporary fine-grained personal access token because GitHub's built-in
`GITHUB_TOKEN` cannot create repository Actions secrets.

1. Create a fine-grained token restricted to **only** the
   `ItsDropper/Vanta` repository, with **Actions: read and write** repository
   permission. Give it a short expiry. Do not grant Contents, Administration,
   or unrelated permissions.
2. In repository Settings → Secrets and variables → Actions, add that token as
   the temporary repository secret `VANTA_RELEASE_BOOTSTRAP_TOKEN`.
3. Open Actions → **Bootstrap release manifest signing key**, select the
   `main` branch, and run the workflow.
4. The workflow generates a fresh Ed25519 key in runner memory, encrypts the
   Base64 private key using GitHub's repository Actions public key, and installs
   it as `VANTA_RELEASE_SIGNING_KEY`. It then commits only the public key to
   `src/main/resources/release-trusted-keys.json`. The private key is never
   printed, written to a file, committed, or uploaded as an artifact.
5. After the workflow succeeds, delete `VANTA_RELEASE_BOOTSTRAP_TOKEN` from
   repository Actions secrets and revoke the temporary fine-grained token in
   GitHub Settings → Developer settings → Personal access tokens. Keep the
   resulting `VANTA_RELEASE_SIGNING_KEY` secret; the release workflow needs it
   to sign future manifests.

The bootstrap is deliberately one-time: it refuses to run if the trust store
already contains a key or if `VANTA_RELEASE_SIGNING_KEY` already exists. It
does not replace an existing key or implement key rotation. If secret creation
succeeds but the public-key commit fails, remove the newly created
`VANTA_RELEASE_SIGNING_KEY` secret, confirm the trust store is still empty,
and rerun the bootstrap. Do not rerun against a populated trust store.

The generated key ID is `vanta-release-2026-01`. The workflow prints only the
public key and its SHA-256 fingerprint for audit; neither is secret. The
workflow is restricted to manual runs from `main`, serializes executions, and
uses the repository-scoped bootstrap token only in the key-installation step.

The release workflow fails at the signing step if the secret is absent, invalid,
or does not match the pinned public key. Release tooling and the trust store are
checked out from protected `main`, not from the release tag. The secret is
exposed only to the signing step in the publish job.


Protect the `v*` tag namespace with a GitHub repository ruleset: restrict tag
creation to trusted maintainers, prevent tag deletion and force updates, and
require the release source to have passed the normal main-branch review and CI
process. A tag-triggered workflow is defined by the tagged commit, so tag
protection is part of the signing-key security boundary.

## Key rotation and revocation

Rotation is an overlap, not an in-place key replacement:

1. Generate a new key with a new key ID.
2. Add its public key to the trust store while retaining the old public key.
3. Publish and distribute a launcher containing both public keys.
4. Change the workflow's public key ID to the new ID and replace the
   `VANTA_RELEASE_SIGNING_KEY` repository secret with the new private key.
5. Publish a release signed by the new key only after the launcher containing
   that key is available to users.
6. Retain the old public key while old signed release manifests must remain
   verifiable. To revoke a compromised key, remove it from the trust store and
   ship a launcher update; this intentionally invalidates manifests signed by
   that key.

The signing key is separate from the runtime security-policy key. Do not reuse
one key for both trust domains.

## Release pipeline

The release workflow builds all five platform archives, validates their ZIP
contents and SHA-256 sidecars, generates `manifest.json`, signs its exact bytes
into `manifest.sig`, and publishes both files with the archives. GitHub build
provenance attestations cover the packages, manifest and signature envelope.

To test a signature locally after provisioning a key:

```powershell
$env:VANTA_RELEASE_SIGNING_KEY = (Get-Content -Raw release-signing-key.txt).Trim()
python release/sign_manifest.py --manifest release-assets/manifest.json --output release-assets/manifest.sig --key-id vanta-release-2026-01
```

The Java updater verifies the detached signature before parsing the manifest,
then validates the strict manifest schema and platform metadata. Artifact
downloads are independently checked against the signed size and SHA-256 digest.

## Manifest validation

The JSON contract is defined by [manifest.schema.json](../release/manifest.schema.json).
The release workflow generates it with [generate_manifest.py](../release/generate_manifest.py)
only after all platform build jobs have completed. The generator validates the
completed document against the Draft 2020-12 schema using pinned dependencies.

For every required platform, manifest generation:

1. Requires the expected versioned ZIP filename and matching SHA-256 sidecar.
2. Recomputes SHA-256 from the downloaded CI artifact.
3. Opens every ZIP, verifies CRCs, rejects traversal paths, symbolic links and
   encrypted entries, and checks native launcher/runtime/configuration files.
4. Rejects extra or untracked files.
5. Records the artifact filename, GitHub release URL, byte size and digest.
6. Fails publication if any required platform artifact is missing or invalid.

The updater validates schema version, product, channel, semantic version,
timestamp, minimum launcher version, release-notes URL, exact root/artifact
field sets, all five platform entries, URLs, sizes and SHA-256 formats.

## Build provenance

The release workflow creates GitHub artifact attestations for all release
assets after manifest generation and signing. These attestations bind artifact
digests to the GitHub Actions workflow identity and source repository. They
complement, but do not replace, the launcher's pinned Ed25519 publisher
verification.

With GitHub CLI installed:

```sh
gh attestation verify Vanta-1.3.3-windows-x64.zip --repo ItsDropper/Vanta
```

## Compatibility and current deployment state

Unsigned historical releases cannot be authenticated by the new updater and
are not eligible for automatic update or rollback. A launcher build with a
populated trust store is required before signed releases can be consumed.

**Initial key provisioning is a required deployment gate.** The committed
trust store intentionally contains no key until the repository owner generates
a private key locally and pins its corresponding public key. In this state,
the updater fails closed with an explicit missing-trust-anchor error; it never
accepts an unsigned manifest as a fallback.
