# Operation Engineer Workflow

## Overview

This workflow covers the producer classes, which hold the logic of the API operations: one producer per
`api.yml` tag. The code shape is defined in "Producers" and "List operations and pagination" of
@.claude/skills/implementation/SKILL.md; read the closest reference module named at the top of that skill
before writing code.

## Input

- Generated interfaces and models (`generated/api/`, `generated/model/`), regenerated after the last
  `api.yml` change
- The Client class
- `api.yml` (to understand the operations)
- Mapper functions (`src/mappers.ts`)

## Output

- Producer files in `src/`, one per tag
- Every method of the generated `<Tag>ProducerApi` implemented
- Errors raised as core errors
- Results returned through mappers

## Key Principles

- Operation logic lives in producers; the Client only handles the connection
- Producer methods take no credentials, tokens or base URLs; those come from the Client
- Signatures are copied from the generated interface, so no `Promise<any>`
- Conversion happens in mappers
- Failures surface as core errors

## Detailed Steps

### Step 1: Create Producer File

- Create the file in `src/` (flat, no subfolder)
- **File and class naming:** See "Layout and naming" in @.claude/skills/implementation/SKILL.md

### Step 2: Import Dependencies

- Generated API interface and models, the Client, mapper functions, core errors, `handleAxiosError`
- **Import rules:** See "Imports" in @.claude/skills/implementation/SKILL.md

### Step 3: Create Producer Class Structure

- The class implements the generated `<Tag>ProducerApi` and receives the Client in its constructor
- Copy the method signatures from `generated/api/index.ts`; see "Before writing code" in
  @.claude/skills/implementation/SKILL.md
- **Class structure:** See "Producers" in @.claude/skills/implementation/SKILL.md

### Step 4: Implement List Operation

- Check the generated signature: a paged list fills a `PagedResults` instead of returning an array
- Pick the pagination mode the provider supports
- **List pattern:** See "List operations and pagination" in @.claude/skills/implementation/SKILL.md and read
  @.claude/skills/implementation/references/pagination.md before writing the method

### Step 5: Implement Get Operation

- Request the resource, pass failures to the error handler with the resource type and id, return the
  mapper's result
- Where a `200` can still mean "not found", check the body and throw the not-found core error
- **Get pattern:** See "Producers" and "Errors" in @.claude/skills/implementation/SKILL.md

### Step 6: Implement Create Operation

- Send the request body, pass failures to the error handler, return the mapped result
- **Pattern:** See "Producers" in @.claude/skills/implementation/SKILL.md

### Step 7: Implement Update Operation

- Send the update with the method the provider uses (PUT or PATCH), pass failures to the error handler,
  return the mapped result
- **Pattern:** See "Producers" in @.claude/skills/implementation/SKILL.md

### Step 8: Implement Delete Operation

- Send the delete, pass failures to the error handler, return what the generated signature declares
- **Pattern:** See "Producers" in @.claude/skills/implementation/SKILL.md

### Step 9: Validate Implementation

- Run the `check-src.sh` script described under "No environment access, no secrets" in
  @.claude/skills/implementation/SKILL.md and clear its findings
- Go through "Finishing" in the same skill: every generated method implemented with the generated
  signature and no `any` return type
- No connection values in method parameters; no inline conversion in the producer

### Step 10: Build and Test

- `zbb compile`, then `zbb --slot <slot> test` from the module directory
- Fix compilation and test errors in the implementation; commands are in
  @.claude/skills/build-lifecycle/SKILL.md

## Common Patterns

- **Input validation:** validate required business parameters and throw `InvalidInputError` for a missing
  or invalid one
- **Response handling:** return the mapper's result, not the raw response (@.claude/skills/mapping/SKILL.md)
- **Error propagation:** one error-handling mechanism per module; see "Errors" in
  @.claude/skills/implementation/SKILL.md and "Calling it" in
  @.claude/skills/implementation/references/errors.md

## Anti-Patterns to Avoid

| Anti-pattern | Why | Reference |
|---|---|---|
| Credentials, tokens or base URL as method parameters | the Client owns the connection; a producer with its own values bypasses `disconnect()` | "Producers" in @.claude/skills/implementation/SKILL.md |
| `Promise<any>` return types | hides mapping mistakes from the compiler | "Producers" in @.claude/skills/implementation/SKILL.md |
| Returning raw `response.data` | the caller expects the generated model | "The mapper shape" in @.claude/skills/mapping/SKILL.md |
| Paging by hand when codegen gave an array | the spec lacks the `link` header | "3. Pagination" in @.claude/skills/api-spec/SKILL.md |

## Success Criteria

- Every generated operation is implemented in a producer
- Generated types throughout, no `Promise<any>`
- Mappers do the conversion
- Core errors only
- No connection values in parameters
- `zbb compile` and unit tests pass

## Related Documentation

- @.claude/skills/implementation/SKILL.md - producers, pagination, errors, check script
- @.claude/skills/mapping/SKILL.md - mappers
- @.claude/skills/api-spec/SKILL.md - pagination and operation naming in the spec
- @.claude/skills/gates/SKILL.md - checkpoints and failure table
