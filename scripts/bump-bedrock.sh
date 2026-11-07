#!/usr/bin/env bash
set -euo pipefail
shopt -s inherit_errexit

# Rewrite every artifacts.lock line that has to move in step with Bedrock to
# its newest build at least 48 hours old, so a release pulled for being broken
# or compromised is never picked up: each line served by GeyserMC's download
# API (Geyser and Floodgate) to that project's newest build, and the
# MCXboxBroadcast extension to its newest GitHub release that isn't a
# prerelease. --skip-wait takes the newest build regardless, for when a Bedrock
# client update has locked players out.
#
# Prints one line per artifact it changed and nothing when the lock is already
# current. Never moves a line to an older build than it already pins, which a
# scheduled run would otherwise do the day after a --skip-wait bump.
#
# GitHub's API is called with $GITHUB_TOKEN when it is set: without one, a
# hosted runner shares the anonymous rate limit with every other job on its IP.
#
# Needs bash 4.4 or later, curl, jq and a `sort` that has -V.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lock="${repo_root}/artifacts.lock"
api="https://download.geysermc.org/v2/projects"
broadcaster="MCXboxBroadcast/Broadcaster"
broadcaster_downloads="https://github.com/${broadcaster}/releases/download"
broadcaster_asset="MCXboxBroadcastExtension.jar"
min_age_seconds=$((48 * 60 * 60))

skip_wait=false
case "${1:-}" in
  "") ;;
  --skip-wait) skip_wait=true ;;
  *) echo "usage: $0 [--skip-wait]" >&2; exit 2 ;;
esac

get() { curl -fsSL --retry 3 "$1"; }
get_github() {
  local headers=(-H 'Accept: application/vnd.github+json')
  if [[ -n "${GITHUB_TOKEN:-}" ]]; then headers+=(-H "Authorization: Bearer ${GITHUB_TOKEN}"); fi
  curl -fsSL --retry 3 "${headers[@]}" "https://api.github.com/$1"
}

# Prints "<version> <build> <sha256> <time>" for <project>'s newest eligible
# build, searching versions newest first, or nothing if none qualifies.
newest_build() {
  local project="$1" versions version builds pick
  versions="$(get "${api}/${project}" | jq -r '.versions[]' | sort -rV)"
  for version in $versions; do
    builds="$(get "${api}/${project}/versions/${version}/builds")"
    pick="$(jq -r --argjson skip_wait "$skip_wait" --argjson min_age "$min_age_seconds" '
      .version as $v
      | [.builds
          | sort_by(.build)[]
          | select(.channel == "default" and .downloads.spigot.sha256 != null)
          | select($skip_wait or (now - (.time | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601)) >= $min_age)]
      | last // empty
      | "\($v) \(.build) \(.downloads.spigot.sha256) \(.time)"' <<<"$builds")"
    if [[ -n "$pick" ]]; then
      echo "$pick"
      return
    fi
  done
}

# Prints "<build> <sha256> <time>" for MCXboxBroadcast's newest eligible
# release, or nothing if none qualifies. Its tags are its build numbers, and
# GitHub gives each asset's sha256 as its digest, "sha256:<hex>".
newest_broadcaster_release() {
  get_github "repos/${broadcaster}/releases?per_page=100" \
    | jq -r --arg asset "$broadcaster_asset" --argjson skip_wait "$skip_wait" --argjson min_age "$min_age_seconds" '
      [.[]
        | select((.draft or .prerelease) | not)
        | select(.tag_name | test("^[0-9]+$"))
        | select($skip_wait or (now - (.published_at | fromdateiso8601)) >= $min_age)
        | {build: (.tag_name | tonumber), time: .published_at,
           digest: ([.assets[] | select(.name == $asset) | .digest // empty][0] // "")}
        | select(.digest | test("^sha256:[0-9a-f]{64}$"))]
      | sort_by(.build) | last // empty
      | "\(.build) \(.digest | ltrimstr("sha256:")) \(.time)"'
}

# Whether version string $1 sorts strictly after $2.
newer_than() {
  [[ "$1" != "$2" && "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n1)" == "$1" ]]
}

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

while IFS= read -r line || [[ -n "$line" ]]; do
  read -r kind name pinned _ url <<<"$line"
  if [[ "$kind" == \#* ]]; then
    printf '%s\n' "$line" >>"$tmp"
    continue
  fi

  case "${url:-}" in
    "${api}/"*)
      project="${url#"${api}/"}"
      project="${project%%/*}"
      found="$(newest_build "$project")"
      if [[ -z "$found" ]]; then
        echo "no eligible ${project} build; keeping ${name} ${pinned}" >&2
        printf '%s\n' "$line" >>"$tmp"
        continue
      fi
      read -r version build new_sha time <<<"$found"
      target="${version}-${build}"
      new_url="${api}/${project}/versions/${version}/builds/${build}/downloads/spigot"
      ;;
    "${broadcaster_downloads}/"*)
      found="$(newest_broadcaster_release)"
      if [[ -z "$found" ]]; then
        echo "no eligible ${broadcaster} release; keeping ${name} ${pinned}" >&2
        printf '%s\n' "$line" >>"$tmp"
        continue
      fi
      read -r target new_sha time <<<"$found"
      new_url="${broadcaster_downloads}/${target}/${broadcaster_asset}"
      ;;
    *)
      printf '%s\n' "$line" >>"$tmp"
      continue
      ;;
  esac
  if ! newer_than "$target" "$pinned"; then
    printf '%s\n' "$line" >>"$tmp"
    continue
  fi

  # Keep the sha256 and url columns where they were: only the version's own
  # field, with its padding, is rebuilt.
  [[ "$line" =~ ^([^[:space:]]+[[:space:]]+[^[:space:]]+[[:space:]]+)[^[:space:]]+([[:space:]]+) ]]
  head="${BASH_REMATCH[1]}"
  width=$(( ${#pinned} + ${#BASH_REMATCH[2]} ))
  (( width >= ${#target} + 2 )) || width=$(( ${#target} + 2 ))
  printf '%s%-*s%s  %s\n' "$head" "$width" "$target" "$new_sha" "$new_url" >>"$tmp"
  echo "${name} ${pinned} -> ${target} (built ${time})"
done <"$lock"

cat "$tmp" >"$lock"
