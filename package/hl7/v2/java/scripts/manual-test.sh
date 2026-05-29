#!/usr/bin/env bash
#
# Manual test harness for the HL7 v2 buffer (Phase 2), runnable without the
# project's gradle/maven toolchain. Fetches the public deps it needs (cached),
# compiles the buffer, and either runs the JUnit suite or an interactive demo.
#
# Usage:
#   java/scripts/manual-test.sh test    # run BufferStoreTest (the automated suite)
#   java/scripts/manual-test.sh demo    # insert/take/ack a few messages, leave a buffer.db
#
# After 'demo', inspect the database by hand:
#   sqlite3 /tmp/hl7-demo/buffer.db 'SELECT control_id,status,lease_id FROM messages;'
#
set -euo pipefail

MODE="${1:-demo}"
JAVA_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LIB="${HL7_LIB:-/tmp/hl7-validate/lib}"
OUT="$(mktemp -d)"
RES="$JAVA_DIR/src/main/resources"
BASE="https://repo1.maven.org/maven2"

mkdir -p "$LIB"
fetch() {  # fetch <maven/path/artifact.jar>
  local f="$LIB/$(basename "$1")"
  [ -f "$f" ] || { echo "downloading $(basename "$1")..."; curl -fsS -m 120 -o "$f" "$BASE/$1"; }
}
fetch org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar
fetch org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar
fetch org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar

CP="$(ls "$LIB"/sqlite-jdbc-*.jar "$LIB"/slf4j-api-*.jar | tr '\n' ':')"

echo "compiling buffer..."
javac -cp "$CP" -d "$OUT" "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/buffer/*.java

case "$MODE" in
  test)
    JUNIT="$(ls "$LIB"/junit-platform-console-standalone-*.jar)"
    javac -cp "${CP}${JUNIT}:${OUT}" -d "$OUT" \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/buffer/BufferStoreTest.java
    echo "running BufferStoreTest..."
    java -jar "$JUNIT" execute \
      -cp "${CP}${OUT}:${RES}" \
      --select-class=com.zerobias.module.hl7.buffer.BufferStoreTest --details=tree \
      2>&1 | grep -vE 'Thanks for using|sponsoring|SLF4J|^$'
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
      System.out.println("   A again -> "+s.insert(row("A",0,schema))+"   (false = duplicate dropped, sender still got an ack)");
      counts(s);
      System.out.println("\n2) take(2): the platform leases the 2 oldest; they become in_flight:");
      Lease lease = s.take(null, 2, Duration.ofMinutes(5));
      System.out.println("   leaseId = "+lease.leaseId());
      System.out.println("   got = "+lease.messages().stream().map(BufferRow::controlId).toList()
        +"   remaining backlog = "+lease.remaining());
      counts(s);
      System.out.println("\n3) ack ONLY 'A' (partial ack); B stays in_flight, C still new:");
      System.out.println("   rows acked = "+s.ack(lease.leaseId(), List.of("A")));
      counts(s);
      System.out.println("\nDB written to "+db+" — inspect it with sqlite3 (see below).");
    }
  }
}
JAVA
    javac -cp "$CP$OUT" -d "$OUT" "$OUT/Demo.java"
    echo; echo "running demo..."; echo
    java -cp "${CP}${OUT}:${RES}" Demo "$DB"
    echo
    echo "now inspect the real database:"
    echo "  sqlite3 $DB 'SELECT control_id, status, lease_id FROM messages ORDER BY received_at;'"
    ;;
  *)
    echo "usage: $0 [test|demo]" >&2; exit 2 ;;
esac
