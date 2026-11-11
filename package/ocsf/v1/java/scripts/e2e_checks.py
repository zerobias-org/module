"""PASS/FAIL checks against a running OCSF Receiver container (driven by e2e-local.sh).

Everything goes through the operations port (nginx, as the Hub Node calls it), except the
folder drops, which write the bind-mounted inbox exactly as a producer would. Sample events
are real OCSF samples from schema.ocsf.io, one per class, cached in --samples.

  python3 -I e2e_checks.py --base URL --inbox HOST_DIR --container NAME --samples DIR --spec API_YML
"""
import argparse
import concurrent.futures
import gzip
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

ap = argparse.ArgumentParser()
ap.add_argument("--base", required=True)
ap.add_argument("--inbox", required=True)
ap.add_argument("--container", required=True)
ap.add_argument("--samples", required=True)
ap.add_argument("--spec", required=True, help="the interface api.yml, for the class -> operation names")
args = ap.parse_args()

CONN = "e2e"
RECEIVER = "/ocsf-receiver"
SAMPLE_SERVER = "https://schema.ocsf.io"
MAX_SAMPLE_BYTES = 256 * 1024   # as the interface's fixtures: a few random samples reach ~1 MB
results = []


def step(title):
    print(f"\n\033[1;36m== {title} ==\033[0m")


def check(label, cond, detail=""):
    results.append(bool(cond))
    print(("  \033[1;32mPASS\033[0m " if cond else "  \033[1;31mFAIL\033[0m ") + label + ("" if cond else f"  -- {detail}"))


def http(method, path, body=None, raw=None, timeout=60):
    data, ctype = (raw, "application/octet-stream") if raw is not None else \
        (None if body is None else json.dumps(body).encode(), "application/json")
    req = urllib.request.Request(args.base + path, data=data, method=method, headers={"content-type": ctype})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def rpc(method, arg_map=None):
    status, body = http("POST", f"/connections/{CONN}/{method}", {"argMap": arg_map or {}})
    return status, body.decode()


def rpc_json(method, arg_map=None):
    status, text = rpc(method, arg_map)
    if status != 200:
        raise AssertionError(f"{method} -> {status}: {text[:200]}")
    return json.loads(text)


def upload(file_name, data):
    q = urllib.parse.urlencode({"objectId": f"{RECEIVER}/inbox/inbox", "fileName": file_name})
    status, body = http("POST", f"/connections/{CONN}/BinaryApi.uploadBinaryContent?{q}", raw=data)
    return status, body.decode()


def drop(name, data):
    """A producer's write: a dotfile first (ignored by the poller), then an atomic rename."""
    tmp = os.path.join(args.inbox, f".{name}.part")
    with open(tmp, "wb") as f:
        f.write(data)
    os.rename(tmp, os.path.join(args.inbox, name))


def settled(name, timeout=60):
    for _ in range(timeout * 4):
        for suffix in (".done", ".error"):
            if os.path.exists(os.path.join(args.inbox, name + suffix)):
                return suffix
        time.sleep(0.25)
    return None


def file_docs():
    out = {}
    page = rpc_json("ObjectsApi.getChildren", {"objectId": f"{RECEIVER}/files", "pageSize": 1000})
    for item in page["items"]:
        doc = rpc_json("DocumentsApi.getDocumentData", {"objectId": item["id"]})
        out[doc["fileName"]] = dict(doc, objectId=item["id"])
    return out


# --- operation names, from the interface spec as a consumer's generated client would have them
SPEC = open(args.spec).read()
OPS = {}
for m in re.finditer(r'- "(\w+)"\n\s+operationId: "(list\w+)"\n\s+summary: "[^"]*\(class_uid (\d+)\)', SPEC):
    tag, list_op, uid = m.groups()
    api = "".join(p[:1].upper() + p[1:] for p in tag.split("_")) + "Api"
    OPS[int(uid)] = {"list": f"{api}.{list_op}", "get": f"{api}.get{list_op[4:]}"}


# --- sample events: one per class per version, fetched once and cached
def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return r.read()
        except (urllib.error.URLError, TimeoutError):
            time.sleep(1 + attempt)
    return None


