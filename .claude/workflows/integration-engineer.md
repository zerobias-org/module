# Integration Engineer Workflow

## Overview

This workflow covers the communication layer between a module and the external API: the Client, the error
handler and the producers. Code shape and file layout are defined in @.claude/skills/implementation/SKILL.md;
read the closest reference module named at the top of that skill before writing code.

## Input

- API specification (`api.yml`)
- Connection profile schema (`connectionProfile.yml`, connector modules only)
- Generated types and interfaces (`generated/api/`, `generated/model/`)

## Output

- Client class (`src/<Name>Client.ts`)
- Producer classes (`src/<Tag>ProducerImpl.ts`, one per `api.yml` tag; `src/` is flat)
- Error handling in `src/util.ts`
- Unit tests passing; e2e tests passing for a connector

## Detailed Steps

### Step 1: Review API Specification

- Read `api.yml` and the generated interfaces
- Identify base URL, authentication method, operations, request and response formats
- Note how the provider reports errors
- Record findings in `.claude/.localmemory/{workflow}-{module}/integration-notes.md`

### Step 2: Review Connection Profile

- Connector: read `connectionProfile.yml`, note the credential fields and which are required
- Plain module: there is no profile and no lifecycle; skip the connect/disconnect parts of Step 3
- **Profile patterns:** See "2. Pick the profile shape (connector)" in @.claude/skills/connection-profile/SKILL.md

### Step 3: Implement Client Class

Create or edit `src/<Name>Client.ts`:

1. **connect()** - create the axios instance from the profile; return a state only when the module has a
   `connectionState.yml` (the generated interface shows the signature)
2. **isConnected()** - as described in the skill
3. **disconnect()** - clear the token and the axios instance

The Client exposes the axios instance to producers and has no HTTP helper or resource methods.

**Patterns:** See "The Client" in @.claude/skills/implementation/SKILL.md and
@.claude/skills/implementation/references/client.md

### Step 4: Implement Error Handling

- Put the status-to-core-error mapping in one `handleAxiosError` function in `src/util.ts`
- Use one mechanism per module (per-call handling or a response interceptor), not both

**Error mapping:** See "Errors" in @.claude/skills/implementation/SKILL.md
**Reference handler and interceptor choice:** See "Reference `handleAxiosError`" and "Per-call handling or
interceptor" in @.claude/skills/implementation/references/errors.md

### Step 5: Implement Producer Classes

For each `api.yml` tag:

1. Create `src/<Tag>ProducerImpl.ts` implementing the generated `<Tag>ProducerApi`
2. Copy the method signatures from `generated/api/`; codegen decides parameters and return types
3. Call the API through the Client's axios instance and pass failures to the error handler
4. Return the mapper's result; where a `200` can still mean "not found", check the body first

**Producer patterns:** See "Producers" in @.claude/skills/implementation/SKILL.md
**List operations:** See "List operations and pagination" in @.claude/skills/implementation/SKILL.md and
@.claude/skills/implementation/references/pagination.md
**Mappers:** @.claude/skills/mapping/SKILL.md

### Step 6: Configure Timeouts and Retries

- Set a `timeout` in `axios.create` (see "The Client" in @.claude/skills/implementation/SKILL.md)
- Retries: add them only when the provider's behaviour calls for it. Retry transient failures (5xx, network
  errors) with backoff and a small attempt limit; a 4xx is the caller's error and is not retried. No skill
  covers retries, so record the choice in the decisions file (Step 10).

### Step 7: Validate Implementation

- Run the `check-src.sh` script described under "No environment access, no secrets" in
  @.claude/skills/implementation/SKILL.md and clear its findings
- Go through "Finishing" in @.claude/skills/implementation/SKILL.md (every generated method implemented, no
  `any` return type)

### Step 8: Run Unit Tests

Run `zbb --slot <slot> test` from the module directory.

Verify:
- HTTP calls are mocked with nock
- Each operation's success path and each error mapping is covered
- Connector: the connection lifecycle is covered

On failure fix the implementation or the mock and run again.

**Testing patterns:** See "Unit tests" in @.claude/skills/testing/SKILL.md

### Step 9: Run Integration Tests

Connector modules: run `zbb --slot <slot> testDirect` from the module directory. A plain module has no
`test/e2e`; see "Plain modules: no `test/e2e`" in @.claude/skills/testing/SKILL.md.

Verify:
- The suite really ran (a missing secret records one skipped test and stays green)
- Authentication and error handling work against the real API

On failure:
- Check the module's zbb secret: see "Credentials: zbb secrets" in @.claude/skills/testing/SKILL.md
- Check the base URL and network connectivity

**e2e testing:** See "e2e tests (connector modules)" in @.claude/skills/testing/SKILL.md

### Step 10: Document Decisions

Create `.claude/.localmemory/{workflow}-{module}/integration-decisions.md` with:
- Timeout configuration and justification
- Retry strategy, if any
- Error-handling mechanism chosen (per-call or interceptor)
- Deviations from the reference modules, with the reason

## Common Issues

| Symptom | Fix | Reference |
|---|---|---|
| `throw new Error(` in `src/` | throw the matching core error | "Errors" in @.claude/skills/implementation/SKILL.md |
| Every failure becomes `UnexpectedError` 500 | interceptor and per-call handler combined; keep one | "Per-call handling or interceptor" in @.claude/skills/implementation/references/errors.md |
| Wrong pages from a list | see the mistakes list | "Mistakes that produce wrong pages" in @.claude/skills/implementation/references/pagination.md |
| e2e green with zero tests | no secret in the slot, or file not `*.test.ts` | "Quick diagnosis" in @.claude/skills/testing/SKILL.md |

## Success Criteria

- Client implements the lifecycle for the module kind
- All failures surface as core errors
- Producers implement every generated operation with generated types
- Timeout configured
- Unit tests pass with nock; e2e tests pass for a connector
- No credentials in error messages or logs

## Tools Used

- axios
- nock (unit tests)
- Core error types from `@zerobias-org/types-core-js`
- Mapper functions from `src/mappers.ts`

## Related Documentation

- @.claude/skills/implementation/SKILL.md - Client, producers, errors, pagination
- @.claude/skills/mapping/SKILL.md - mappers
- @.claude/skills/testing/SKILL.md - unit and e2e tests, zbb secrets
- @.claude/skills/connection-profile/SKILL.md - profile and state
- @.claude/skills/gates/SKILL.md - checkpoints and the stamp
