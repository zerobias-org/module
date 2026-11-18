#!/usr/bin/env bash
# check-module.sh — static pre-check of one module against the six gate checkpoints.
#
# Usage:  check-module.sh <module-dir> [--final]
#
#   <module-dir>  e.g. package/nist/cmvp (absolute or relative to the cwd)
#   --final       run it this way right before opening a PR: a missing stamp and
#                 untracked files become failures instead of warnings
#
# Reads files and asks git read-only questions. It never runs gradle, zbb, npm or
# the network, so it proves nothing about compilation or tests: only `zbb gate`
# does that. Its job is to catch, in a second, the mistakes that would otherwise
# cost a full gate run or produce a stamp CI rejects.
#
# FAIL = wrong in every module, whatever the toolchain version (exit 1).
# WARN = depends on the build-tools version, or is a pre-publish convention that
#        older gate-green modules do not all follow (does not change the exit code).
#
# Exit codes: 0 no FAIL, 1 at least one FAIL, 2 usage error.

set -u

usage() { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

FINAL=0
MOD_ARG=""
for arg in "$@"; do
  case "$arg" in
    --final) FINAL=1 ;;
    -h|--help) usage ;;
    -*) echo "unknown option: $arg" >&2; usage ;;
    *) [ -z "$MOD_ARG" ] && MOD_ARG="$arg" || usage ;;
  esac
done
[ -n "$MOD_ARG" ] || usage
[ -d "$MOD_ARG" ] || { echo "not a directory: $MOD_ARG" >&2; exit 2; }
cd "$MOD_ARG" || exit 2
[ -f package.json ] && [ -f build.gradle.kts ] || {
  echo "not a module directory (package.json + build.gradle.kts expected): $(pwd)" >&2; exit 2; }

FAILS=0; WARNS=0
pass() { printf '  PASS  %s\n' "$1"; }
info() { printf '  INFO  %s\n' "$1"; }
warn() { printf '  WARN  %s\n' "$1"; WARNS=$((WARNS + 1)); }
fail() { printf '  FAIL  %s\n' "$1"; FAILS=$((FAILS + 1)); }
soft() { if [ "$FINAL" -eq 1 ]; then fail "$1"; else warn "$1"; fi; }
show() { sed 's/^/          /' | head -"${2:-8}"; }   # indent evidence lines
section() { printf '\n[%s] %s\n' "$1" "$2"; }

# First string value of a JSON key, grep-based (enough for flat package.json keys).
json_str() {
  grep -oE "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$2" 2>/dev/null | head -1 \
    | sed -E 's/^"[^"]*"[[:space:]]*:[[:space:]]*"//; s/"$//'
}
# Entries of the package.json "files" array, one per line.
pkg_files() {
  awk '/"files"[[:space:]]*:/ {f=1} f {print} f && /\]/ {exit}' package.json \
    | grep -oE '"[^"]+"' | tr -d '"' | grep -v '^files$'
}

IN_GIT=0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 && IN_GIT=1

PLUGIN=$(grep -oE 'id\("zb\.[a-z-]+"\)' build.gradle.kts | head -1 | sed -E 's/id\("//; s/"\)//')
HAS_PROFILE=0; [ -f connectionProfile.yml ] && HAS_PROFILE=1
IS_INTERFACE=0
grep -Eq '"interface"[[:space:]]*:[[:space:]]*true' package.json && IS_INTERFACE=1
case "$PLUGIN" in
  zb.typescript-connector) KIND=connector ;;
  zb.typescript)           KIND=plain ;;
  *)                       KIND=other ;;
esac

echo "module:  $(pwd)"
echo "plugin:  ${PLUGIN:-none found}   kind: $KIND   connectionProfile.yml: $HAS_PROFILE   interface: $IS_INTERFACE   mode: $([ "$FINAL" -eq 1 ] && echo final || echo development)"

# ── 1. API spec ───────────────────────────────────────────────────────────────
section 1 "API spec (gradle: validate)"
if [ ! -f api.yml ]; then
  fail "api.yml is missing"
