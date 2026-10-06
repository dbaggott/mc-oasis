#!/usr/bin/env bash
set -euo pipefail

# Download every artifact in artifacts.lock into build/artifacts/ and verify
# its sha256. Exits non-zero on the first mismatch or failed download, so
# nothing unverified reaches the Docker build context.
#
#   build/artifacts/server.jar          the one server jar, whatever its name
#   build/artifacts/plugins/<name>.jar
#   build/artifacts/datapacks/<name>.zip
#   build/artifacts/libraries/<name>.jar
#
# Runs anywhere bash, curl and sha256sum (or macOS shasum) exist, so a build
# can be reproduced locally exactly as CI does it.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lock="${repo_root}/artifacts.lock"
out="${repo_root}/build/artifacts"

sha256_of() {
  if command -v sha256sum >/dev/null; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

# Where an artifact of <kind> named <name> lands, relative to $out.
destination() {
  local kind="$1" name="$2"
  case "$kind" in
    server) echo "server.jar" ;;
    plugin) echo "plugins/${name}.jar" ;;
    datapack) echo "datapacks/${name}.zip" ;;
    library) echo "libraries/${name}.jar" ;;
    *) return 1 ;;
  esac
}

# Fetched into a staging directory and moved into place only once every file
# verifies, so build/artifacts/ is always either a complete verified set or
# absent: never a partial one a `docker build` would bake in, and never a
# leftover from an older lock with no line vouching for it.
rm -rf "$out"
mkdir -p "${repo_root}/build"
staging="$(mktemp -d "${repo_root}/build/.artifacts.XXXXXX")"
trap 'rm -rf "$staging"' EXIT
mkdir -p "${staging}/plugins" "${staging}/datapacks" "${staging}/libraries"

servers=0
line_no=0
while read -r kind name version sha256 url extra || [[ -n "${kind:-}" ]]; do
  line_no=$((line_no + 1))
  [[ -z "${kind:-}" || "$kind" == \#* ]] && continue

  if [[ -z "${url:-}" || -n "${extra:-}" ]]; then
    echo "error: artifacts.lock:${line_no}: expected <kind> <name> <version> <sha256> <url>" >&2
    exit 1
  fi
  if ! [[ "$sha256" =~ ^[0-9a-f]{64}$ ]]; then
    echo "error: artifacts.lock:${line_no}: '${sha256}' is not a lowercase sha256" >&2
    exit 1
  fi
  if [[ "$url" != https://* ]]; then
    echo "error: artifacts.lock:${line_no}: ${name} is not fetched over https" >&2
    exit 1
  fi
  if ! rel="$(destination "$kind" "$name")"; then
    echo "error: artifacts.lock:${line_no}: unknown kind '${kind}'" >&2
    exit 1
  fi
  [[ "$kind" == server ]] && servers=$((servers + 1))

  dest="${staging}/${rel}"
  if [[ -e "$dest" ]]; then
    echo "error: artifacts.lock:${line_no}: ${rel} is listed twice" >&2
    exit 1
  fi

  echo "fetching ${kind} ${name} ${version}"
  curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
    --retry 3 --retry-all-errors --output "$dest" "$url"

  actual="$(sha256_of "$dest")"
  if [[ "$actual" != "$sha256" ]]; then
    rm -f "$dest"
    echo "error: ${name} ${version}: sha256 mismatch" >&2
    echo "  expected ${sha256}" >&2
    echo "  got      ${actual}" >&2
    exit 1
  fi
done <"$lock"

if ((servers != 1)); then
  echo "error: artifacts.lock lists ${servers} server jars; the image takes exactly one" >&2
  exit 1
fi

mv "$staging" "$out"
echo "all artifacts verified"
