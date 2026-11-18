import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("generate_manifest.py")
REQUIRED_FILES = {
    "windows-x64": ("Vanta.exe", "runtime/bin/java.dll", "runtime/lib/modules", "app/Vanta.cfg"),
    "linux-x64": ("bin/Vanta", "lib/runtime/lib/modules", "lib/app/Vanta.cfg"),
    "linux-aarch64": ("bin/Vanta", "lib/runtime/lib/modules", "lib/app/Vanta.cfg"),
    "macos-x64": ("Contents/MacOS/Vanta", "Contents/runtime/lib/modules", "Contents/app/Vanta.cfg"),
    "macos-aarch64": ("Contents/MacOS/Vanta", "Contents/runtime/lib/modules", "Contents/app/Vanta.cfg"),
}

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
            with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as package:
                for entry in REQUIRED_FILES[platform]:
                    package.writestr(entry, f"fixture:{platform}:{entry}".encode())
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            (self.assets / f"{archive.name}.sha256").write_text(
                f"{digest}  {archive.name}\n", encoding="utf-8"
            )

    def run_generator(self, minimum_version="1.0.0"):
        return subprocess.run(
            [
                sys.executable, str(SCRIPT),
                "--version", "1.3.3",
                "--channel", "stable",
                "--assets-dir", str(self.assets),
                "--output", str(self.output),
                "--min-launcher-version", minimum_version,
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

    def test_rejects_checksum_sidecar_for_another_archive(self):
        checksum = self.assets / "Vanta-1.3.3-windows-x64.zip.sha256"
        checksum.write_text(
            hashlib.sha256((self.assets / "Vanta-1.3.3-windows-x64.zip").read_bytes()).hexdigest()
            + "  Vanta-1.3.3-linux-x64.zip\n",
            encoding="utf-8",
        )
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("malformed checksum sidecar", result.stderr.lower())

    def test_rejects_unexpected_release_archive(self):
        unexpected = self.assets / "Vanta-1.3.3-freebsd-x64.zip"
        unexpected.write_bytes(b"unexpected")
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unexpected release archive name", result.stderr.lower())

    def test_rejects_minimum_launcher_version_newer_than_release(self):
        result = self.run_generator(minimum_version="2.0.0")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("minimum launcher version", result.stderr.lower())


    def test_rejects_corrupt_zip_archive(self):
        archive = self.assets / "Vanta-1.3.3-windows-x64.zip"
        archive.write_bytes(b"not a zip archive")
        checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
        (self.assets / (archive.name + ".sha256")).write_text(
            f"{checksum}  {archive.name}\n", encoding="utf-8"
        )
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid zip archive", result.stderr.lower())

    def test_rejects_archive_path_traversal(self):
        archive = self.assets / "Vanta-1.3.3-windows-x64.zip"
        with zipfile.ZipFile(archive, "w") as package:
            package.writestr("../escape", b"unsafe")
            for entry in REQUIRED_FILES["windows-x64"]:
                package.writestr(entry, b"fixture")
        checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
        (self.assets / (archive.name + ".sha256")).write_text(
            f"{checksum}  {archive.name}\n", encoding="utf-8"
        )
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unsafe zip entry path", result.stderr.lower())

    def test_rejects_incomplete_application_image(self):
        archive = self.assets / "Vanta-1.3.3-windows-x64.zip"
        with zipfile.ZipFile(archive, "w") as package:
            package.writestr("Vanta.exe", b"launcher")
        checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
        (self.assets / (archive.name + ".sha256")).write_text(
            f"{checksum}  {archive.name}\n", encoding="utf-8"
        )
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing required application image files", result.stderr.lower())

    def test_rejects_unmanifested_release_assets(self):
        (self.assets / "untracked-installer.exe").write_bytes(b"untracked")
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("does not match the validated platform set", result.stderr.lower())
    def test_rejects_checksum_mismatch(self):
        checksum = self.assets / "Vanta-1.3.3-windows-x64.zip.sha256"
        checksum.write_text("0" * 64 + "  Vanta-1.3.3-windows-x64.zip\n", encoding="utf-8")
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("checksum sidecar does not match", result.stderr.lower())


if __name__ == "__main__":
    unittest.main()