else
  OPS=$(grep -E '^[[:space:]]*operationId:' api.yml | sed -E 's/^[[:space:]]*operationId:[[:space:]]*//' | tr -d "\"'" | sed -E 's/[[:space:]]+$//')
  if [ -z "$OPS" ]; then
    fail "api.yml declares no operationId"
  else
    DUPS=$(printf '%s\n' "$OPS" | sort | uniq -d)
    if [ -n "$DUPS" ]; then fail "duplicate operationId in api.yml:"; printf '%s\n' "$DUPS" | show
    else pass "$(printf '%s\n' "$OPS" | wc -l | tr -d ' ') operationIds, all unique"; fi
    ODD=$(printf '%s\n' "$OPS" | grep -vE '^[a-z][a-zA-Z0-9]*$')
    if [ -n "$ODD" ]; then warn "operationId not camelCase (see the api-spec skill):"; printf '%s\n' "$ODD" | show; fi
  fi
fi
if [ "$KIND" = connector ] && [ "$HAS_PROFILE" -eq 0 ]; then
  fail "plugin zb.typescript-connector but no connectionProfile.yml (validateConnector rejects this); a profile-less module uses zb.typescript"
fi
if [ "$KIND" = plain ] && [ "$HAS_PROFILE" -eq 1 ]; then
  info "plugin zb.typescript with a connectionProfile.yml: codegen still emits the connector interface"
fi

# ── 2. Type generation ───────────────────────────────────────────────────────
section 2 "Type generation (gradle: generate)"
if [ -f generated/api/index.ts ]; then
  if [ api.yml -nt generated/api/index.ts ]; then
    warn "api.yml is newer than generated/api/index.ts — regenerate (zbb generate) before trusting compile results"
  else
    pass "generated/ is not older than api.yml"
  fi
else
  info "generated/ not present (it is gitignored; zbb generate or any later task creates it)"
fi
if [ "$IN_GIT" -eq 1 ]; then
  TRACKED_OUT=$(git ls-files -- dist generated build node_modules hub-sdk/generated hub-sdk/dist 2>/dev/null)
  if [ -n "$TRACKED_OUT" ]; then fail "build output is tracked by git (must stay ignored):"; printf '%s\n' "$TRACKED_OUT" | show 5
  else pass "no build output tracked by git"; fi
fi
if [ -f .gitignore ]; then
  for p in 'node_modules/' 'dist/' 'generated/'; do
    grep -qxF "$p" .gitignore || warn ".gitignore has no '$p' line"
  done
else
  warn "no .gitignore in the module directory"
fi

# ── 3. Implementation ────────────────────────────────────────────────────────
section 3 "Implementation (gradle: lint, compile)"
if [ ! -d src ]; then
  fail "src/ is missing"
