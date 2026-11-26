#!/usr/bin/env bash
# Read-only check of a scaffolded TypeScript module: generator output present,
# and the post-scaffold fixes applied. Usage:
#   check-scaffold.sh package/<vendor>/[<suite>/]<product>
# Lines: "ok", "FIX" (edit needed before the gate), "note" (information).
# Exit code 1 when any FIX line was printed.
set -u

dir="${1:-}"
if [ -z "$dir" ] || [ ! -f "$dir/package.json" ]; then
  echo "usage: $0 package/<vendor>/[<suite>/]<product>   (directory with a package.json)" >&2
  exit 2
fi
command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 2; }
cd "$dir" || exit 2

fix=0
ok()   { printf 'ok    %s\n' "$1"; }
bad()  { printf 'FIX   %s\n' "$1"; fix=1; }
note() { printf 'note  %s\n' "$1"; }

plugin="$(sed -n 's/.*id("\([^"]*\)").*/\1/p' build.gradle.kts 2>/dev/null | head -n1)"
case "$plugin" in
  zb.typescript-connector) kind=connector ;;
  zb.typescript)           kind=plain ;;
  "") bad "build.gradle.kts missing or without a plugin line"; kind=unknown ;;
  *)  note "plugin $plugin: not a generator scaffold, nothing to check here"; exit 0 ;;
esac
note "module kind: $kind ($plugin)"
if [ "$kind" = "connector" ] && [ "$(jq -r '.interface // false' package.json)" = "true" ]; then
  note 'connector plugin with "interface": true: a shared interface package (as under package/interface/), not a module scaffold; nothing to check here'
  exit 0
fi

# --- files the generator writes -----------------------------------------
for f in package.json tsconfig.json .mocharc.json .gitignore api.yml README.md hub-sdk/package.json src/index.ts; do
  if [ -f "$f" ]; then ok "$f"; else bad "$f missing"; fi
done
impl="$(ls src/*Impl.ts 2>/dev/null | grep -v 'Producer' | head -n1)"
if [ -n "$impl" ]; then ok "$impl"; else bad "no src/<Class>Impl.ts"; fi
if [ -e .npmrc ]; then ok ".npmrc resolves"; else bad ".npmrc missing or a broken symlink (link it to the repo root .npmrc)"; fi
[ -e .nvmrc ] || note ".nvmrc not linked (optional)"

# --- package.json shape --------------------------------------------------
name="$(jq -r '.name // ""' package.json)"
case "$name" in
  @zerobias-org/module-*) ok "name $name" ;;
  *) bad "name '$name': modules in this repo are @zerobias-org/module-*" ;;
esac
[ "$(jq -r '.type // ""' package.json)" = "module" ] && ok '"type": "module"' || bad '"type" is not "module"'
[ "$(jq -r '.main // ""' package.json)" = "dist/src/index.js" ] && ok '"main": dist/src/index.js' || bad '"main" is not dist/src/index.js'
[ "$(jq -r '.scripts // {} | keys | join(",")' package.json)" = "clean" ] && ok 'scripts: only "clean"' || note 'scripts differ from the template (only "clean" expected)'
jq -e '.zerobias.package and .zerobias["import-artifact"] == "module" and .moduleId' package.json >/dev/null 2>&1 \
  && ok "zerobias block + moduleId" || bad "zerobias block or moduleId missing"

reg="$(jq -r '.publishConfig.registry // ""' package.json)"
if [ "$reg" = "https://pkg.zerobias.org/" ]; then ok "publishConfig.registry $reg"; else bad "publishConfig.registry '$reg' -> https://pkg.zerobias.org/"; fi

sdk="$(jq -r '.name // ""' hub-sdk/package.json 2>/dev/null)"
case "$sdk" in
  @zerobias-org/hub-sdk-*) ok "hub-sdk name $sdk" ;;
  *) bad "hub-sdk/package.json name '$sdk' -> @zerobias-org/hub-sdk-${name#@zerobias-org/module-}" ;;
esac

if jq -e '.devDependencies.nock' package.json >/dev/null 2>&1; then
  ok "nock in devDependencies"
elif grep -rqs "from 'nock'" test/unit 2>/dev/null; then
  bad "test/unit imports nock but devDependencies has none"
else
  note "nock not in devDependencies (add it before writing unit tests that mock HTTP)"
fi

# --- connector vs plain --------------------------------------------------
iface="$(jq -r '.interface // false' package.json)"
if [ "$kind" = "connector" ]; then
  [ -f connectionProfile.yml ] && ok "connectionProfile.yml" || bad "connector module without connectionProfile.yml"
  jq -e '.files | index("connectionProfile.yml")' package.json >/dev/null 2>&1 \
    && ok 'files[] ships connectionProfile.yml' || bad 'files[] does not list connectionProfile.yml'
fi
if [ "$kind" = "plain" ]; then
  [ -f connectionProfile.yml ] && bad "plain module has a connectionProfile.yml (delete it, or switch the plugin to zb.typescript-connector)"
  [ "$iface" = "true" ] && ok '"interface": true' || bad 'add "interface": true to package.json (plain modules build no server or image)'
  if [ -n "$impl" ] && grep -Eq 'implements [A-Za-z0-9]+Connector|ConnectionProfile' "$impl"; then
    bad "$impl still implements <Class>Connector / imports ConnectionProfile (replace the stub; see package/nist/cmvp/src/CmvpImpl.ts)"
  elif [ -n "$impl" ]; then
    ok "$impl has no connector lifecycle"
  fi
  [ -d test/e2e ] && bad "test/e2e exists: remove it for a plain module (the gate's container test step fails on it)" || ok "no test/e2e"
fi

# --- staging -------------------------------------------------------------
untracked="$(git ls-files --others --exclude-standard -- . 2>/dev/null | wc -l | tr -d ' ')"
if [ "${untracked:-0}" != "0" ]; then
  bad "$untracked untracked file(s): git add the module before the final gate run (the stamp hashes tracked files only)"
else
  ok "no untracked files"
fi
[ -f gate-stamp.json ] && ok "gate-stamp.json present" || note "no gate-stamp.json yet (written by the gate; commit it)"

exit $fix
