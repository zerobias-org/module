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
# nginx exactly as the Hub Node would — root → /files → /by-type/835/<GS08> →
# validate → take → ack → purge → downloadBinary (bytes compared to the fixture).
# Then: a file carrying two interchanges that reuse GS06/ST02 (both transaction
# sets land), a symlink in the inbox (skipped, never followed), and two boots that
# must fail fast (an unknown config key, a read-only inbox).
#
# Simulates the Hub Node by hand (docker run with MODULE_CONFIG injected). Every
# step prints PASS/FAIL; the containers are torn down on exit whatever happens.
#
# Usage:  READ_TOKEN=$(gh auth token) java/scripts/e2e-local.sh
# Requires: docker (daemon up), mvn + GitHub Packages read access (READ_TOKEN or
# GITHUB_TOKEN, see x12-common.sh), python3, curl, cmp.
#
set -uo pipefail

# shellcheck source=x12-common.sh
. "$(dirname "$0")/x12-common.sh"

IMAGE="module-x12-x12:e2e"
NAME="x12-e2e"
BAD="x12-e2e-bad"
X12_CONN="e2e"
FIXTURE_NAME="835-005010X221A1.x12"
ISA13="000000101"            # the 835 fixture's interchange control number

PASS=0; FAIL=0
step()  { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }
ok()    { PASS=$((PASS+1)); printf '  \033[1;32mPASS\033[0m %s\n' "$1"; }
bad()   { FAIL=$((FAIL+1)); printf '  \033[1;31mFAIL\033[0m %s\n' "$1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }   # check "label" 'condition'

WORK="$(mktemp -d -t x12-e2e.XXXXXX)"
INBOX="$WORK/inbox"; BUFFER="$WORK/buffer"
# The container runs as uid 10001: both bind-mounted dirs must be writable by it.
mkdir -p "$INBOX" "$BUFFER"; chmod 777 "$INBOX" "$BUFFER"

cleanup() {
  step "teardown"
  x12_stop "$NAME"
  x12_stop "$BAD"
  rm -rf "$WORK" 2>/dev/null || true
  printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
  [ "$FAIL" -eq 0 ] && echo "E2E PASS" || echo "E2E FAIL"
}
trap cleanup EXIT

# Container logs contain a fixed string. Logs go to a file first: `logs | grep -q` under
# pipefail fails whenever grep exits before docker has written everything (SIGPIPE).
logs_have() {   # logs_have NAME STRING
  dk logs "$1" > "$WORK/logs.txt" 2>&1
  grep -qF -- "$2" "$WORK/logs.txt"
}

wait_for() {   # wait_for PATH SECONDS -> 0 when PATH exists
  local i
  for i in $(seq 1 "$2"); do
    [ -e "$1" ] && return 0
    sleep 1
  done
  return 1
}

step "build jar"
if x12_build_jar; then ok "jar built: $X12_JAR"; else bad "mvn package"; exit 1; fi

step "build image $IMAGE"
if x12_build_image "$IMAGE"; then ok "image built"; else bad "docker build"; exit 1; fi

step "start container (inbox=$INBOX, buffer=$BUFFER, ops on :$X12_OPS)"
if x12_start "$NAME" "$IMAGE" "$INBOX" "$BUFFER" 2 1; then ok "docker run"; else bad "docker run"; exit 1; fi
if x12_wait_healthy 60; then ok "GET /healthz -> 200"; else bad "GET /healthz never returned 200"; dk logs "$NAME" | tail -40; exit 1; fi
check "container runs as uid $X12_UID" "[ \"\$(dk exec $NAME id -u)\" = $X12_UID ]"
check "ackDurability defaults to full (not set in MODULE_CONFIG)" "logs_have $NAME 'ackDurability=full'"
H0="$(curl -fsS "$X12_API/healthz")"; echo "  $H0"
check "healthz poller.up=true, bufferDepth=0, no backpressure" "[ \"$(jget "$H0" 'j["poller"]["up"] and j["poller"]["bufferDepth"]==0 and not j["poller"]["backpressure"]')\" = True ]"
check "healthz source inbox writable, not stalled, no failed scans" "[ \"$(jget "$H0" '(s:=j["poller"]["sources"][0])["writable"] and not s["stalled"] and s["consecutiveFailures"]==0 and "lastScanStarted" in s')\" = True ]"
check "healthz db reports walBytes + sizeBytes" "[ \"$(jget "$H0" '"walBytes" in j["db"] and j["db"]["sizeBytes"]>0')\" = True ]"

step "drop the 835 fixture into the inbox"
cp "$X12_FIXTURE" "$INBOX/$FIXTURE_NAME"
if wait_for "$INBOX/$FIXTURE_NAME.done" 30; then DONE=yes; else DONE=no; fi
check "file renamed .done within 30s (rename is the ack)" "[ \"$DONE\" = yes ]"
H1="$(curl -fsS "$X12_API/healthz")"
check "healthz bufferDepth=1" "[ \"$(jget "$H1" 'j["poller"]["bufferDepth"]')\" = 1 ]"

step "ObjectsApi.getRootObject"
ROOT="$(x12_rpc ObjectsApi.getRootObject '{}')"; echo "  $ROOT"
check "root id is /" "[ \"$(jget "$ROOT" 'j["id"]')\" = / ]"

step "ObjectsApi.getChildren $X12_RECEIVER/files + DocumentsApi.getDocumentData on the file node"
FILES="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}")"
echo "$FILES" | jpretty | head -30
check "PagedResults count=1" "[ \"$(jget "$FILES" 'j["count"]')\" = 1 ]"
FILE_NODE="$(jget "$FILES" 'j["items"][0]["id"]')"
FILE_SUM="$(jget "$FILES" 'j["items"][0]["checksum"]')"
check "file node is [container, document, binary] with status:consumed" "[ \"$(jget "$FILES" 'j["items"][0]["objectClass"]==["container","document","binary"] and "status:consumed" in j["items"][0]["tags"]')\" = True ]"
check "checksum matches the fixture's sha256" "[ \"$FILE_SUM\" = \"$(sha256 "$X12_FIXTURE")\" ]"
FDOC="$(x12_rpc DocumentsApi.getDocumentData "{\"objectId\":$(jstr "$FILE_NODE")}")"
echo "$FDOC" | jpretty | head -24
FILE_ID="$(jget "$FDOC" 'j["fileId"]')"
echo "  fileId=$FILE_ID"
echo "  node=$FILE_NODE"
check "fileId = /var/lib/x12/inbox/<name>@<12 hex of sha256>" "[ \"$FILE_ID\" = \"/var/lib/x12/inbox/$FIXTURE_NAME@${FILE_SUM:0:12}\" ]"
check "filePath is the discovery path, currentPath the .done target" "[ \"$(jget "$FDOC" 'j["filePath"]=="/var/lib/x12/inbox/'"$FIXTURE_NAME"'" and j["currentPath"]=="/var/lib/x12/inbox/'"$FIXTURE_NAME"'.done" and j["status"]=="consumed" and j["transactionCount"]==1')\" = True ]"

