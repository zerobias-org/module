# Synthetic X12 test fixtures

Everything in this directory is **authored from scratch** for this module and is
committed. Nothing here is derived from x12.org (those examples are ASC X12
intellectual property and live in the git-ignored sibling `../x12org/`,
populated by `java/scripts/fetch-x12org-examples.py`).

All parties are fictional and use the same identifiers everywhere:

| Role                | Value                                         |
|---------------------|-----------------------------------------------|
| Payer               | `EXAMPLE HEALTH PLAN`, payer id `EHPID00001`, ISA/GS id `EXAMPLEPAYER` |
| Provider (837P/835) | `EXAMPLE MEDICAL GROUP`, NPI `1234567893` (valid Luhn), TIN `000000000` (00-0000000), ISA/GS id `EXAMPLEPROV` |
| Provider (837I)     | `EXAMPLE COMMUNITY HOSPITAL`, same NPI/TIN                                   |
| Rendering/attending | `SMITH, JOHN`, NPI `1234567893`                                              |
| Subscribers         | `DOE, JANE` member `EHP000000001`; `ROE, RICHARD` member `EHP000000002`      |
| Address             | `SPRINGFIELD, IL 62701-0000`                                                 |
| Dates               | ISA/GS date `2026-09-22 12:00`; service dates 2026-09-01 / -02 / -05         |
| Separators          | element `*`, component `:`, repetition `^`, segment `~` + newline            |
| ISA                 | 106 characters incl. `~`, `00501`, usage `T`, `ISA13 = 0000001xx`, `GS06 = 1xx` (xx per file, see below) |

Files are one segment per line (the `~` terminator is kept). Structural checks
(ISA length, ST/SE, GE, IEA counts) can be re-run at any time with
`python3 java/scripts/check-x12-structure.py java/src/test/resources/fixtures`.

## Well-formed files

### `835-005010X221A1.x12` — Health Care Claim Payment/Advice

- `GS*HP`, `GS08 = 005010X221A1`, `ISA13 = 000000101`, `GS06 = 101`, `ST02 = 0001`.
- **46 segments** in the file; `SE*42*0001` (42 segments ST..SE inclusive).
- `BPR02 = 450.00` (ACH, CCP) = CLP04 total 460.00 minus the PLB 10.00.
  `TRN02 = EFT000000101`, `DTM*405 = 20260922`.
- Payer `N1*PR`, payee `N1*PE` with NPI, `REF*TJ` TIN.
- One `LX*1` with **2 claims**:
  - `CLP*CLM0001` charged 300.00, paid 220.00, patient responsibility 40.00,
    status 1, payer claim control `EHP2026000001`, POS 11. `AMT*AU 260.00`.
    2 service lines: `SVC*HC:99213` 200.00 → 160.00 (`CAS*CO*45*20.00`,
    `CAS*PR*3*20.00`, `AMT*B6 180.00`) and `SVC*HC:36415` 100.00 → 60.00
    (`CAS*CO*45*20.00`, `CAS*PR*2*20.00`, `AMT*B6 80.00`).
  - `CLP*CLM0002` charged 250.00, paid 240.00, patient responsibility 0.00,
    `EHP2026000002`. 1 service line `SVC*HC:99214` 250.00 → 240.00
    (`CAS*CO*45*10.00`, `AMT*B6 240.00`).
- `PLB*1234567893*20261231*WO:CLM0000*10.00` (one provider-level adjustment).
- Invariants a test can assert: 2 CLP, 3 SVC, 5 CAS (3 CO, 2 PR), 1 PLB;
  sum(CLP04) − PLB04 == BPR02; per claim sum(SVC03) == CLP04 and
  sum(CAS PR amounts) == CLP05; every SVC02 − CO − PR == SVC03.

### `837P-005010X222A1.x12` — Professional claim

- `GS*HC`, `GS08 = 005010X222A1`, `ST03 = 005010X222A1`, `ISA13 = 000000102`,
  `GS06 = 102`, `BHT03 = BATCH000001`.
- **34 segments**; `SE*30*0001`.
- Hierarchy: `HL*1` billing provider (20) → `HL*2` subscriber (22, no patient
  child: subscriber is the patient). `SBR*P*18`, payer `NM1*PR`.
