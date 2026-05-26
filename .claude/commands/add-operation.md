---
description: Add a single operation to an existing ESM/gradle module (45-70 min, 6 gates)
argument-hint: <module-identifier> <operation-name>
---

Execute the Add Operation workflow for module: $1, operation: $2

`$1` is `<vendor>-[<suite>-]<service>` (e.g. `github-github`, `amazon-aws-s3`).
`$2` is the new operationId (must match `^[a-z][a-zA-Z0-9]+$` and start with `list`, `get`, `create`, `update`, `delete`, or `search`).

All commands run from inside the module directory (`cd package/<vendor>/[<suite>/]<service>`).

## Phase 0 — Credentials (mandatory)

- Check that the target slot has the credentials/secrets the operation needs: `zbb secret list --slot local` and `zbb env list --slot local`
- If anything is missing, **ask the user** before continuing
- Never put credentials in code or in a `.env` file

## Phase 1 — Research

- @api-researcher: **targeted-scope** research for *this one endpoint only*. Test it against the real API with the slot's credentials. Document parameters, response shape, pagination style, error responses. Save a sanitized sample response.
- @operation-engineer: confirm operationId, naming, and whether the operation belongs in an existing producer tag or a new one

## Phase 2 — API design

- @api-architect: add the path + operationId to `api.yml`, define the response component schema if new
- @schema-specialist: complex schema design (`$ref` composition, polymorphism) if needed
- @api-reviewer: validate against `@.claude/skills/gate-api-spec/SKILL.md`
- Run: `zbb bundleSpec` — must exit 0
- **Gate 1** — API spec passes the checklist

## Phase 3 — Type generation

- Run: `zbb generate`
- Verify `generated/api/manifest.json` includes the new operation
- Verify `hub-sdk/generated/api/index.ts` exports the new method
- **Gate 2** — `@.claude/skills/gate-type-generation/SKILL.md` clean

## Phase 4 — Implementation

- @operation-engineer: add the method to the appropriate `<Tag>ProducerImpl.ts`
- @mapping-engineer: write or extend the mapper; use `map()` from `@zerobias-org/util-hub-module-utils`
- @style-reviewer: run `zbb lint`
- Run mapper runtime validation per `@.claude/skills/mapper-runtime/SKILL.md` — zero missing fields
- **Gate 3** — `@.claude/skills/gate-implementation/SKILL.md` clean

## Phase 5 — Tests

- @mock-specialist: sanitized fixtures for the new operation
- @producer-ut-engineer: extend `test/unit/<Tag>ProducerTest.test.ts` with happy + at least one error path
- @producer-it-engineer: extend `test/e2e/<name>.test.ts` with a `describeModule<T>`-driven case for the new operation
- @ut-reviewer + @it-reviewer: run gates
- Run: `zbb test --slot local` and `zbb testDirect --slot local`; connectors also `zbb testDocker --slot local`
- **Gate 4** — tests exist and are shaped right
- **Gate 5** — tests pass

## Phase 6 — Build & gate

- Run: `zbb gate --slot local`
- **Gate 6** — `gate-stamp.json` updated; commit it alongside the new code
- @build-reviewer: confirm Gate 6 closed

## Success criteria

- All 6 gates green
- One commit (or a clean conventional-commits sequence) staged
- Only files under `package/<vendor>/[<suite>/]<service>/` + the module's `gate-stamp.json` modified
- New operation tested in unit and at least direct-mode e2e