else
  [ -f src/index.ts ] && pass "src/index.ts present" || fail "src/index.ts is missing (package.json main is dist/src/index.js)"

  NOJS=$(grep -rnE "from ['\"]\.\.?/[^'\"]*['\"]" src test 2>/dev/null | grep -vE "\.js(on)?['\"]")
  if [ -n "$NOJS" ]; then fail "relative imports without a .js extension (NodeNext resolution rejects them):"; printf '%s\n' "$NOJS" | show
  else pass "relative imports in src/ and test/ end in .js"; fi

  ENVUSE=$(grep -rn 'process\.env' src 2>/dev/null)
  if [ -n "$ENVUSE" ]; then fail "process.env read in src/ (configuration reaches the module through the connection profile):"; printf '%s\n' "$ENVUSE" | show
  else pass "no process.env in src/"; fi

  ANY=$(grep -rn 'Promise<any>' src 2>/dev/null)
  [ -n "$ANY" ] && { warn "Promise<any> in src/ (use the generated types; the scaffold stub ships with it):"; printf '%s\n' "$ANY" | show 5; }
  CONS=$(grep -rnE 'console\.(log|error|warn|debug|info)\(' src 2>/dev/null)
  [ -n "$CONS" ] && { warn "console.* in src/:"; printf '%s\n' "$CONS" | show 5; }
  RAW=$(grep -rnE 'throw new Error\(' src 2>/dev/null)
  [ -n "$RAW" ] && { warn "raw 'throw new Error(' in src/ (core error types are preferred; see the implementation skill):"; printf '%s\n' "$RAW" | show 5; }
  TEMP=$(grep -rn 'TEMP-RAW-API' src test 2>/dev/null)
  [ -n "$TEMP" ] && { warn "leftover TEMP-RAW-API debug markers:"; printf '%s\n' "$TEMP" | show 5; }

  if [ "$KIND" != other ]; then
    CONN_IMPL=$(grep -rnE 'implements [A-Za-z0-9]+Connector([^A-Za-z0-9]|$)' src 2>/dev/null)
    if [ "$HAS_PROFILE" -eq 1 ]; then
      [ -n "$CONN_IMPL" ] && pass "an Impl implements the generated <Class>Connector interface" \
        || fail "connectionProfile.yml exists but nothing in src/ implements <Class>Connector"
    else
      if [ -n "$CONN_IMPL" ]; then
        fail "no connectionProfile.yml, yet src/ implements a <Class>Connector (codegen emits plain <Class> without a profile; replace the scaffold stub):"
        printf '%s\n' "$CONN_IMPL" | show 5
      else
        pass "profile-less module: no <Class>Connector implementation in src/"
      fi
      [ "$IS_INTERFACE" -eq 1 ] && pass "profile-less module has \"interface\": true in package.json" \
        || warn "profile-less module without \"interface\": true in package.json — the gate then generates a server that expects a ConnectionProfile"
    fi
  else
    info "plugin $PLUGIN: connector/plain implementation checks skipped"
  fi
fi

[ "$(json_str type package.json)" = module ] && pass 'package.json "type": "module"' \
  || fail 'package.json is missing "type": "module"'
MAIN=$(json_str main package.json)
[ "$MAIN" = dist/src/index.js ] || warn "package.json main is '${MAIN:-unset}', expected dist/src/index.js"
FILEDEPS=$(grep -nE '"[^"]+"[[:space:]]*:[[:space:]]*"file:' package.json)
if [ -n "$FILEDEPS" ]; then fail "file: dependency in package.json (cannot be installed from the registry):"; printf '%s\n' "$FILEDEPS" | show 5; fi

# ── 4. Tests exist ───────────────────────────────────────────────────────────
section 4 "Tests exist (counted into the stamp from test/unit, test/integration, test/e2e)"
TOTAL=0
for suite in unit integration e2e; do
  if [ -d "test/$suite" ]; then
    n=$(grep -rhoE "(^|[^A-Za-z0-9_.])it\(" "test/$suite" 2>/dev/null | wc -l | tr -d ' ')
    info "test/$suite: $n it(...) calls"
    TOTAL=$((TOTAL + n))
  fi
done
if [ "$TOTAL" -eq 0 ]; then fail "no test found under test/unit, test/integration or test/e2e"
else pass "$TOTAL test cases found"; fi
if [ -d test ] && [ ! -f .mocharc.json ]; then fail ".mocharc.json is missing (the e2e tasks pass --config .mocharc.json)"; fi
if [ -d test ]; then
  ONLY=$(grep -rnE '(describe|it|context)\.only\(' test 2>/dev/null)
  if [ -n "$ONLY" ]; then fail ".only( left in tests (the rest of the suite silently does not run):"; printf '%s\n' "$ONLY" | show 5; fi
  SKIPS=$(grep -rnE '(describe|it)\.skip\(' test/unit 2>/dev/null)
  [ -n "$SKIPS" ] && { warn "skipped unit tests:"; printf '%s\n' "$SKIPS" | show 5; }
  if grep -rqE "from ['\"]nock['\"]" test 2>/dev/null; then
    grep -q '"nock"' package.json && pass "nock is imported and declared in package.json" \
      || fail "tests import nock but package.json does not declare it (the scaffold does not add it)"
    STAR=$(grep -rn 'import \* as nock' test 2>/dev/null)
    [ -n "$STAR" ] && { warn "wildcard nock import (the gate-green modules use the default import):"; printf '%s\n' "$STAR" | show 3; }
  fi
  DOTENV=$(grep -rnE "from ['\"]dotenv|require\(['\"]dotenv" src test 2>/dev/null)
  [ -n "$DOTENV" ] && { warn "dotenv import (test values come from the zbb slot; see the testing skill):"; printf '%s\n' "$DOTENV" | show 3; }
