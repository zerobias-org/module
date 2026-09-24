#!/usr/bin/env bash
#
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate; only e2e-local.sh sources it.
# Shared helpers for e2e-local.sh. Source it; do not run it.
# ─────────────────────────────────────────────────────────────────────────────
#
# Requires: docker (daemon up; used directly, or through `sg docker` when the
# current shell is not yet in the docker group), mvn, python3, curl, cmp.
# Building the jar needs GitHub Packages read access: set READ_TOKEN (or
# GITHUB_TOKEN) to a token with read:packages, and GITHUB_ACTOR to its owner
# (defaults to the `gh` login). ~/.m2/settings.xml must map server id `github` to
# those env vars (see CLAUDE.md). Extra maven flags go in MAVEN_ARGS (e.g. -o).

X12_MOD_DIR="${X12_MOD_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
X12_JAR="$X12_MOD_DIR/java/target/x12-receiver-1.0.0.jar"
X12_FIXTURE="$X12_MOD_DIR/java/src/test/resources/fixtures/835-005010X221A1.x12"
X12_OPS="${X12_OPS:-18888}"                  # host -> container 8888 (operations, nginx)
X12_API="http://localhost:$X12_OPS"
X12_CONN="${X12_CONN:-e2e}"
X12_RECEIVER="/x12-receiver"
X12_UID=10001                                # the image's unprivileged user (Dockerfile)

# docker, directly or via `sg docker` (a session that predates the group grant).
dk() {
  if docker info >/dev/null 2>&1; then
    docker "$@"
  else
    local q=""; local a
    for a in "$@"; do q+="$(printf '%q ' "$a")"; done
    sg docker -c "docker $q"
  fi
}

# MODULE_CONFIG for one inbox source. ackDurability is left out on purpose: the
# default (full) is what production runs. allowFileManagement is ON here so the scripted
# run can load data through the DataProducer API (upload/mkdir/delete under
# /x12-receiver/inbox) instead of reaching around the module to write the bind mount.
# It ships false in runtimeConfig.yml.
x12_module_config() {   # $1 = pollIntervalSec, $2 = stableForSec
  printf '{"sources":[{"name":"inbox","path":"/var/lib/x12/inbox","pattern":"*.{x12,edi,txt,835,837,277,999,dat}","pollIntervalSec":%s,"stableForSec":%s}],"consumedSuffix":".done","errorSuffix":".error","allowFileManagement":true}' \
    "${1:-2}" "${2:-1}"
}

x12_build_jar() {
  local token="${READ_TOKEN:-${GITHUB_TOKEN:-}}"
  if [ -z "$token" ]; then
    echo "READ_TOKEN (or GITHUB_TOKEN) is not set; export a token with read:packages, e.g. READ_TOKEN=\$(gh auth token)" >&2
    return 1
  fi
  (cd "$X12_MOD_DIR/java" && GITHUB_ACTOR="${GITHUB_ACTOR:-$(gh api user --jq .login 2>/dev/null || echo x)}" \
     READ_TOKEN="$token" GITHUB_TOKEN="$token" mvn -q -DskipTests package)
  [ -f "$X12_JAR" ] || { echo "missing $X12_JAR after build" >&2; return 1; }
}

x12_build_image() {   # $1 = tag
  dk build -q -t "$1" "$X12_MOD_DIR" >/dev/null
}

# x12_start NAME IMAGE INBOX_DIR BUFFER_DIR POLL STABLE
# Both host dirs must be writable by uid 10001 (the container user): the inbox for
# the .done/.error rename, the buffer for buffer.db.
x12_start() {
  local name="$1" image="$2" inbox="$3" buffer="$4" poll="${5:-2}" stable="${6:-1}"
  dk rm -f "$name" >/dev/null 2>&1 || true
  dk run -d --name "$name" \
    -e HUB_NODE_INSECURE=true \
    -e MODULE_CONFIG="$(x12_module_config "$poll" "$stable")" \
    -v "$inbox:/var/lib/x12/inbox" -v "$buffer:/var/lib/module" \
    -p "$X12_OPS:8888" "$image" >/dev/null
}

