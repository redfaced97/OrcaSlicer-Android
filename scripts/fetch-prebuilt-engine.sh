#!/usr/bin/env bash
# The unmodified OrcaMicro ARM64 engine, built from the retained native sources.
# Its hash is the digest published by GitHub's artifact API, not a hash from the mirror.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
sha256sum --check scripts/prebuilt-native.sha256
archive="$(mktemp)"
trap 'rm -f "$archive"' EXIT
curl --fail --location --retry 2 \
  https://nightly.link/whoxllm/OrcaMicro/actions/artifacts/12009974088.zip -o "$archive"
printf '%s  %s\n' 58988793c158e9e117557958ead7e9571c9cad70ca2be1ceec8afc066cd5e7d3 "$archive" | sha256sum --check -
mkdir -p "$root/app/src/main/jniLibs"
unzip -o "$archive" -d "$root/app/src/main/jniLibs"
