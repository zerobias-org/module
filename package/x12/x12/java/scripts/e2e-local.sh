#!/usr/bin/env bash
#
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate, and nothing references it.
# The standardized test surface is the JUnit suite under java/src/test, run by the
# real gate:  ./gradlew :x12:x12:gate  (see CLAUDE.md). This script is an optional
# convenience for hand-verification and is safe to delete.
# ─────────────────────────────────────────────────────────────────────────────
#
# Standalone end-to-end test: builds the uber jar and the REAL module container, then
# drives everything through the DataProducer RPC envelope over HTTP through nginx,
# exactly as the Hub Node would. Nothing reaches around the module to touch the volume.
#
#   connect → isSupported → uploadBinaryContent (the 835 lands on the mounted inbox
#   THROUGH the API) → watch it get renamed `.done` (rename is the ack) → root →
#   /files → /by-type/835 → validate → take → ack → purge → downloadBinary (bytes
#   compared to the fixture) → file management (mkdir → upload into the new folder →
#   live browse → download → delete, and the refusals that guard it) → /healthz drained.
#
# The container runs with `allowFileManagement: true` (see x12_module_config); it ships
# false, so a production deployment has no upload path at all.
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

step "connect (DataProducer handshake)"
C="$(x12_connect)"; echo "  $C"
check "POST /connections -> connected" "[ \"$(jget "$C" 'j["status"]')\" = connected ]"

step "isSupported reflects the real capability set (allowFileManagement=true here)"
for op in uploadBinaryContent createChildObject deleteObject downloadBinary getChildren; do
  check "isSupported($op) = true" "[ \"$(x12_is_supported $op)\" = True ]"
done
for op in updateObject addCollectionElement deleteCollectionElement updateDocumentData; do
  check "isSupported($op) = false (data is receive-only)" "[ \"$(x12_is_supported $op)\" = False ]"
done

step "load the 835 THROUGH the DataProducer API (uploadBinaryContent -> the volume)"
SRC="$X12_RECEIVER/inbox/inbox"
UP="$(x12_upload "$SRC" "835-005010X221A1.x12" "$X12_FIXTURE")"
echo "$UP" | jpretty | head -14
check "upload returned the new binary node" "[ \"$(jget "$UP" 'j["id"]')\" = \"$SRC/835-005010X221A1.x12\" ]"
UP_CLASS="$(jget "$UP" '",".join(j["objectClass"])')"
UP_INGEST="$(jget "$UP" 'j["ingest"]')"
check "node is a live binary with ingest=watched" "[ \"$UP_CLASS\" = binary ] && [ \"$UP_INGEST\" = watched ]"
check "uploaded size matches the fixture" "[ \"$(jget "$UP" 'j["size"]')\" = $(stat -c%s "$X12_FIXTURE") ]"
check "bytes really landed on the bind mount" "[ -f \"$INBOX/835-005010X221A1.x12\" ] || [ -f \"$INBOX/835-005010X221A1.x12.done\" ]"
check "no .part-* temporary left in the inbox" "! ls -A \"$INBOX\" | grep -q '[.]part-'"
DONE=""
for i in $(seq 1 30); do
  if [ -f "$INBOX/835-005010X221A1.x12.done" ]; then DONE=yes; break; fi
  sleep 1
done
check "the API-loaded file was consumed and renamed .done within 30s" "[ \"$DONE\" = yes ]"
H1="$(curl -fsS "$X12_API/healthz")"
check "healthz bufferDepth=1" "[ \"$(jget "$H1" 'j["poller"]["bufferDepth"]')\" = 1 ]"

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

# Before the drain on purpose: ops/purge deletes acked transactions AND their object graph,
# so the business collections are legitimately empty once the fixture has been purged.
step "business entities: /claims with named columns, segments and typed filters"
CLAIMS="$X12_RECEIVER/claims"
CL_OBJ="$(x12_rpc ObjectsApi.getObject "{\"objectId\":$(jstr "$CLAIMS")}")"
echo "  $CL_OBJ"
CL_SCHEMA="$(jget "$CL_OBJ" 'j["collectionSchema"]')"
CL_SIZE="$(jget "$CL_OBJ" 'j["collectionSize"]')"
check "/claims is a collection bound to the Claim business schema" "[ \"$CL_SCHEMA\" = schema:business:x12.835.Claim ]"
check "/claims holds the two claims from the 835" "[ \"$CL_SIZE\" = 2 ]"
SCHEMA_OK="$(jget "$(x12_rpc SchemasApi.getSchema "{\"schemaId\":\"schema:business:x12.835.Claim\"}")" 'len([p for p in j["properties"] if p["name"] in ("claimId","paidAmount","patientLastName","payerName","fileId")])')"
check "the business schema is served and names its columns" "[ \"$SCHEMA_OK\" = 5 ]"

ROWS="$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"pageSize\":10,\"pageNumber\":1}")"
echo "$ROWS" | jpretty | head -26
CLAIM_ID="$(jget "$ROWS" 'j["items"][0]["claimId"]')"
PAID_EQ="$(jget "$ROWS" 'j["items"][0]["paidAmount"] == 220.00')"
PATIENT="$(jget "$ROWS" 'j["items"][0]["patientLastName"]')"
PAYER="$(jget "$ROWS" 'j["items"][0]["payerName"]')"
PROV="$(jget "$ROWS" 'j["items"][0]["fileId"]')"
check "a claim row carries its business identity" "[ \"$CLAIM_ID\" = CLM0001 ]"
check "paidAmount equals 220.00" "[ \"$PAID_EQ\" = True ]"
# The wire form must keep the scale; jget cannot assert it (json.loads makes it a float) and the
# body cannot go into an eval'd condition (its own quotes break the quoting).
SCALE_OK="$(x12_contains "$ROWS" '"paidAmount":220.00')"
check "paidAmount keeps two decimal places on the wire" "[ \"$SCALE_OK\" = yes ]"
check "the patient came from NM1*QC by qualifier" "[ \"$PATIENT\" = DOE ]"
check "the payer dimension is carried onto the row" "[ \"$PAYER\" = \"EXAMPLE HEALTH PLAN\" ]"
check "provenance: the row names the interchange it came from" "[ -n \"$PROV\" ]"