step "/by-type/835 is a container of per-guide collections"
T835="$(x12_rpc ObjectsApi.getObject "{\"objectId\":\"$X12_RECEIVER/by-type/835\"}")"; echo "  $T835"
check "/by-type/835 objectClass = [container]" "[ \"$(jget "$T835" 'j["objectClass"]==["container"]')\" = True ]"
TKIDS="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/by-type/835\"}")"
check "its one child is the 005010X221A1 collection with the guide's table schema" "[ \"$(jget "$TKIDS" 'j["count"]==1 and j["items"][0]["id"]=="'"$X12_RECEIVER"'/by-type/835/005010X221A1" and j["items"][0]["collectionSchema"]=="schema:table:x12.005010X221A1.835"')\" = True ]"
CODE="$(x12_rpc_code CollectionsApi.getCollectionElements "{\"objectId\":\"$X12_RECEIVER/by-type/835\"}" "$WORK/err.json")"
check "getCollectionElements on the container -> 400 (not a collection)" "[ \"$CODE\" = 400 ]"

step "CollectionsApi.getCollectionElements $X12_RECEIVER/by-type/835/005010X221A1"
ELEMS="$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":\"$X12_RECEIVER/by-type/835/005010X221A1\",\"pageSize\":10,\"pageNumber\":1}")"
echo "$ELEMS" | jpretty | head -40
check "count=1, status new, typed body present" "[ \"$(jget "$ELEMS" 'j["count"]==1 and j["items"][0]["status"]=="new" and "header" in j["items"][0]')\" = True ]"
KEY="$(jget "$ELEMS" 'j["items"][0]["elementKey"]')"
check "elementKey = <fileId>:<ISA13>:<GS06>:<ST02>" "[ \"$KEY\" = \"$FILE_ID:$ISA13:101:0001\" ]"
check "transactionType 835, control numbers kept as strings" "[ \"$(jget "$ELEMS" '(e:=j["items"][0])["transactionType"]=="835" and e["isaControlNumber"]=="'"$ISA13"'" and e["gsControlNumber"]=="101"')\" = True ]"

