#!/bin/sh
# Zips a resource pack directory so the same files always give the same bytes:
# entries in sorted order, every timestamp 1980-01-01, every file mode 0644,
# and no compression, which would leave the bytes to the zlib build. Dotfiles,
# such as a .DS_Store Finder leaves, are left out.
#
#   scripts/build-pack.sh <pack dir> <zip>
#
# The Java pack's URL is named by the zip's SHA-1, so this is what keeps a pack
# that hasn't changed at the URL it was already deployed at. CI builds the Java
# pack (resourcepack/) with it in both the server and the site job, and the
# Dockerfile builds the Bedrock pack (resourcepack-bedrock/) with it, in an
# image with no bash, hence plain sh.
set -eu

if [ $# -ne 2 ] || [ ! -d "$1" ]; then
  echo "usage: $0 <pack dir> <zip>" >&2
  exit 2
fi

python3 -I - "$1" "$2" <<'EOF'
import os
import sys
import zipfile

src, out = sys.argv[1], sys.argv[2]
files = sorted(
    os.path.relpath(os.path.join(root, name), src).replace(os.sep, "/")
    for root, _, names in os.walk(src)
    for name in names
    if not name.startswith(".")
)
with zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as pack:
    for name in files:
        entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        entry.external_attr = 0o100644 << 16
        entry.create_system = 3
        with open(os.path.join(src, name), "rb") as f:
            pack.writestr(entry, f.read())
EOF
