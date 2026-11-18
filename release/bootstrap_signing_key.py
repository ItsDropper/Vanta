#!/usr/bin/env python3
"""One-time GitHub Actions bootstrap for Vanta's release-manifest signing key.

The private key exists only in process memory. It is encrypted with GitHub's
repository Actions public key and installed as VANTA_RELEASE_SIGNING_KEY.
Only the corresponding public key is written to the repository trust store.
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from nacl.public import PublicKey, SealedBox

REPOSITORY = "ItsDropper/Vanta"
KEY_ID = "vanta-release-2026-01"
SECRET_NAME = "VANTA_RELEASE_SIGNING_KEY"
API_ROOT = f"https://api.github.com/repos/{REPOSITORY}"
KEYRING_PATH = Path("src/main/resources/release-trusted-keys.json")
KEY_ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9._-]{2,63}$")


def api_request(url: str, token: str, method: str = "GET", payload: dict | None = None) -> tuple[int, bytes]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Vanta-release-key-bootstrap",
            **({"Content-Type": "application/json"} if body is not None else {}),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        # Never include request headers or bodies: they contain secret material.
        detail = error.read(2048).decode("utf-8", "replace")
        raise RuntimeError(f"GitHub API request failed ({error.code}): {detail}") from None
    except urllib.error.URLError as error:
        raise RuntimeError(f"GitHub API connection failed: {error.reason}") from None


def main() -> int:
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        raise RuntimeError("This bootstrap is restricted to ItsDropper/Vanta.")
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise RuntimeError("Run this workflow from the main branch only.")
    if not KEY_ID_PATTERN.fullmatch(KEY_ID):
        raise RuntimeError("Configured release key ID is invalid.")

    bootstrap_token = os.environ.get("VANTA_RELEASE_BOOTSTRAP_TOKEN", "").strip()
    if not bootstrap_token:
        raise RuntimeError(
            "Missing VANTA_RELEASE_BOOTSTRAP_TOKEN. Add a temporary fine-grained token "
            "with Actions: write access to this repository, then rerun."
        )

    root = json.loads(KEYRING_PATH.read_text(encoding="utf-8"))
    if not isinstance(root, dict) or set(root) != {"schema_version", "keys"} or root["schema_version"] != 1:
        raise RuntimeError("The committed release trust store has an unsupported format.")
    if not isinstance(root["keys"], list) or root["keys"]:
        raise RuntimeError("Initial bootstrap requires an empty release trust store; refusing to rotate or overwrite keys.")

    # Refuse to overwrite an existing secret. This is a one-time bootstrap, not
    # a key-rotation mechanism.
    try:
        api_request(f"{API_ROOT}/actions/secrets/{SECRET_NAME}", bootstrap_token)
    except RuntimeError as error:
        if "GitHub API request failed (404)" not in str(error):
            raise
    else:
        raise RuntimeError(
            f"{SECRET_NAME} already exists. Refusing to replace it; use the documented key-rotation procedure."
        )

    private_key = Ed25519PrivateKey.generate()
    private_bytes = private_key.private_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PrivateFormat.Raw,
        encryption_algorithm=serialization.NoEncryption(),
    )
    public_bytes = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    encoded_private = base64.b64encode(private_bytes).decode("ascii")
    encoded_public = base64.b64encode(public_bytes).decode("ascii")

    _, key_response = api_request(f"{API_ROOT}/actions/secrets/public-key", bootstrap_token)
    github_key = json.loads(key_response)
    try:
        github_public_key = PublicKey(base64.b64decode(github_key["key"], validate=True))
        key_id = github_key["key_id"]
    except (KeyError, ValueError, TypeError) as error:
        raise RuntimeError("GitHub returned an invalid Actions secrets public key.") from error

    encrypted_value = base64.b64encode(SealedBox(github_public_key).encrypt(encoded_private.encode("ascii"))).decode("ascii")
    status, _ = api_request(
        f"{API_ROOT}/actions/secrets/{SECRET_NAME}",
        bootstrap_token,
        method="PUT",
        payload={"encrypted_value": encrypted_value, "key_id": key_id},
    )
    if status not in (201, 204):
        raise RuntimeError(f"GitHub did not confirm creation of {SECRET_NAME} (HTTP {status}).")

    # The private key is never written to disk, printed, or uploaded. The
    # workflow runner is ephemeral; only the encrypted repository secret persists.
    root["keys"].append({"key_id": KEY_ID, "public_key": encoded_public})
    temporary = KEYRING_PATH.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(root, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, KEYRING_PATH)

    print(f"Installed repository Actions secret: {SECRET_NAME}")
    print(f"Pinned public key ID: {KEY_ID}")
    print(f"Public key (Base64): {encoded_public}")
    print(f"Public key SHA-256 fingerprint: {hashlib.sha256(public_bytes).hexdigest()}")
    print("Private key was not printed, written to disk, or uploaded as an artifact.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print(f"Release signing-key bootstrap failed: {error}", file=sys.stderr)
        raise SystemExit(1)
