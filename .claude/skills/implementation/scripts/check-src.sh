#!/usr/bin/env bash
# Static checks on a module's TypeScript source (src/*.ts and below).
# Usage: check-src.sh <module dir>      e.g. check-src.sh package/nist/cmvp
# Read-only. Exits 1 when any check reports a finding, 2 on bad usage.

set -u

dir="${1:-}"
if [ -z "$dir" ] || [ ! -d "$dir/src" ]; then
  echo "usage: $(basename "$0") <module dir containing src/>" >&2
  exit 2
fi

src="$dir/src"
findings=0

check() {
  local title="$1" pattern="$2" exclude="${3:-}"
  local out
  out=$(grep -rnE --include='*.ts' "$pattern" "$src" 2>/dev/null)
  if [ -n "$exclude" ] && [ -n "$out" ]; then
    out=$(printf '%s\n' "$out" | grep -vE "$exclude")
  fi
  if [ -n "$out" ]; then
    echo "FINDING: $title"
    printf '%s\n' "$out" | sed 's/^/  /'
    findings=$((findings + 1))
  else
    echo "ok: $title"
  fi
}

check "no process.env in src/ (a module's only inputs are the profile and the operation arguments)" \
  'process\.env'

check "relative imports end in .js (NodeNext)" \
  "from '\.{1,2}/[^']*'" "\.js'|\.json'"

check "no console.* in src/ (use @zerobias-org/logger, never log an axios error object)" \
  'console\.(log|error|warn|info|debug)\('

check "no bare Error (throw a core error from @zerobias-org/types-core-js)" \
  'throw new Error\('

check "no Promise<any> return types" \
  'Promise<any>'

if [ ! -f "$src/index.ts" ]; then
  echo "FINDING: src/index.ts is missing"
  findings=$((findings + 1))
else
  if grep -qE 'export function new[A-Z]' "$src/index.ts"; then
    echo "ok: src/index.ts exports a new<Class>() factory"
  else
    echo "FINDING: src/index.ts has no 'export function new<Class>()' factory"
    findings=$((findings + 1))
  fi
  n=$(grep -cE "export \* from '\.\./generated/(api|model)/index\.js'" "$src/index.ts")
  if [ "$n" -eq 2 ]; then
    echo "ok: src/index.ts re-exports generated api and model"
  else
    echo "FINDING: src/index.ts should re-export ../generated/api/index.js and ../generated/model/index.js"
    findings=$((findings + 1))
  fi
fi

if [ "$findings" -gt 0 ]; then
  echo "$findings check(s) with findings in $src"
  exit 1
fi
echo "all checks passed for $src"