step "FunctionsApi.invokeFunction ops/validate (materializer behind the seam)"
V="$(x12_fn validate "{\"elementKey\":$(jstr "$KEY")}")"
echo "$V" | jpretty | head -20
check "stored.valid + rematerialized.valid + repsAgree" "[ \"$(jget "$V" 'j["stored"]["valid"] and j["rematerialized"]["valid"] and j["repsAgree"]')\" = True ]"

step "function input is schema-checked before anything runs"
CODE="$(x12_rpc_code FunctionsApi.invokeFunction "{\"objectId\":\"$X12_RECEIVER/ops/purge\",\"requestBody\":{\"olderthan\":\"P30D\"}}" "$WORK/err.json")"
check "ops/purge with a misspelled key -> 400" "[ \"$CODE\" = 400 ]"
VI="$(x12_rpc FunctionsApi.validateFunctionInput "{\"objectId\":\"$X12_RECEIVER/ops/purge\",\"validateFunctionInputRequest\":{\"input\":{\"olderthan\":\"P30D\"}}}")"; echo "  $VI"
check "validateFunctionInput reports unknown_property" "[ \"$(jget "$VI" 'not j["valid"] and j["errors"][0]["code"]=="unknown_property"')\" = True ]"

step "FunctionsApi.invokeFunction ops/take"
TAKE="$(x12_fn take '{"max":10}')"
echo "$TAKE" | jpretty | head -12
LEASE="$(jget "$TAKE" 'j["leaseId"]')"
check "leased 1 transaction, remaining 0" "[ \"$(jget "$TAKE" 'len(j["transactions"])==1 and j["remaining"]==0 and j["leaseId"] is not None')\" = True ]"

step "FunctionsApi.invokeFunction ops/ack  leaseId=$LEASE"
ACK="$(x12_fn ack "{\"leaseId\":$(jstr "$LEASE")}")"; echo "  $ACK"
check "acked=1" "[ \"$(jget "$ACK" 'j["acked"]')\" = 1 ]"
CODE="$(x12_rpc_code FunctionsApi.invokeFunction "{\"objectId\":\"$X12_RECEIVER/ops/ack\",\"requestBody\":{\"leaseId\":$(jstr "$LEASE")}}" "$WORK/err.json")"
check "ack of a finalized lease -> 404 not found (type lease)" "[ \"$CODE\" = 404 ] && [ \"$(jget "$(cat "$WORK/err.json")" 'j["key"]=="err.no.such.object" and j["type"]=="lease"')\" = True ]"

step "FunctionsApi.invokeFunction ops/purge"
PURGE="$(x12_fn purge '{}')"; echo "  $PURGE"
check "purged=1" "[ \"$(jget "$PURGE" 'j["purged"]')\" = 1 ]"

