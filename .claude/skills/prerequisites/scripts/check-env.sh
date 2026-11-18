#!/usr/bin/env bash
# Read-only environment check for module work in this repo.
# Prints one line per item: "ok", "warn" (needed only for some work) or "MISSING" (required).
# Never prints a token value. Exit code 1 when a required item is missing.
set -u

fail=0
ok()   { printf 'ok       %s\n' "$1"; }
warn() { printf 'warn     %s\n' "$1"; }
miss() { printf 'MISSING  %s\n' "$1"; fail=1; }

# version_ge <have> <min>  -> true when have >= min (numeric dotted versions)
version_ge() {
  [ "$(printf '%s\n%s\n' "$2" "$1" | sort -t. -k1,1n -k2,2n -k3,3n | head -n1)" = "$2" ]
}

root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$root" ] || [ ! -f "$root/zbb.yaml" ] || [ ! -f "$root/settings.gradle.kts" ]; then
  miss "repo root: not inside the module repo (no zbb.yaml + settings.gradle.kts at the git root)"
  exit 1
fi
ok "repo root: $root"

# --- Node ---------------------------------------------------------------
want_node="$(tr -d ' \n' < "$root/.nvmrc" 2>/dev/null)"
gradle_node="$(sed -n 's/^nodeVersion=//p' "$root/gradle.properties" 2>/dev/null)"
if command -v node >/dev/null 2>&1; then
  have_node="$(node -v)"
  if [ "${have_node%.*}" = "${want_node%.*}" ]; then
    ok "node $have_node (.nvmrc $want_node, gradle provisions ${gradle_node:-?})"
  else
    warn "node $have_node, .nvmrc pins $want_node: gradle tasks use their own Node, but npm commands you run by hand (lockfile changes) use this one"
  fi
else
  miss "node not on PATH (.nvmrc wants $want_node)"
fi

# --- Java ---------------------------------------------------------------
if command -v java >/dev/null 2>&1; then
  jv="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n1)"
  if [ "$jv" = "21" ]; then
    ok "java 21"
  else
    warn "java ${jv:-?} on PATH; zbb --slot switches to a Java 21 if one is installed, direct ./gradlew needs 21"
  fi
else
  miss "java not on PATH (Java 21 needed by gradle)"
fi

# --- zbb, dataloader ----------------------------------------------------
if command -v zbb >/dev/null 2>&1; then
  ok "$(zbb --version 2>/dev/null | head -n1)"
else
  miss "zbb not on PATH (npm i -g @zerobias-org/zbb)"
fi

min_dl="$(awk '/^tools:/{t=1} t && /version:/{gsub(/[^0-9.]/,"",$2); print $2; exit}' "$root/zbb.yaml")"
if command -v dataloader >/dev/null 2>&1; then
  dl_pkg="$(npm root -g 2>/dev/null)/@zerobias-com/platform-dataloader/package.json"
  have_dl="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$dl_pkg" 2>/dev/null | head -n1)"
  if [ -z "$have_dl" ]; then
    warn "dataloader on PATH but not under this Node's global modules; zbb's tool check may fail (zbb.yaml wants >= ${min_dl:-?})"
  elif version_ge "$have_dl" "${min_dl:-0}"; then
    ok "dataloader $have_dl (zbb.yaml wants >= ${min_dl:-?})"
  else
    miss "dataloader $have_dl, zbb.yaml wants >= $min_dl (npm i -g @zerobias-com/platform-dataloader)"
  fi
else
  miss "dataloader not on PATH for this Node; zbb test/gate need it (npm i -g @zerobias-com/platform-dataloader)"
fi

# --- Slot + stack, tokens (presence only) -------------------------------
if [ -n "${ZB_SLOT:-}" ]; then ok "slot: $ZB_SLOT"; else miss "ZB_SLOT unset: session not launched through zbb (exit and relaunch through the slot)"; fi
if [ -n "${ZB_ORG_ID:-}" ]; then ok "stack env: ZB_ORG_ID set"; else miss "ZB_ORG_ID unset: launched without a stack (relaunch with --stack module or from the repo root)"; fi
if [ -n "${ZB_TOKEN:-}" ]; then ok "ZB_TOKEN set (registry + gate dataloader step)"; else miss "ZB_TOKEN unset: npm cannot read pkg.zerobias.org"; fi

gh_var=""
for name in READ_TOKEN NPM_TOKEN GITHUB_TOKEN; do
  eval "val=\${$name:-}"
  if [ -n "$val" ]; then gh_var="$name"; break; fi
done
if [ -n "$gh_var" ]; then
  ok "GitHub Packages token: gradle will use $gh_var (must carry read:packages; scope not checked here)"
else
  miss "no READ_TOKEN / NPM_TOKEN / GITHUB_TOKEN: gradle cannot resolve the zb.* plugins"
fi

# --- Needed only for some work ------------------------------------------
if command -v jq >/dev/null 2>&1; then ok "jq"; else warn "jq not on PATH (the skills' check scripts use it)"; fi
if docker info >/dev/null 2>&1; then ok "docker running"; else warn "docker not running (connector modules and the generator's automatic build need it; plain modules do not)"; fi
if command -v yo >/dev/null 2>&1; then
  gen_pkg="$(npm root -g 2>/dev/null)/@zerobias-org/generator-module/package.json"
  if [ -f "$gen_pkg" ]; then
    ok "yo + generator-module $(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$gen_pkg" | head -n1) (compare: npm view @zerobias-org/generator-module version)"
  else
    warn "yo present, @zerobias-org/generator-module not installed for this Node (only needed to scaffold)"
  fi
else
  warn "yo not on PATH (only needed to scaffold a new module)"
fi

exit $fail
