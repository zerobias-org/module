#!/usr/bin/env bash
#
# Interactive playground for the HL7 v2 module: bring the listener up, send
# messages into it over MLLP, and inspect / drain the buffer through the
# DataProducer API. The container stays running until you `down` it.
#
#   java/scripts/hl7-live.sh up                 # build image + start the listener
#   java/scripts/hl7-live.sh send [FAMILY GIVEN] # send an ADT^A01 (unique id each time)
#   java/scripts/hl7-live.sh health             # /healthz (buffer depth, last received)
#   java/scripts/hl7-live.sh peek               # list buffered messages (typed JSON)
#   java/scripts/hl7-live.sh take               # lease un-acked messages (prints leaseId)
#   java/scripts/hl7-live.sh ack <leaseId>      # confirm (consume) a lease
#   java/scripts/hl7-live.sh purge              # delete acked rows
#   java/scripts/hl7-live.sh logs               # tail container logs
#   java/scripts/hl7-live.sh down               # stop + remove the container
#
set -euo pipefail
MOD_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE="hl7-v2-live:local"
NAME="hl7-v2-live"
OPS=18888
MLLP=12575
API="http://localhost:$OPS"
JAR="$MOD_DIR/java/target/hl7-listener-1.0.0.jar"

conn() { curl -fsS -X POST "$API/connections" -H 'content-type: application/json' -d '{"connectionId":"live"}' >/dev/null 2>&1 || true; }
rpc()  { curl -fsS -X POST "$API/connections/live/$1" -H 'content-type: application/json' -d "$2"; }

case "${1:-}" in
  up)
    [ -f "$JAR" ] || { echo "missing $JAR — run: (cd java && mvn -DskipTests package)"; exit 1; }
    echo "building image..."; docker build -q -t "$IMAGE" "$MOD_DIR" >/dev/null
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    docker run -d --name "$NAME" -e HUB_NODE_INSECURE=true -e LISTENER_PORT_MLLP=2575 \
      -p "$OPS:8888" -p "$MLLP:2575" "$IMAGE" >/dev/null
    printf "waiting for the listener"
    for i in $(seq 1 40); do curl -fsS -m2 "$API/healthz" >/dev/null 2>&1 && break; printf .; sleep 1; done
    echo; conn
    echo "LISTENER UP."
    echo "  MLLP (send HL7 here): localhost:$MLLP"
    echo "  Operations API:       $API"
    echo "  Try:  java/scripts/hl7-live.sh send SMITH JOHN   then   java/scripts/hl7-live.sh peek"
    ;;
  send)
    python3 - "$MLLP" "${2:-DOE}" "${3:-JANE}" <<'PY'
import socket, sys, time
port=int(sys.argv[1]); fam=sys.argv[2]; giv=sys.argv[3]
cid="MSG-"+str(int(time.time()*1000))
mrn=cid[-7:]
msg="\r".join([
  f"MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260601120000||ADT^A01^ADT_A01|{cid}|P|2.5.1",
  "EVN|A01|20260601120000",
  f"PID|1||{mrn}^^^EPIC^MR||{fam}^{giv}||19850315|F",
  "PV1|1|I"]) + "\r"
s=socket.create_connection(("127.0.0.1",port),timeout=8); s.settimeout(8)
s.sendall(b"\x0b"+msg.encode()+b"\x1c\x0d")
ack=s.recv(4096).strip(b"\x0b\x1c\x0d").decode(errors="replace"); s.close()
msa=next((x for x in ack.split("\r") if x.startswith("MSA")),"(no MSA)")
print(f"sent controlId={cid}  patient={fam}^{giv}  ->  {msa}")
PY
    ;;
  health) curl -fsS "$API/healthz" | python3 -m json.tool ;;
  peek)   conn; rpc "CollectionsApi.searchCollectionElements" '{"argMap":{"objectId":"/hl7-v2-receiver/messages"}}' | python3 -m json.tool ;;
  take)   conn; rpc "FunctionsApi.invokeFunction" '{"argMap":{"objectId":"/hl7-v2-receiver/ops/take","requestBody":{"max":50}}}' | python3 -m json.tool ;;
  ack)    conn; rpc "FunctionsApi.invokeFunction" "{\"argMap\":{\"objectId\":\"/hl7-v2-receiver/ops/ack\",\"requestBody\":{\"leaseId\":\"${2:?leaseId required}\"}}}"; echo ;;
  release) conn; rpc "FunctionsApi.invokeFunction" "{\"argMap\":{\"objectId\":\"/hl7-v2-receiver/ops/release\",\"requestBody\":{\"leaseId\":\"${2:?leaseId required}\"}}}"; echo ;;
  replay) conn; rpc "FunctionsApi.invokeFunction" '{"argMap":{"objectId":"/hl7-v2-receiver/ops/replay","requestBody":{}}}'; echo ;;
  purge)  conn; rpc "FunctionsApi.invokeFunction" '{"argMap":{"objectId":"/hl7-v2-receiver/ops/purge","requestBody":{}}}'; echo ;;
  logs)   docker logs --tail 40 "$NAME" ;;
  down)   docker rm -f "$NAME" >/dev/null 2>&1 && echo "stopped" || echo "not running" ;;
  *) sed -n '2,20p' "$0" ;;
esac
