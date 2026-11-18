#!/usr/bin/env python3
"""Create and verify Vanta's detached Ed25519 release-manifest signatures."""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import tempfile
from pathlib import Path
from typing import Mapping

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from jsonschema import Draft202012Validator, FormatChecker

DOMAIN_PREFIX = b"VANTA-RELEASE-MANIFEST-V1\n"
KEY_ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9._-]{2,63}$")
SIGNATURE_FIELDS = {"schema_version", "algorithm", "key_id", "signature"}
MAX_MANIFEST_BYTES = 1024 * 1024


def decode_key(value: str) -> bytes:
    try:
        decoded = base64.b64decode(value, validate=True)
    except (ValueError, base64.binascii.Error) as error:
        raise ValueError("Signing key must be canonical Base64.") from error
    if base64.b64encode(decoded).decode("ascii") != value or len(decoded) != 32:
        raise ValueError("Signing key must be canonical Base64 for exactly 32 raw Ed25519 private-key bytes.")
    return decoded


def validate_manifest(payload: bytes) -> None:
    if not payload or len(payload) > MAX_MANIFEST_BYTES:
        raise ValueError("Manifest must be between 1 byte and 1 MiB.")
    try:
        manifest = json.loads(payload, object_pairs_hook=_reject_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Manifest is not valid UTF-8 JSON.") from error
    schema_path = Path(__file__).with_name("manifest.schema.json")
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    errors = list(Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(manifest))
    if errors:
        first = errors[0]
        location = "/".join(str(part) for part in first.absolute_path) or "<root>"
        raise ValueError(f"Manifest does not satisfy its schema at {location}: {first.message}")


def create_signature(payload: bytes, private_key: Ed25519PrivateKey, key_id: str) -> bytes:
    if not KEY_ID_PATTERN.fullmatch(key_id):
        raise ValueError("Invalid release signing key ID.")
    signature = private_key.sign(DOMAIN_PREFIX + payload)
    envelope = {
        "schema_version": 1,
        "algorithm": "Ed25519",
        "key_id": key_id,
        "signature": base64.b64encode(signature).decode("ascii"),
    }
    return (json.dumps(envelope, indent=2, ensure_ascii=True) + "\n").encode("ascii")


def verify_signature(payload: bytes, envelope_bytes: bytes, trusted_keys: Mapping[str, bytes]) -> str:
    if len(envelope_bytes) > 16 * 1024:
        raise ValueError("Signature envelope is too large.")
    try:
        envelope = json.loads(envelope_bytes, object_pairs_hook=_reject_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Signature envelope is not valid JSON.") from error
    if not isinstance(envelope, dict) or set(envelope) != SIGNATURE_FIELDS:
        raise ValueError("Signature envelope has missing or unknown fields.")
    if envelope["schema_version"] != 1 or envelope["algorithm"] != "Ed25519":
        raise ValueError("Unsupported signature envelope format.")
    key_id = envelope["key_id"]
    if not isinstance(key_id, str) or not KEY_ID_PATTERN.fullmatch(key_id):
        raise ValueError("Invalid release signing key ID.")
    public_bytes = trusted_keys.get(key_id)
    if public_bytes is None:
        raise ValueError(f"Untrusted release signing key: {key_id}")
    try:
        encoded_signature = envelope["signature"]
        if not isinstance(encoded_signature, str):
            raise ValueError("Signature must be a Base64 string.")
        signature = base64.b64decode(encoded_signature, validate=True)
        if base64.b64encode(signature).decode("ascii") != encoded_signature or len(signature) != 64:
            raise ValueError("Signature must be canonical Base64 for exactly 64 bytes.")
        Ed25519PublicKey.from_public_bytes(public_bytes).verify(signature, DOMAIN_PREFIX + payload)
    except (ValueError, TypeError, InvalidSignature) as error:
        raise ValueError("Release manifest signature verification failed.") from error
    return key_id


def _reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON field: {key}")
        result[key] = value
    return result


def load_private_key(args: argparse.Namespace) -> Ed25519PrivateKey:
    if args.key_file:
        encoded = args.key_file.read_text(encoding="ascii").strip()
    else:
        encoded = os.environ.get("VANTA_RELEASE_SIGNING_KEY", "").strip()
    if not encoded:
        raise ValueError(
            "No signing key supplied. Set VANTA_RELEASE_SIGNING_KEY or pass --key-file for local signing."
        )
    return Ed25519PrivateKey.from_private_bytes(decode_key(encoded))


def atomic_write(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--key-id", required=True)
    parser.add_argument("--key-file", type=Path)
    args = parser.parse_args()

    try:
        payload = args.manifest.read_bytes()
        validate_manifest(payload)
        private_key = load_private_key(args)
        atomic_write(args.output, create_signature(payload, private_key, args.key_id))
    except (OSError, ValueError) as error:
        print(f"Release manifest signing failed: {error}", file=sys.stderr)
        return 1
    print(f"Signed {args.manifest.name} with Ed25519 key {args.key_id}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
