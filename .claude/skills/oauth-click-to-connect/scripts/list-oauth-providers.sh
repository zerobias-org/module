#!/usr/bin/env bash
# List the OAuth providers that exist in the zerobias-org/oauth content repo,
# with the provider code a module puts in `x-oauth-providers` and the
# authorize URL. Read-only; needs an authenticated `gh`.
#
# Usage: list-oauth-providers.sh [filter]    e.g. list-oauth-providers.sh microsoft
set -euo pipefail

repo="zerobias-org/oauth"
filter="${1:-}"

command -v gh >/dev/null || { echo "gh CLI not found" >&2; exit 1; }

paths=$(gh api "repos/$repo/git/trees/main:package?recursive=1" \
  --jq '.tree[] | select(.path | endswith("index.yml")) | .path')

if [ -z "$paths" ]; then
  echo "No providers found in $repo (check gh authentication and access to the repo)." >&2
  exit 1
fi

found=0
while IFS= read -r path; do
  dir=$(dirname "$path")
  if [ -n "$filter" ] && [[ "$dir" != *"$filter"* ]]; then continue; fi
  code=$(gh api "repos/$repo/contents/package/$dir/package.json" --jq '.content' \
    | base64 --decode | sed -n 's/.*"package": *"\([^"]*\)".*/\1/p' | head -1)
  url=$(gh api "repos/$repo/contents/package/$path" --jq '.content' \
    | base64 --decode | sed -n 's/^url: *//p' | head -1)
  printf '%-28s %-24s %s\n' "${code:-?}" "package/$dir" "$url"
  found=1
done <<< "$paths"

if [ "$found" -eq 0 ]; then
  echo "No provider matches '$filter'. Add one: see references/provider-package.md" >&2
  exit 2
fi
