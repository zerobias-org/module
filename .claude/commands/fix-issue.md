---
description: Diagnose and fix a problem in an existing ESM/gradle module (15 min – 2 hours)
argument-hint: <module-identifier> [issue-description]
---

Diagnose + fix a module: $1

`$1` is `<vendor>-[<suite>-]<service>` (e.g. `github-github`, `amazon-aws-s3`).
`$2` (optional) is a free-text description of the symptom.

If `$2` is empty, start with the diagnostic pass and let the symptom emerge.

## Phase 1 — Diagnose

Run, in order:

```bash
cd package/<vendor>/[<suite>/]<service>

zbb gateCheck                                             # is the stamp consistent with source?
zbb bundleSpec                                            # does the spec still bundle?
zbb generate                                              # does codegen still succeed?
zbb compile                                               # does it compile?
zbb lint                                                  # does eslint pass?
zbb test --slot local                                     # unit tests
zbb testDirect --slot local                               # e2e direct (if credentials)
```

The first failing step localizes the issue. Don't run later steps until earlier ones pass.

## Phase 2 — Categorize

| Failing step               | Probable cause                                    | Specialist                       |
|----------------------------|---------------------------------------------------|----------------------------------|
| `zbb gateCheck` exits 1    | Source changed since last `zbb gate`              | (just re-run `zbb gate`)         |
| `zbb bundleSpec`           | `$ref` won't resolve / malformed YAML             | @api-reviewer + @api-architect   |
| `zbb generate`             | Spec parses, codegen rejects (inline schemas, bad operationId) | @api-reviewer            |
| `zbb compile`              | Source uses removed/renamed generated type, or impl drift | @typescript-expert + @operation-engineer |
| `zbb lint`                 | Eslint violation                                  | @style-reviewer                  |
| `zbb test`                 | Unit test failing — nock URL mismatch, mapper drift, or impl bug | @producer-ut-engineer + the relevant impl owner |
| `zbb testDirect`           | Real API shape changed or fixture missing         | @producer-it-engineer            |
| `zbb testDocker`           | Packaging issue / env not on slot                 | @connection-it-engineer + @build-reviewer |

## Phase 3 — Fix

Route the fix to the specialist. Common shapes:

- **Spec needs change → regen → impl follow-up**: @api-architect edits `api.yml`, then run `zbb generate`, then @operation-engineer / @mapping-engineer absorbs the new types
- **Mapper drift**: run the runtime validation pass in `@.claude/skills/mapper-runtime/SKILL.md`, surface the missing/renamed field, fix the mapper or the spec
- **Test cross-talk**: check `nock.pendingMocks()` and `nock.cleanAll()` per `@.claude/skills/nock-mocking/SKILL.md`

## Phase 4 — Validate

After the fix:

```bash
# Re-run the failing step + everything downstream
zbb <failing-task>
# ... then upward through the chain ...
zbb gate --slot local
```

`zbb gate` re-runs from cold. If it goes green and writes a new `gate-stamp.json`, the fix sticks.

## Phase 5 — Regression check

- Confirm `gate-stamp.json` updated and committed
- Confirm no sibling-module files touched
- Re-run the original failing command to prove the fix
- Check that other test cases the impl touches still pass

## Common patterns

- **`Cannot find module './X'`** at compile or runtime — relative import missing `.js`. ESM rule.
- **`Got 404 against https://…`** in unit test — nock URL doesn't match producer URL. Print `nock.activeMocks()` and the producer's URL side by side.
- **`expiresIn` missing on connection refresh** — `connectionState.yml` doesn't extend `baseConnectionState`. Add the `allOf` wrapper.
- **`InlineResponse200` types appearing** — inline schema in `api.yml`. Hoist to `components/schemas/` and `$ref` it.
- **CI fails with `gate-stamp.json is missing or invalid`** — you didn't run `zbb gate` after the fix, or you edited a tracked file after running it. Run again, commit the new stamp.

## Success criteria

- All lifecycle tasks green from cold (`zbb clean && zbb build`)
- `zbb gate --slot local` green
- `gate-stamp.json` current and committed
- No regression in tests that previously passed
- Only files inside the target module path were touched

## Example

```
/fix-issue github-github "listWebhooks returns empty array on docker mode"
/fix-issue amazon-aws-s3
```
