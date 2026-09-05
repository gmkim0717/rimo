#!/usr/bin/env bash
# Generate the update manifest (update.json) for a signed release APK.
#
# Usage:
#   scripts/make-update-json.sh <apk> <versionCode> <versionName> <apkUrl> [changelog]
#
# Example:
#   scripts/make-update-json.sh app-release.apk 2 0.2.0 \
#     https://github.com/gmkim0717/rimo/releases/download/v0.2.0/rimo-0.2.0.apk "fix reconnect"
#
# Prints the manifest to stdout. Redirect it to dist/update.json:
#   scripts/make-update-json.sh ... > dist/update.json
set -euo pipefail

if [ "$#" -lt 4 ]; then
  echo "usage: $0 <apk> <versionCode> <versionName> <apkUrl> [changelog]" >&2
  exit 2
fi

apk="$1"
version_code="$2"
version_name="$3"
apk_url="$4"
changelog="${5:-}"

[ -f "$apk" ] || { echo "apk not found: $apk" >&2; exit 1; }
case "$version_code" in *[!0-9]*|"") echo "versionCode must be a positive integer" >&2; exit 1;; esac
case "$apk_url" in https://*) ;; *) echo "apkUrl must be https://" >&2; exit 1;; esac

# sha256sum on Linux/Git-Bash; shasum -a 256 on macOS.
if command -v sha256sum >/dev/null 2>&1; then
  sha256="$(sha256sum "$apk" | awk '{print $1}')"
else
  sha256="$(shasum -a 256 "$apk" | awk '{print $1}')"
fi

size="$(wc -c < "$apk" | tr -d ' ')"

# Escape backslashes and double quotes in the changelog for JSON.
escaped_changelog="$(printf '%s' "$changelog" | sed 's/\\/\\\\/g; s/"/\\"/g')"

cat <<JSON
{
  "versionCode": $version_code,
  "versionName": "$version_name",
  "apkUrl": "$apk_url",
  "sha256": "$sha256",
  "apkSizeBytes": $size,
  "changelog": "$escaped_changelog"
}
JSON
