#!/usr/bin/env bash
#
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate, and nothing references it.
# Shared helpers for e2e-local.sh (scripted PASS/FAIL run) and x12-live.sh
# (interactive playground). Source it; do not run it.
# ─────────────────────────────────────────────────────────────────────────────
#
# Requires: docker (daemon up; used directly, or through `sg docker` when the
# current shell is not yet in the docker group), python3, curl, cmp.

X12_MOD_DIR="${X12_MOD_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
X12_JAR="$X12_MOD_DIR/java/target/x12-receiver-1.0.0.jar"
X12_FIXTURE="$X12_MOD_DIR/java/src/test/resources/fixtures/835-005010X221A1.x12"
X12_OPS="${X12_OPS:-18888}"                  # host -> container 8888 (operations, nginx)
X12_API="http://localhost:$X12_OPS"
X12_CONN="${X12_CONN:-e2e}"
X12_RECEIVER="/x12-receiver"

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

# allowFileManagement is ON here so the scripted run can load data through the
# DataProducer API (upload/mkdir/delete under /x12-receiver/inbox) instead of reaching
# around the module to write the bind mount. It ships false in runtimeConfig.yml.
x12_module_config() {   # $1 = pollIntervalSec, $2 = stableForSec
  printf '{"sources":[{"name":"inbox","path":"/var/lib/x12/inbox","pattern":"*.{x12,edi,txt,835,837,277,999,dat}","pollIntervalSec":%s,"stableForSec":%s}],"consumedSuffix":".done","errorSuffix":".error","ackDurability":"normal","allowFileManagement":true}' \
    "${1:-2}" "${2:-1}"
}

x12_build_jar() {
  (cd "$X12_MOD_DIR/java" && GITHUB_ACTOR="${GITHUB_ACTOR:-$(gh api user --jq .login 2>/dev/null || echo x)}" \
     READ_TOKEN="${READ_TOKEN:-$(gh auth token 2>/dev/null || true)}" mvn -q -DskipTests package)
  [ -f "$X12_JAR" ] || { echo "missing $X12_JAR after build" >&2; return 1; }
}

x12_build_image() {   # $1 = tag
  dk build -q -t "$1" "$X12_MOD_DIR" >/dev/null
}

# Create the dirs bind-mounted into the container, world-writable. The image may run as an
# unprivileged uid (10001) that does not own a host dir, and the receiver must write and
# rename in both; 0777 is also what lets the host user clean up afterwards without chown.
x12_mkmounts() {   # dirs...
  mkdir -p "$@" && chmod 0777 "$@"
}

# x12_start NAME IMAGE INBOX_DIR BUFFER_DIR POLL STABLE
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

# Stop and remove the container. Nothing is chowned back: the bind-mounted dirs are 0777
# (x12_mkmounts), and unlinking an entry needs write permission on its directory, not
# ownership of the file, so the host user can delete what the container created in them.
x12_stop() {   # $1 = name
  dk rm -f "$1" >/dev/null 2>&1 || true
}

# rm -rf a scratch tree the container wrote into; call after x12_stop. A subdirectory the
# receiver created (mkdir through the API) belongs to the container's uid, and its entries
# are not the host user's to unlink. Those are removed by a throwaway container running as
# the image's own user — the uid that created them — never by exec into the module
# container and never by a user the module did not already run as.
x12_rm_scratch() {   # $1 = dir, $2 = image
  local dir="$1" image="$2" sub
  rm -rf "$dir" 2>/dev/null && return 0
  for sub in "$dir"/*/; do
    [ -d "$sub" ] || continue
    dk run --rm -v "${sub%/}:/x12-scratch" "$image" \
      sh -c 'rm -rf /x12-scratch/* /x12-scratch/.[!.]* 2>/dev/null; exit 0' >/dev/null 2>&1 || true
  done
  rm -rf "$dir" 2>/dev/null || echo "left behind (not removable by $(id -un)): $dir" >&2
}

x12_connect() {
  curl -fsS -m5 -X POST "$X12_API/connections" -H 'content-type: application/json' \
    -d "{\"connectionId\":\"$X12_CONN\"}"
}

# rpc ApiClass.method '<argMap JSON>'  -> response body (JSON)
x12_rpc() {
  curl -fsS -m10 -X POST "$X12_API/connections/$X12_CONN/$1" -H 'content-type: application/json' \
    -d "{\"argMap\":$2}"
}

# rpc_code ApiClass.method '<argMap JSON>'  -> the body, then the HTTP status on its own last
# line. No -f: for a refusal the status and the error body are what is being checked.
x12_rpc_code() {
  curl -sS -m10 -X POST "$X12_API/connections/$X12_CONN/$1" -H 'content-type: application/json' \
    -d "{\"argMap\":$2}" -w '\n%{http_code}'
}

# rpc_bin ApiClass.downloadBinary '<argMap JSON>' OUTFILE
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

# Does a raw response contain this literal substring? Use this instead of interpolating a
# JSON body into an eval'd `check` condition: the body's own quotes break the quoting, and
# `jget` cannot help because json.loads normalises 220.00 to a float.
x12_contains() {   # $1 = haystack, $2 = literal needle -> "yes"/"no"
  case "$1" in
    *"$2"*) echo yes ;;
    *) echo no ;;
  esac
}

x12_urlenc() { python3 -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"; }

x12_fn() {   # $1 = function name, $2 = requestBody JSON
  x12_rpc "FunctionsApi.invokeFunction" "{\"objectId\":\"$X12_RECEIVER/ops/$1\",\"requestBody\":${2:-{\}}}"
}

# The 835 fixture as interchange ISA13=$1, GS06/ST02 unchanged — the same renumbering as
# InboxPollerTest.interchange835, so two of them in one file reuse their group and set numbers.
x12_interchange835() {   # $1 = ISA13
  python3 -c 'import sys
t = open(sys.argv[1]).read().strip()
print(t.replace("*000000101*0*T*", "*" + sys.argv[2] + "*0*T*").replace("IEA*1*000000101~", "IEA*1*" + sys.argv[2] + "~"))' \
    "$X12_FIXTURE" "$1"
}

# JSON helpers (python3 stdlib): jget JSON 'expr' evaluates `expr` against the parsed object `j`.
jget() { python3 -c 'import sys,json; j=json.loads(sys.argv[1]); print(eval(sys.argv[2]))' "$1" "$2"; }
jpretty() { python3 -m json.tool; }

# JSON-escape a string for embedding in a body.
jstr() { python3 -c 'import sys,json; print(json.dumps(sys.argv[1]))' "$1"; }
