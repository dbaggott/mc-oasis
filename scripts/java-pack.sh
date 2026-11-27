#!/usr/bin/env bash
# Builds the Java resource pack into <dir>/oasis-pack-<sha1>.zip and prints
# <sha1>. The pack is resourcepack/ plus the assets/ folder of the Dungeons and
# Taverns datapack, which doubles as its resource pack: its chart and key
# models, textures and names. That comes from the zip scripts/fetch-artifacts.sh
# fetched and verified, so run that first, and a D&T bump in artifacts.lock
# changes the pack. The site serves it at
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
dnt="${repo_root}/build/artifacts/datapacks/20-dungeons-and-taverns.zip"
if [[ ! -f "$dnt" ]]; then
  echo "error: ${dnt} is missing; run scripts/fetch-artifacts.sh first" >&2
  exit 1
fi

mkdir -p "$1"
staged="$(mktemp "$1/.oasis-pack.XXXXXX")"
tree="$(mktemp -d)"
trap 'rm -rf "$staged" "$tree"' EXIT
cp -R "${repo_root}/resourcepack/." "$tree"

# D&T's assets/ and its credits go in beside the repo's own files. A path both
# have is an error rather than one silently winning.
python3 -I - "$dnt" "$tree" <<'EOF'
import os
import sys
import zipfile

dnt, tree = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dnt) as pack:
    wanted = {
        name: name
        for name in pack.namelist()
        if name.startswith("assets/") and not name.endswith("/")
    }
    wanted["credits.txt"] = "CREDITS-dungeons-and-taverns.txt"
    for name, rel in sorted(wanted.items()):
        parts = rel.split("/")
        if ".." in parts or rel.startswith("/"):
            sys.exit(f"error: {dnt} holds an unsafe path: {name}")
        dest = os.path.join(tree, *parts)
        if os.path.exists(dest):
            sys.exit(f"error: resourcepack/{rel} is also in {dnt}")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as out:
            out.write(pack.read(name))
EOF

"${repo_root}/scripts/build-pack.sh" "$tree" "$staged"
sha1="$(python3 -I -c 'import hashlib, sys; print(hashlib.sha1(open(sys.argv[1], "rb").read()).hexdigest())' "$staged")"
mv "$staged" "$1/oasis-pack-${sha1}.zip"
echo "$sha1"
