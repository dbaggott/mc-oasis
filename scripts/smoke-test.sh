#!/usr/bin/env bash
set -euo pipefail

# Start the built image against a throwaway world and check that it comes up,
# enables every plugin and datapack the image carries, applies the settings the
# repo owns, answers RCON and stops cleanly. Uses no real player data: the
# whitelist is left empty.
#
# Usage: scripts/smoke-test.sh <image>

image="${1:?usage: scripts/smoke-test.sh <image>}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
name="mc-oasis-smoke-$$"
# The same image with none of the repo's server configs, for the config files
# the server writes on its own: every key it knows, at its defaults.
reference_name="${name}-reference"
startup_deadline_seconds=600
stop_timeout_seconds=120

cleanup() { docker rm --force --volumes "$name" "$reference_name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# Smaller heap than production so the test fits any CI runner. The capability
# and privilege flags are the ones the server runs with (mc-oasis-run in
# dbaggott/infrastructure's apps/mc-oasis/bootstrap).
docker run --detach --name "$name" --env MEMORY=2G \
  --cap-drop ALL --cap-add CHOWN --cap-add SETUID --cap-add SETGID \
  --security-opt no-new-privileges \
  "$image" >/dev/null
# Started alongside, so its start overlaps the main one's. The image copies
# nothing from a COPY_CONFIG_SRC that doesn't exist.
docker run --detach --name "$reference_name" --env MEMORY=2G \
  --env COPY_CONFIG_SRC=/nonexistent \
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
# The plugins in artifacts.lock, and the repo's own by the name in each
# plugin-src/*/plugin.yml.
failed=()
while read -r plugin; do
  if ! grep --quiet --fixed-strings "Enabling ${plugin} v" <<<"$logs" \
    || grep --quiet --fixed-strings "Error occurred while enabling ${plugin} " <<<"$logs"; then
    failed+=("$plugin")
  fi
done < <(
  while read -r kind plugin _ || [[ -n "${kind:-}" ]]; do
    if [[ "${kind:-}" == plugin ]]; then echo "$plugin"; fi
  done <"${repo_root}/artifacts.lock"
  for descriptor in "${repo_root}"/plugin-src/*/plugin.yml; do
    if [[ -f "$descriptor" ]]; then sed -n 's/^name: *//p' "$descriptor"; fi
  done
)
if ((${#failed[@]} > 0)); then
  grep --extended-regexp --after-context=5 'ERROR\]|Error occurred while enabling' <<<"$logs" | head -n 60 || true
  echo "error: did not enable: ${failed[*]}" >&2
  exit 1
fi
echo "every plugin in artifacts.lock and plugin-src enabled"

# OasisVoice made the voice chat group. Simple Voice Chat starts its voice
# server after the plugins enable, and the group is made then.
deadline=$((SECONDS + 60))
until grep --quiet --fixed-strings '[OasisVoice] Created the open voice group ' <<<"$(docker logs "$name" 2>&1)"; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$name")" != true ]] || ((SECONDS >= deadline)); then
    docker logs "$name" 2>&1 | grep --after-context=10 --fixed-strings 'OasisVoice' | tail -n 60 || true
    echo "error: OasisVoice didn't create the voice chat group within 60s of 'Done ('" >&2
    exit 1
  fi
  sleep 2
done
echo "voice chat group created"

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

# The repo's server configs, data/ at its paths under /data, by every setting
# in them read back from the file the server saved after loading it. The
# server keeps a key it doesn't know, so each key also has to be one the
# reference server wrote. Each file has to be nested maps of `key: value` at
# two-space indents, which is all this reads.
deadline=$((SECONDS + startup_deadline_seconds))
until grep --quiet --fixed-strings 'INFO]: Done (' <<<"$(docker logs "$reference_name" 2>&1)"; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$reference_name")" != true ]] || ((SECONDS >= deadline)); then
    docker logs "$reference_name" 2>&1 | tail -n 100
    echo "error: the reference server didn't finish starting" >&2
    exit 1
  fi
  sleep 5