fi
if [ "$IS_INTERFACE" -eq 1 ] && [ -d test/e2e ]; then
  warn "\"interface\": true together with test/e2e: testDocker has no container to test against and fails the gate"
fi
if [ "$KIND" = connector ] && [ ! -d test/e2e ]; then
  warn "connector without test/e2e: testDirect and testDocker are skipped, the stamp records e2e as skipped"
fi

# ── 5. Tests pass ────────────────────────────────────────────────────────────
section 5 "Tests pass (gradle: test, testDirect, testDocker, testDataloader)"
if [ -f gate-stamp.json ]; then
  BAD_TESTS=$(awk '/"tests"[[:space:]]*:/ {f=1} f' gate-stamp.json | grep -E '"status"[[:space:]]*:[[:space:]]*"(failed|not-run)"')
  if [ -n "$BAD_TESTS" ]; then fail "gate-stamp.json records a suite that failed or did not run:"; printf '%s\n' "$BAD_TESTS" | show
  else pass "no failed / not-run suite recorded in gate-stamp.json"; fi
  awk '/"tests"[[:space:]]*:/ {f=1} f' gate-stamp.json | grep -E '"(unit|integration|e2e)"' | sed -E 's/^[[:space:]]+//; s/,$//' | show
else
  info "not checkable without a stamp: only the gate run proves this"
fi

# ── 6. Build and stamp ───────────────────────────────────────────────────────
section 6 "Build and stamp (gradle: buildArtifacts, writeGateStamp)"
if [ ! -f gate-stamp.json ]; then
  soft "gate-stamp.json is missing — stage the module, then run the gate"
else
  MISSING=""
  for k in version branch sourceHash testHash tasks tests; do
    grep -q "\"$k\"" gate-stamp.json || MISSING="$MISSING $k"
  done
  [ -z "$MISSING" ] && pass "gate-stamp.json has all fields" || fail "gate-stamp.json lacks:$MISSING"
  SRC_HASH=$(json_str sourceHash gate-stamp.json)
  case "$SRC_HASH" in
    e3b0c442*) fail "sourceHash is the hash of nothing — the gate ran before the module was staged; git add, then run the gate again" ;;
  esac
  BAD_TASKS=$(awk '/"tasks"[[:space:]]*:/ {f=1; next} f && /}/ {exit} f' gate-stamp.json \
    | grep -vE '"(passed|up-to-date|skipped)"' | grep -E '"')
  if [ -n "$BAD_TASKS" ]; then fail "gate-stamp.json records a task that did not pass:"; printf '%s\n' "$BAD_TASKS" | show
  else pass "every task recorded in gate-stamp.json passed"; fi
  info "stamp: version $(json_str version gate-stamp.json), branch $(json_str branch gate-stamp.json) — whether it still matches the tree is answered by './gradlew :<project>:gateCheck' from the repo root"
fi

if [ "$IN_GIT" -eq 1 ]; then
  UNTRACKED=$(git ls-files --others --exclude-standard -- . 2>/dev/null)
  if [ -n "$UNTRACKED" ]; then
    soft "untracked files — the stamp hashes via git ls-files, so these count as absent; stage them before the final gate:"
    printf '%s\n' "$UNTRACKED" | show 10
  else
    pass "no untracked files in the module directory"
  fi
  # shellcheck disable=SC2046
  CHANGED=$(git status --porcelain -- api.yml tsconfig.json src test $(pkg_files) 2>/dev/null | grep -v '^??')
  STAMP_CHANGED=$(git status --porcelain -- gate-stamp.json 2>/dev/null)
  if [ -n "$CHANGED" ] && [ -z "$STAMP_CHANGED" ] && [ -f gate-stamp.json ]; then
    warn "hashed inputs differ from HEAD while gate-stamp.json does not — the stamp is probably stale, run the gate again:"
    printf '%s\n' "$CHANGED" | show 8
  fi
  ENVFILES=$(git ls-files -- '.env' '.env.*' '*/.env' '*/.env.*' 2>/dev/null)
  if [ -n "$ENVFILES" ]; then fail ".env file tracked by git:"; printf '%s\n' "$ENVFILES" | show 3; fi
