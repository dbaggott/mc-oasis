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

# A plugin that throws while enabling is disabled, and a datapack file that
# won't parse (an advancement, a function, a loot table) is skipped, and either
# way the server carries on to "Done". Both show only in the log, at ERROR, and
# a healthy start logs nothing at that level.
logs="$(docker logs "$name" 2>&1)"
if grep --quiet --fixed-strings ' ERROR]: ' <<<"$logs"; then
  grep --after-context=3 --fixed-strings ' ERROR]: ' <<<"$logs" | head -n 60 || true
  echo "error: the server logged errors while starting" >&2
  exit 1
fi
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
# Retried because one call right after "Done (" was refused in CI, cause
# unknown; the answer reports any wait so a recurrence shows in a passing run.
rcon() { docker exec --user 1000 "$name" rcon-cli "$@"; }
rcon_start=$SECONDS
rcon_tries=1
deadline=$((SECONDS + 60))
until rcon list >/dev/null 2>&1; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$name")" != true ]]; then
    docker logs "$name" 2>&1 | tail -n 100
    echo "error: the container exited before rcon answered" >&2
    exit 1
  fi
  if ((SECONDS >= deadline)); then
    docker logs "$name" 2>&1 | tail -n 100
    rcon list >/dev/null || true
    echo "error: rcon didn't answer within 60s of 'Done ('" >&2
    exit 1
  fi
  sleep 2
  rcon_tries=$((rcon_tries + 1))
done
if ((rcon_tries > 1)); then
  echo "rcon answered on try ${rcon_tries}, after $((SECONDS - rcon_start))s"
else
  echo "rcon answered"
fi

# The image copies ICON in only if it is already a 64x64 PNG, and otherwise
# converts it, so check that what the server serves is the repo's file as is.
if ! docker exec --user 1000 "$name" cmp --quiet /server-icon.png /data/server-icon.png; then
  echo "error: /data/server-icon.png is not the image's /server-icon.png" >&2
  exit 1
fi
echo "server icon in place"

# The Dockerfile's RCON_CMDS_STARTUP. The image runs them through RCON once the
# server listens and only logs a failure, so what they set is checked below.
deadline=$((SECONDS + 120))
until grep --quiet 'stopping rcon cmd service' <<<"$(docker logs "$name" 2>&1)"; do
  if ((SECONDS >= deadline)); then
    echo "error: the startup RCON commands didn't finish within 120s" >&2
    exit 1
  fi
  sleep 2
done

# The permission denials: what LuckPerms actually stored, as an export read
# back, since LuckPerms answers RCON asynchronously and its replies never reach
# rcon-cli.
denied=()
while read -r node; do
  denied+=("$node")
