#!/usr/bin/env bash
# Builds the Java resource pack, resourcepack/, into <dir>/oasis-pack-<sha1>.zip
# and prints <sha1>. The site serves it at
# https://www.oasis-smp.com/assets/oasis-pack-<sha1>.zip, and the server image
# names that URL and SHA-1 (RESOURCE_PACK and RESOURCE_PACK_SHA1 in the
# Dockerfile). Run from anywhere; <dir> is created if missing.
#
#   scripts/java-pack.sh <dir>
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <dir>" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$1"
staged="$(mktemp "$1/.oasis-pack.XXXXXX")"
trap 'rm -f "$staged"' EXIT
"${repo_root}/scripts/build-pack.sh" "${repo_root}/resourcepack" "$staged"
sha1="$(python3 -I -c 'import hashlib, sys; print(hashlib.sha1(open(sys.argv[1], "rb").read()).hexdigest())' "$staged")"
mv "$staged" "$1/oasis-pack-${sha1}.zip"
echo "$sha1"
