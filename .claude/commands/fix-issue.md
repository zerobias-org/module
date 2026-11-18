---
description: Diagnose and fix a problem in an existing ESM/gradle module (15 min – 2 hours)
argument-hint: <module-identifier> [issue-description]
---

Diagnose + fix a module: $1

`$1` is `<vendor>-[<suite>-]<service>` (e.g. `nist-cmvp`, `avigilon-alta-access`).
`$2` (optional) is a free-text description of the symptom.

If `$2` is empty, start with the diagnostic pass and let the symptom emerge.

## Phase 1 — Diagnose

Run, in order (commands and what each task runs: `build-lifecycle` skill):

```bash
# from the repo root
bash .claude/skills/gates/scripts/check-module.sh package/<vendor>/[<suite>/]<service>   # static check, seconds
./gradlew :<vendor>:[<suite>:]<service>:gateCheck          # is the committed stamp consistent with the tree?

cd package/<vendor>/[<suite>/]<service>
zbb validate                                               # does the spec lint?
zbb generate                                               # does it bundle and does codegen succeed?
zbb compile                                                # eslint, then tsc
zbb --slot <slot> test                                     # unit tests
zbb --slot <slot> testDirect                               # connector: e2e in-process (needs the module's zbb secret)
```

The script prints the module kind (connector or plain). A plain module has no `test/e2e`, so `testDirect` and `testDocker` do not apply to it. A failing `gateCheck` only says the stamp is stale; continue. Among the build steps, the first one that fails localizes the issue, so fix it before running later ones.

## Phase 2 — Categorize

| Failing step               | Probable cause                                    | Specialist                       |
|----------------------------|---------------------------------------------------|----------------------------------|
| `gateCheck`                | A hashed file changed or was staged after the last gate | (re-run the gate in Phase 4) |
| `zbb validate`             | Structurally invalid spec, unresolved `$ref`, bad example | @api-reviewer + @api-architect |
| `zbb generate`             | `bundleSpec` cannot resolve a `$ref`, or codegen rejects the spec | @api-reviewer            |
| `zbb compile` (lint)       | Eslint violation                                  | @style-reviewer                  |
| `zbb compile` (tsc)        | Source uses removed/renamed generated type, or impl drift | @typescript-expert + @operation-engineer |
| `zbb test`                 | Unit test failing — nock URL mismatch, mapper drift, or impl bug | @producer-ut-engineer + the relevant impl owner |
| `zbb testDirect`           | Real API shape changed, or the secret is missing or wrong | @producer-it-engineer    |
| `zbb testDocker`           | Packaging issue, wire serialization, or `test/e2e` in a plain module | @connection-it-engineer + @build-reviewer |

For a failure inside `zbb gate`, the "When the gate fails" table in @.claude/skills/gates/SKILL.md maps each failing task to its cause, including the two plain-module traps.

## Phase 3 — Fix

Route the fix to the specialist. Common shapes:

- **Spec needs change → regen → impl follow-up**: @api-architect edits `api.yml`, then run `zbb generate`, then @operation-engineer / @mapping-engineer absorbs the new types
- **`<value> is not a valid <Enum>`**: add the missing values to the enum in `api.yml` and regenerate; follow the "Enums" section of the `mapping` skill
- **Mapper drift**: run "Check that every field is mapped" in the `mapping` skill, find the missing or renamed field, fix the mapper or the spec
- **Test cross-talk**: check nock clean-up and pending mocks per the `testing` skill (`references/unit-tests.md`)

Fix the cause; do not skip a test, loosen an assertion or edit `gate-stamp.json` to get a step green.

## Phase 4 — Validate

After the fix, re-run the failing step and everything after it in the Phase 1 list, then run the gate in the order the `gates` skill gives ("The order that produces a valid stamp"): stage the module, `zbb --slot <slot> gate`, stage `gate-stamp.json`, `gateCheck` from the repo root.

## Phase 5 — Regression check

- Confirm `gateCheck` passes on the new stamp; commit it with the fix per the `git-and-sdlc` skill (ask the owner before committing)
- Confirm no sibling-module files touched
- Re-run the original failing command to prove the fix
- Check that other test cases the impl touches still pass

## Common patterns

The `testing` skill ("Quick diagnosis") and the `gates` skill ("When the gate fails") hold the full symptom tables. The ones met most often:

- **`Cannot find module './X'`** at compile or runtime — relative import missing `.js` (ESM).
- **Unit test hits the real API or fails with a NetConnect error** — the nock URL or path differs from what the client calls. Print `nock.activeMocks()` and compare with the client's URL constants.
- **`expiresIn` missing on connection refresh** — `connectionState.yml` is not built on a core state schema; see the `connection-profile` skill.
- **`InlineResponse200` types appearing** — inline schema in `api.yml`. Hoist to `components/schemas/` and `$ref` it.
- **e2e task green, zero tests ran** — no secret for the module in the slot.
- **`compileServer` fails, or `testDocker` reports "module-container.json not found"** — plain-module traps (`"interface": true` missing, or `test/e2e` present); see the `gates` skill.
- **CI rejects `gate-stamp.json`** — the gate ran before the files were staged, or a hashed file changed afterwards. Stage, run the gate again, commit the new stamp.

## Success criteria

- `zbb --slot <slot> gate` green from a clean tree (`zbb clean` first)
- `gateCheck` passes; `gate-stamp.json` committed
- No regression in tests that previously passed
- Only files inside the target module path were touched

## Example

```
/fix-issue avigilon-alta-access "listUsers returns empty array on docker mode"
/fix-issue nist-cmvp
```
