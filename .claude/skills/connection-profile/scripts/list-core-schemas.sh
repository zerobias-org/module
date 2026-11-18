#!/usr/bin/env bash
# List the connection profile / connection state schemas shipped by the
# @zerobias-org/types-core version installed in a module, with their own and
# inherited property names. Read-only.
#
# Usage: list-core-schemas.sh [module-dir]   (default: current directory)
set -euo pipefail

module_dir="${1:-.}"
schema_dir="$module_dir/node_modules/@zerobias-org/types-core/schema"

if [ ! -d "$schema_dir" ]; then
  echo "No $schema_dir" >&2
  echo "Run the module build once so npm dependencies are installed (see the build-lifecycle skill)." >&2
  exit 1
fi

version=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$schema_dir/../package.json" | head -1)
echo "@zerobias-org/types-core $version — $schema_dir"
echo

for f in "$schema_dir"/*.yml; do
  name=$(basename "$f")
  case "$name" in
    *Profile.yml|*ConnectionState.yml|connectionState.yml|oauthTokenState.yml|basicConnection.yml|oauthConnectionDetails.yml) ;;
    *) continue ;;
  esac
  # Property names are the keys indented under a `properties:` line.
  props=$(awk '
    /^[[:space:]]*properties:[[:space:]]*$/ { match($0, /^[[:space:]]*/); want = RLENGTH + 2; next }
    want && /^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*:/ {
      match($0, /^[[:space:]]*/)
      if (RLENGTH == want) { key = $1; sub(":.*", "", key); printf "%s ", key }
      else if (RLENGTH < want) { want = 0 }
    }' "$f")
  # Schemas pulled in through allOf / $ref.
  refs=$(sed -n "s/.*\$ref: *['\"]\{0,1\}\.\/\([A-Za-z]*\.yml\).*/\1/p" "$f" | tr '\n' ' ')
  printf '%-28s %s' "$name" "$props"
  [ -n "$refs" ] && printf '(+ %s)' "$refs"
  printf '\n'
done
