#!/usr/bin/env bash
# Flags mapper patterns that the mapping skill rules out for new code.
# Usage: check-mappers.sh <module-dir>     e.g. check-mappers.sh package/nist/cmvp
# Read-only. Exit 0 = nothing found, 1 = findings, 2 = usage error.
set -u

dir="${1:-}"
if [ -z "$dir" ] || [ ! -d "$dir/src" ]; then
  echo "usage: $0 <module-dir>   (a directory that contains src/)" >&2
  exit 2
fi

file=""
for candidate in "$dir/src/mappers.ts" "$dir/src/Mappers.ts"; do
  if [ -f "$candidate" ]; then file="$candidate"; break; fi
done
if [ -z "$file" ]; then
  echo "no src/mappers.ts in $dir (a module without mappers has nothing to check)"
  exit 0
fi

findings=0
report() {  # report <title> <grep -E pattern>
  local out
  out=$(grep -nE "$2" "$file" || true)
  if [ -n "$out" ]; then
    findings=1
    echo "== $1"
    echo "$out"
    echo
  fi
}

echo "checking $file"
echo
report "Date used for a date field (use DateFormat / DateTime and build ISO strings)" \
  'map\(Date,|new Date\('
report "newInstance() in a mapper (build a typed const output literal instead)" \
  '\.newInstance\('
report "try/catch in the mapper file (an unknown enum value has to throw; fix api.yml)" \
  '(^|[^a-zA-Z])(try \{|catch \()'

# Generated constructors: new <Model>( for every model imported from generated/model.
models=$(tr '\n' ' ' < "$file" \
  | grep -oE "import \{[^}]*\} from '\.\./generated/model/index\.js'" \
  | sed -E 's/import \{//; s/\}.*//' | tr ',' '\n' | tr -d ' ' | grep -E '^[A-Z]' || true)
for model in $models; do
  report "generated constructor new $model(...) in a mapper" "new $model\("
done

# The marker of temporary raw-response logging must not be left behind.
left=$(grep -rn 'TEMP-RAW-API' "$dir/src" || true)
if [ -n "$left" ]; then
  findings=1
  echo "== temporary raw-response logging left in src/"
  echo "$left"
  echo
fi

if [ "$findings" -eq 0 ]; then
  echo "no findings"
fi
exit "$findings"