else
  info "not inside a git work tree: tracking checks skipped"
fi

if [ -f package-lock.json ]; then
  LOCAL=$(grep -nE '"resolved"[[:space:]]*:[[:space:]]*"https?://(localhost|127\.0\.0\.1)' package-lock.json | head -3)
  if [ -n "$LOCAL" ]; then fail "package-lock.json resolves packages from a local registry (verifyNoLocalRegistry stops the gate):"; printf '%s\n' "$LOCAL" | show 3
  else pass "package-lock.json present, no local-registry URLs"; fi
else
  fail "package-lock.json is missing"
fi
[ -f npm-shrinkwrap.json ] && warn "npm-shrinkwrap.json present in the module directory (publish generates and removes its own)"

# ── Pre-publish extras ───────────────────────────────────────────────────────
section + "Pre-publish extras (conventions, not gate tasks)"
NAME=$(json_str name package.json)
case "$NAME" in
  @zerobias-org/module-*) pass "package name $NAME" ;;
  *) warn "package name '$NAME' is not @zerobias-org/module-*" ;;
esac
REG=$(json_str registry package.json)
case "$REG" in
  https://pkg.zerobias.org|https://pkg.zerobias.org/) pass "publishConfig.registry $REG" ;;
  *) warn "publishConfig.registry is '${REG:-unset}' — the root .npmrc resolves every scope from https://pkg.zerobias.org" ;;
esac
REPO_URL=$(awk '/"repository"[[:space:]]*:/ {f=1} f && /"url"/ {print; exit}' package.json)
case "$REPO_URL" in
  *zerobias-org/module*) : ;;
  *) warn "repository.url does not point at zerobias-org/module: $(printf '%s' "$REPO_URL" | sed -E 's/^[[:space:]]+//')" ;;
esac
[ "$(json_str import-artifact package.json)" = module ] || warn 'package.json zerobias.import-artifact is not "module" (the dataloader keys on it)'
if [ -f hub-sdk/package.json ]; then
  SDK=$(json_str name hub-sdk/package.json)
  case "$SDK" in
    @zerobias-org/hub-sdk-*) pass "hub-sdk package name $SDK" ;;
    *) warn "hub-sdk/package.json name '$SDK' is not @zerobias-org/hub-sdk-*" ;;
  esac
elif [ "$KIND" != other ]; then
  warn "hub-sdk/package.json is missing (compileHubSdkExec only runs when it exists)"
fi
if [ "$HAS_PROFILE" -eq 1 ] && ! pkg_files | grep -qxF connectionProfile.yml; then
  warn 'connectionProfile.yml is not listed in package.json "files"'
fi
[ -f README.md ] || warn "README.md is missing (see the readme skill)"
if [ "$IN_GIT" -eq 1 ] && git rev-parse -q --verify origin/dev >/dev/null 2>&1; then
  BASE=$(git merge-base origin/dev HEAD 2>/dev/null)
  PREFIX=$(git rev-parse --show-prefix 2>/dev/null)
  if [ -n "$BASE" ] && git cat-file -e "$BASE:${PREFIX}package.json" 2>/dev/null; then
    BUMP=$(git diff "$BASE" -- package.json 2>/dev/null | grep -E '^[+-][[:space:]]*"version"[[:space:]]*:')
    if [ -n "$BUMP" ]; then
      warn "package.json version changed on this branch — the publish workflow bumps versions, PRs do not (exception: the one-time 2.0.0 gradle-migration bump in the root README):"
      printf '%s\n' "$BUMP" | show 2
    else
      pass "no version bump relative to origin/dev"
    fi
  fi
fi

printf '\nresult: %d FAIL, %d WARN\n' "$FAILS" "$WARNS"
[ "$FAILS" -eq 0 ] || exit 1
exit 0
