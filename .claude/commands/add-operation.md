---
description: Add a single operation to an existing ESM/gradle module (45-70 min, 6 gates)
argument-hint: <module-identifier> <operation-name>
---

Execute the Add Operation workflow for module: $1, operation: $2

`$1` is `<vendor>-[<suite>-]<service>` (e.g. `nist-cmvp`, `avigilon-alta-access`).
`$2` is the new operationId: lowerCamel, a verb plus the full resource, unique in the spec (naming rules in the `api-spec` skill).

`zbb <task>` runs from inside the module directory (`cd package/<vendor>/[<suite>/]<service>`). The six gates are the checkpoints of @.claude/skills/gates/SKILL.md; the commands are in the `build-lifecycle` skill.

First read the module kind from `build.gradle.kts`: `zb.typescript-connector` (connector, has `connectionProfile.yml`) or `zb.typescript` (plain, no profile). Steps marked "connector" do not apply to a plain module.

## Phase 0 — Credentials (connector)

- Check that the slot holds a secret for the module: `zbb --slot <slot> secret list --module <package name>` (see the `testing` skill)
- If it is missing, ask the user to create it before continuing; credentials stay out of code, files and the transcript
- Plain module: nothing to check, the source is public

## Phase 1 — Research

- @api-researcher: **targeted-scope** research for *this one endpoint only*. Test it against the real API (connector: with the slot's credentials). Document parameters, response shape, pagination style, error responses. Save a sanitized sample response.
- @operation-engineer: confirm operationId, naming, and whether the operation belongs in an existing producer tag or a new one

## Phase 2 — API design

- @api-architect: add the path + operationId to `api.yml`, define the response component schema if new
- @schema-specialist: complex schema design (`$ref` composition, polymorphism) if needed
- @api-reviewer: validate against the `api-spec` skill (its `scripts/check-spec.sh`)
- Run: `zbb validate` — must exit 0
- **Gate 1** — API spec passes

## Phase 3 — Type generation

- Run: `zbb generate`
- Verify `generated/api/manifest.json` includes the new operation and `generated/api/index.ts` has the new method (`hub-sdk/generated/` is rebuilt later, by the gate)
- **Gate 2** — generation clean

## Phase 4 — Implementation

- @operation-engineer: add the method to the tag's producer, per the `implementation` skill
- @mapping-engineer: write or extend the mapper per the `mapping` skill, including its "Check that every field is mapped" section
- @style-reviewer: run `zbb compile` (lint runs first, then `tsc`)
- **Gate 3** — lint and compile pass; `scripts/check-src.sh` and `scripts/check-mappers.sh` of those two skills report nothing new

## Phase 5 — Tests

- @mock-specialist: sanitized fixtures for the new operation
- @producer-ut-engineer: extend the tag's unit test file under `test/unit/` with the success path and each error mapping
- @producer-it-engineer (connector): extend `test/e2e/<name>.test.ts` with a `describeModule<T>`-driven case for the new operation. A plain module has no `test/e2e`; unit tests against fixtures are its coverage.
- @ut-reviewer + @it-reviewer: review against the `testing` skill
- Run: `zbb --slot <slot> test`; connectors also `zbb --slot <slot> testDirect` and `zbb --slot <slot> testDocker`
- **Gate 4** — tests exist and are shaped right
- **Gate 5** — tests pass, and the e2e tests really ran rather than being skipped for a missing secret

## Phase 6 — Build & gate

- Follow "The order that produces a valid stamp" in the `gates` skill: static check script, stage the module, `zbb --slot <slot> gate`, stage `gate-stamp.json`, `gateCheck` from the repo root, script with `--final`
- **Gate 6** — gate green and `gateCheck` passes; the stamp is committed with the new code
- @build-reviewer: confirm Gate 6 closed

## Success criteria

- All 6 gates green
- One commit (or a clean conventional-commits sequence) prepared per the `git-and-sdlc` skill; ask the owner before committing
- Only files under `package/<vendor>/[<suite>/]<service>/`, including its `gate-stamp.json`, modified
- New operation tested in unit and, for a connector, at least direct-mode e2e
