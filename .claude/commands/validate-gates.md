---
description: Run all 6 quality gates for an ESM/gradle module (5–10 min)
argument-hint: <module-identifier>
---

Validate all 6 gates for module: $1

`$1` is `<vendor>-[<suite>-]<service>`; the module directory is `package/<vendor>/[<suite>/]<service>` and the gradle project `:<vendor>:[<suite>:]<service>`.

The six gates are checkpoints inside one gradle run (`zbb gate`), not six tools. The single checklist, the stamp rules, the connector / plain differences and the failure table are in @.claude/skills/gates/SKILL.md; this command only sequences it. Do not add greps of your own: the static part is one script.

## Step 1 — Static check (seconds, no gradle)

From the repo root:

```bash
bash .claude/skills/gates/scripts/check-module.sh package/<vendor>/[<suite>/]<service>
```

It prints the plugin, the module kind (connector or plain), the test counts, and `PASS` / `WARN` / `FAIL` lines grouped under the six checkpoints. Compare the kind and the counts with what you expect, then fix every `FAIL`. A clean run is a reason to start the gate, not evidence that the gate passes.

## Step 2 — Is the committed stamp still valid?

```bash
./gradlew :<vendor>:[<suite>:]<service>:gateCheck      # repo root; needs Java 21, no slot
```

- Passes and Step 1 has no `FAIL`: all six gates hold for this tree. Go to the report.
- Fails, or there is no stamp: continue with Step 3.

## Step 3 — Per-gate run (to see which gate fails)

From the module directory, in order; stop at the first failure and look it up in "When the gate fails" in the `gates` skill.

| Gate | Checkpoint | Command |
|---|---|---|
| 1 | API spec | `zbb validate` |
| 2 | Type generation | `zbb generate` |
| 3 | Implementation | `zbb compile` (lint, then tsc) |
| 4 | Tests exist | no task; read the counts from Step 1 |
| 5 | Tests pass | `zbb --slot <slot> test`; connector also `zbb --slot <slot> testDirect` and `zbb --slot <slot> testDocker` |
| 6 | Build and stamp | Step 4 |

A plain (profile-less) module has no `test/e2e`, builds no image and skips `testDirect` / `testDocker`; its stamp records those suites as `skipped`, which passes. For a connector, confirm the e2e tests ran: with no `zbb secret` for the module the suite is skipped and the task still exits 0.

## Step 4 — Full gate and stamp

Follow "The order that produces a valid stamp" in the `gates` skill: stage the module directory, `zbb --slot <slot> gate` from the module directory, stage `gate-stamp.json`, `gateCheck` again, then the script with `--final`:

```bash
bash .claude/skills/gates/scripts/check-module.sh package/<vendor>/[<suite>/]<service> --final
```

Staging changes the index, and the gate rewrites `gate-stamp.json`. When the user asked only for a report, stop after Step 3 and say that the stamp needs a new gate run. Committing follows the `git-and-sdlc` skill (ask the owner first).

## Output format

Report gate status from the task exit codes and the stamp, with the evidence (failing task name, script line, stamp field), not from the script alone.

```
VALIDATION RESULTS: <module-id>   (kind: connector | plain)

Gate 1: API spec            [PASS | FAIL]   validate
Gate 2: Type generation     [PASS | FAIL]   generate
Gate 3: Implementation      [PASS | FAIL]   lint + compile
Gate 4: Tests exist         [PASS | FAIL]   unit <n>, e2e <n>
Gate 5: Test execution      [PASS | FAIL | SKIPPED: reason]   test / testDirect / testDocker / testDataloader
Gate 6: Build + stamp       [PASS | FAIL]   gate + gateCheck

Overall: [ALL GATES PASSED | FAILURES]

[Per failure: the fix and the skill that owns it.]
```

## Examples

```
/validate-gates nist-cmvp
/validate-gates avigilon-alta-access
```
