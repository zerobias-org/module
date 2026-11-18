#!/usr/bin/env bash
# Quick, offline checks for a module api.yml. It does not replace the `validate`
# task (redocly lint); it catches the mistakes redocly accepts but codegen or
# review does not. Usage: check-spec.sh <path/to/api.yml>
# Exit 1 when a FAIL line was printed, 0 otherwise (WARN lines do not fail).
set -u
spec="${1:-api.yml}"
[ -f "$spec" ] || { echo "usage: $0 <path/to/api.yml>"; exit 2; }
dir="$(cd "$(dirname "$spec")" && pwd)"
rc=0
fail() { echo "FAIL  $*"; rc=1; }
warn() { echo "WARN  $*"; }
ok()   { echo "ok    $*"; }

# 1. OpenAPI version used by every module in this repo
if head -1 "$spec" | grep -q '^openapi: 3\.0\.3'; then ok "openapi 3.0.3"
else fail "line 1 is not 'openapi: 3.0.3'"; fi

# 2. No root-level servers / security
if grep -nE '^(servers|security):' "$spec"; then fail "root-level servers/security (see above)"
else ok "no root-level servers/security"; fi

# 3. operationId unique
dups="$(grep -E '^\s+operationId:' "$spec" | awk '{print $2}' | sort | uniq -d)"
if [ -n "$dups" ]; then fail "duplicate operationId: $(echo $dups)"; else ok "operationIds unique"; fi

# 4. operationId + x-method-name pairs unique per tag is checked by codegen; here: every
#    locally defined operation that has an operationId and a tag should have x-method-name
ops=$(grep -cE '^\s+operationId:' "$spec"); xm=$(grep -cE '^\s+x-method-name:' "$spec")
if [ "$ops" -ne "$xm" ]; then warn "$ops operationId vs $xm x-method-name (fine only for hand-written connection/health operations)"
else ok "every operation has x-method-name ($ops)"; fi

# 5. Core refs that do not exist in @zerobias-org/types-core
if grep -nE 'types-core/schema/(params|headers)\.yml|linksHeader|@auditmation/|@zerobias-com/types-core' "$spec"; then
  fail "reference to a core file/scope that does not exist (see above)"
else ok "no stale core refs"; fi

# 6. Every relative node_modules \$ref resolves on disk (needs dependencies installed)
if [ -d "$dir/node_modules" ]; then
  missing=0
  for ref in $(grep -oE "\./node_modules/[^'\"# ]+" "$spec" | sort -u); do
    [ -e "$dir/$ref" ] || { fail "unresolved file: $ref"; missing=1; }
  done
  [ "$missing" -eq 0 ] && ok "node_modules refs resolve"
else
  warn "no node_modules next to the spec; skipped \$ref resolution"
fi

# 7. Paging: header must be named 'link'; list* operations should declare it
if grep -nE '^\s+links:\s*$' "$spec"; then fail "response header named 'links' (the name is 'link')"; fi
awk '
  /^[[:space:]]+operationId:/ { if (op ~ /^list/ && !hdr) print "WARN  " op " has no link response header: codegen returns Promise<Array<T>> instead of PagedResults"; op=$2; hdr=0 }
  /^[[:space:]]+link:[[:space:]]*$/    { hdr=1 }
  /^components:/     { if (op ~ /^list/ && !hdr) print "WARN  " op " has no link response header: codegen returns Promise<Array<T>> instead of PagedResults"; op="" }
  END                { if (op ~ /^list/ && !hdr) print "WARN  " op " has no link response header: codegen returns Promise<Array<T>> instead of PagedResults" }
' "$spec"

# 8. Naming the framework does not use
if grep -nE 'name: (orderBy|orderDir|limit|offset)\s*$' "$spec"; then
  warn "non-core paging/sorting parameter name (core: pageNumber, pageSize, pageToken, sortBy, sortDir)"; fi
if grep -nE '^\s+nullable:' "$spec"; then warn "nullable is not used by the gate-green specs; leave the property out of required instead"; fi
if grep -nE '^\s+[a-z][a-zA-Z0-9]*_[a-zA-Z0-9_]+:\s*$' "$spec"; then warn "snake_case key (properties are camelCase)"; fi

exit $rc
