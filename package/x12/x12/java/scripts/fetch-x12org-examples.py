#!/usr/bin/env python3
"""
Fetch the x12.org HIPAA 005010 example transmissions into a git-ignored test
resource directory.

    fetch-x12org-examples.py [--guides 005010x221,005010x222,...] [--out DIR] [--force]
                             [--only slug1,slug2] [--reannotate]

Why this exists
---------------
x12.org publishes worked examples for every HIPAA TR3 guide. They are ASC X12
intellectual property: "Examples posted here are the intellectual property of
X12 and cannot be reproduced or recreated without the express consent of
ASC X12. Properly cited links to this site may be used..." Therefore the
files this script writes are NEVER committed (see package .gitignore); each
developer / CI job that wants the conformance IT to run fetches them locally.

How x12.org is structured (verified 2026-09; Drupal, server-rendered, no login)
--------------------------------------------------------------------------------
* https://x12.org/examples                 -> links /examples/<tr3-id-lowercase>
                                              (also duplicate /index%2Ephp/... forms)
* https://x12.org/examples/<id>            -> <article class="node node-example">
                                              <div class="examples-list"> ...
                                              <a href="/examples/<id>/example-NN-slug">
* https://x12.org/examples/<id>/example-.. -> the EDI is in
                                              <div class="example_item__example-body">
                                              as repeated <p class="data">..</p>.
  Some example pages are PARENTS with no EDI of their own: they carry another
  <div class="examples-list"> whose links are sibling sub-examples
  (e.g. /examples/005010x221/example-5a). Some are narrative only (no EDI at all).
  Variants: 835 puts one segment per p.data with <wbr> inside ISA and &nbsp;
  padding; 837/277/270/271/276 pages are annotated (h3.more loop headings, plain
  <p> descriptions) and contain ST..SE ONLY; 999 puts several segments per p.data
  separated by <br> and splits the ISA segment across a <br> mid-segment; &gt;
  entities appear for the '>' component separator. <p class="dataplus"> holds
  hypothetical snippets and is excluded.

Output
------
  <out>/<tr3-id-lowercase>/<example-slug>.x12        one segment per line, '~' kept
  <out>/<tr3-id-lowercase>/<example-slug>.meta.json  {url, title, tr3, transactionSet,
                                                      envelope: "file"|"synthetic", ...}
  <out>/README.md                                     IP terms + "never commit"

Envelope-less examples are wrapped in a synthetic ISA/GS ... GE/IEA so the
poller can consume them like any other file; the meta file says so
(envelope: "synthetic"). Python 3 stdlib only.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import html
import json
import re
import sys
import time
import urllib.error
import urllib.request
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterator, Optional

BASE = "https://x12.org"
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) fetch-x12org-examples/1.0"
DELAY_SEC = 0.5
RETRY_PAUSE_SEC = 2.0

DEFAULT_GUIDES = [
    "005010x221",  # 835  Health Care Claim Payment/Advice
    "005010x222",  # 837P Professional claim
    "005010x223",  # 837I Institutional claim
    "005010x214",  # 277CA Claim Acknowledgement
    "005010x231",  # 999  Implementation Acknowledgment
    "005010x279",  # 270/271 Eligibility
    "005010x212",  # 276/277 Claim Status
]

# ST01 -> GS01 functional identifier code
FUNCTIONAL_ID = {
    "837": "HC",
    "835": "HP",
    "277": "HN",
    "999": "FA",
    "270": "HS",
    "271": "HB",
    "276": "HR",
    "278": "HI",
    "820": "RA",
    "834": "BE",
    "997": "FA",
}

SYNTHETIC_ID = "X12ORGTEST"
SYNTHETIC_ISA13 = "000000001"
SYNTHETIC_GS06 = "1"

IP_NOTICE = (
    "Examples posted here are the intellectual property of X12 and cannot be "
    "reproduced or recreated without the express consent of ASC X12. Properly "
    "cited links to this site may be used…"
)


# --------------------------------------------------------------------------- HTML
VOID_TAGS = {
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
    "param", "source", "track", "wbr",
}


class Node:
    __slots__ = ("tag", "attrs", "children", "parent")

    def __init__(self, tag: str, attrs: dict, parent: Optional["Node"]):
        self.tag = tag
        self.attrs = attrs
        self.children: list = []  # Node | str
        self.parent = parent

    def classes(self) -> set:
        return set((self.attrs.get("class") or "").split())

    def has_class(self, cls: str) -> bool:
        return cls in self.classes()

    def descendants(self) -> Iterator["Node"]:
        for c in self.children:
            if isinstance(c, Node):
                yield c
                yield from c.descendants()

    def find(self, tag: Optional[str] = None, cls: Optional[str] = None) -> Iterator["Node"]:
        for n in self.descendants():
            if tag is not None and n.tag != tag:
                continue
            if cls is not None and not n.has_class(cls):
                continue
            yield n

    def first(self, tag: Optional[str] = None, cls: Optional[str] = None) -> Optional["Node"]:
        return next(self.find(tag, cls), None)

    def text(self, drop: tuple = ()) -> str:
        """Concatenated text; children whose tag is in `drop` contribute nothing."""
        out = []
        for c in self.children:
            if isinstance(c, str):
                out.append(c)
            elif c.tag in drop:
                continue
            else:
                out.append(c.text(drop))
        return "".join(out)


class TreeBuilder(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Node("#root", {}, None)
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        node = Node(tag, dict(attrs), self.stack[-1])
        self.stack[-1].children.append(node)
        if tag not in VOID_TAGS:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        self.stack[-1].children.append(Node(tag, dict(attrs), self.stack[-1]))

    def handle_endtag(self, tag):
        if tag in VOID_TAGS:
            return
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                return
        # stray end tag: ignore

    def handle_data(self, data):
        if data:
            self.stack[-1].children.append(data)


def parse_html(text: str) -> Node:
    b = TreeBuilder()
    b.feed(text)
    b.close()
    return b.root


# --------------------------------------------------------------------------- HTTP
class Fetcher:
    def __init__(self, delay: float = DELAY_SEC):
        self.delay = delay
        self._last = 0.0
        self.requests = 0

    def get(self, url: str) -> str:
        for attempt in (1, 2):
            wait = self.delay - (time.monotonic() - self._last)
            if wait > 0:
                time.sleep(wait)
            self._last = time.monotonic()
            self.requests += 1
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            try:
                with urllib.request.urlopen(req, timeout=30) as resp:
                    raw = resp.read()
                    charset = resp.headers.get_content_charset() or "utf-8"
                    return raw.decode(charset, errors="replace")
            except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError) as e:
                if attempt == 2:
                    raise
                print(f"  ! {url}: {e}; retrying once", file=sys.stderr)
                time.sleep(RETRY_PAUSE_SEC)
        raise AssertionError("unreachable")


# --------------------------------------------------------------------------- site helpers
def normalize_href(href: str) -> str:
    """'/index%2Ephp/examples/x' | 'https://x12.org/examples/x' -> '/examples/x'."""
    href = href.strip()
    if href.startswith(BASE):
        href = href[len(BASE):]
    href = re.sub(r"^/index(?:%2E|\.)php", "", href, flags=re.IGNORECASE)
    href = href.split("#", 1)[0].split("?", 1)[0]
    return href.rstrip("/")


def index_guide_ids(root: Node) -> set:
    ids = set()
    for a in root.find("a"):
        href = normalize_href(a.attrs.get("href", ""))
        m = re.fullmatch(r"/examples/([0-9]{6}x[0-9]{3})", href, flags=re.IGNORECASE)
        if m:
            ids.add(m.group(1).lower())
    return ids


def article_of(root: Node) -> Optional[Node]:
    for a in root.find("article"):
        if a.has_class("node-example"):
            return a
    return None


def list_links(scope: Node, guide: str, exclude: str) -> list:
    """Links inside div.examples-list under `scope` that point below /examples/<guide>/."""
    prefix = f"/examples/{guide}/"
    out: list = []
    for lst in scope.find("div", "examples-list"):
        for a in lst.find("a"):
            href = normalize_href(a.attrs.get("href", ""))
            if href.lower().startswith(prefix) and href != exclude and href not in out:
                out.append(href)
    return out


def page_details(article: Node) -> dict:
    """Parse 'X12 Version: 005010 | Transaction Set: 835 | TR3 ID: 005010X221'."""
    d: dict = {}
    p = article.first("p", "example-list-details")
    if p is None:
        return d
    for part in p.text().split("|"):
        if ":" in part:
            k, v = part.split(":", 1)
            d[k.strip()] = v.strip()
    return d


def page_title(root: Node, article: Node) -> str:
    h1 = article.first("h1") or root.first("h1")
    return re.sub(r"\s+", " ", h1.text()).strip() if h1 is not None else ""


# --------------------------------------------------------------------------- EDI extraction
def extract_segments(body: Node) -> list:
    """article.node-example div.example_item__example-body p.data -> segments."""
    pieces = []
    for p in body.find("p"):
        cls = p.classes()
        if "data" not in cls:  # excludes <p class="dataplus"> and descriptions
            continue
        t = p.text(drop=("wbr", "br"))  # <wbr> and <br> contribute ''
        t = html.unescape(t)            # convert_charrefs already did most of it
        t = t.replace("\xa0", " ").replace("\r", "").replace("\n", "")
        pieces.append(t)
    blob = "".join(pieces)
    segs = []
    for raw in blob.split("~"):          # never split on newlines
        s = raw.strip()
        if s:
            segs.append(s)
    return segs


def element_sep(segs: list) -> str:
    if segs and segs[0].startswith("ISA") and len(segs[0]) > 3:
        return segs[0][3]
    for s in segs:
        if s.startswith("ST"):
            return s[2] if len(s) > 2 and not s[2].isalnum() else "*"
    return "*"


def component_sep(segs: list, esep: str) -> str:
    """':' unless the transaction reveals it uses '>' (no ':' anywhere)."""
    body = esep.join(segs)
    if ":" in body:
        return ":"
    if ">" in body:
        return ">"
    return ":"


def build_isa(esep: str, csep: str, now: _dt.datetime) -> str:
    f = [
        "ISA", "00", " " * 10, "00", " " * 10,
        "ZZ", SYNTHETIC_ID.ljust(15), "ZZ", SYNTHETIC_ID.ljust(15),
        now.strftime("%y%m%d"), now.strftime("%H%M"), "^", "00501",
        SYNTHETIC_ISA13, "0", "T", csep,
    ]
    isa = esep.join(f) + "~"
    assert len(isa) == 106, f"ISA length {len(isa)} != 106"
    return isa


def synthesize_envelope(segs: list, esep: str, tr3_from_page: str, now: _dt.datetime) -> tuple:
    """Wrap ST..SE (possibly several) in ISA/GS ... GE/IEA. Returns (segments, gs08)."""
    st_segs = [s for s in segs if s.split(esep)[0] == "ST"]
    if not st_segs:
        raise ValueError("no ST segment found; cannot synthesize an envelope")
    st = st_segs[0].split(esep)
    st01 = st[1] if len(st) > 1 else ""
    st03 = st[3] if len(st) > 3 and st[3] else ""
    gs08 = st03 or tr3_from_page.upper()
    fid = FUNCTIONAL_ID.get(st01)
    if fid is None:
        raise ValueError(f"no functional identifier known for transaction set {st01!r}")
    csep = component_sep(segs, esep)
    isa = build_isa(esep, csep, now)
    gs = esep.join(["GS", fid, SYNTHETIC_ID, SYNTHETIC_ID, now.strftime("%Y%m%d"),
                    now.strftime("%H%M"), SYNTHETIC_GS06, "X", gs08]) + "~"
    ge = esep.join(["GE", str(len(st_segs)), SYNTHETIC_GS06]) + "~"
    iea = esep.join(["IEA", "1", SYNTHETIC_ISA13]) + "~"
    return [isa, gs] + [s + "~" for s in segs] + [ge, iea], gs08


# Note prefixes the conformance IT keys on (X12OrgConformanceIT):
#   "source SE01=…" / "source SE02=…" / "source ST without SE"  -> expected NON-FATAL parser errors
#   "source CLM05 …"                                             -> a FATAL known source defect
NOTE_CLM05 = "source CLM05"


def transaction_notes(segs: list, esep: str) -> list:
    """Report inconsistencies present in the SOURCE; these are recorded, never repaired.

    * ST/SE control-number and SE01 count mismatches (x12.org has a few off-by-one SE01
      values) — the wrapper reports them as non-fatal parser errors.
    * The 837 CLM element-separator defect: the TR3 shows ``CLM*id*827***22:B:1*…`` but
      four x12.org pages carry ``CLM*id*827**22:B:1*Y*…`` (one ``*`` short), so the CLM05
      place-of-service composite lands in CLM04 and CLM05..CLM09 sit one position left;
      imsweb's loop-start code validation then rejects the claim (CLM07 'Y' is not in
      A|B|C) — a fatal parse. Flagged when a CLM segment's 5th element is not a 3-part
      composite (CLM05 = C023 facility:qualifier:frequency, all three required).
    """
    notes: list = []
    csep = component_sep(segs, esep)
    st = None
    for s in segs:
        f = s.split(esep)
        if st is not None:
            st["n"] += 1
        if f[0] == "CLM":
            clm05 = f[5] if len(f) > 5 else ""
            if len(clm05.split(csep)) < 3:
                shown = esep.join(f[:6]) if len(f) > 5 else s
                notes.append(f"{NOTE_CLM05} is not a 3-part composite: '{shown}' "
                             "(element separator missing; the CLM05 composite landed in CLM04)")
        if f[0] == "ST":
            st = {"ctl": f[2] if len(f) > 2 else "", "n": 1}
        elif f[0] == "SE" and st is not None:
            se01 = f[1] if len(f) > 1 else ""
            se02 = f[2] if len(f) > 2 else ""
            if se01 != str(st["n"]):
                notes.append(f"source SE01={se01} but ST..SE holds {st['n']} segments")
            if se02 != st["ctl"]:
                notes.append(f"source SE02={se02} != ST02={st['ctl']}")
            st = None
    if st is not None:
        notes.append("source ST without SE")
    return notes


# --------------------------------------------------------------------------- driver
class GuideStats:
    def __init__(self):
        self.leaf_pages = 0
        self.written = 0
        self.skipped_existing = 0
        self.synthesized = 0
        self.narrative = 0
        self.errors: list = []


def reannotate(out_dir: Path, guides: list, only: Optional[set]) -> int:
    """Offline: recompute `notes` in every existing .meta.json from the .x12 beside it
    (after a change to transaction_notes) without touching x12.org. Returns the count updated."""
    updated = 0
    for guide in guides:
        gdir = out_dir / guide
        if not gdir.is_dir():
            continue
        for x12_path in sorted(gdir.glob("*.x12")):
            slug = x12_path.stem
            if only and slug not in only:
                continue
            meta_path = x12_path.with_name(f"{slug}.meta.json")
            if not meta_path.exists():
                continue
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            lines = [ln.strip() for ln in x12_path.read_text(encoding="utf-8").splitlines() if ln.strip()]
            segs = [ln[:-1] if ln.endswith("~") else ln for ln in lines]
            esep = meta.get("elementSeparator") or element_sep(segs)
            notes = transaction_notes(segs, esep)
            if notes != meta.get("notes", []):
                meta["notes"] = notes
                meta_path.write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
                updated += 1
                print(f"  ~ {guide}/{slug}: notes -> {notes}")
    return updated


def process_page(fetcher: Fetcher, guide: str, href: str, out_dir: Path, force: bool,
                 stats: GuideStats, anomalies: list, only: Optional[set] = None, depth: int = 0) -> None:
    slug = href.rsplit("/", 1)[-1]
    x12_path = out_dir / guide / f"{slug}.x12"
    meta_path = out_dir / guide / f"{slug}.meta.json"
    url = BASE + href
    indent = "  " * (depth + 1)

    if only and slug not in only and x12_path.exists():
        # --only: leave every other existing leaf alone (parents are still walked).
        stats.leaf_pages += 1
        stats.skipped_existing += 1
        return

    if x12_path.exists() and not force:
        # A leaf we already have; parents never get a .x12 so they are always re-walked.
        stats.leaf_pages += 1
        stats.skipped_existing += 1
        print(f"{indent}= {slug}: exists, skipping (use --force)")
        return

    try:
        root = parse_html(fetcher.get(url))
    except Exception as e:  # noqa: BLE001
        stats.errors.append(f"{href}: {e}")
        anomalies.append(f"{href}: fetch failed: {e}")
        print(f"{indent}! {slug}: fetch failed: {e}")
        return

    article = article_of(root)
    if article is None:
        anomalies.append(f"{href}: no <article class=\"node-example\">")
        stats.errors.append(f"{href}: no article.node-example")
        print(f"{indent}! {slug}: no article.node-example")
        return

    body = article.first("div", "example_item__example-body")
    segs = extract_segments(body) if body is not None else []

    if not segs:
        children = list_links(article, guide, href)
        if children:
            print(f"{indent}> {slug}: parent page, {len(children)} sub-example(s)")
            if depth >= 3:
                anomalies.append(f"{href}: examples-list nesting deeper than 3; not followed")
                return
            for child in children:
                process_page(fetcher, guide, child, out_dir, force, stats, anomalies, only, depth + 1)
            return
        stats.narrative += 1
        print(f"{indent}- {slug}: narrative only (no p.data), skipped")
        return

    stats.leaf_pages += 1
    details = page_details(article)
    tr3 = details.get("TR3 ID", guide.upper())
    ts = details.get("Transaction Set", "")
    esep = element_sep(segs)
    envelope = "file"
    gs08 = None
    if segs[0].startswith("ISA"):
        lines = [s + "~" for s in segs]
        gs_seg = next((s for s in segs if s.split(esep)[0] == "GS"), None)
        if gs_seg is not None:
            parts = gs_seg.split(esep)
            gs08 = parts[8] if len(parts) > 8 else None
        else:
            anomalies.append(f"{href}: ISA present but no GS segment")
    else:
        if not segs[0].startswith("ST"):
            anomalies.append(f"{href}: first segment is {segs[0][:3]!r}, expected ISA or ST")
        envelope = "synthetic"
        try:
            lines, gs08 = synthesize_envelope(segs, esep, tr3, _dt.datetime.now())
        except ValueError as e:
            stats.errors.append(f"{href}: {e}")
            anomalies.append(f"{href}: {e}")
            print(f"{indent}! {slug}: {e}")
            return
        stats.synthesized += 1

    if not ts:
        st = next((s for s in segs if s.split(esep)[0] == "ST"), None)
        if st is not None:
            ts = st.split(esep)[1]

    notes = transaction_notes(segs, esep)
    for n in notes:
        anomalies.append(f"{href}: {n}")

    x12_path.parent.mkdir(parents=True, exist_ok=True)
    x12_path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    meta = {
        "url": url,
        "title": page_title(root, article),
        "tr3": tr3,
        "transactionSet": ts,
        "envelope": envelope,
        "segmentCount": len(lines),
        "fetchedAt": _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat(),
        "guide": guide,
        "gs08": gs08,
        "elementSeparator": esep,
        "about": article.attrs.get("about"),
        "sourceSegmentCount": len(segs),
        "notes": notes,
    }
    meta_path.write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    stats.written += 1
    flag = f"  [{'; '.join(notes)}]" if notes else ""
    print(f"{indent}+ {slug}: {len(lines)} segments ({envelope}){flag}")


README = """# x12.org example transmissions (LOCAL ONLY — never commit)