def samples(version):
    d = os.path.join(args.samples, version)
    if not os.path.isdir(d) or not os.listdir(d):
        os.makedirs(d, exist_ok=True)
        names = [c["name"] for c in json.loads(fetch(f"{SAMPLE_SERVER}/{version}/api/classes?extensions="))
                 if c["name"] != "base_event"]
        with concurrent.futures.ThreadPoolExecutor(8) as pool:
            bodies = pool.map(lambda n: fetch(f"{SAMPLE_SERVER}/{version}/sample/classes/{n}?extensions="), names)
            for n, body in zip(names, bodies):
                if body and len(body) <= MAX_SAMPLE_BYTES:
                    open(os.path.join(d, f"{n}.json"), "wb").write(body)
    return [json.loads(open(os.path.join(d, f)).read()) for f in sorted(os.listdir(d))]


step("connect, and what this deployment supports")
status, body = http("POST", "/connections", {"connectionId": CONN})
check("POST /connections -> connected", status == 200 and json.loads(body)["status"] == "connected", body)
for op in ("IamApi.listAuthentication", "listClasses", "ObjectsApi.getChildren", "BinaryApi.uploadBinaryContent"):
    s, b = http("GET", f"/connections/{CONN}/isSupported/{op}")
    check(f"isSupported({op}) = true", json.loads(b).get("supported") is True, b)
for op in ("SystemApi.listAuthentication", "listBaseEvent", "CollectionsApi.addCollectionElement"):
    s, b = http("GET", f"/connections/{CONN}/isSupported/{op}")
    check(f"isSupported({op}) = false", json.loads(b).get("supported") is False, b)

step("fetch real OCSF sample events (schema.ocsf.io)")
s19, s11 = samples("1.9.0"), samples("1.1.0")
check(f"{len(s19)} OCSF 1.9.0 and {len(s11)} OCSF 1.1.0 samples", len(s19) > 70 and len(s11) > 40)
check(f"{len(OPS)} class operations read from the interface spec", len(OPS) == 79, len(OPS))

step("1. every 1.9.0 class, uploaded through the API (uploadBinaryContent -> the volume)")
lines = [json.dumps(e, separators=(",", ":")) for e in s19]
payload = ("\n".join(lines) + "\n").encode()
status, body = upload("all-1.9.0.ndjson", payload)
check(f"upload -> 201 ({len(payload) // 1024} KiB)", status == 201, body[:200])
check("the bytes landed on the bind mount, picked up within 60s", settled("all-1.9.0.ndjson") == ".done")
check("no upload temporary left behind", not any(".part" in n for n in os.listdir(args.inbox)))
doc = file_docs()["all-1.9.0.ndjson"]
check(f"{len(lines)} events stored, none rejected", doc.get("eventCount") == len(lines) and doc.get("rejectedCount") == 0, doc)
classes = rpc_json("ClassesApi.listClasses")
check(f"listClasses = {len(classes)} classes", len(classes) == len({e['class_uid'] for e in s19}), len(classes))

step("2. each class on its own operation, body byte for byte")
by_uid = {json.loads(l)["class_uid"]: l for l in lines}
bad = []
for c in classes:
    status, text = rpc(OPS[c["uid"]]["list"])
    if status != 200:
        bad.append((c["name"], status, text[:100]))
        continue
    raw = text[text.index("[") + 1:text.rindex("]")]
    if json.loads(text)["count"] != 1 or raw != by_uid[c["uid"]]:
        bad.append((c["name"], "body differs"))
check(f"{len(classes) - len(bad)}/{len(classes)} served verbatim", not bad, bad[:5])
e = next(json.loads(l) for l in lines if json.loads(l).get("metadata", {}).get("uid"))
status, text = rpc(OPS[e["class_uid"]]["get"], {"uid": e["metadata"]["uid"]})
check(f"get by metadata.uid ({OPS[e['class_uid']]['get']})", status == 200 and json.loads(text) == e, (status, text[:100]))
other = 1001 if e["class_uid"] != 1001 else 3002
status, _ = rpc(OPS[other]["get"], {"uid": e["metadata"]["uid"]})
check("the same uid under another class is 404", status == 404, status)

step("3. downloadBinary returns the consumed file's bytes")
status, data = http("POST", f"/connections/{CONN}/BinaryApi.downloadBinary", {"argMap": {"objectId": doc["objectId"]}})
check(f"downloadBinary -> {status}, {len(data)} bytes identical", status == 200 and data == payload, (status, len(data)))