step "BinaryApi.downloadBinary on the file node (bytes vs fixture)"
x12_rpc_bin BinaryApi.downloadBinary "{\"objectId\":$(jstr "$FILE_NODE")}" "$WORK/download.x12"
check "downloaded bytes == fixture" "cmp -s \"$WORK/download.x12\" \"$X12_FIXTURE\""
CODE="$(x12_rpc_code BinaryApi.downloadBinaryContent "{\"objectId\":$(jstr "$FILE_NODE")}" "$WORK/err.json")"
check "BinaryApi.downloadBinaryContent (not a wire name) -> 400" "[ \"$CODE\" = 400 ]"
CODE="$(x12_rpc_code DocumentsApi.getDocument "{\"objectId\":\"$X12_RECEIVER/stats\"}" "$WORK/err.json")"
check "DocumentsApi.getDocument (not a wire name) -> 400" "[ \"$CODE\" = 400 ]"

step "DocumentsApi.getDocumentData $X12_RECEIVER/stats"
STATS="$(x12_rpc DocumentsApi.getDocumentData "{\"objectId\":\"$X12_RECEIVER/stats\"}")"; echo "  $STATS"
check "stats: fileCount=1, doneFileCount=1, dbSizeBytes, per-source lastScanCompleted" "[ \"$(jget "$STATS" 'j["fileCount"]==1 and j["doneFileCount"]==1 and j["dbSizeBytes"]>0 and "lastScanCompleted" in j["sources"][0]')\" = True ]"

step "a file with two interchanges reusing GS06/ST02 -> both transaction sets land"
python3 - "$X12_FIXTURE" "$WORK/two.x12" "$ISA13" <<'PY'
import sys
src, out, isa13 = sys.argv[1], sys.argv[2], sys.argv[3]
one = open(src, "rb").read().decode("ascii")
# Second interchange: same GS06 (101) and ST02 (0001), only ISA13/IEA02 differ.
two = one.replace("*" + isa13 + "*0*T*:~", "*000000201*0*T*:~").replace("IEA*1*" + isa13 + "~", "IEA*1*000000201~")
assert two != one
open(out, "wb").write((one + two).encode("ascii"))
PY
cp "$WORK/two.x12" "$INBOX/two-interchanges.x12"
if wait_for "$INBOX/two-interchanges.x12.done" 30; then DONE=yes; else DONE=no; fi
check "two-interchange file renamed .done" "[ \"$DONE\" = yes ]"
FILE2_ID="/var/lib/x12/inbox/two-interchanges.x12@$(sha256 "$WORK/two.x12" | cut -c1-12)"
FILE2_NODE="$X12_RECEIVER/files/$(python3 -c 'import sys; print(sys.argv[1].replace("%","%25").replace("/","%2F"))' "$FILE2_ID")"
FDOC2="$(x12_rpc DocumentsApi.getDocumentData "{\"objectId\":$(jstr "$FILE2_NODE")}")"; echo "  $FDOC2"
check "files row: consumed, isaCount=2, transactionCount=2" "[ \"$(jget "$FDOC2" 'j["status"]=="consumed" and j["isaCount"]==2 and j["transactionCount"]==2')\" = True ]"
TX2="$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$FILE2_NODE/transactions")}")"
check "its transactions collection holds both keys, told apart by ISA13" "[ \"$(jget "$TX2" 'sorted(e["elementKey"] for e in j["items"])==sorted(["'"$FILE2_ID"':'"$ISA13"':101:0001","'"$FILE2_ID"':000000201:101:0001"])')\" = True ]"

step "a symlink in the inbox is skipped, never followed"
ln -s /var/lib/module/buffer.db "$INBOX/link.x12"
RS="$(x12_fn rescan '{}')"; echo "  $RS"
check "rescan scanned nothing (the link is not a candidate)" "[ \"$(jget "$RS" 'j["scanned"]==0 and j["consumed"]==0 and j["errored"]==0')\" = True ]"
check "link left in place, not renamed .done/.error" "[ -L \"$INBOX/link.x12\" ] && [ ! -e \"$INBOX/link.x12.done\" ] && [ ! -e \"$INBOX/link.x12.error\" ]"
check "no files row for the link (still 2 files)" "[ \"$(jget "$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}")" 'j["count"]')\" = 2 ]"
check "the skip is logged" "logs_have $NAME 'link.x12 is a symbolic link; skipped'"

