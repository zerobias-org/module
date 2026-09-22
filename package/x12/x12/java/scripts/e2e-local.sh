#!/usr/bin/env bash
#
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate, and nothing references it.
# The standardized test surface is the JUnit suite under java/src/test, run by the
# real gate:  ./gradlew :x12:x12:gate  (see CLAUDE.md). This script is an optional
# convenience for hand-verification and is safe to delete.
# ─────────────────────────────────────────────────────────────────────────────
#
# Standalone end-to-end test: builds the uber jar and the REAL module container,
# drops a real 835 file into a bind-mounted inbox, watches it get renamed `.done`
# (rename is the ack), then drives the DataProducer RPC envelope over HTTP through
# nginx exactly as the Hub Node would — root → /files → /by-type/835 → take → ack →
# purge → downloadBinary (bytes compared to the fixture) → /healthz drained.
#
# Simulates the Hub Node by hand (docker run with MODULE_CONFIG injected). Every
# step prints PASS/FAIL; the container is torn down on exit whatever happens.
# Requires: docker (daemon up — used through `sg docker` when the shell is not yet
# in the docker group), mvn + GitHub Packages auth (gh), python3, curl, cmp.
#
set -uo pipefail

# shellcheck source=x12-common.sh
. "$(dirname "$0")/x12-common.sh"

IMAGE="module-x12-x12:e2e"
NAME="x12-e2e"
X12_CONN="e2e"

PASS=0; FAIL=0
step()  { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }
ok()    { PASS=$((PASS+1)); printf '  \033[1;32mPASS\033[0m %s\n' "$1"; }
bad()   { FAIL=$((FAIL+1)); printf '  \033[1;31mFAIL\033[0m %s\n' "$1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }   # check "label" 'condition'

WORK="$(mktemp -d -t x12-e2e.XXXXXX)"
INBOX="$WORK/inbox"; BUFFER="$WORK/buffer"
mkdir -p "$INBOX" "$BUFFER"; chmod 777 "$INBOX" "$BUFFER"

cleanup() {
  step "teardown"
  x12_stop "$NAME"
  rm -rf "$WORK" 2>/dev/null || true
  printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
  [ "$FAIL" -eq 0 ] && echo "E2E PASS" || echo "E2E FAIL"
}
trap cleanup EXIT

step "build jar"
if x12_build_jar; then ok "jar built: $X12_JAR"; else bad "mvn package"; exit 1; fi

step "build image $IMAGE"
if x12_build_image "$IMAGE"; then ok "image built"; else bad "docker build"; exit 1; fi

step "start container (inbox=$INBOX, buffer=$BUFFER, ops on :$X12_OPS)"
if x12_start "$NAME" "$IMAGE" "$INBOX" "$BUFFER" 2 1; then ok "docker run"; else bad "docker run"; exit 1; fi
if x12_wait_healthy 60; then ok "GET /healthz -> 200"; else bad "GET /healthz never returned 200"; dk logs "$NAME" | tail -40; exit 1; fi
H0="$(curl -fsS "$X12_API/healthz")"
check "healthz poller.up=true, bufferDepth=0" "[ \"$(jget "$H0" 'j["poller"]["up"] and j["poller"]["bufferDepth"]==0')\" = True ]"
check "healthz source inbox writable" "[ \"$(jget "$H0" 'j["poller"]["sources"][0]["writable"]')\" = True ]"

step "drop the 835 fixture into the inbox"
cp "$X12_FIXTURE" "$INBOX/835-005010X221A1.x12"
DONE=""
for i in $(seq 1 30); do
  if [ -f "$INBOX/835-005010X221A1.x12.done" ]; then DONE=yes; break; fi
  sleep 1
done
check "file renamed .done within 30s (rename is the ack)" "[ \"$DONE\" = yes ]"
H1="$(curl -fsS "$X12_API/healthz")"
check "healthz bufferDepth=1" "[ \"$(jget "$H1" 'j["poller"]["bufferDepth"]')\" = 1 ]"

step "connect (DataProducer handshake)"
C="$(x12_connect)"; echo "  $C"
check "POST /connections -> connected" "[ \"$(jget "$C" 'j["status"]')\" = connected ]"

step "ObjectsApi.getRootObject"
ROOT="$(x12_rpc ObjectsApi.getRootObject '{}')"; echo "  $ROOT"
check "root id is /" "[ \"$(jget "$ROOT" 'j["id"]')\" = / ]"

step "ObjectsApi.getChildren $X12_RECEIVER/files"
FILES="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}")"
echo "$FILES" | jpretty | head -30
check "PagedResults count=1" "[ \"$(jget "$FILES" 'j["count"]')\" = 1 ]"
FILE_ID="$(jget "$FILES" 'j["items"][0]["fileId"]')"
FILE_NODE="$(jget "$FILES" 'j["items"][0]["id"]')"
FILE_SUM="$(jget "$FILES" 'j["items"][0]["checksum"]')"
echo "  fileId=$FILE_ID"
echo "  node=$FILE_NODE"
check "fileId = /var/lib/x12/inbox/<name>@<12 hex of sha256>" "[ \"$FILE_ID\" = \"/var/lib/x12/inbox/835-005010X221A1.x12@${FILE_SUM:0:12}\" ]"
check "checksum matches the fixture's sha256" "[ \"$FILE_SUM\" = \"$(sha256sum "$X12_FIXTURE" | cut -c1-64)\" ]"
check "node is container+binary with status:consumed" "[ \"$(jget "$FILES" '"binary" in j["items"][0]["objectClass"] and "status:consumed" in j["items"][0]["tags"]')\" = True ]"
check "filePath is the raw discovery path" "[ \"$(jget "$FILES" 'j["items"][0]["filePath"]')\" = /var/lib/x12/inbox/835-005010X221A1.x12 ]"

