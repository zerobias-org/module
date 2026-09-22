#!/usr/bin/env python3
"""
Tiny structural sanity check for X12 files (stdlib only).

    check-x12-structure.py FILE_OR_DIR [...]

Per file it verifies: ISA segment is exactly 106 characters (incl. '~'), ISA16
and the element separator are consistent, every ST has a matching SE whose
SE01 equals the ST..SE segment count and SE02 == ST02, GE01 == number of ST in
the group and GE02 == GS06, IEA01 == number of GS and IEA02 == ISA13. Files under
`malformed/` are expected to FAIL and are reported as such (exit code ignores them).
Exit 1 if any non-malformed file fails.
"""
from __future__ import annotations

import sys
from pathlib import Path


def check(path: Path) -> list:
    problems: list = []
    text = path.read_text(encoding="utf-8", errors="replace")
    if not text.strip():
        return ["file is empty"]
    if not text.startswith("ISA"):
        return [f"does not start with ISA (starts with {text[:3]!r})"]
    esep = text[3]
    body = text.replace("\r", "").replace("\n", "")
    isa_end = body.find("~")
    if isa_end == -1:
        return ["no segment terminator '~' found"]
    isa = body[: isa_end + 1]
    if len(isa) != 106:
        problems.append(f"ISA length {len(isa)} != 106")
    isa_f = isa[:-1].split(esep)
    if len(isa_f) != 17:
        problems.append(f"ISA has {len(isa_f)} elements, expected 17")
        return problems
    isa13, csep = isa_f[13], isa_f[16]
    if csep == esep or csep == "~":
        problems.append(f"ISA16 component separator {csep!r} collides with other separators")

    segs = [s for s in body.split("~") if s.strip()]
    st_stack: list = []
    gs_stack: list = []
    gs_count = 0
    st_in_group = 0
    for s in segs:
        f = s.split(esep)
        tag = f[0]
        if st_stack:
            st_stack[-1]["n"] += 1
        if tag == "GS":
            gs_count += 1
            st_in_group = 0
            gs_stack.append(f[6] if len(f) > 6 else "")
        elif tag == "ST":
            st_stack.append({"id": f[1] if len(f) > 1 else "", "ctl": f[2] if len(f) > 2 else "", "n": 1})
            st_in_group += 1
        elif tag == "SE":
            if not st_stack:
                problems.append("SE without ST")
                continue
            st = st_stack.pop()
            if len(f) < 3:
                problems.append("SE has fewer than 2 elements")
                continue
            if f[1] != str(st["n"]):
                problems.append(f"SE01={f[1]} but ST..SE holds {st['n']} segments (ST02={st['ctl']})")
            if f[2] != st["ctl"]:
                problems.append(f"SE02={f[2]} != ST02={st['ctl']}")
        elif tag == "GE":
            if not gs_stack:
                problems.append("GE without GS")
                continue
            gs06 = gs_stack.pop()
            if len(f) < 3 or f[1] != str(st_in_group):
                problems.append(f"GE01={f[1] if len(f) > 1 else ''} != {st_in_group} transaction sets")
            if len(f) < 3 or f[2] != gs06:
                problems.append(f"GE02={f[2] if len(f) > 2 else ''} != GS06={gs06}")
        elif tag == "IEA":
            if len(f) < 3 or f[1] != str(gs_count):
                problems.append(f"IEA01={f[1] if len(f) > 1 else ''} != {gs_count} functional groups")
            if len(f) < 3 or f[2] != isa13:
                problems.append(f"IEA02={f[2] if len(f) > 2 else ''} != ISA13={isa13}")
    if st_stack:
        problems.append(f"{len(st_stack)} ST without SE")
    if gs_stack:
        problems.append(f"{len(gs_stack)} GS without GE")
    if segs[-1].split(esep)[0] != "IEA":
        problems.append(f"last segment is {segs[-1].split(esep)[0]}, not IEA")
    return problems


def main(argv: list) -> int:
    files: list = []
    for a in argv:
        p = Path(a)
        files += sorted(p.rglob("*.x12")) if p.is_dir() else [p]
    bad = 0
    for f in files:
        probs = check(f)
        expected_bad = "malformed" in f.parts
        status = "OK" if not probs else ("EXPECTED-FAIL" if expected_bad else "FAIL")
        if probs and not expected_bad:
            bad += 1
        print(f"{status:<14}{f}")
        for p in probs:
            print(f"              - {p}")
    print(f"\n{len(files)} files, {bad} unexpected failures")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
