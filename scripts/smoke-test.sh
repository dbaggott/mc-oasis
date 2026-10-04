#!/usr/bin/env bash
set -euo pipefail

# Start the built image against a throwaway world and check that it comes up,
# enables every plugin and datapack in artifacts.lock, answers RCON and stops
# cleanly. Uses no real player data: the whitelist is left empty.
#
# Usage: scripts/smoke-test.sh <image>

image="${1:?usage: scripts/smoke-test.sh <image>}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
name="mc-oasis-smoke-$$"
startup_deadline_seconds=600
stop_timeout_seconds=120

cleanup() { docker rm --force --volumes "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# Smaller heap than production so the test fits any CI runner. The capability
# and privilege flags are the ones the server runs with (mc-oasis-run in
# dbaggott/infrastructure's apps/mc-oasis/bootstrap).
docker run --detach --name "$name" --env MEMORY=2G \
  --cap-drop ALL --cap-add CHOWN --cap-add SETUID --cap-add SETGID \
  --security-opt no-new-privileges \
  "$image" >/dev/null

echo "waiting for the server to finish starting"
deadline=$((SECONDS + startup_deadline_seconds))
until grep --quiet --fixed-strings 'Done (' <<<"$(docker logs "$name" 2>&1)"; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$name")" != true ]]; then
    docker logs "$name" 2>&1 | tail -n 100
    echo "error: the container exited before the server finished starting" >&2
    exit 1
  fi
  if ((SECONDS >= deadline)); then
    docker logs "$name" 2>&1 | tail -n 100
    echo "error: no 'Done (' within ${startup_deadline_seconds}s" >&2
    exit 1
  fi
  sleep 5
done

# A plugin that throws while enabling is disabled and the server carries on to
# "Done", so a broken plugin only shows in the log.
logs="$(docker logs "$name" 2>&1)"
failed=()
while read -r kind plugin _ || [[ -n "${kind:-}" ]]; do
  [[ "${kind:-}" == plugin ]] || continue
  if ! grep --quiet --fixed-strings "Enabling ${plugin} v" <<<"$logs" \
    || grep --quiet --fixed-strings "Error occurred while enabling ${plugin} " <<<"$logs"; then
    failed+=("$plugin")
  fi
done <"${repo_root}/artifacts.lock"
if ((${#failed[@]} > 0)); then
  grep --extended-regexp --after-context=5 'ERROR\]|Error occurred while enabling' <<<"$logs" | head -n 60 || true
  echo "error: did not enable: ${failed[*]}" >&2
  exit 1
fi
echo "every plugin in artifacts.lock enabled"

# rcon-cli authenticates with the password the image generated at start, so
# this also proves nothing has to supply one. It has to run as the server's own
# user (uid 1000), as it does on the host, which runs the container with the
# same dropped capabilities.
rcon() { docker exec --user 1000 "$name" rcon-cli "$@"; }
rcon list >/dev/null
echo "rcon answered"

# A new world logs each datapack it enables, in load order, lowest precedence
# first; a pack that fails to load stops the world loading, so the server never
# gets to "Done". `datapack list` can't stand in for this: its reply is cut
# short after a few packs.
expected=()
while read -r kind pack _ || [[ -n "${kind:-}" ]]; do
  [[ "${kind:-}" == datapack ]] && expected+=("$pack")
done <"${repo_root}/artifacts.lock"
loaded=()
while read -r pack; do
  loaded+=("$pack")
done < <(sed -n 's/.*Found new data pack file\/\(.*\)\.zip, loading it automatically.*/\1/p' <<<"$logs")
echo "datapacks, in load order: ${loaded[*]}"
if [[ "${loaded[*]}" != "${expected[*]}" ]]; then
  echo "error: expected these datapacks, in this order: ${expected[*]}" >&2
  exit 1
fi
echo "every datapack in artifacts.lock enabled, in lock order"

echo "stopping"
docker stop --time "$stop_timeout_seconds" "$name" >/dev/null
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$name")"
if [[ "$exit_code" != 0 ]]; then
  docker logs "$name" 2>&1 | tail -n 100
  echo "error: the server exited ${exit_code} on stop, not 0" >&2
  exit 1
fi

echo "smoke test passed"