step "drain the rest: take -> ack -> purge"
TAKE2="$(x12_fn take '{"max":10}')"
LEASE2="$(jget "$TAKE2" 'j["leaseId"]')"
check "leased 2 transactions" "[ \"$(jget "$TAKE2" 'len(j["transactions"])')\" = 2 ]"
check "acked=2" "[ \"$(jget "$(x12_fn ack "{\"leaseId\":$(jstr "$LEASE2")}")" 'j["acked"]')\" = 2 ]"
check "purged=2" "[ \"$(jget "$(x12_fn purge '{"olderThan":"PT0S"}')" 'j["purged"]')\" = 2 ]"

step "file management through the API: mkdir -> upload -> live browse -> delete"
STAGING="$SRC/staging"
MK="$(x12_mkdir "$SRC" staging)"; echo "  $MK"
MK_ID="$(jget "$MK" 'j["id"]')"
MK_CLASS="$(jget "$MK" '",".join(j["objectClass"])')"
check "createChildObject made a container" "[ \"$MK_ID\" = \"$STAGING\" ] && [ \"$MK_CLASS\" = container ]"
check "the directory exists on the volume" "[ -d \"$INBOX/staging\" ]"

UP2="$(x12_upload "$STAGING" "staged.x12" "$X12_FIXTURE")"
check "uploaded into the chosen folder" "[ \"$(jget "$UP2" 'j["id"]')\" = \"$STAGING/staged.x12\" ]"
check "ingest=ignored:subdirectory (the poller scans each source flat)" "[ \"$(jget "$UP2" 'j["ingest"]')\" = ignored:subdirectory ]"

LIVE="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":$(jstr "$STAGING")}")"
echo "$LIVE" | jpretty | head -16
LIVE_COUNT="$(jget "$LIVE" 'j["count"]')"
LIVE_NAME="$(jget "$LIVE" 'j["items"][0]["name"]')"
check "browse is live: the never-ingested file is listed" "[ \"$LIVE_COUNT\" = 1 ] && [ \"$LIVE_NAME\" = staged.x12 ]"
FILES2="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}")"
STAGED_ROWS="$(jget "$FILES2" 'len([i for i in j["items"] if i["fileName"] == "staged.x12"])')"
check "and it is NOT in /files (nothing consumed it)" "[ \"$STAGED_ROWS\" = 0 ]"

x12_rpc_bin BinaryApi.downloadBinary "{\"objectId\":$(jstr "$STAGING/staged.x12")}" "$WORK/staged.x12"
check "downloadBinary serves bytes that were never ingested" "cmp -s \"$WORK/staged.x12\" \"$X12_FIXTURE\""

# The refusals that make this safe to ship.
check "deleting a non-empty folder is refused" "! x12_delete \"$STAGING\" >/dev/null 2>&1"
check "deleting a configured source is refused" "! x12_delete \"$SRC\" >/dev/null 2>&1"
check "re-uploading the same name is refused (never replaces)" "! x12_upload \"$STAGING\" staged.x12 \"$X12_FIXTURE\" >/dev/null 2>&1"
check "path traversal is refused" "! x12_upload \"$SRC/../../etc\" passwd \"$X12_FIXTURE\" >/dev/null 2>&1"

D1="$(x12_delete "$STAGING/staged.x12")"; echo "  $D1"
check "deleteObject removed the file" "[ \"$(jget "$D1" 'j["status"]')\" = deleted ] && [ ! -f \"$INBOX/staging/staged.x12\" ]"
x12_delete "$STAGING" >/dev/null
check "deleteObject removed the now-empty folder" "[ ! -d \"$INBOX/staging\" ]"
check "live browse shows the source empty again except .done" "[ \"$(jget "$(x12_rpc ObjectsApi.getChildren "{\"objectId\":$(jstr "$SRC")}")" 'len(j["items"])')\" = 1 ]"

