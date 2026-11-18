#!/usr/bin/env python3
"""Unit tests for Vanta's Ed25519 release-manifest signing tools."""

from __future__ import annotations

import base64
import sys
import unittest
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

sys.path.insert(0, str(Path(__file__).parent))
import sign_manifest


class SignManifestTests(unittest.TestCase):
    def setUp(self) -> None:
        self.private_key = Ed25519PrivateKey.generate()
        self.public_key = self.private_key.public_key().public_bytes(
            encoding=Encoding.Raw, format=PublicFormat.Raw
        )
        self.payload = b'{"schema_version":1,"version":"1.2.3"}'

    def test_signature_round_trip(self) -> None:
        envelope = sign_manifest.create_signature(self.payload, self.private_key, "release-test-01")
        self.assertEqual(
            "release-test-01",
            sign_manifest.verify_signature(self.payload, envelope, {"release-test-01": self.public_key}),
        )

    def test_modified_payload_is_rejected(self) -> None:
        envelope = sign_manifest.create_signature(self.payload, self.private_key, "release-test-01")
        with self.assertRaises(ValueError):
            sign_manifest.verify_signature(self.payload + b" ", envelope, {"release-test-01": self.public_key})

    def test_unknown_key_and_invalid_key_id_are_rejected(self) -> None:
        envelope = sign_manifest.create_signature(self.payload, self.private_key, "release-test-01")
        with self.assertRaisesRegex(ValueError, "Untrusted"):
            sign_manifest.verify_signature(self.payload, envelope, {})
        with self.assertRaises(ValueError):
            sign_manifest.create_signature(self.payload, self.private_key, "../invalid")

    def test_duplicate_and_unknown_envelope_fields_are_rejected(self) -> None:
        envelope = sign_manifest.create_signature(self.payload, self.private_key, "release-test-01")
        duplicate = envelope.replace(
            b'"algorithm": "Ed25519",',
            b'"algorithm": "Ed25519",\n  "algorithm": "Ed25519",',
        )
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            sign_manifest.verify_signature(self.payload, duplicate, {"release-test-01": self.public_key})
        extra = envelope[:-2] + b', "extra": true}\n'
        with self.assertRaisesRegex(ValueError, "missing or unknown"):
            sign_manifest.verify_signature(self.payload, extra, {"release-test-01": self.public_key})

    def test_signing_key_must_match_the_pinned_public_key(self) -> None:
        import json
        import tempfile

        public_key = base64.b64encode(self.public_key).decode("ascii")
        with tempfile.TemporaryDirectory() as directory:
            keyring = Path(directory) / "trusted-keys.json"
            keyring.write_text(json.dumps({
                "schema_version": 1,
                "keys": [{"key_id": "release-test-01", "public_key": public_key}],
            }), encoding="utf-8")
            sign_manifest.require_pinned_signing_key(self.private_key, "release-test-01", keyring)
            with self.assertRaisesRegex(ValueError, "does not match"):
                sign_manifest.require_pinned_signing_key(
                    Ed25519PrivateKey.generate(), "release-test-01", keyring
                )

    def test_keyring_supports_overlapping_rotation_keys(self) -> None:
        import json
        import tempfile

        second = Ed25519PrivateKey.generate().public_key().public_bytes(
            encoding=Encoding.Raw, format=PublicFormat.Raw
        )
        entries = [
            {"key_id": "release-old-01", "public_key": base64.b64encode(self.public_key).decode("ascii")},
            {"key_id": "release-new-01", "public_key": base64.b64encode(second).decode("ascii")},
        ]
        with tempfile.TemporaryDirectory() as directory:
            keyring = Path(directory) / "trusted-keys.json"
            keyring.write_text(json.dumps({"schema_version": 1, "keys": entries}), encoding="utf-8")
            trusted = sign_manifest.load_trusted_keys(keyring)
            self.assertEqual({"release-old-01", "release-new-01"}, set(trusted))

    def test_private_key_decoder_requires_canonical_32_byte_key(self) -> None:
        encoded = base64.b64encode(self.private_key.private_bytes_raw()).decode("ascii")
        self.assertEqual(32, len(sign_manifest.decode_key(encoded)))
        with self.assertRaises(ValueError):
            sign_manifest.decode_key("AQID")


if __name__ == "__main__":
    unittest.main(verbosity=2)
