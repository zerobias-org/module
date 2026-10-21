#!/usr/bin/env bash
#
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate. The standardized test surface is
# the JUnit suite (./gradlew :ocsf:v1:gate). This is hand-verification of the real image.
# ─────────────────────────────────────────────────────────────────────────────
#
# Builds the uber jar and the REAL module container, starts it as the Hub Node would
# (MODULE_CONFIG injected, inbox + buffer bind-mounted, operations through nginx), and runs
# e2e_checks.py: every OCSF class uploaded through the API, 1.1.0 samples dropped on the
# folder, verbatim reads per class, time window + paging, bad input, and a restart.
#
# allowFileManagement is ON here so events can arrive through uploadBinaryContent; it ships
# false. Sample events come from schema.ocsf.io and are cached in $OCSF_E2E_SAMPLES.
# Requires: docker (daemon up), mvn + GitHub Packages auth (gh), python3, npm install done
# (the jar bundles node_modules/@zerobias-org/module-interface-ocsf/api.yml).
#
set -uo pipefail

MOD_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SPEC="$MOD_DIR/node_modules/@zerobias-org/module-interface-ocsf/api.yml"
IMAGE="module-ocsf-v1:e2e"
NAME="ocsf-e2e"
OPS_PORT="${OCSF_E2E_PORT:-18888}"
SAMPLES="${OCSF_E2E_SAMPLES:-${XDG_CACHE_HOME:-$HOME/.cache}/ocsf-e2e-samples}"

WORK="$(mktemp -d -t ocsf-e2e.XXXXXX)"
INBOX="$WORK/inbox"; BUFFER="$WORK/buffer"
# The image runs as uid 10001; 0777 lets it write and rename here, and the host user clean up.
mkdir -p "$INBOX" "$BUFFER" "$SAMPLES" && chmod 0777 "$INBOX" "$BUFFER"

cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  rm -rf "$WORK" 2>/dev/null || echo "left behind: $WORK" >&2
}
trap cleanup EXIT

[ -f "$SPEC" ] || { echo "missing $SPEC: run npm install in $MOD_DIR" >&2; exit 1; }

echo "== build jar"
(cd "$MOD_DIR/java" && GITHUB_ACTOR="${GITHUB_ACTOR:-$(gh api user --jq .login 2>/dev/null || echo x)}" \
   READ_TOKEN="${READ_TOKEN:-$(gh auth token 2>/dev/null || true)}" mvn -q -DskipTests package) || exit 1

echo "== build image $IMAGE"
docker build -q -t "$IMAGE" "$MOD_DIR" >/dev/null || exit 1

echo "== start container (ops on :$OPS_PORT, inbox $INBOX)"
CONFIG='{"sources":[{"name":"inbox","path":"/var/lib/ocsf/inbox","pollIntervalSec":1,"stableForSec":1}],"ackDurability":"normal","allowFileManagement":true}'
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" -e HUB_NODE_INSECURE=true -e MODULE_CONFIG="$CONFIG" \
  -v "$INBOX:/var/lib/ocsf/inbox" -v "$BUFFER:/var/lib/module" -p "$OPS_PORT:8888" "$IMAGE" >/dev/null || exit 1
for i in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' -m2 "http://localhost:$OPS_PORT/healthz")" = 200 ] && break
  [ "$i" = 60 ] && { echo "healthz never returned 200" >&2; docker logs "$NAME" | tail -40; exit 1; }
  sleep 1
done

python3 -I "$MOD_DIR/java/scripts/e2e_checks.py" --base "http://localhost:$OPS_PORT" --inbox "$INBOX" \
  --container "$NAME" --samples "$SAMPLES" --spec "$SPEC"
STATUS=$?
[ "$STATUS" = 0 ] || { echo "--- container log (tail)"; docker logs "$NAME" 2>&1 | tail -30; }
[ "$STATUS" = 0 ] && echo "E2E PASS" || echo "E2E FAIL"
exit "$STATUS"