SEGMENTS="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":$(jstr "$CLAIMS"),\"pageSize\":50}")"
SEG_NAMES="$(jget "$SEGMENTS" '",".join(sorted(i["name"] for i in j["items"]))')"
check "claims are segmentable by file and by payer" "echo \"$SEG_NAMES\" | grep -q 'by-file' && echo \"$SEG_NAMES\" | grep -q 'by-payerName'"
BY_PAYER="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":$(jstr "$CLAIMS/by-payerName"),\"pageSize\":50}")"
PAYER_NODE="$(jget "$BY_PAYER" 'j["items"][0]["id"]')"
PAYER_CLAIMS="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$PAYER_NODE"),\"pageSize\":10}")" 'j["count"]')"
check "the payer segment emerged from the data" "[ -n \"$PAYER_NODE\" ]"
check "'claims for this payer' returns both claims" "[ \"$PAYER_CLAIMS\" = 2 ]"
BY_FILE="$(x12_rpc ObjectsApi.getChildren "{\"objectId\":$(jstr "$CLAIMS/by-file"),\"pageSize\":50}")"
CLAIM_FILE_NODE="$(jget "$BY_FILE" 'j["items"][0]["id"]')"   # NOT $FILE_NODE: that is the binary node downloadBinary uses later
FILE_CLAIMS="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIM_FILE_NODE"),\"pageSize\":10}")" 'j["count"]')"
check "'claims from this file' returns both claims" "[ \"$FILE_CLAIMS\" = 2 ]"

HIGH="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"filter\":\"(paidAmount>=230)\",\"pageSize\":10}")" 'j["count"]')"
NONE="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"filter\":\"(paidAmount>=1000)\",\"pageSize\":10}")" 'j["count"]')"
DENIED="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"filter\":\"(claimStatus=1)\",\"pageSize\":10}")" 'j["count"]')"
check "(paidAmount>=230) matches only the 240.00 claim" "[ \"$HIGH\" = 1 ]"
check "(paidAmount>=1000) matches nothing" "[ \"$NONE\" = 0 ]"
check "(claimStatus=1) matches both" "[ \"$DENIED\" = 2 ]"
# lite-filter drives both filter paths, so the extensions work here as well as on /transactions
ENDS="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"filter\":\"(claimId:endsWith:0002)\",\"pageSize\":10}")" 'j["count"]')"
check "(claimId:endsWith:0002) — a lite-filter extension on a business collection" "[ \"$ENDS\" = 1 ]"
BYPAYER_F="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"filter\":\"(payerName=EXAMPLE HEALTH PLAN)\",\"pageSize\":10}")" 'j["count"]')"
check "a dimension is filterable like any column" "[ \"$BYPAYER_F\" = 2 ]"
SORTED_DESC="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"sortBy\":\"paidAmount\",\"sortDir\":\"desc\",\"pageSize\":10}")" 'j["items"][0]["claimId"]')"
SORTED_ASC="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":$(jstr "$CLAIMS"),\"sortBy\":\"paidAmount\",\"sortDir\":\"asc\",\"pageSize\":10}")" 'j["items"][0]["claimId"]')"
check "sortBy=paidAmount desc puts the 240.00 claim first" "[ \"$SORTED_DESC\" = CLM0002 ]"
check "sortBy=paidAmount asc puts the 220.00 claim first" "[ \"$SORTED_ASC\" = CLM0001 ]"
check "an unknown sortBy is rejected" "! x12_rpc CollectionsApi.getCollectionElements \"{\\\"objectId\\\":\\\"$X12_RECEIVER/claims\\\",\\\"sortBy\\\":\\\"nope\\\"}\" >/dev/null 2>&1"
TX_SORTED="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":\"$X12_RECEIVER/transactions\",\"sortBy\":\"receivedAt\",\"sortDir\":\"asc\",\"pageSize\":10}")" 'j["count"]')"
check "the structural path sorts too (ORDER BY in SQL)" "[ \"$TX_SORTED\" = 1 ]"
LINES="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":\"$X12_RECEIVER/service-lines\",\"pageSize\":10}")" 'j["count"]')"
check "/service-lines has its own grain: 3 lines" "[ \"$LINES\" = 3 ]"
REMITS="$(jget "$(x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":\"$X12_RECEIVER/remittances\",\"pageSize\":10}")" 'j["count"]')"
check "/remittances has one row per transaction set" "[ \"$REMITS\" = 1 ]"
check "an unknown filter column is rejected, not silently empty" "! x12_rpc CollectionsApi.getCollectionElements \"{\\\"objectId\\\":\\\"$X12_RECEIVER/claims\\\",\\\"filter\\\":\\\"(nope=1)\\\"}\" >/dev/null 2>&1"

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
check "transactions collection empty but the files row remains" "[ \"$(jget "$(x12_rpc ObjectsApi.getObject "{\"objectId\":\"$X12_RECEIVER/transactions\"}")" 'j["collectionSize"]')\" = 0 ] && [ \"$(jget "$(x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}")" 'j["count"]')\" = 1 ]"

exit 0