done < <(grep -o 'lp group default permission set [^ ]* false' "${repo_root}/Dockerfile" | awk '{print $6}')
if ((${#denied[@]} > 0)); then
  rcon "lp export smoke-permissions --without-users" >/dev/null
  deadline=$((SECONDS + 60))
  until docker exec --user 1000 "$name" test -s /data/plugins/LuckPerms/smoke-permissions.json.gz; do
    if ((SECONDS >= deadline)); then
      echo "error: LuckPerms export didn't appear within 60s" >&2
      exit 1
    fi
    sleep 2
  done
  exported="$(docker exec --user 1000 "$name" gzip -dc /data/plugins/LuckPerms/smoke-permissions.json.gz)"
  not_denied=()
  for node in "${denied[@]}"; do
    grep --quiet --fixed-strings "\"${node}\",\"value\":false" <<<"$exported" || not_denied+=("$node")
  done
  if ((${#not_denied[@]} > 0)); then
    echo "error: not denied to the default group: ${not_denied[*]}" >&2
    exit 1
  fi
  echo "denied to every player: ${denied[*]}"
fi

# The world borders, by width; `worldborder get` doesn't report the center.
# Every `worldborder set` line has to parse, so a reworded one fails here
# instead of going unchecked.
borders=()
while read -r border; do
  borders+=("$border")
done < <(sed -n 's/.*execute in minecraft:\([a-z_]*\) run worldborder set \([0-9]*\).*/\1 \2/p' "${repo_root}/Dockerfile")
if ((${#borders[@]} != $(grep -c 'worldborder set' "${repo_root}/Dockerfile"))); then
  echo "error: a worldborder set line in the Dockerfile isn't 'execute in minecraft:<dimension> run worldborder set <width>'" >&2
  exit 1
fi
for border in ${borders[@]+"${borders[@]}"}; do
  read -r dimension width <<<"$border"
  reply="$(rcon "execute in minecraft:${dimension} run worldborder get")"
  if ! grep --quiet --fixed-strings "currently ${width} block(s) wide" <<<"$reply"; then
    echo "error: the ${dimension} border isn't ${width} wide: ${reply}" >&2
    exit 1
  fi
  echo "${dimension} border ${width} wide"
done

# The spawn point and the spawn region, by the replies the image logged for
# them: WorldGuard saves regions only later, and the vanilla spawn point has no
# command that reports it. Every `rg flag` line is checked, so a misspelled flag,
# which WorldGuard refuses, fails here.
startup_replies="$(docker logs "$name" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')"
expect_reply() {
  if ! grep --quiet --fixed-strings "[Rcon loop] $1" <<<"$startup_replies"; then
    echo "error: no startup reply '$1'" >&2
    exit 1
  fi
}
read -r spawn_x spawn_y spawn_z < <(sed -n 's/^setworldspawn \(-*[0-9]*\) \(-*[0-9]*\) \(-*[0-9]*\).*/\1 \2 \3/p' "${repo_root}/Dockerfile")
expect_reply "Set the world spawn point to ${spawn_x}, ${spawn_y}, ${spawn_z} "
echo "world spawn at ${spawn_x} ${spawn_y} ${spawn_z}"
expect_reply "Region 'spawn' has been updated with a new area."
flags=()
while read -r flag; do
  flags+=("$flag")
done < <(sed -n 's/^rg flag -w world spawn \([a-z-]*\) \([a-z]*\).*/\1 \2/p' "${repo_root}/Dockerfile")
if ((${#flags[@]} != $(grep -c '^rg flag ' "${repo_root}/Dockerfile"))); then
  echo "error: an rg flag line in the Dockerfile isn't 'rg flag -w world spawn <flag> <value>'" >&2
  exit 1
fi
for flag in ${flags[@]+"${flags[@]}"}; do
  read -r flag_name flag_value <<<"$flag"
  expect_reply "Region flag ${flag_name} set on 'spawn' to '${flag_value^^}'."
done
echo "spawn region defined, with ${#flags[@]} flags"

# A new world logs each datapack it enables, in load order, lowest precedence
# first; a pack that fails to load stops the world loading, so the server never
# gets to "Done". `datapack list` can't stand in for this: its reply is cut
# short after a few packs. A new world enables them in name order, the lock's
# and the repo's own interleaved.
expected=()
while read -r pack; do
  expected+=("$pack")
done < <(
  {
    while read -r kind pack _ || [[ -n "${kind:-}" ]]; do
      if [[ "${kind:-}" == datapack ]]; then echo "$pack"; fi
    done <"${repo_root}/artifacts.lock"
    for dir in "${repo_root}"/datapacks/*/; do basename "$dir"; done
  } | LC_ALL=C sort
)
loaded=()
while read -r pack; do
  loaded+=("$pack")
done < <(sed -n 's/.*Found new data pack file\/\(.*\)\.zip, loading it automatically.*/\1/p' <<<"$logs")
echo "datapacks, in load order: ${loaded[*]}"
if [[ "${loaded[*]}" != "${expected[*]}" ]]; then
  echo "error: expected these datapacks, in this order: ${expected[*]}" >&2
  exit 1
fi
echo "every datapack enabled, in name order"

# The Xaero fair-play pack's load function made its join-tracking objective.
# Asked for by name, since `scoreboard objectives list` is cut short too: an
# empty score is the answer an existing objective gives.
if ! grep --quiet --fixed-strings 'none is set' <<<"$(rcon scoreboard players get '#smoke' xaero_fair_play)"; then
  echo "error: the xaero_fair_play objective doesn't exist; its pack's load function didn't run" >&2
  exit 1
fi
echo "xaero fair-play pack loaded"

echo "stopping"
docker stop --time "$stop_timeout_seconds" "$name" >/dev/null
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$name")"
if [[ "$exit_code" != 0 ]]; then
  docker logs "$name" 2>&1 | tail -n 100
  echo "error: the server exited ${exit_code} on stop, not 0" >&2
  exit 1
fi

echo "smoke test passed"
