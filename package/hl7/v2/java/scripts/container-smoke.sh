#!/usr/bin/env bash
#
# Container plumbing smoke test (Phase 10). Validates the Dockerfile + startup.sh
# + nginx.conf wiring WITHOUT the real application jar: it builds the image with a
# tiny stub jar (JDK HttpServer that serves /healthz on INTERNAL_PORT and binds
# LISTENER_PORT_MLLP), then asserts the two container-level contracts:
#
#   A. no LISTENER_PORT_MLLP            -> container refuses to start (exit 1, DESIGN §3.2)
#   B. with port + HUB_NODE_INSECURE    -> nginx fronts 8888 -> 8889, /healthz is 200
#
# The real uber jar (Javalin + HAPI + …) comes from the maven/gradle build; this
# only exercises the container scaffolding. Requires docker + a JDK.
#
set -euo pipefail

MOD_DIR="$(cd "$(dirname "$0")/../.." && pwd)"      # package/hl7/v2
JAVA_DIR="$MOD_DIR/java"
IMAGE="hl7-v2-smoke:local"
JAR="$JAVA_DIR/target/hl7-listener-1.0.0.jar"
CID=""

cleanup() {
  [ -n "$CID" ] && docker rm -f "$CID" >/dev/null 2>&1 || true
  docker rmi "$IMAGE" >/dev/null 2>&1 || true
  rm -f "$JAR"
}
trap cleanup EXIT

echo "==> building stub jar (plumbing stand-in for the real app)"
STUB="$(mktemp -d)"
cat > "$STUB/StubServer.java" <<'JAVA'
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
public class StubServer {
  public static void main(String[] args) throws Exception {
    int http = Integer.parseInt(System.getenv().getOrDefault("INTERNAL_PORT", "8889"));
    int mllp = Integer.parseInt(System.getenv().getOrDefault("LISTENER_PORT_MLLP", "2575"));
    ServerSocket mllpSock = new ServerSocket(mllp);          // mimic the MLLP listener bind
    HttpServer s = HttpServer.create(new InetSocketAddress(http), 0);
    s.createContext("/healthz", ex -> {
      byte[] b = "{\"listener\":{\"up\":true},\"stub\":true}".getBytes();
      ex.sendResponseHeaders(200, b.length);
      ex.getResponseBody().write(b);
      ex.close();
    });
    s.start();
    System.out.println("stub up: http=" + http + " mllp=" + mllp);
    Thread.currentThread().join();
  }
}
JAVA
mkdir -p "$JAVA_DIR/target"
javac -d "$STUB" "$STUB/StubServer.java"
jar --create --file "$JAR" --main-class StubServer -C "$STUB" .

echo "==> docker build"
docker build -q -t "$IMAGE" "$MOD_DIR" >/dev/null
echo "    built $IMAGE"

echo "==> check A: refuses to boot without LISTENER_PORT_MLLP"
set +e
OUT_A="$(docker run --rm "$IMAGE" 2>&1)"; RC_A=$?
set -e
if [ "$RC_A" -ne 0 ] && echo "$OUT_A" | grep -q "LISTENER_PORT_MLLP is not set"; then
  echo "    PASS (exit $RC_A, refused as designed)"
else
  echo "    FAIL: expected non-zero exit + refusal message; got exit $RC_A:"; echo "$OUT_A"; exit 1
fi

echo "==> check B: boots with the port, nginx proxies /healthz (HTTP/insecure mode)"
CID="$(docker run -d -e LISTENER_PORT_MLLP=2575 -e HUB_NODE_INSECURE=true -p 18888:8888 "$IMAGE")"
# poll for readiness (nginx + stub up)
ok=""
for i in $(seq 1 20); do
  if curl -fsS -m 2 http://localhost:18888/healthz >/dev/null 2>&1; then ok=1; break; fi
  sleep 0.5
done
BODY="$(curl -fsS -m 2 http://localhost:18888/healthz 2>/dev/null || true)"
if [ -n "$ok" ] && echo "$BODY" | grep -q '"up":true'; then
  echo "    PASS  GET /healthz (via nginx 8888 -> java 8889) -> $BODY"
else
  echo "    FAIL: /healthz not served through nginx"; echo "    --- container logs ---"; docker logs "$CID" 2>&1 | tail -20; exit 1
fi

echo "==> all container plumbing checks passed"
