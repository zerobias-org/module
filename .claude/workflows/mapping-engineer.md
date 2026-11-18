# Mapping Engineer Workflow

## Purpose
Implement the mappers in `src/mappers.ts` that turn a raw provider record into a generated model.

All mapper rules (shape, helpers, null handling, formats, dates, enums) are in
@.claude/skills/mapping/SKILL.md. This file gives the order of work and points at the section for each step.

## Input Required
- API specification (`api.yml`) with response schemas
- Generated models in `generated/model/` (run `zbb generate` first)
- A real provider payload or the committed fixtures

## Step-by-Step Process

### Step 1: Analyze API Response Schema

- Read the operation's response schema in `api.yml`
- Note the structure (single object, array, nested), required and optional properties
- Note the provider's field names and formats
- **Schema rules:** See "5. Schemas" and "7. Formats" in @.claude/skills/api-spec/SKILL.md

### Step 2: Review Target Model

- Open `generated/model/<Model>.ts`; it is the authority for field names and types
- Note optional properties and nested models

### Step 3: Create Mapper Function Signature

- One mapper per generated model, in `src/mappers.ts`
- **Signature and naming:** See "The mapper shape" in @.claude/skills/mapping/SKILL.md

### Step 4: Validate Required Fields

- List the properties that are `required` in `api.yml` in the required-field check at the top of the mapper
- **Helper and policy:** See "The four helpers" and "Missing fields and null" in @.claude/skills/mapping/SKILL.md

### Step 5: Implement Field Mappings

- Build one typed `const output` literal that lists every property of the model
- **Field mapping:** See "The mapper shape" in @.claude/skills/mapping/SKILL.md

### Step 6: Handle Core Type Conversions

- Map each field to the type the generated model declares
- **Conversions:** See "Format to class" and "Dates" in @.claude/skills/mapping/SKILL.md
- **Enums:** See "Enums" in @.claude/skills/mapping/SKILL.md. An unknown enum value is fixed in `api.yml`,
  not in the mapper.

### Step 7: Handle Nested Objects

- Nested objects and arrays call the nested model's own mapper
- **Nested and array helpers:** See "The mapper shape" and "The four helpers" in @.claude/skills/mapping/SKILL.md

### Step 8: Handle Optional Fields

- **Optional handling:** See "Missing fields and null" in @.claude/skills/mapping/SKILL.md

### Step 9: Validate Field Coverage

- Every property of the generated model appears once in the output literal
- Run the `check-mappers.sh` script and, for a new operation, compare against a real payload
- **Validation:** See "Check that every field is mapped" in @.claude/skills/mapping/SKILL.md

### Step 10: Array Mapping (if needed)

- Arrays reuse the single-object mapper; no separate array mapper function is needed
- **Array helper:** See "The four helpers" in @.claude/skills/mapping/SKILL.md

### Step 11: Document and Export

- Export each mapper from `src/mappers.ts`
- Comment only what is not obvious from the code (a provider quirk, a casing rule)
- **Comment rules:** See "Comments" in @.claude/skills/implementation/SKILL.md

## Field Validation Workflow

1. Read `api.yml` and identify the required properties
2. Open the generated model and list its properties
3. Put every required property in the required-field check
4. Map every model property in the output literal
5. Run `zbb compile` and the `check-mappers.sh` script
6. Compare with real data: "Temporary raw-response logging" in @.claude/skills/mapping/SKILL.md

## Error Handling

- Missing required field: the helper throws; see "The four helpers" in @.claude/skills/mapping/SKILL.md
- Invalid core-type value or unknown enum value: the core class throws; do not catch it in the mapper
- HTTP errors belong to producers: "Errors" in @.claude/skills/implementation/SKILL.md

## Testing Strategy

- Mappers are tested through the producer's unit tests, against committed fixtures
- Cover required fields, optional fields that are absent, and a record missing a required field
- Assert on the generated value types, not on `Date`
- **Test patterns:** See "Unit tests" and "Fixtures" in @.claude/skills/testing/SKILL.md

## Success Criteria

- Every model property is in the output literal
- Required properties are checked
- Fields hold the types the generated model declares
- Optional properties map to `undefined`, with no invented defaults
- Nested objects and arrays use the nested model's mapper
- `check-mappers.sh` reports nothing new; `zbb compile` and unit tests pass

## Reference Files

- @.claude/skills/mapping/SKILL.md - mapper shape, helpers, formats, dates, enums, coverage check
- @.claude/skills/api-spec/SKILL.md - schema and format rules; `api.yml` is the source of truth
- @.claude/skills/implementation/SKILL.md - producers, errors, comments
- @.claude/skills/testing/SKILL.md - unit tests and fixtures