x12_wait_healthy() {   # $1 = attempts (1s apart)
  local i
  for i in $(seq 1 "${1:-60}"); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' -m2 "$X12_API/healthz" 2>/dev/null)" = "200" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# Wait for a detached container to exit; prints its exit code, or "running" on timeout.
x12_wait_exit() {   # $1 = name, $2 = attempts (1s apart)
  local i state
  for i in $(seq 1 "${2:-30}"); do
    state="$(dk inspect -f '{{.State.Status}} {{.State.ExitCode}}' "$1" 2>/dev/null)"
    case "$state" in
      exited*) echo "${state#exited }"; return 0 ;;
    esac
    sleep 1
  done
  echo running
}

# Remove the container. Files it wrote into the bind-mounted dirs belong to uid 10001;
# the host user owns the dirs themselves, so it can still delete them.
x12_stop() {   # $1 = name
  dk rm -f "$1" >/dev/null 2>&1 || true
}

x12_connect() {
  curl -fsS -m5 -X POST "$X12_API/connections" -H 'content-type: application/json' \
    -d "{\"connectionId\":\"$X12_CONN\"}"
}

# rpc ApiClass.method '<argMap JSON>'  -> response body (JSON); fails on a non-2xx status
x12_rpc() {
  curl -fsS -m10 -X POST "$X12_API/connections/$X12_CONN/$1" -H 'content-type: application/json' \
    -d "{\"argMap\":$2}"
}

# rpc_code ApiClass.method '<argMap JSON>' OUTFILE -> prints the HTTP status; body in OUTFILE
x12_rpc_code() {
  curl -sS -m10 -o "$3" -w '%{http_code}' -X POST "$X12_API/connections/$X12_CONN/$1" \
    -H 'content-type: application/json' -d "{\"argMap\":$2}"
}

# rpc_bin BinaryApi.downloadBinary '<argMap JSON>' OUTFILE
x12_rpc_bin() {
  curl -fsS -m10 -X POST "$X12_API/connections/$X12_CONN/$1" -H 'content-type: application/json' \
    -d "{\"argMap\":$2}" -o "$3"
}

# upload OBJECT_ID FILE_NAME LOCAL_FILE  -> the new file's object JSON
# The body is the raw bytes, so objectId/fileName ride on the query string (DESIGN §2.9).
x12_upload() {
  curl -fsS -m30 -X POST \
    "$X12_API/connections/$X12_CONN/BinaryApi.uploadBinaryContent?objectId=$(x12_urlenc "$1")&fileName=$(x12_urlenc "$2")" \
    -H 'content-type: application/octet-stream' --data-binary "@$3"
}

# mkdir PARENT_OBJECT_ID NAME  -> the new container's object JSON
x12_mkdir() {
  x12_rpc ObjectsApi.createChildObject \
    "{\"objectId\":$(jstr "$1"),\"createObjectRequest\":{\"name\":$(jstr "$2"),\"objectClass\":[\"container\"]}}"
}

# delete OBJECT_ID
x12_delete() {
  x12_rpc ObjectsApi.deleteObject "{\"objectId\":$(jstr "$1")}"
}

# is_supported OPERATION_ID -> "True"/"False"
x12_is_supported() {
  jget "$(curl -fsS -m5 "$X12_API/connections/$X12_CONN/isSupported/$1")" 'j["supported"]'
}

x12_urlenc() { python3 -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"; }

x12_fn() {   # $1 = function name, $2 = requestBody JSON
  x12_rpc "FunctionsApi.invokeFunction" "{\"objectId\":\"$X12_RECEIVER/ops/$1\",\"requestBody\":${2:-{\}}}"
}

# JSON helpers (python3 stdlib): jget JSON 'expr' evaluates `expr` against the parsed object `j`.
jget() { python3 -c 'import sys,json; j=json.loads(sys.argv[1]); print(eval(sys.argv[2]))' "$1" "$2"; }
jpretty() { python3 -m json.tool; }

# JSON-escape a string for embedding in a body.
jstr() { python3 -c 'import sys,json; print(json.dumps(sys.argv[1]))' "$1"; }

# sha256 of a file, hex.
sha256() { python3 -c 'import sys,hashlib; print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$1"; }
