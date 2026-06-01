#!/usr/bin/env bash
#
# Manual test harness for the HL7 v2 module (Phases 2-4), runnable without the
# project's gradle/maven toolchain. Fetches the public deps it needs (cached),
# compiles the buffer + materializer + listener, and runs tests or a demo.
#
# Usage:
#   java/scripts/manual-test.sh test       # run the JUnit suite (buffer + listener + normalizer + filter)
#   java/scripts/manual-test.sh demo       # buffer-only: insert/take/ack, leave a buffer.db
#   java/scripts/manual-test.sh listener   # start the MLLP listener, send a real HL7 message
#   java/scripts/manual-test.sh filter     # show RFC4515 filters -> SQLite WHERE -> matching rows
#   java/scripts/manual-test.sh producer   # drive the DataProducer read ops over a seeded buffer
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
LITEFILTER_SRC="$JAVA_DIR/../../../../../util/packages/lite-filter/java/src/main/java"

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
fetch me/xdrop/fuzzywuzzy/1.4.0/fuzzywuzzy-1.4.0.jar
fetch org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar

CP="$(ls "$LIB"/*.jar | grep -v junit-platform | tr '\n' ':')"
JUNIT="$(ls "$LIB"/junit-platform-console-standalone-*.jar)"

echo "compiling lite-filter (from source) + buffer + materializer + listener + filter..."
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
  "$LITEFILTER_SRC"/com/zerobias/litefilter/*.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/buffer/*.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/materializer/*.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/listener/*.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/filter/*.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/producer/*.java \
  "$JAVA_DIR"/src/main/java/com/zerobias/module/hl7/health/*.java

run_junit() {  # run_junit <fully.qualified.ClassName>...
  local sel=(); for c in "$@"; do sel+=(--select-class="$c"); done
  java -jar "$JUNIT" execute -cp "${CP}${OUT}:${RES}" "${sel[@]}" --details=tree \
    2>&1 | grep -vE 'Thanks for using|sponsoring|SLF4J|^$'
}

case "$MODE" in
  test)
    # Generate the REAL structure index via the codegen (exactly as the build does),
    # so MaterializerIT validates against it. Codegen needs hapi-structures-v251 (in CP).
    GENO="$(mktemp -d)"; export HL7_INDEX_DIR="$(mktemp -d)"
    echo "generating structure index (codegen)..."
    javac -encoding UTF-8 -cp "$CP" -d "$GENO" \
      "$JAVA_DIR"/codegen/src/main/java/com/zerobias/module/hl7/codegen/model/*.java \
      "$JAVA_DIR"/codegen/src/main/java/com/zerobias/module/hl7/codegen/*.java
    java -cp "${CP}${GENO}" com.zerobias.module.hl7.codegen.SchemaGenerator \
      v251 "$HL7_INDEX_DIR" ADT_A01 ORU_R01 2>&1 | grep -E 'Generated' || true
    javac -encoding UTF-8 -cp "${CP}${JUNIT}:${OUT}" -d "$OUT" \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/buffer/BufferStoreTest.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/materializer/Hl7NormalizerTest.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/materializer/MaterializerIT.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/listener/Hl7ListenerIT.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/filter/Hl7SqlAdapterIT.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/producer/Hl7ProducerIT.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/producer/Hl7OperationsIT.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/health/HealthCheckTest.java \
      "$JAVA_DIR"/src/test/java/com/zerobias/module/hl7/health/HealthSelfTestIT.java
    echo "running tests..."
    run_junit com.zerobias.module.hl7.buffer.BufferStoreTest \
              com.zerobias.module.hl7.materializer.Hl7NormalizerTest \
              com.zerobias.module.hl7.materializer.MaterializerIT \
              com.zerobias.module.hl7.listener.Hl7ListenerIT \
              com.zerobias.module.hl7.filter.Hl7SqlAdapterIT \
              com.zerobias.module.hl7.producer.Hl7ProducerIT \
              com.zerobias.module.hl7.producer.Hl7OperationsIT \
              com.zerobias.module.hl7.health.HealthCheckTest \
              com.zerobias.module.hl7.health.HealthSelfTestIT
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
    javac -encoding UTF-8 -cp "$CP$OUT" -d "$OUT" "$OUT/Demo.java"
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
    javac -encoding UTF-8 -cp "$CP$OUT" -d "$OUT" "$OUT/LDemo.java"
    echo; echo "running listener demo..."; echo
    java -cp "${CP}${OUT}:${RES}" LDemo "$DB" 2>&1 | grep -v SLF4J
    echo; echo "inspect: sqlite3 $DB 'SELECT control_id, status, mapped_json FROM messages;'"
    ;;
  filter)
    DB="/tmp/hl7-demo/buffer.db"; rm -f /tmp/hl7-demo/buffer.db*; mkdir -p /tmp/hl7-demo
    cat > "$OUT/FDemo.java" <<'JAVA'
import com.zerobias.module.hl7.buffer.*;
import com.zerobias.module.hl7.filter.*;
import com.google.gson.Gson;
import java.time.*; import java.util.*;
public class FDemo {
  static final Gson G = new Gson();
  static BufferRow row(String cid, String recvIso, String status, String app,
                       String code, String family, int age, String idNum) {
    Map<String,Object> o = new LinkedHashMap<>();
    o.put("controlId", cid); o.put("status", status); o.put("receivedAt", recvIso);
    o.put("msh", Map.of("messageType", Map.of("messageCode", code),
                        "sendingApplication", Map.of("namespaceId", app)));
    o.put("pid", Map.of("patientFamilyName", family, "ageYears", age,
                        "patientIdentifierList", Map.of("idNumber", idNum)));
    return new BufferRow(0, Instant.parse(recvIso), cid, "ADT_A01", code, "A01",
      app, "HOSP", "2.5.1", "schema:table:hl7v2.v251.ADT_A01",
      ("raw-"+cid).getBytes(), G.toJson(o), MessageStatus.fromWire(status), null, null, null);
  }
  public static void main(String[] a) throws Exception {
    String db = a[0];
    try (BufferStore s = new BufferStore(db, false)) {
      s.insert(row("M1","2026-05-28T10:00:00Z","new","EPIC","ADT","SMITH",45,"5551212"));
      s.insert(row("M2","2026-05-28T11:00:00Z","new","CERNER","ORU","SMYTHE",30,"9001234"));
      s.insert(row("M3","2026-05-28T12:00:00Z","acked","epic","ADT","ASHWORTH",60,"5559999"));
      s.insert(row("M4","2025-12-31T23:59:59Z","new","EPIC","ORM","BOOTH",48,"5551313"));
      System.out.println("seeded 4 messages: M1..M4\n");
      String[] filters = {
        "(status=new)",
        "(msh.sendingApplication.namespaceId=epic)",
        "(pid.patientIdentifierList.idNumber=5551*)",
        "(&(msh.messageType.messageCode=ADT)(status=new))",
        "(|(msh.messageType.messageCode=ADT)(msh.messageType.messageCode=ORM))",
        "(receivedAt>=2026-05-27)",
        "(pid.ageYears:between:40,50)",
        "(receivedAt:year:2026)",
      };
      for (String f : filters) {
        String where = Hl7Filter.toWhereClause(f);
        List<String> ids = new ArrayList<>();
        for (BufferRow r : s.search(where, 100)) ids.add(r.controlId());
        System.out.println("filter : " + f);
        System.out.println("  SQL  : " + where);
        System.out.println("  rows : " + ids + "\n");
      }
    }
  }
}
JAVA
    javac -encoding UTF-8 -cp "$CP$OUT" -d "$OUT" "$OUT/FDemo.java"
    echo; echo "running filter demo..."; echo
    java -cp "${CP}${OUT}:${RES}" FDemo "$DB" 2>&1 | grep -v SLF4J
    ;;
  producer)
    GEN="$(mktemp -d)"
    cat > "$OUT/PDemo.java" <<'JAVA'
import com.zerobias.module.hl7.buffer.*;
import com.zerobias.module.hl7.producer.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.time.*; import java.util.*;
public class PDemo {
  static final Gson G = new Gson();
  static void schema(Path dir, String rel, String id) throws Exception {
    Path f = dir.resolve(rel); Files.createDirectories(f.getParent());
    Files.writeString(f, "{\"id\":\""+id+"\",\"dataTypes\":[],\"properties\":[]}\n");
  }
  static void row(BufferStore b, String cid, String struct, String code, String app,
                  String status, String family) throws Exception {
    b.insert(new BufferRow(0, Instant.parse("2026-05-28T10:00:00Z"), cid, struct, code, "A01",
      app, "HOSP", "2.5.1", "schema:table:hl7v2.v251."+struct, ("raw-"+cid).getBytes(),
      "{\"pid\":{\"patientFamilyName\":\""+family+"\"}}", MessageStatus.fromWire(status), null, null, null));
  }
  static String op(Hl7ProducerFacade f, String m, Object... kv) throws Exception {
    Map<String,Object> a = new LinkedHashMap<>();
    for (int i=0;i<kv.length;i+=2) a.put((String)kv[i], kv[i+1]);
    return OperationRouter.executeOperation(f, m, a);
  }
  public static void main(String[] a) throws Exception {
    Path gen = Path.of(a[0]);
    schema(gen, "schemas/shared/message-envelope.json", "schema:shared:hl7v2.message-envelope");
    schema(gen, "schemas/v251/messages/ADT_A01.json", "schema:table:hl7v2.v251.ADT_A01");
    schema(gen, "schemas/v251/messages/ORU_R01.json", "schema:table:hl7v2.v251.ORU_R01");
    try (BufferStore b = new BufferStore(gen.resolve("buffer.db").toString(), false)) {
      row(b,"M1","ADT_A01","ADT","EPIC","new","SMITH");
      row(b,"M2","ADT_A01","ADT","EPIC","acked","ASHWORTH");
      row(b,"M3","ORU_R01","ORU","CERNER","new","BOOTH");
      SchemaRegistry reg = SchemaRegistry.fromDirectory(gen);
      Hl7ProducerFacade f = new Hl7ProducerFacade(b, new ObjectTree(b, reg, "v251"), reg);
      System.out.println("seeded 3 messages (M1,M2 ADT/EPIC; M3 ORU/CERNER)\n");
      System.out.println("getRootObject              -> " + op(f,"ObjectsApi.getRootObject"));
      System.out.println("getChildren /hl7-v2-receiver-> " + op(f,"ObjectsApi.getChildren","objectId","/hl7-v2-receiver"));
      System.out.println("getObject  /messages        -> " + op(f,"ObjectsApi.getObject","objectId","/hl7-v2-receiver/messages"));
      System.out.println("children   /by-type         -> " + op(f,"ObjectsApi.getChildren","objectId","/hl7-v2-receiver/by-type"));
      System.out.println("children   /by-sender       -> " + op(f,"ObjectsApi.getChildren","objectId","/hl7-v2-receiver/by-sender"));
      System.out.println("search /by-type/ADT_A01     -> " + op(f,"CollectionsApi.searchCollectionElements","objectId","/hl7-v2-receiver/by-type/ADT_A01"));
      System.out.println("  + filter (status=new)     -> " + op(f,"CollectionsApi.searchCollectionElements","objectId","/hl7-v2-receiver/by-type/ADT_A01","filter","(status=new)"));
      System.out.println("getElement /messages/M2     -> " + op(f,"CollectionsApi.getCollectionElement","objectId","/hl7-v2-receiver/messages","elementKey","M2"));
      System.out.println("getSchema  message-envelope -> " + op(f,"SchemasApi.getSchema","objectId","schema:shared:hl7v2.message-envelope"));
      try { op(f,"CollectionsApi.addCollectionElement","objectId","/hl7-v2-receiver/messages","element",Map.of("x",1)); }
      catch (ProducerException e) { System.out.println("addCollectionElement (rejected)-> "+e.httpStatus()+" "+e.key()+": "+e.getMessage()); }

      System.out.println("\n--- drain cycle (ops functions) ---");
      String take = op(f,"FunctionsApi.invokeFunction","objectId","/hl7-v2-receiver/ops/take","requestBody",Map.of("max",2));
      System.out.println("ops/take {max:2}            -> " + take);
      String leaseId = G.fromJson(take, com.google.gson.JsonObject.class).get("leaseId").getAsString();
      System.out.println("ops/ack {leaseId}           -> " + op(f,"FunctionsApi.invokeFunction","objectId","/hl7-v2-receiver/ops/ack","requestBody",Map.of("leaseId",leaseId)));
      System.out.println("ops/purge {} (all acked)    -> " + op(f,"FunctionsApi.invokeFunction","objectId","/hl7-v2-receiver/ops/purge","requestBody",Map.of()));
      System.out.println("messages remaining          -> " + op(f,"ObjectsApi.getObject","objectId","/hl7-v2-receiver/messages"));
    }
  }
}
JAVA
    javac -encoding UTF-8 -cp "$CP$OUT" -d "$OUT" "$OUT/PDemo.java"
    echo; echo "running producer demo..."; echo
    java -cp "${CP}${OUT}:${RES}" PDemo "$GEN" 2>&1 | grep -v SLF4J
    ;;
  *)
    echo "usage: $0 [test|demo|listener|filter|producer]" >&2; exit 2 ;;
esac
