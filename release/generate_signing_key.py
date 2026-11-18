#!/usr/bin/env python3
"""Generate a local Ed25519 key pair for Vanta release-manifest signing."""

from __future__ import annotations

import argparse
import base64
import hashlib
import os
import re
import sys
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, NoEncryption, PrivateFormat, PublicFormat

DEFAULT_KEY_ID = "vanta-release-2026-01"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("release-signing-key.txt"))
    parser.add_argument("--key-id", default=DEFAULT_KEY_ID)
    args = parser.parse_args()

    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,63}", args.key_id):
        parser.error("key ID must match [a-z0-9][a-z0-9._-]{2,63}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    private_key = Ed25519PrivateKey.generate()
    private_bytes = private_key.private_bytes(
        encoding=Encoding.Raw,
        format=PrivateFormat.Raw,
        encryption_algorithm=NoEncryption(),
    )
    public_bytes = private_key.public_key().public_bytes(
        encoding=Encoding.Raw,
        format=PublicFormat.Raw,
    )
    encoded_private = base64.b64encode(private_bytes) + b"\n"

    descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(encoded_private)
            stream.flush()
            os.fsync(stream.fileno())
    except Exception:
        try:
            args.output.unlink()
        except OSError:
            pass
        raise

    fingerprint = hashlib.sha256(public_bytes).hexdigest()
    print("Vanta release signing key generated.")
    print(f"Key ID: {args.key_id}")
    print(f"Private key file: {args.output}")
    print("Public key (Base64, safe to commit after review):")
    print(base64.b64encode(public_bytes).decode("ascii"))
    print(f"Public key SHA-256 fingerprint: {fingerprint}")
    print("The private key was not printed. Keep its file private and out of Git.")
    if os.name == "nt":
        print("Windows note: verify the file ACL grants access only to your account.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