step "ops/packs: the bundled content this deployment shipped with"
PACKS="$(x12_fn packs '{}')"
echo "$PACKS" | jpretty | head -24
PACK_COUNT="$(jget "$PACKS" 'j["packCount"]')"
PACK_SCHEMAS="$(jget "$PACKS" 'j["schemaCount"]')"
REGISTRY="$(jget "$PACKS" 'j["registrySize"]')"
DEGRADED="$(jget "$PACKS" 'len([p for p in j["packs"] if p["status"] != "active"])')"
NON_BUNDLED="$(jget "$PACKS" 'len([p for p in j["packs"] if p["source"] != "bundled"])')"
check "packs reported (one per guide label, plus codes and core)" "[ \"$PACK_COUNT\" -ge 12 ]"
check "every pack is active (no declared id the registry cannot serve)" "[ \"$DEGRADED\" = 0 ]"
check "every pack is bundled in a stock deployment" "[ \"$NON_BUNDLED\" = 0 ]"
check "pack schema count reconciles with the registry" "[ \"$PACK_SCHEMAS\" -le \"$REGISTRY\" ] && [ \"$PACK_SCHEMAS\" -gt 0 ]"
GUIDE_835="$(jget "$PACKS" 'j["guides"].count("005010X221A1")')"
check "the 835 guide has a pack" "[ \"$GUIDE_835\" = 1 ]"
CORE="$(jget "$PACKS" 'len([p for p in j["packs"] if p["core"]])')"
check "exactly one core pack (the module contract, never superseded)" "[ \"$CORE\" = 1 ]"
ONE="$(x12_fn packs '{"name":"x12-guide-005010X221A1"}')"
check "filtering by name returns just that pack" "[ \"$(jget "$ONE" 'j["packCount"]')\" = 1 ]"
NOPE="$(x12_fn packs "{\"name\":\"x12-guide-nope\"}")"
NOPE_COUNT="$(jget "$NOPE" 'j["packCount"]')"
check "an unknown pack is an empty report, not an error" "[ \"$NOPE_COUNT\" = 0 ]"

step "healthz drained"
H2="$(curl -fsS "$X12_API/healthz")"; echo "  $H2"
check "bufferDepth back to 0" "[ \"$(jget "$H2" 'j["poller"]["bufferDepth"]')\" = 0 ]"
check "transactions collection empty but the files rows remain" "[ \"$(jget "$(x12_rpc ObjectsApi.getObject "{\"objectId\":\"$X12_RECEIVER/transactions\"}")" 'j["collectionSize"]')\" = 0 ] && [ \"$(jget "$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}")" 'j["count"]')\" = 2 ]"
x12_stop "$NAME"

step "fail-fast boot: an unknown module-config key"
BUF2="$WORK/buffer2"; mkdir -p "$BUF2"; chmod 777 "$BUF2"
dk run -d --name "$BAD" -e HUB_NODE_INSECURE=true \
  -e MODULE_CONFIG='{"sources":[{"name":"inbox","path":"/var/lib/x12/inbox"}],"retension":{"maxAge":"P7D"}}' \
  -v "$INBOX:/var/lib/x12/inbox" -v "$BUF2:/var/lib/module" "$IMAGE" >/dev/null
check "container exits 1" "[ \"$(x12_wait_exit "$BAD" 60)\" = 1 ]"
check "log names the unknown key" "logs_have $BAD \"unknown key 'retension'\""
x12_stop "$BAD"

step "fail-fast boot: a read-only inbox"
dk run -d --name "$BAD" -e HUB_NODE_INSECURE=true -e MODULE_CONFIG="$(x12_module_config 2 1)" \
  -v "$INBOX:/var/lib/x12/inbox:ro" -v "$BUF2:/var/lib/module" "$IMAGE" >/dev/null
check "container exits 1" "[ \"$(x12_wait_exit "$BAD" 60)\" = 1 ]"
check "log says the source is not writable/renameable" "logs_have $BAD 'is not writable/renameable'"

exit 0