step "CollectionsApi.getCollectionElements $X12_RECEIVER/by-type/835"
ELEMS="$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":\"$X12_RECEIVER/by-type/835\",\"pageSize\":10,\"pageNumber\":1}")"
echo "$ELEMS" | jpretty | head -40
check "count=1, status new, typed body present" "[ \"$(jget "$ELEMS" 'j["count"]==1 and j["items"][0]["status"]=="new" and "header" in j["items"][0]')\" = True ]"
KEY="$(jget "$ELEMS" 'j["items"][0]["elementKey"]')"
check "elementKey = <fileId>:<GS06>:<ST02>" "[ \"$KEY\" = \"$FILE_ID:101:0001\" ]"
check "schema is the 835 table" "[ \"$(jget "$ELEMS" 'j["items"][0]["transactionType"]')\" = 835 ]"

step "FunctionsApi.invokeFunction ops/validate (materializer behind the seam)"
V="$(x12_fn validate "{\"elementKey\":$(jstr "$KEY")}")"
echo "$V" | jpretty | head -20
check "stored.valid + rematerialized.valid + repsAgree" "[ \"$(jget "$V" 'j["stored"]["valid"] and j["rematerialized"]["valid"] and j["repsAgree"]')\" = True ]"

step "FunctionsApi.invokeFunction ops/take"
TAKE="$(x12_fn take '{"max":10}')"
echo "$TAKE" | jpretty | head -12
LEASE="$(jget "$TAKE" 'j["leaseId"]')"
check "leased 1 transaction, remaining 0" "[ \"$(jget "$TAKE" 'len(j["transactions"])==1 and j["remaining"]==0 and j["leaseId"] is not None')\" = True ]"

step "FunctionsApi.invokeFunction ops/ack  leaseId=$LEASE"
ACK="$(x12_fn ack "{\"leaseId\":$(jstr "$LEASE")}")"; echo "  $ACK"
check "acked=1" "[ \"$(jget "$ACK" 'j["acked"]')\" = 1 ]"

step "FunctionsApi.invokeFunction ops/purge"
PURGE="$(x12_fn purge '{}')"; echo "  $PURGE"
check "purged=1" "[ \"$(jget "$PURGE" 'j["purged"]')\" = 1 ]"

step "BinaryApi.downloadBinary on the file node (bytes vs fixture)"
x12_rpc_bin BinaryApi.downloadBinary "{\"objectId\":$(jstr "$FILE_NODE")}" "$WORK/download.x12"
check "downloaded bytes == fixture" "cmp -s \"$WORK/download.x12\" \"$X12_FIXTURE\""

step "healthz drained"
H2="$(curl -fsS "$X12_API/healthz")"; echo "  $H2"
check "bufferDepth back to 0" "[ \"$(jget "$H2" 'j["poller"]["bufferDepth"]')\" = 0 ]"
check "transactions collection empty but the files row remains" "[ \"$(jget "$(x12_rpc ObjectsApi.getObject "{\"objectId\":\"$X12_RECEIVER/transactions\"}")" 'j["collectionSize"]')\" = 0 ] && [ \"$(jget "$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}")" 'j["count"]')\" = 1 ]"

exit 0
