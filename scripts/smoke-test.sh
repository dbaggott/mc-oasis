#!/usr/bin/env bash
set -euo pipefail

# Start the built image against a throwaway world and check that it comes up,
# answers RCON and stops cleanly. Uses no real player data: the whitelist is
# left empty.
#
# Usage: scripts/smoke-test.sh <image>

image="${1:?usage: scripts/smoke-test.sh <image>}"
name="mc-oasis-smoke-$$"
startup_deadline_seconds=600
stop_timeout_seconds=120

cleanup() { docker rm --force --volumes "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# Smaller heap than production so the test fits any CI runner.
docker run --detach --name "$name" --env MEMORY=2G "$image" >/dev/null

echo "waiting for the server to finish starting"
deadline=$((SECONDS + startup_deadline_seconds))
until docker logs "$name" 2>&1 | grep --quiet 'Done ('; do
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

# rcon-cli authenticates with the password the image generated at start, so
# this also proves nothing has to supply one.
echo "plugins as the server reports them:"
docker exec "$name" rcon-cli plugins

echo "stopping"
docker stop --time "$stop_timeout_seconds" "$name" >/dev/null
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$name")"
if [[ "$exit_code" != 0 ]]; then
  docker logs "$name" 2>&1 | tail -n 100
  echo "error: the server exited ${exit_code} on stop, not 0" >&2
  exit 1
fi

echo "smoke test passed"