step("4. 1.1.0 samples (what Security Lake writes), dropped on the folder as a gzipped JSON array")
drop("lake-1.1.0.json.gz", gzip.compress(json.dumps(s11).encode()))
check("renamed .done", settled("lake-1.1.0.json.gz") == ".done")
doc = file_docs()["lake-1.1.0.json.gz"]
check(f"all {len(s11)} accepted", doc.get("eventCount", 0) + doc.get("duplicateCount", 0) == len(s11)
      and doc.get("rejectedCount") == 0, doc)
versions = sorted({x.get("metadata", {}).get("version") for x in rpc_json(OPS[3002]["list"])["items"]})
check(f"metadata.version kept as written {versions}", "1.1.0" in versions and "1.9.0" in versions, versions)

step("5. time window and paging")
auth, t0 = json.loads(by_uid[3002]), 1_800_000_000_000
window = [json.dumps(dict(auth, time=t0 + i * 1000, metadata=dict(auth.get("metadata", {}), uid=f"window-{i}")))
          for i in range(5)]
drop("window.jsonl", ("\n".join(window) + "\n").encode())
settled("window.jsonl")
page = rpc_json(OPS[3002]["list"], {"startTime": t0 + 1000, "endTime": t0 + 4000, "pageSize": 2})
times = [x["time"] for x in page["items"]]
check(f"count 3 in [t+1s, t+4s), newest first {times}", page["count"] == 3 and times == [t0 + 3000, t0 + 2000], page["count"])
page2 = rpc_json(OPS[3002]["list"], {"startTime": t0 + 1000, "endTime": t0 + 4000, "pageSize": 2, "pageNumber": 2})
check("page 2 holds the third", [x["time"] for x in page2["items"]] == [t0 + 1000])
status, text = rpc(OPS[3002]["list"], {"startTime": t0, "endTime": t0})
check("an empty window (startTime == endTime) is a 400", status == 400, (status, text[:100]))

step("6. bad input")
drop("garbage.json", b"{not json")
drop("mixed.ndjson", (by_uid[1001] + "\n" + '{"class_uid": 0, "time": 1}' + "\nnot json\n").encode())
drop("all-1.9.0-again.ndjson", payload)
drop("notes.txt", b"not an event file")
check("garbage -> .error", settled("garbage.json") == ".error")
check("mixed -> .done", settled("mixed.ndjson") == ".done")
check("re-sent file -> .done", settled("all-1.9.0-again.ndjson") == ".done")
docs = file_docs()
check(f"garbage: error, reason {docs['garbage.json'].get('errorMessage', '')[:30]!r}", docs["garbage.json"]["status"] == "error")
check("mixed: partial, 2 rejected with reasons", docs["mixed.ndjson"]["status"] == "partial"
      and docs["mixed.ndjson"]["rejectedCount"] == 2 and "line 2: unknown-class" in docs["mixed.ndjson"]["errorMessage"],
      docs["mixed.ndjson"])
check("re-sent file: duplicate", docs["all-1.9.0-again.ndjson"]["status"] == "duplicate")
time.sleep(3)
check("notes.txt untouched (not an event file name)", os.path.exists(os.path.join(args.inbox, "notes.txt")))
status2, text2 = rpc("ObjectsApi.deleteObject", {"objectId": f"{RECEIVER}/files"})
check("writes outside /inbox are refused", status2 == 400, (status2, text2[:100]))

step("7. restart the container: the buffer is durable")
before = rpc_json(OPS[3002]["list"], {"pageSize": 1000})
files_before = len(file_docs())
subprocess.run(["docker", "restart", args.container], check=True, capture_output=True)
for _ in range(60):
    try:
        if http("GET", "/healthz", timeout=2)[0] == 200:
            break
    except OSError:
        pass
    time.sleep(1)
http("POST", "/connections", {"connectionId": CONN})
check("events identical after restart", rpc_json(OPS[3002]["list"], {"pageSize": 1000}) == before)
check(f"files history kept ({files_before})", len(file_docs()) == files_before)
status, health = http("GET", "/healthz")
health = json.loads(health)
check(f"healthz {status}, eventCount {health['poller'].get('eventCount')}", status == 200 and health["poller"]["up"])

passed = sum(results)
print(f"\n{passed} passed, {len(results) - passed} failed")
raise SystemExit(0 if passed == len(results) else 1)
