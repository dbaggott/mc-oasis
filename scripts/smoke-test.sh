#!/usr/bin/env bash
set -euo pipefail

# Start the built image against a throwaway world and check that it comes up,
# enables every plugin in artifacts.lock, answers RCON and stops cleanly. Uses
# no real player data: the whitelist is left empty.
#
# Usage: scripts/smoke-test.sh <image>

image="${1:?usage: scripts/smoke-test.sh <image>}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
name="mc-oasis-smoke-$$"
startup_deadline_seconds=600
stop_timeout_seconds=120

cleanup() { docker rm --force --volumes "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# Smaller heap than production so the test fits any CI runner.
docker run --detach --name "$name" --env MEMORY=2G "$image" >/dev/null

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
# this also proves nothing has to supply one.
docker exec "$name" rcon-cli list >/dev/null
echo "rcon answered"

echo "stopping"
docker stop --time "$stop_timeout_seconds" "$name" >/dev/null
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$name")"
if [[ "$exit_code" != 0 ]]; then
  docker logs "$name" 2>&1 | tail -n 100
  echo "error: the server exited ${exit_code} on stop, not 0" >&2
  exit 1
fi

echo "smoke test passed"
