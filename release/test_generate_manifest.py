import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("generate_manifest.py")
PLATFORMS = (
    "windows-x64",
    "linux-x64",
    "linux-aarch64",
    "macos-x64",
    "macos-aarch64",
)


class GenerateManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / "assets"
        self.assets.mkdir()
        self.output = self.root / "manifest.json"
        for platform in PLATFORMS:
            archive = self.assets / f"Vanta-1.3.3-{platform}.zip"
            archive.write_bytes((f"fixture:{platform}").encode())
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            (self.assets / f"{archive.name}.sha256").write_text(
                f"{digest}  {archive.name}\n", encoding="utf-8"
            )

    def run_generator(self):
        return subprocess.run(
            [
                sys.executable, str(SCRIPT),
                "--version", "1.3.3",
                "--channel", "stable",
                "--assets-dir", str(self.assets),
                "--output", str(self.output),
            ],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_generates_complete_platform_manifest(self):
        result = self.run_generator()
        self.assertEqual(0, result.returncode, result.stderr)
        manifest = json.loads(self.output.read_text(encoding="utf-8"))
        self.assertEqual(1, manifest["schema_version"])
        self.assertEqual("vanta", manifest["product"])
        self.assertEqual("stable", manifest["channel"])
        self.assertEqual("1.3.3", manifest["version"])
        self.assertEqual(set(PLATFORMS), set(manifest["artifacts"]))
        for platform, artifact in manifest["artifacts"].items():
            self.assertEqual(f"Vanta-1.3.3-{platform}.zip", artifact["file_name"])
            self.assertEqual(64, len(artifact["sha256"]))
            self.assertGreater(artifact["size_bytes"], 0)
            self.assertIn("/releases/download/v1.3.3/", artifact["download_url"])

    def test_rejects_missing_platform_artifact(self):
        (self.assets / "Vanta-1.3.3-macos-aarch64.zip").unlink()
        (self.assets / "Vanta-1.3.3-macos-aarch64.zip.sha256").unlink()
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing required platform artifacts", result.stderr.lower())

    def test_rejects_checksum_mismatch(self):
        checksum = self.assets / "Vanta-1.3.3-windows-x64.zip.sha256"
        checksum.write_text("0" * 64, encoding="utf-8")
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("checksum sidecar does not match", result.stderr.lower())


if __name__ == "__main__":
    unittest.main()
