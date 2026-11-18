# Build Validation Workflow

## Overview

This workflow covers two checkpoints of the gate:
- **Gate 2**: Type generation (after an `api.yml` change)
- **Gate 6**: Build and stamp (before the task is reported complete)

Both must pass before the work moves on. Commands and what each task runs are in
@.claude/skills/build-lifecycle/SKILL.md; the checkpoint list, the stamp rules and the failure table are in
@.claude/skills/gates/SKILL.md. Run every command from the module directory unless stated otherwise.

## Gate 2: Type Generation Validation

### When to Run
- After any `api.yml` change (also after adding or removing `connectionProfile.yml` or `connectionState.yml`)
- After the API reviewer passes the spec
- Before implementation starts

### Process

1. **Clear previous generation** (only when the output looks stale)
   - `zbb clean`. See "Regenerating after an `api.yml` edit" in @.claude/skills/build-lifecycle/SKILL.md

2. **Run type generation**
   - `zbb generate`
   - A non-zero exit blocks the workflow: report the failing task and its message

3. **List generated files**
   - `generated/api/` and `generated/model/` exist and contain TypeScript files
   - `generated/api/manifest.json` exists
   - `hub-sdk/generated/` is absent at this point; see "Where `hub-sdk/generated` comes from" in
     @.claude/skills/build-lifecycle/SKILL.md

4. **Check for InlineResponse types**
   - `grep -rlE 'InlineResponse|InlineRequestBody' generated/`
   - A hit means a response or request body has an inline schema; report it to the spec owner, who names it
     under `components/schemas` (see @.claude/skills/api-spec/SKILL.md)

5. **Validate TypeScript compilation**
   - `zbb compile` (lint runs first, then `tsc`)
   - On failure report the lint or type errors and block

6. **Report results**
   - Status: PASSED/FAILED
   - File count
   - Any InlineResponse types found
   - Compilation result

### Success Criteria

- Generation exit code 0
- `generated/api/` and `generated/model/` populated
- No InlineResponse types
- `zbb compile` succeeds

### Common Failures

Map the failing task name to its cause with "When the gate fails" in @.claude/skills/gates/SKILL.md. The fix
for a generation failure is in `api.yml`; `generated/` is rewritten on every run.

## Gate 6: Final Build Validation

### When to Run
- After all implementation and tests are finished
- Before the task is reported complete and before a commit is proposed

### Process

Follow "The order that produces a valid stamp" in @.claude/skills/gates/SKILL.md. The steps below keep this
workflow's order and name the command for each.

1. **Source code linting**
   - `zbb lint`; zero errors. Fix the code, not the lint rule.

2. **Static check and staging**
   - Run the static check script from the `gates` skill and clear every `FAIL`
   - The module directory must be staged before the gate, because the stamp hashes tracked files only.
     Staging changes git state: ask the caller to stage, or stage only when the caller has delegated it.

3. **Run build**
   - `zbb build` builds the artifacts and runs no test (see "What `build` and `gate` run" in
     @.claude/skills/build-lifecycle/SKILL.md)
   - A non-zero exit blocks: report the failing task

4. **Validate artifacts**
   - `dist/src/index.js` exists (package.json `main`), with `.d.ts` files beside the JavaScript
   - `hub-sdk/generated/` and `hub-sdk/dist/` exist
   - Paths and what produces them: "What is on disk" in @.claude/skills/build-lifecycle/SKILL.md

5. **Run the gate and verify the stamp**
   - `zbb --slot <slot> gate` writes `gate-stamp.json`
   - Check the stamp with "Reading the stamp" in @.claude/skills/gates/SKILL.md
   - Confirm from the repo root: `./gradlew :<vendor>:[<suite>:]<product>:gateCheck`
   - There is no `npm-shrinkwrap.json` in a module

6. **Type check**
   - Covered by `compile` inside the gate; report any TypeScript error from its output

7. **Report results**
   - Lint status: error and warning counts
   - Build status: PASSED/FAILED
   - Artifact count (JS files, .d.ts files)
   - Stamp: task results, `tests.*` counts, `gateCheck` result
   - Any TypeScript errors

### Success Criteria

- `zbb build` and `zbb gate` exit 0
- `dist/` holds JavaScript and declaration files
- `gate-stamp.json` written on the staged tree and accepted by `gateCheck`
- No lint or TypeScript errors

### Common Failures

Use "When the gate fails" in @.claude/skills/gates/SKILL.md. Two that come up here:

- **`gateCheck` fails right after a green gate:** a hashed file changed or was staged after the gate; stage
  and run the gate again.
- **Plain module fails in `compileServer` or `testDocker`:** see "Connector or plain" in
  @.claude/skills/gates/SKILL.md.

## Output Format

### Gate 2 Output

- **Status:** PASSED or FAILED
- **Generation results:** exit code and the failing task, if any
- **Files generated:** count by directory (api/, model/)
- **Type quality checks:** InlineResponse detection result
- **Compilation:** success or failure
- **Decision:** whether implementation can start

### Gate 6 Output

- **Status:** PASSED or FAILED
- **Build execution:** exit code, failing task if any
- **Artifacts generated:** file tree and counts
- **Gate stamp:** task results, test counts, `gateCheck` result
- **Decision:** whether the task is ready for completion

## Tools Used

- `zbb generate`, `zbb compile`, `zbb lint`, `zbb build`, `zbb --slot <slot> gate`
- `./gradlew :<vendor>:[<suite>:]<product>:gateCheck` (repo root)
- grep, find, ls

## Related Documentation

- @.claude/skills/build-lifecycle/SKILL.md - task graph, commands, what is on disk
- @.claude/skills/gates/SKILL.md - checkpoints, stamp, failure table, static check script
- @.claude/skills/api-spec/SKILL.md - spec fixes for generation failures
