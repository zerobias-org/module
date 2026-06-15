#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# DEV / LOCAL TOOL — NOT part of the build or CI gate, and nothing references it.
# The standardized test surface is the JUnit suite under java/src/test, run by the
# real gate:  ./gradlew :hl7:v2:gate  (see CLAUDE.md). A convenience for feeding the
# listener real-shaped data; safe to delete.
# ─────────────────────────────────────────────────────────────────────────────
"""
Stream real Synthea patient data into this module's HL7 v2 MLLP listener.

Synthea emits FHIR (not HL7 v2), so this reads Synthea's FHIR transaction
bundles (output/fhir/*.json), builds HL7 v2.7 messages from the real
Patient / Encounter / Observation resources, frames them in MLLP
(0x0B <msg> 0x1C 0x0D), sends each to the listener, and prints the ACK.

  ADT^A01  (admit)  — one per patient, from Patient + first Encounter
  ORU^R01  (result) — optional (--oru), one per numeric Observation

Usage:
  ./run_synthea -p 5            # in ~/synthea — generates output/fhir/*.json
  python3 synthea_to_mllp.py --fhir-dir ~/synthea/output/fhir
  python3 synthea_to_mllp.py --fhir-dir ~/synthea/output/fhir --oru --limit 3
  python3 synthea_to_mllp.py --dry-run                 # print, don't send

Default target is localhost:2575 (the prescribed runtimeConfig MLLP port,
published to the host via MODULE_PORTS in the dev appliance). Stdlib only.
"""
import argparse, glob, json, os, socket, sys, time

VT, FS, CR = b"\x0b", b"\x1c", b"\x0d"


def ts(fhir_dt=None):
    """FHIR dateTime (or now) -> HL7 TS (YYYYMMDDHHMMSS / YYYYMMDD)."""
    if fhir_dt:
        digits = "".join(c for c in fhir_dt if c.isdigit())
        if len(digits) >= 14:
            return digits[:14]
        if len(digits) >= 8:
            return digits[:8]
    return time.strftime("%Y%m%d%H%M%S")


def esc(s):
    """Escape HL7 delimiter chars in a component value."""
    if s is None:
        return ""
    s = str(s)
    for a, b in (("\\", "\\E\\"), ("|", "\\F\\"), ("^", "\\S\\"), ("~", "\\R\\"), ("&", "\\T\\")):
        s = s.replace(a, b)
    return s


def find(bundle, rtype):
    for e in bundle.get("entry", []):
        r = e.get("resource", {})
        if r.get("resourceType") == rtype:
            yield r


def mrn(patient):
    for ident in patient.get("identifier", []):
        for c in ident.get("type", {}).get("coding", []):
            if c.get("code") == "MR":
                return ident.get("value", "")
    ids = patient.get("identifier", [])
    return ids[0].get("value", "") if ids else patient.get("id", "")


def pid_segment(patient):
    name = (patient.get("name") or [{}])[0]
    family = esc(name.get("family", ""))
    given = "^".join(esc(g) for g in name.get("given", []))
    sex = {"male": "M", "female": "F"}.get(patient.get("gender", ""), "U")
    dob = ts(patient.get("birthDate"))[:8]
    addr = (patient.get("address") or [{}])[0]
    line = esc((addr.get("line") or [""])[0])
    xad = f"{line}^^{esc(addr.get('city',''))}^{esc(addr.get('state',''))}^{esc(addr.get('postalCode',''))}"
    return f"PID|1||{esc(mrn(patient))}^^^HOSP^MR||{family}^{given}||{dob}|{sex}|||{xad}"


PATIENT_CLASS = {"IMP": "I", "ACUTE": "I", "AMB": "O", "EMER": "E", "OBSENC": "O", "SS": "O"}


def pv1_segment(encounter):
    cls = (encounter or {}).get("class", {}).get("code", "AMB")
    pc = PATIENT_CLASS.get(cls, "O")
    start = ts((encounter or {}).get("period", {}).get("start"))
    return f"PV1|1|{pc}|^^^HOSP|||||||||||||||||{start}"


HL7_VERSION = "2.7"


def msh(msg_type, ctrl_id, when=None):
    return f"MSH|^~\\&|SYNTHEA|SYNTHEA|HUB|ZB|{when or ts()}||{msg_type}|{ctrl_id}|P|{HL7_VERSION}"


