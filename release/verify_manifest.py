#!/usr/bin/env python3
"""Validate a Vanta release manifest and verify its detached Ed25519 signature."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from sign_manifest import load_trusted_keys, validate_manifest, verify_signature


def verify_files(manifest_path: Path, signature_path: Path, trusted_keys_path: Path) -> str:
    manifest = manifest_path.read_bytes()
    signature = signature_path.read_bytes()
    validate_manifest(manifest)
    trusted_keys = load_trusted_keys(trusted_keys_path)
    return verify_signature(manifest, signature, trusted_keys)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--signature", type=Path, required=True)
    parser.add_argument(
        "--trusted-keys",
        type=Path,
        default=Path(__file__).parents[1] / "src/main/resources/release-trusted-keys.json",
    )
    args = parser.parse_args()
    try:
        key_id = verify_files(args.manifest, args.signature, args.trusted_keys)
    except (OSError, ValueError) as error:
        print(f"Release manifest verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Manifest schema and Ed25519 signature verified with trusted key {key_id}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
