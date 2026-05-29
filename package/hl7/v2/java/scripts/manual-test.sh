#!/usr/bin/env bash
#
# Manual test harness for the HL7 v2 module (Phases 2-4), runnable without the
# project's gradle/maven toolchain. Fetches the public deps it needs (cached),
# compiles the buffer + materializer + listener, and runs tests or a demo.
#
# Usage:
#   java/scripts/manual-test.sh test       # run the JUnit suite (buffer + listener + normalizer)
#   java/scripts/manual-test.sh demo       # buffer-only: insert/take/ack, leave a buffer.db
#   java/scripts/manual-test.sh listener   # start the MLLP listener, send a real HL7 message
#
# After demo/listener, inspect the database by hand:
#   sqlite3 /tmp/hl7-demo/buffer.db 'SELECT control_id,status,mapped_json FROM messages;'
#
set -euo pipefail

MODE="${1:-demo}"
JAVA_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LIB="${HL7_LIB:-/tmp/hl7-validate/lib}"
OUT="$(mktemp -d)"
RES="$JAVA_DIR/src/main/resources"
BASE="https://repo1.maven.org/maven2"

mkdir -p "$LIB"
fetch() {
  local f="$LIB/$(basename "$1")"
  [ -f "$f" ] || { echo "downloading $(basename "$1")..."; curl -fsS -m 120 -o "$f" "$BASE/$1"; }
}
fetch org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar
fetch org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar
fetch com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
fetch ca/uhn/hapi/hapi-base/2.5.1/hapi-base-2.5.1.jar
fetch ca/uhn/hapi/hapi-structures-v251/2.5.1/hapi-structures-v251-2.5.1.jar
fetch org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar

CP="$(ls "$LIB"/*.jar | grep -v junit-platform | tr '\n' ':')"
JUNIT="$(ls "$LIB"/junit-platform-console-standalone-*.jar)"

