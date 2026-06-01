#!/usr/bin/env bash
#
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate, and nothing references it.
# The standardized test surface is the JUnit suite under java/src/test, run by the
# real gate:  ./gradlew :hl7:v2:gate  (see CLAUDE.md). These scripts are optional
# conveniences for hand-exploration and are safe to delete.
# ─────────────────────────────────────────────────────────────────────────────
#
# Standalone end-to-end test (Phase 11): runs the REAL module container, sends a
# real HL7 v2 message into the MLLP listener over the wire, then drives the
# DataProducer API to watch the full path — receive -> ack-on-persist ->
# materialize -> browse -> lease/take -> ack -> purge.
#
# Simulates the Hub Node by hand (docker run with the listener port injected).
# Requires: docker (daemon up), python3, curl, and the built uber jar at
# java/target/hl7-listener-1.0.0.jar (run `mvn -DskipTests package` in java/ first,
# or let CI/the gate build it).
#
set -euo pipefail

MOD_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE="hl7-v2-e2e:local"
NAME="hl7-v2-e2e"
OPS=18888            # host -> container 8888 (operations, nginx)
MLLP=12575           # host -> container 2575 (MLLP listener)
JAR="$MOD_DIR/java/target/hl7-listener-1.0.0.jar"

step() { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

[ -f "$JAR" ] || { echo "missing $JAR — build it first: (cd java && mvn -DskipTests package)"; exit 1; }

step "build image"
docker build -q -t "$IMAGE" "$MOD_DIR" >/dev/null && echo "built $IMAGE"

step "start container (MLLP listener on :$MLLP, operations on :$OPS)"
cleanup
docker run -d --name "$NAME" \
  -e HUB_NODE_INSECURE=true -e LISTENER_PORT_MLLP=2575 \
  -p "$OPS:8888" -p "$MLLP:2575" "$IMAGE" >/dev/null
for i in $(seq 1 40); do
  curl -fsS -m2 "http://localhost:$OPS/healthz" >/dev/null 2>&1 && break; sleep 1
done
echo "healthz: $(curl -fsS "http://localhost:$OPS/healthz")"

step "send a real ADT^A01 into the MLLP listener (over TCP, MLLP-framed)"
python3 - "$MLLP" <<'PY'
import socket, sys
port = int(sys.argv[1])
msg = "\r".join([
    "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260601120000||ADT^A01^ADT_A01|MSG-E2E-1|P|2.5.1",
    "EVN|A01|20260601120000",
    "PID|1||5551212^^^EPIC^MR||DOE^JANE||19850315|F",
    "PV1|1|I",
]) + "\r"
framed = b"\x0b" + msg.encode() + b"\x1c\x0d"          # MLLP: <VT> ... <FS><CR>
s = socket.create_connection(("localhost", port), timeout=5)
s.sendall(framed)
ack = s.recv(4096).strip(b"\x0b\x1c\x0d").decode(errors="replace")
s.close()
msa = next((seg for seg in ack.split("\r") if seg.startswith("MSA")), "")
print("  ACK:", msa, " <- AA = accepted & durably persisted")
PY

API="http://localhost:$OPS"
post() { curl -fsS -m5 -X POST "$API/connections/t/$1" -H 'content-type: application/json' -d "$2"; }

step "connect (DataProducer handshake)"
curl -fsS -m5 -X POST "$API/connections" -H 'content-type: application/json' -d '{"connectionId":"t"}'; echo

step "browse: searchCollectionElements on /messages (the materialized JSON)"
post "CollectionsApi.searchCollectionElements" '{"argMap":{"objectId":"/hl7-v2-receiver/messages"}}' \
  | python3 -m json.tool

step "health now shows the buffered message"
curl -fsS "$API/healthz" | python3 -m json.tool

step "drain: ops/take (lease it)"
TAKE=$(post "FunctionsApi.invokeFunction" '{"argMap":{"objectId":"/hl7-v2-receiver/ops/take","requestBody":{"max":10}}}')
echo "$TAKE" | python3 -m json.tool
LEASE=$(echo "$TAKE" | python3 -c "import sys,json; print(json.load(sys.stdin)['leaseId'])")

step "drain: ops/ack (confirm consumed)  leaseId=$LEASE"
post "FunctionsApi.invokeFunction" "{\"argMap\":{\"objectId\":\"/hl7-v2-receiver/ops/ack\",\"requestBody\":{\"leaseId\":\"$LEASE\"}}}"; echo

step "purge acked + final depth"
post "FunctionsApi.invokeFunction" '{"argMap":{"objectId":"/hl7-v2-receiver/ops/purge","requestBody":{}}}'; echo
curl -fsS "$API/healthz" | python3 -m json.tool

echo; echo "E2E complete."