This directory is populated by `java/scripts/fetch-x12org-examples.py` from
<https://x12.org/examples>. It is listed in the package `.gitignore` and **must
never be committed** or copied into any published artifact.

X12's terms (<https://x12.org/examples/disclaimers>):

> {notice}

Only links to x12.org may be shared. Each `<guide>/<example>.x12` here has a
sibling `<example>.meta.json` recording the source `url`, `title`, `tr3`,
`transactionSet`, `envelope` (`file` = the page carried ISA…IEA; `synthetic` =
the page carried ST…SE only and this script wrapped it in an ISA/GS…GE/IEA
built from `{sender}` identifiers), `segmentCount`, `fetchedAt` and `notes` (source
inconsistencies which x12.org itself carries on a few pages and which this script
records but never repairs: an off-by-one `SE01` — a non-fatal parser error — and
`source CLM05 …`, the 837 CLM element-separator defect that makes imsweb reject the
claim, which the conformance IT reads from here as a known fatal source defect).

The Java conformance IT (`X12OrgConformanceIT`) parses every `*.x12` under
this directory and skips itself when the directory is absent, so CI stays
green without these files.

Regenerate:

    python3 java/scripts/fetch-x12org-examples.py            # default HIPAA guides
    python3 java/scripts/fetch-x12org-examples.py --force    # re-download everything
    python3 java/scripts/fetch-x12org-examples.py --reannotate   # offline: recompute notes only