echo "compiling buffer + materializer + listener..."
javac -cp "$CP" -d "$OUT" \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/buffer/*.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/materializer/Hl7Normalizer.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/materializer/MessageMaterializer.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/materializer/EnvelopeMaterializer.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/listener/*.java

run_junit() {  # run_junit <fully.qualified.ClassName>...
  local sel=(); for c in "$@"; do sel+=(--select-class="$c"); done
  java -jar "$JUNIT" execute -cp "${CP}${OUT}:${RES}" "${sel[@]}" --details=tree \
    2>&1 | grep -vE 'Thanks for using|sponsoring|SLF4J|^$'
}

case "$MODE" in
  test)
    javac -cp "${CP}${JUNIT}:${OUT}" -d "$OUT" \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/buffer/BufferStoreTest.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/materializer/Hl7NormalizerTest.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/listener/Hl7ListenerIT.java
    echo "running tests..."
    run_junit com.zerobias.module.hl7.buffer.BufferStoreTest \
              com.zerobias.module.hl7.materializer.Hl7NormalizerTest \
              com.zerobias.module.hl7.listener.Hl7ListenerIT
    ;;
  demo)
    DB="/tmp/hl7-demo/buffer.db"; rm -f /tmp/hl7-demo/buffer.db*; mkdir -p /tmp/hl7-demo
    cat > "$OUT/Demo.java" <<'JAVA'
import com.zerobias.module.hl7.buffer.*;
import java.time.*; import java.util.*;
public class Demo {
  static BufferRow row(String cid, long off, String schema) {
    return new BufferRow(0, Instant.parse("2026-05-29T00:00:00Z").plusSeconds(off), cid,
      "ADT_A01","ADT","A01","EPIC","HOSP","2.5.1", schema,
      ("MSH|^~\\&|EPIC|HOSP|||ADT^A01|"+cid).getBytes(),
      "{\"controlId\":\""+cid+"\",\"patientName\":\"SMITH^JOHN\"}",
      MessageStatus.NEW, null, null, null);
  }
  static void counts(BufferStore s) throws Exception {
    System.out.printf("   total=%d  new=%d  in_flight=%d  acked=%d%n",
      s.count(), s.count(MessageStatus.NEW), s.count(MessageStatus.IN_FLIGHT), s.count(MessageStatus.ACKED));
  }
  public static void main(String[] a) throws Exception {
    String db = a[0]; String schema = "schema:table:hl7v2.v251.ADT_A01";
    try (BufferStore s = new BufferStore(db, false)) {
      System.out.println("1) insert A,B,C  then insert A again (a duplicate re-send):");
      System.out.println("   A -> "+s.insert(row("A",0,schema))+"   B -> "+s.insert(row("B",1,schema))
        +"   C -> "+s.insert(row("C",2,schema)));
      System.out.println("   A again -> "+s.insert(row("A",0,schema))+"   (false = duplicate dropped)");
      counts(s);
      System.out.println("\n2) take(2): lease the 2 oldest; they become in_flight:");
      Lease lease = s.take(null, 2, Duration.ofMinutes(5));
      System.out.println("   got = "+lease.messages().stream().map(BufferRow::controlId).toList()
        +"   remaining = "+lease.remaining());
      counts(s);
      System.out.println("\n3) ack ONLY 'A' (partial ack):");
      System.out.println("   rows acked = "+s.ack(lease.leaseId(), List.of("A")));
      counts(s);
      System.out.println("\nDB written to "+db);
    }
  }
}
JAVA
    javac -cp "$CP$OUT" -d "$OUT" "$OUT/Demo.java"
    echo; echo "running buffer demo..."; echo
    java -cp "${CP}${OUT}:${RES}" Demo "$DB"
    echo; echo "inspect: sqlite3 $DB 'SELECT control_id, status, lease_id FROM messages ORDER BY received_at;'"
    ;;
  listener)
    DB="/tmp/hl7-demo/buffer.db"; rm -f /tmp/hl7-demo/buffer.db*; mkdir -p /tmp/hl7-demo
    cat > "$OUT/LDemo.java" <<'JAVA'
import com.zerobias.module.hl7.buffer.*;
import com.zerobias.module.hl7.listener.*;
import com.zerobias.module.hl7.materializer.*;
import ca.uhn.hl7v2.*; import ca.uhn.hl7v2.app.Connection; import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import java.net.ServerSocket; import java.time.Duration;
public class LDemo {
  static int freePort() throws Exception { try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); } }
  public static void main(String[] a) throws Exception {
    String db = a[0]; int port = freePort();
    String er7 = String.join("\r",
      "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260529103000||ADT^A01^ADT_A01|MSG00042|P|2.5.1",
      "EVN|A01|20260529103000",
      "PID|1||5551212^^^EPIC^MR||SMITH^JOHN||19800101|M",
      "PV1|1|I") + "\r";
    try (BufferStore buffer = new BufferStore(db, false);
         Hl7ListenerService listener = new Hl7ListenerService(
             port, new BufferingApp(buffer, new EnvelopeMaterializer(), "v251"));
         HapiContext client = new DefaultHapiContext()) {
      listener.start();
      System.out.println("MLLP listener started on 127.0.0.1:" + port);
      Connection c = client.newClient("localhost", port, false);
      System.out.println("\n--> sending an ADT^A01 (patient admit) over MLLP ...");
      Message ack = c.getInitiator().sendAndReceive(client.getPipeParser().parse(er7));
      Terser at = new Terser(ack);
      System.out.println("<-- ACK  MSA-1=" + at.get("/MSA-1") + "  (AA = accepted & persisted)"
        + "   MSA-2=" + at.get("/MSA-2") + "  (echoes the message control id)");
      System.out.println("\nbuffered count = " + buffer.count());
      Lease lease = buffer.take(null, 1, Duration.ofMinutes(5));
      BufferRow r = lease.messages().get(0);
      System.out.println("stored row:");
      System.out.println("  controlId  = " + r.controlId());
      System.out.println("  structure  = " + r.messageStructure());
      System.out.println("  schemaId   = " + r.schemaId());
      System.out.println("  mappedJson = " + r.mappedJson());
      buffer.release(lease.leaseId(), null);
      c.close();
      System.out.println("\nDB written to " + db);
    }
  }
}
JAVA
    javac -cp "$CP$OUT" -d "$OUT" "$OUT/LDemo.java"
    echo; echo "running listener demo..."; echo
    java -cp "${CP}${OUT}:${RES}" LDemo "$DB" 2>&1 | grep -v SLF4J
    echo; echo "inspect: sqlite3 $DB 'SELECT control_id, status, mapped_json FROM messages;'"
    ;;
  *)
    echo "usage: $0 [test|demo|listener]" >&2; exit 2 ;;
esac
