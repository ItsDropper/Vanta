#!/usr/bin/env python3
"""Generate a deterministic Vanta release manifest from verified CI artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

from jsonschema import Draft202012Validator, FormatChecker

VERSION_PATTERN = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
ARTIFACT_PATTERN = re.compile(
    r"^Vanta-(?P<version>[0-9]+\.[0-9]+\.[0-9]+)-"
    r"(?P<platform>windows-x64|linux-x64|linux-aarch64|macos-x64|macos-aarch64)\.zip$"
)
MAX_ARTIFACT_BYTES = 1024 * 1024 * 1024
SCHEMA_PATH = Path(__file__).with_name("manifest.schema.json")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()



PLATFORM_REQUIRED_FILES = {
    "windows-x64": ("Vanta.exe", "runtime/bin/java.dll", "runtime/lib/modules", "app/Vanta.cfg"),
    "linux-x64": ("bin/Vanta", "lib/runtime/lib/modules", "lib/app/Vanta.cfg"),
    "linux-aarch64": ("bin/Vanta", "lib/runtime/lib/modules", "lib/app/Vanta.cfg"),
    "macos-x64": ("Contents/MacOS/Vanta", "Contents/runtime/lib/modules", "Contents/app/Vanta.cfg"),
    "macos-aarch64": ("Contents/MacOS/Vanta", "Contents/runtime/lib/modules", "Contents/app/Vanta.cfg"),
}
MAX_ARCHIVE_ENTRIES = 100_000
MAX_UNCOMPRESSED_BYTES = 2 * 1024 * 1024 * 1024


def validate_archive(path: Path, platform: str) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            if not entries or len(entries) > MAX_ARCHIVE_ENTRIES:
                raise SystemExit(f"Invalid ZIP entry count in {path.name}: {len(entries)}")
            total_uncompressed = 0
            names: set[str] = set()
            regular_files: set[str] = set()
            file_sizes: dict[str, int] = {}
            for entry in entries:
                name = entry.filename
                path_parts = name.rstrip("/").split("/")
                if (not name or name.startswith("/") or "\\" in name or "\x00" in name
                        or ":" in path_parts[0]
                        or any(part in ("", ".", "..") for part in path_parts)):
                    raise SystemExit(f"Unsafe ZIP entry path in {path.name}: {name!r}")
                if name in names:
                    raise SystemExit(f"Duplicate ZIP entry in {path.name}: {name}")
                names.add(name)
                if entry.flag_bits & 0x1:
                    raise SystemExit(f"Encrypted ZIP entries are not supported: {name}")
                mode = (entry.external_attr >> 16) & 0o170000
                if stat.S_ISLNK(mode):
                    raise SystemExit(f"Symbolic links are not allowed in release archives: {name}")
                total_uncompressed += entry.file_size
                if total_uncompressed > MAX_UNCOMPRESSED_BYTES:
                    raise SystemExit(f"Uncompressed release archive is too large: {path.name}")
                if not entry.is_dir():
                    regular_files.add(name)
                    file_sizes[name] = entry.file_size
            required_files = set(PLATFORM_REQUIRED_FILES[platform])
            missing = sorted(required_files - regular_files)
            if missing:
                raise SystemExit(f"{path.name} is missing required application image files: " + ", ".join(missing))
            empty_files = sorted(name for name in required_files if file_sizes[name] <= 0)
            if empty_files:
                raise SystemExit(f"{path.name} contains empty required application image files: " + ", ".join(empty_files))
            corrupt_entry = archive.testzip()
            if corrupt_entry is not None:
                raise SystemExit(f"Corrupt ZIP entry in {path.name}: {corrupt_entry}")
    except zipfile.BadZipFile as invalid_archive:
        raise SystemExit(f"Invalid ZIP archive: {path.name}") from invalid_archive

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--channel", choices=("stable",), default="stable")
    parser.add_argument("--assets-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--min-launcher-version", default="1.0.0")
    args = parser.parse_args()

    if not VERSION_PATTERN.fullmatch(args.version):
        raise SystemExit(f"Invalid release version: {args.version}")
    if not VERSION_PATTERN.fullmatch(args.min_launcher_version):
        raise SystemExit(f"Invalid minimum launcher version: {args.min_launcher_version}")
    if tuple(map(int, args.min_launcher_version.split("."))) > tuple(map(int, args.version.split("."))):
        raise SystemExit("The minimum launcher version cannot be newer than the release version.")

    artifacts: dict[str, dict[str, object]] = {}
    archives = sorted(args.assets_dir.glob("Vanta-*.zip"))
    if not archives:
        raise SystemExit("No platform update archives were found.")

    for archive in archives:
        match = ARTIFACT_PATTERN.fullmatch(archive.name)
        if not match:
            raise SystemExit(f"Unexpected release archive name: {archive.name}")
        if match.group("version") != args.version:
            raise SystemExit(f"Archive version does not match release: {archive.name}")
        platform = match.group("platform")
        if platform in artifacts:
            raise SystemExit(f"Duplicate platform artifact: {platform}")
        validate_archive(archive, platform)
        size = archive.stat().st_size
        if size <= 0 or size > MAX_ARTIFACT_BYTES:
            raise SystemExit(f"Invalid artifact size for {archive.name}: {size}")

        checksum_file = archive.with_name(archive.name + ".sha256")
        if not checksum_file.is_file():
            raise SystemExit(f"Missing checksum sidecar: {checksum_file.name}")
        checksum_text = checksum_file.read_text(encoding="utf-8").strip().split()
        if (len(checksum_text) != 2
                or not re.fullmatch(r"[a-fA-F0-9]{64}", checksum_text[0])
                or checksum_text[1].lstrip("*") != archive.name):
            raise SystemExit(f"Malformed checksum sidecar: {checksum_file.name}")
        actual_hash = sha256(archive)
        if actual_hash != checksum_text[0].lower():
            raise SystemExit(f"Checksum sidecar does not match {archive.name}")

        file_name = archive.name
        download_url = (
            "https://github.com/ItsDropper/Vanta/releases/download/"
            f"v{args.version}/{quote(file_name)}"
        )
        artifacts[platform] = {
            "file_name": file_name,
            "download_url": download_url,
            "size_bytes": size,
            "sha256": actual_hash,
        }

    expected_platforms = {
        "windows-x64", "linux-x64", "linux-aarch64", "macos-x64", "macos-aarch64"
    }
    missing = sorted(expected_platforms - artifacts.keys())
    if missing:
        raise SystemExit("Release is missing required platform artifacts: " + ", ".join(missing))

    manifest = {
        "schema_version": 1,
        "product": "vanta",
        "channel": args.channel,
        "version": args.version,
        "published_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "min_launcher_version": args.min_launcher_version,
        "release_notes_url": f"https://github.com/ItsDropper/Vanta/releases/tag/v{args.version}",
        "artifacts": dict(sorted(artifacts.items())),
    }
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    errors = sorted(
        Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(manifest),
        key=lambda error: list(error.absolute_path),
    )
    if errors:
        details = "; ".join(
            f"{'/'.join(str(part) for part in error.absolute_path) or '<root>'}: {error.message}"
            for error in errors
        )
        raise SystemExit("Generated release manifest does not satisfy its JSON schema: " + details)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, sort_keys=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