def build_adt(patient, encounter, ctrl):
    when = ts((encounter or {}).get("period", {}).get("start"))
    return "\r".join([
        msh("ADT^A01", ctrl, when),
        f"EVN|A01|{when}",
        pid_segment(patient),
        pv1_segment(encounter),
    ])


def build_oru(patient, obs, ctrl):
    code = (obs.get("code", {}).get("coding") or [{}])[0]
    obs_id = esc(code.get("code", ""))
    obs_name = esc(code.get("display", ""))
    when = ts(obs.get("effectiveDateTime"))
    vq = obs.get("valueQuantity", {})
    return "\r".join([
        msh("ORU^R01", ctrl, when),
        pid_segment(patient),
        f"OBR|1|||{obs_id}^{obs_name}^LN|||{when}",
        f"OBX|1|NM|{obs_id}^{obs_name}^LN||{vq.get('value','')}|{esc(vq.get('unit',''))}|||||F",
    ])


def mllp_send(host, port, message, timeout=10):
    frame = VT + message.encode("utf-8") + FS + CR
    with socket.create_connection((host, port), timeout=timeout) as s:
        s.sendall(frame)
        ack = b""
        while FS not in ack:
            chunk = s.recv(65536)
            if not chunk:
                break
            ack += chunk
    return ack.replace(VT, b"").replace(FS, b"").replace(CR, b"\n").decode(errors="replace").strip()


def ack_code(ack):
    for line in ack.splitlines():
        if line.startswith("MSA"):
            f = line.split("|")
            return f[1] if len(f) > 1 else "?"
    return "?"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fhir-dir", default=os.path.expanduser("~/synthea/output/fhir"))
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", type=int, default=2575)
    ap.add_argument("--limit", type=int, default=0, help="max patients (0 = all)")
    ap.add_argument("--oru", action="store_true", help="also send ORU^R01 per numeric Observation")
    ap.add_argument("--oru-max", type=int, default=5, help="max ORU per patient")
    ap.add_argument("--dry-run", action="store_true", help="print messages, don't send")
    args = ap.parse_args()

    files = sorted(glob.glob(os.path.join(args.fhir_dir, "*.json")))
    files = [f for f in files if not os.path.basename(f).lower().startswith(("hospital", "practitioner"))]
    if not files:
        sys.exit(f"No FHIR bundles in {args.fhir_dir} — run `./run_synthea -p 5` in ~/synthea first.")

    sent = acks = pcount = 0
    ctrl = int(time.time())
    for f in files:
        if args.limit and pcount >= args.limit:
            break
        try:
            bundle = json.load(open(f))
        except Exception as e:
            print(f"skip {os.path.basename(f)}: {e}")
            continue
        patients = list(find(bundle, "Patient"))
        if not patients:
            continue
        patient = patients[0]
        pcount += 1
        encounters = list(find(bundle, "Encounter"))
        name = (patient.get("name") or [{}])[0]
        who = f"{(name.get('given') or [''])[0]} {name.get('family','')}".strip()

        ctrl += 1
        adt = build_adt(patient, encounters[0] if encounters else None, f"S{ctrl}")
        if args.dry_run:
            print(f"--- ADT^A01 {who} ---\n{adt.replace(chr(13), chr(10))}\n")
        else:
            try:
                code = ack_code(mllp_send(args.host, args.port, adt))
                sent += 1
                acks += 1 if code.startswith("A") else 0
                print(f"ADT^A01  {who:<28} -> {code}")
            except Exception as e:
                print(f"ADT^A01  {who:<28} -> SEND FAILED: {e}")

        if args.oru:
            obs = [o for o in find(bundle, "Observation") if "valueQuantity" in o][: args.oru_max]
            for o in obs:
                ctrl += 1
                oru = build_oru(patient, o, f"S{ctrl}")
                if args.dry_run:
                    print(f"--- ORU^R01 {who} ---\n{oru.replace(chr(13), chr(10))}\n")
                else:
                    try:
                        ack_code(mllp_send(args.host, args.port, oru))
                        sent += 1
                    except Exception as e:
                        print(f"ORU^R01  {who:<28} -> SEND FAILED: {e}")

    if not args.dry_run:
        print(f"\nSent {sent} message(s), {acks} ADT accepted (MSA|AA).")


if __name__ == "__main__":
    main()
