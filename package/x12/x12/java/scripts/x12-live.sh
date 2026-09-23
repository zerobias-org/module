#!/usr/bin/env bash
#
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate, and nothing references it.
# The standardized test surface is the JUnit suite under java/src/test, run by the
# real gate:  ./gradlew :x12:x12:gate  (see CLAUDE.md). This script is an optional
# convenience for hand-exploration and is safe to delete.
# ─────────────────────────────────────────────────────────────────────────────
#
# Interactive playground for the X12 module: bring the receiver up on a scratch
# inbox, drop files into it, and inspect / drain the buffer through the
# DataProducer API. The container stays running until you `down` it.
#
#   java/scripts/x12-live.sh up               # build jar + image, start the receiver
#   java/scripts/x12-live.sh drop <file>      # copy an X12 file into the watched inbox
#   java/scripts/x12-live.sh inbox            # list the inbox (.done / .error state)
#   java/scripts/x12-live.sh health           # /healthz (buffer depth, sources)
#   java/scripts/x12-live.sh files            # /x12-receiver/files (one node per file)
#   java/scripts/x12-live.sh peek             # /x12-receiver/transactions (typed JSON)
#   java/scripts/x12-live.sh take             # lease un-acked transactions (prints leaseId)
#   java/scripts/x12-live.sh ack <leaseId>    # confirm (consume) a lease
#   java/scripts/x12-live.sh release <leaseId>
#   java/scripts/x12-live.sh purge            # delete acked rows
#   java/scripts/x12-live.sh rescan           # force an immediate poll
#   java/scripts/x12-live.sh logs             # tail container logs
#   java/scripts/x12-live.sh down             # stop + remove the container (inbox kept)
#
# State: $X12_LIVE_DIR (default ~/.cache/x12-live) holds inbox/ and buffer/.
#
set -euo pipefail

# shellcheck source=x12-common.sh
. "$(dirname "$0")/x12-common.sh"

IMAGE="module-x12-x12:live"
NAME="x12-live"
X12_CONN="live"
LIVE_DIR="${X12_LIVE_DIR:-$HOME/.cache/x12-live}"
INBOX="$LIVE_DIR/inbox"; BUFFER="$LIVE_DIR/buffer"

conn() { x12_connect >/dev/null 2>&1 || true; }

case "${1:-}" in
  up)
    mkdir -p "$INBOX" "$BUFFER"; chmod 777 "$INBOX" "$BUFFER"
    echo "building jar..."; x12_build_jar
    echo "building image..."; x12_build_image "$IMAGE"
    x12_start "$NAME" "$IMAGE" "$INBOX" "$BUFFER" 2 1
    printf "waiting for the receiver"
    x12_wait_healthy 60 || { echo; echo "receiver did not become healthy:"; dk logs "$NAME" | tail -40; exit 1; }
    echo; conn
    echo "RECEIVER UP."
    echo "  Inbox (drop X12 here): $INBOX"
    echo "  Operations API:        $X12_API"
    echo "  Try:  java/scripts/x12-live.sh drop java/src/test/resources/fixtures/835-005010X221A1.x12   then   java/scripts/x12-live.sh peek"
    ;;
  drop)
    f="${2:?file required}"; [ -f "$f" ] || { echo "no such file: $f"; exit 1; }
    cp "$f" "$INBOX/$(basename "$f")"
    echo "dropped $(basename "$f") into $INBOX (picked up within ~3s; see 'inbox')"
    ;;
  inbox)  ls -la "$INBOX" ;;
  health) curl -fsS "$X12_API/healthz" | jpretty ;;
  files)  conn; x12_rpc ObjectsApi.getChildren "{\"objectId\":\"$X12_RECEIVER/files\"}" | jpretty ;;
  peek)   conn; x12_rpc CollectionsApi.getCollectionElements "{\"objectId\":\"$X12_RECEIVER/transactions\",\"pageSize\":50,\"pageNumber\":1}" | jpretty ;;
  take)   conn; x12_fn take '{"max":50}' | jpretty ;;
  ack)    conn; x12_fn ack "{\"leaseId\":$(jstr "${2:?leaseId required}")}"; echo ;;
  release) conn; x12_fn release "{\"leaseId\":$(jstr "${2:?leaseId required}")}"; echo ;;
  replay) conn; x12_fn replay '{}'; echo ;;
  purge)  conn; x12_fn purge '{}'; echo ;;
  rescan) conn; x12_fn rescan '{}'; echo ;;
  logs)   dk logs --tail 40 "$NAME" ;;
  down)   x12_stop "$NAME" && echo "stopped (inbox kept at $INBOX)" ;;
  *) sed -n '2,28p' "$0" ;;
esac