"""


def main(argv: Optional[list] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n", 1)[0])
    ap.add_argument("--guides", default=",".join(DEFAULT_GUIDES),
                    help="comma-separated TR3 ids (lowercase, e.g. 005010x221). "
                         f"Default: {','.join(DEFAULT_GUIDES)}")
    default_out = Path(__file__).resolve().parent.parent / "src" / "test" / "resources" / "x12org"
    ap.add_argument("--out", type=Path, default=default_out,
                    help=f"output directory (default: {default_out})")
    ap.add_argument("--force", action="store_true", help="re-download files that already exist")
    ap.add_argument("--only", default="",
                    help="comma-separated example slugs to (re)fetch; every other existing leaf is left alone")
    ap.add_argument("--reannotate", action="store_true",
                    help="offline: recompute `notes` in the existing .meta.json files from the .x12 beside them")
    ap.add_argument("--delay", type=float, default=DELAY_SEC,
                    help=f"seconds between requests (default {DELAY_SEC})")
    args = ap.parse_args(argv)

    guides = [g.strip().lower() for g in args.guides.split(",") if g.strip()]
    out_dir: Path = args.out
    only = {o.strip() for o in args.only.split(",") if o.strip()} or None

    if args.reannotate:
        n = reannotate(out_dir, guides, only)
        print(f"reannotated {n} meta file(s) under {out_dir}")
        return 0

    fetcher = Fetcher(args.delay)
    anomalies: list = []
    table: dict = {}

    print(f"Output: {out_dir}")
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "README.md").write_text(README.format(notice=IP_NOTICE, sender=SYNTHETIC_ID),
                                       encoding="utf-8")

    # Index: only used to warn about guide ids the site does not list.
    try:
        known = index_guide_ids(parse_html(fetcher.get(f"{BASE}/examples")))
        for g in guides:
            if g not in known:
                print(f"! guide {g} is not linked from {BASE}/examples (trying anyway)")
                anomalies.append(f"{g}: not linked from /examples index")
    except Exception as e:  # noqa: BLE001
        print(f"! could not read {BASE}/examples index: {e}")
        anomalies.append(f"/examples index: {e}")

    for guide in guides:
        stats = GuideStats()
        table[guide] = stats
        print(f"\n== {guide}")
        try:
            root = parse_html(fetcher.get(f"{BASE}/examples/{guide}"))
        except Exception as e:  # noqa: BLE001
            stats.errors.append(f"guide page: {e}")
            anomalies.append(f"/examples/{guide}: fetch failed: {e}")
            print(f"  ! guide page fetch failed: {e}")
            continue
        article = article_of(root)
        if article is None:
            anomalies.append(f"/examples/{guide}: no article.node-example")
            print("  ! no article.node-example on guide page")
            continue
        links = list_links(article, guide, exclude="")
        if not links:
            anomalies.append(f"/examples/{guide}: no examples-list links")
            print("  ! no example links found")
            continue
        for href in links:
            process_page(fetcher, guide, href, out_dir, args.force, stats, anomalies, only)

    # Summary
    print("\nSummary")
    hdr = f"{'guide':<12}{'leaf pages':>11}{'written':>9}{'skipped':>9}{'synthetic':>11}{'narrative':>11}{'errors':>8}"
    print(hdr)
    print("-" * len(hdr))
    tot = GuideStats()
    for g, s in table.items():
        print(f"{g:<12}{s.leaf_pages:>11}{s.written:>9}{s.skipped_existing:>9}{s.synthesized:>11}{s.narrative:>11}{len(s.errors):>8}")
        tot.leaf_pages += s.leaf_pages
        tot.written += s.written
        tot.skipped_existing += s.skipped_existing
        tot.synthesized += s.synthesized
        tot.narrative += s.narrative
        tot.errors += s.errors
    print("-" * len(hdr))
    print(f"{'total':<12}{tot.leaf_pages:>11}{tot.written:>9}{tot.skipped_existing:>9}{tot.synthesized:>11}{tot.narrative:>11}{len(tot.errors):>8}")
    print(f"HTTP requests: {fetcher.requests}")
    if anomalies:
        print("\nPages that did not match the documented structure / notes:")
        for a in anomalies:
            print(f"  - {a}")
    return 1 if tot.errors else 0


if __name__ == "__main__":
    sys.exit(main())
