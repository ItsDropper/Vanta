#!/usr/bin/env bash
set -euo pipefail
manifest="${1:-security/runtime-policy.json}"
key="${VANTA_RUNTIME_POLICY_SIGNING_KEY_FILE:-}"
[[ -n "$key" && -f "$key" ]] || { echo "Set VANTA_RUNTIME_POLICY_SIGNING_KEY_FILE to an offline Ed25519 PEM." >&2; exit 2; }
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
openssl pkeyutl -sign -rawin -inkey "$key" -in "$manifest" -out "$tmp"
openssl base64 -A -in "$tmp" > "${manifest%.json}.sig"
printf '\n' >> "${manifest%.json}.sig"