done
# Every setting in a YAML file of nested maps of `key: value` at two-space
# indents, as a yaml-path and its value, a tab between; fails on anything else.
yaml_settings() {
  awk '
    /^[[:space:]]*(#|$)/ { next }
    !/^( {2})*[A-Za-z0-9_-]+:( .*)?$/ { exit 1 }
    {
      depth = (match($0, /[^ ]/) - 1) / 2
      key = $0; sub(/^ */, "", key); sub(/:.*/, "", key)
      value = $0; sub(/^[^:]*: */, "", value)
      keys[depth] = key
      if (value == "") next
      path = "$"
      for (i = 0; i <= depth; i++) path = path "[\x27" keys[i] "\x27]"
      print path "\t" value
    }' "$1"
}
config_value() {
  docker exec --user 1000 "$1" mc-image-helper yaml-path --file "/data/$2" "$3" 2>/dev/null
}
config_settings=0
while read -r file; do
  config="${repo_root}/data/${file}"
  if ! settings="$(yaml_settings "$config")"; then
    echo "error: ${file} isn't nested 'key: value' maps at two-space indents" >&2
    exit 1
  fi
  while IFS=$'\t' read -r path value; do
    if ! config_value "$reference_name" "$file" "$path" >/dev/null; then
      echo "error: ${file} ${path} isn't a setting the server knows" >&2
      exit 1
    fi
    live="$(config_value "$name" "$file" "$path")" || live="(missing)"
    if [[ "$live" != "$value" ]]; then
      echo "error: ${file} ${path} is ${live}, not ${value}" >&2
      exit 1
    fi
    config_settings=$((config_settings + 1))
  done <<<"$settings"
done < <(cd "${repo_root}/data" && find . -type f -name '*.yml' | sed 's|^\./||' | LC_ALL=C sort)
docker rm --force --volumes "$reference_name" >/dev/null
echo "${config_settings} config settings in place"

# The repo's plugin .properties files, by every `key=value` in them found as
# the same line in the file the plugin is running with.
properties_settings=0
while read -r file; do
  live="$(docker exec --user 1000 "$name" cat "/data/plugins/${file}")"
  while read -r line; do
    if ! grep --quiet --line-regexp --fixed-strings --regexp="$line" <<<"$live"; then
      echo "error: plugins/${file} doesn't have ${line}" >&2
      exit 1
    fi
    properties_settings=$((properties_settings + 1))
  done < <(grep --invert-match --extended-regexp '^[[:space:]]*(#|$)' "${repo_root}/plugins/${file}")
done < <(cd "${repo_root}/plugins" && find . -type f -name '*.properties' | sed 's|^\./||' | LC_ALL=C sort)
echo "${properties_settings} plugin properties in place"

# The repo's LibertyBans settings, each read back from the file LibertyBans
# rewrote on loading it. The repo's files leave keys out, so LibertyBans
# rewrites each one in full, keeping the keys it knows and dropping any other.
libertybans_settings=0
while read -r file; do
  if ! settings="$(yaml_settings "${repo_root}/plugins/LibertyBans/${file}")"; then
    echo "error: plugins/LibertyBans/${file} isn't nested 'key: value' maps at two-space indents" >&2
    exit 1
  fi
  while IFS=$'\t' read -r path value; do
    live="$(config_value "$name" "plugins/LibertyBans/${file}" "$path")" || live="(missing)"
    if [[ "$live" != "$value" ]]; then
      echo "error: plugins/LibertyBans/${file} ${path} is ${live}, not ${value}" >&2
      exit 1
    fi
    libertybans_settings=$((libertybans_settings + 1))
  done <<<"$settings"
done < <(cd "${repo_root}/plugins/LibertyBans" && find . -type f -name '*.yml' | sed 's|^\./||' | LC_ALL=C sort)
echo "${libertybans_settings} LibertyBans settings in place"
if ! grep --quiet --fixed-strings 'ExemptionLuckPermsAddon] LuckPerms detected and hooked' <<<"$logs"; then
  echo "error: LibertyBans' LuckPerms exemption add-on didn't hook into LuckPerms" >&2
  exit 1
fi
echo "LibertyBans exempts staff by LuckPerms group weight"

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

# LuckPerms' groups and tracks: every node and track in the repo's files found
# in what LuckPerms exports, so loaded and not just copied, and no group or
# track it has that the repo doesn't. Read as an export because LuckPerms
# answers RCON asynchronously and its replies never reach rcon-cli. The files
# have to be in the form LuckPerms writes them, which is all this reads.
lp_storage="${repo_root}/plugins/LuckPerms/yaml-storage"
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
names_in() { find "$1" -name '*.yml' -exec basename {} .yml \; | LC_ALL=C sort | tr '\n' ' '; }
exported_names() { grep -o "\"$1\":{.*" <<<"$exported" | grep -o "\"[a-z0-9_-]*\":{\"$2\":" | cut -d'"' -f2 | LC_ALL=C sort | tr '\n' ' '; }
if [[ "$(exported_names groups nodes)" != "$(names_in "${lp_storage}/groups")" ]]; then
  echo "error: LuckPerms has groups $(exported_names groups nodes)but the repo has $(names_in "${lp_storage}/groups")" >&2
  exit 1
fi
lp_nodes=0
for file in "${lp_storage}"/groups/*.yml; do
  group="$(basename "$file" .yml)"
  # To the `]` that closes the nodes list: one inside a prefix isn't followed
  # by the `}` or `,` that follows the list's.
  group_nodes="$(grep -oE "\"${group}\":\{\"nodes\":\[([^]]|\][^},])*\]" <<<"$exported")"
  if ! nodes="$(awk '
    /^[[:space:]]*(#|$)/ || /^name: / { next }
    /^[a-z]+:$/ { section = $1; next }
    section == "permissions:" && /^- [^ :]+$/ { print $2 "\ttrue"; next }
    section == "permissions:" && /^- [^ :]+:$/ { node = substr($2, 1, length($2) - 1); next }
    section == "permissions:" && /^    value: (true|false)$/ && node != "" { print node "\t" $2; node = ""; next }
    section == "parents:" && /^- [^ :]+$/ { print "group." $2 "\ttrue"; next }
    section == "prefixes:" && /^- [^ :]+:$/ { prefix = substr($2, 1, length($2) - 1); next }
    section == "prefixes:" && /^- \x27[^\x27]+\x27:$/ { prefix = substr($0, 4, length($0) - 5); next }
    section == "prefixes:" && /^    priority: [0-9]+$/ && prefix != "" { print "prefix." $2 "." prefix "\ttrue"; prefix = ""; next }
    { exit 1 }' "$file")"; then
    echo "error: ${file#"${repo_root}/"} isn't in the form LuckPerms writes" >&2
    exit 1
  fi
  while IFS=$'\t' read -r node value; do
    if ! grep --quiet --fixed-strings "\"key\":\"${node}\",\"value\":${value}}" <<<"$group_nodes"; then
      echo "error: LuckPerms' ${group} group doesn't have ${node} ${value}" >&2
      exit 1
    fi
    lp_nodes=$((lp_nodes + 1))
  done <<<"$nodes"
done
if [[ "$(exported_names tracks groups)" != "$(names_in "${lp_storage}/tracks")" ]]; then
  echo "error: LuckPerms has tracks $(exported_names tracks groups)but the repo has $(names_in "${lp_storage}/tracks")" >&2
  exit 1
fi
for file in "${lp_storage}"/tracks/*.yml; do
  track="$(basename "$file" .yml)"
  groups="$(sed -n 's/^- \([^ ]*\)$/"\1"/p' "$file" | paste -sd, -)"
  if ! grep --quiet --fixed-strings "\"${track}\":{\"groups\":[${groups}]}" <<<"$exported"; then
    echo "error: LuckPerms' ${track} track isn't [${groups}]" >&2
    exit 1
  fi
done
echo "LuckPerms has the repo's groups, ${lp_nodes} nodes, and tracks"

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

# The spawn point, by the reply the image logged for `setworldspawn`: no
# command reports it.
if ! read -r spawn_x spawn_y spawn_z < <(sed -n 's/^setworldspawn \(-*[0-9]*\) \(-*[0-9]*\) \(-*[0-9]*\)\\n\\$/\1 \2 \3/p' "${repo_root}/Dockerfile"); then
  echo "error: no 'setworldspawn <x> <y> <z>' line in the Dockerfile" >&2
  exit 1
fi
startup_replies="$(docker logs "$name" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')"
if ! grep --quiet --fixed-strings "[Rcon loop] Set the world spawn point to ${spawn_x}, ${spawn_y}, ${spawn_z} " <<<"$startup_replies"; then
  echo "error: the world spawn wasn't set to ${spawn_x} ${spawn_y} ${spawn_z}" >&2
  exit 1
fi
echo "world spawn at ${spawn_x} ${spawn_y} ${spawn_z}"

# The game rules, each read back in the dimension it was set in: the overworld
# unless the line runs it `execute in` another. Every `gamerule` line has to
# parse, so a reworded one fails here instead of going unchecked.
rules=()
while read -r rule; do
  rules+=("$rule")
done < <(sed -n 's/^gamerule \([a-z_]*\) \([a-z0-9]*\)\\n\\$/minecraft:overworld \1 \2/p;s/^execute in \(minecraft:[a-z_]*\) run gamerule \([a-z_]*\) \([a-z0-9]*\)\\n\\$/\1 \2 \3/p' "${repo_root}/Dockerfile")
if ((${#rules[@]} != $(grep -c 'gamerule ' "${repo_root}/Dockerfile"))); then
  echo "error: a gamerule line in the Dockerfile isn't '[execute in minecraft:<dimension> run ]gamerule <rule> <value>'" >&2
  exit 1
fi
for rule in ${rules[@]+"${rules[@]}"}; do
  read -r dimension rule_name rule_value <<<"$rule"
  reply="$(rcon "execute in ${dimension} run gamerule ${rule_name}")"
  if ! grep --quiet --fixed-strings "currently set to: ${rule_value}" <<<"$reply"; then
    echo "error: ${rule_name} in ${dimension} isn't ${rule_value}: ${reply}" >&2
    exit 1
  fi
  echo "${rule_name} ${rule_value} in ${dimension}"
done

# The scores, each read back. Every `scoreboard players set` line has to parse,
# so a reworded one fails here instead of going unchecked.
scores=()
while read -r score; do
  scores+=("$score")
done < <(sed -n 's/^scoreboard players set \([^ ]*\) \([^ ]*\) \(-*[0-9]*\)\\n\\$/\1 \2 \3/p' "${repo_root}/Dockerfile")
if ((${#scores[@]} != $(grep -c '^scoreboard players set ' "${repo_root}/Dockerfile"))); then
  echo "error: a scoreboard line in the Dockerfile isn't 'scoreboard players set <holder> <objective> <value>'" >&2
  exit 1
fi
for score in ${scores[@]+"${scores[@]}"}; do
  read -r holder objective score_value <<<"$score"
  reply="$(rcon scoreboard players get "$holder" "$objective")"
  if ! grep --quiet --fixed-strings "${holder} has ${score_value} [" <<<"$reply"; then
    echo "error: ${holder} ${objective} isn't ${score_value}: ${reply}" >&2
    exit 1
  fi
  echo "${holder} ${objective} ${score_value}"
done

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

# The spawn region, from the regions file WorldGuard saved on the way down.
# WorldGuard adds and redefines regions in the background, so whether its reply
# to the startup command reaches the log is a race; the saved file is not. Each
# region's flags are saved on one line, which YAML may wrap, so the file is
# read with its whitespace collapsed.
regions="$(docker cp "${name}:/data/plugins/WorldGuard/worlds/world/regions.yml" - | tar -xO | tr -s ' \n' ' ')"
corners=()
while read -r corner; do
  corners+=("$corner")
done < <(sed -n 's/^\/\/pos[12] \(-*[0-9]*\),\(-*[0-9]*\),\(-*[0-9]*\)\\n\\$/\1 \2 \3/p' "${repo_root}/Dockerfile")
if ((${#corners[@]} != 2)); then
  echo "error: expected a '//pos1 x,y,z' and a '//pos2 x,y,z' line in the Dockerfile" >&2
  exit 1
fi
read -r x1 y1 z1 <<<"${corners[0]}"
read -r x2 y2 z2 <<<"${corners[1]}"
bounds="min: {x: $((x1 < x2 ? x1 : x2)), y: $((y1 < y2 ? y1 : y2)), z: $((z1 < z2 ? z1 : z2))} max: {x: $((x1 > x2 ? x1 : x2)), y: $((y1 > y2 ? y1 : y2)), z: $((z1 > z2 ? z1 : z2))}"
if ! grep --quiet --fixed-strings "spawn: ${bounds}" <<<"$regions"; then
  echo "error: the spawn region isn't ${bounds}: ${regions}" >&2
  exit 1
fi
flags=()
while read -r flag; do
  flags+=("$flag")
done < <(sed -n 's/^rg flag -w world spawn \([a-z-]*\) \([a-z]*\)\\n\\$/\1 \2/p;s/^rg flag -w world spawn \([a-z-]*\) \([a-z]*\)"$/\1 \2/p' "${repo_root}/Dockerfile")
if ((${#flags[@]} != $(grep -c '^rg flag ' "${repo_root}/Dockerfile"))); then
  echo "error: an rg flag line in the Dockerfile isn't 'rg flag -w world spawn <flag> <value>'" >&2
  exit 1
fi
region_flags="$(sed -n 's/.* spawn: .* flags: {\([^}]*\)}.*/\1/p' <<<"$regions")"
for flag in ${flags[@]+"${flags[@]}"}; do
  read -r flag_name flag_value <<<"$flag"
  if ! grep --quiet --extended-regexp "(^|, )${flag_name}: ${flag_value}(,|$)" <<<"$region_flags"; then
    echo "error: the spawn region doesn't have ${flag_name} ${flag_value}: ${region_flags}" >&2
    exit 1
  fi
done
echo "spawn region ${bounds}, with ${#flags[@]} flags"

# A LuckPerms group file and an OasisFilter word list file the repo doesn't
# have, left on the world volume as ones deleted from the repo would be, are
# gone once the server starts again.
stale_files=(
  /data/plugins/LuckPerms/yaml-storage/groups/smoke-stale.yml
  /data/plugins/OasisFilter/lists/slurs/smoke-stale.txt
)
stale_dir="$(mktemp -d)"
echo "# left by the smoke test" >"${stale_dir}/content"
for stale_file in "${stale_files[@]}"; do
  cp "${stale_dir}/content" "${stale_dir}/$(basename "$stale_file")"
  COPYFILE_DISABLE=1 tar -c --no-xattrs -C "$stale_dir" "$(basename "$stale_file")" | docker cp - "${name}:$(dirname "$stale_file")"
done
rm -r "$stale_dir"
restarted_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker start "$name" >/dev/null
deadline=$((SECONDS + 120))
until grep --quiet --fixed-strings 'Copying any plugins from' <<<"$(docker logs --since "$restarted_at" "$name" 2>&1)"; do
  if ((SECONDS >= deadline)); then
    echo "error: the restarted server didn't copy its plugins within 120s" >&2
    exit 1
  fi
  sleep 2
done
for stale_file in "${stale_files[@]}"; do
  if docker exec --user 1000 "$name" test -e "$stale_file"; then
    echo "error: ${stale_file} survived a restart" >&2
    exit 1
  fi
done
docker stop --time "$stop_timeout_seconds" "$name" >/dev/null
echo "LuckPerms groups and OasisFilter word lists the repo doesn't have are removed at start"

echo "smoke test passed"