- One claim `CLM*CLM0001*300.00` (`11:B:1`), diagnosis `HI*ABK:J069`,
  rendering provider `NM1*82` + `PRV*PE*PXC*207Q00000X`.
- 2 service lines: `SV1*HC:99213*200.00` and `SV1*HC:36415*100.00`, both
  `DTP*472*D8*20260901`, line ids `REF*6R*LINE0001` / `LINE0002`.
- Invariants: 2 HL, 1 CLM, 2 LX, 2 SV1; sum(SV102) == CLM02.

### `837I-005010X223A2.x12` — Institutional claim

- `GS*HC`, `GS08 = 005010X223A2`, `ST03 = 005010X223A2`, `ISA13 = 000000103`,
  `GS06 = 103`, `BHT03 = BATCH000002`.
- **36 segments**; `SE*32*0001`.
- `CLM*CLM0002*2500.00` (`13:A:1`, outpatient), statement `DTP*434*RD8`,
  `CL1*1*1*01`, `HI*ABK:K3580`, attending `NM1*71`.
- 3 revenue lines: `SV2*0450*HC:99283*800.00`, `SV2*0300*HC:80048*200.00`,
  `SV2*0320*HC:71046*1500.00`, each `DTP*472*D8*20260905`.
- Invariants: 3 LX, 3 SV2; sum(SV203) == CLM02.

### `277CA-005010X214.x12` — Claim Acknowledgement

- `GS*HN`, `GS08 = 005010X214`, `ST03 = 005010X214`, `ISA13 = 000000104`,
  `GS06 = 104`, `BHT*0085*08*BATCH000001`.
- **26 segments**; `SE*22*0001`.
- Hierarchy: `HL*1` information source (20, payer) → `HL*2` information
  receiver (21, submitter; `STC*A1:19:PR`, `QTY*90*1`, `AMT*YU*300.00`) →
  `HL*3` billing provider (19) → `HL*4` patient (PT): `TRN*2*CLM0001`,
  `STC*A2:20:PR*20260922*WQ*300.00`, `REF*1K*EHP2026000001`.
- Acknowledges the claim in `837P-005010X222A1.x12` (same `CLM0001`,
  `BATCH000001`, payer claim control number as the 835's `CLP07`).
- Invariants: 4 HL, 2 STC, 3 TRN; accepted count 1, accepted amount 300.00.

### `999-005010X231A1.x12` — Implementation Acknowledgment

- `GS*FA`, `GS08 = 005010X231A1`, `ST03 = 005010X231A1`, `ISA13 = 000000105`,
  `GS06 = 105`.
- **14 segments**; `SE*10*0001`.
- `AK1*HC*102*005010X222A1` acknowledges functional group 102 (the 837P
  fixture's `GS06`). Two `AK2*837`: control `0001` accepted (`IK5*A`), control
  `0002` rejected (`IK3*NM1*8*2010AA*8`, `IK4*9*67*7*123456789`, `IK5*R*5`).
  `AK9*P*2*2*1` (partially accepted: 2 received, 2 included, 1 accepted).
- Invariants: 2 AK2, 2 IK5, 1 IK3, 1 IK4, AK9 == `P,2,2,1`.

## `malformed/` — must go to the `.error` path

| File | What is wrong | Expected outcome |
|------|---------------|------------------|
| `truncated-no-iea.x12` | The 835 fixture cut off after `SE*42*0001~`: no `GE`, no `IEA` (44 segments). | Parse fails (unterminated GS/ISA loop); file renamed `.error`, `files.status = error`. |
| `bad-separators.x12` | ISA is 106 chars but declares `\|` as element separator (and `ISA16 = \|` too); every following segment uses `*`. | Separator detection yields `\|`; nothing after ISA splits; parse fails. |
| `unknown-guide-gs08.x12` | Structurally perfect copy of the 835 fixture with `GS08 = 005010X999` (835 has no `ST03` to fall back on). | `TransactionTypes.fileTypeFor("005010X999")` → `unsupported-guide`; `.error`. |
| `empty.x12` | Zero bytes. | Parse fails (no ISA); `.error`. |

`check-x12-structure.py` reports the first, second and fourth as
`EXPECTED-FAIL`; `unknown-guide-gs08.x12` is structurally valid on purpose so
the failure exercised is the guide lookup, not the parser.
