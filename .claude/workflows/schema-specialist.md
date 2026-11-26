# Schema Specialist Workflow

## Overview

This workflow covers designing the schemas in `api.yml`, with a focus on reuse, composition and clean data
modelling. The rules are in @.claude/skills/api-spec/SKILL.md ("5. Schemas", "6. Enums", "7. Formats") with
YAML examples in @.claude/skills/api-spec/references/rule-catalogue.md. Where a rule and a gate-green spec
disagree, the spec is right.

## Input

- API research findings (external API responses)
- Resource definitions
- Business requirements

## Output

- Schemas under `components/schemas`
- Base schemas for composition, where properties are shared
- Reduced and full views (`Summary`, `Info`) where the provider has them
- A spec that passes the checker script and `zbb validate`

## Detailed Steps

### Step 1: Identify Unique Objects

Review the API research findings and list:

1. **Main resources** - these become top-level schemas
2. **Nested objects** - these become supporting schemas, prefixed with the owning resource
3. **Common properties** - properties that appear across resources and may justify a base schema

**Naming and suffixes:** See "5. Schemas" in @.claude/skills/api-spec/SKILL.md

### Step 2: Design Base Schemas

Create a base schema when several schemas share properties that are identical in type, format and meaning.

**Example structure:** See "7. Schema families" in @.claude/skills/api-spec/references/rule-catalogue.md

### Step 3: Create Full Schemas with Composition

Use `allOf` to extend a base schema or to build the detailed view on top of the plain one.

**Composition patterns:** See "7. Schema families" in @.claude/skills/api-spec/references/rule-catalogue.md

### Step 4: Create Nested Object Schemas

- Give a nested object its own named schema and `$ref` it when it is reused or has a meaning of its own
- Mark the properties that are always present as required
- Small one-off nested objects may stay inline

**Nested patterns:** See "5. Schemas" in @.claude/skills/api-spec/SKILL.md

### Step 5: Apply Formats

Set `format` wherever the value has one, because it decides the generated TypeScript type.

**Format table:** See "7. Formats" in @.claude/skills/api-spec/SKILL.md
**Format to class and mapper side:** See "Format to class" in @.claude/skills/mapping/SKILL.md

### Step 6: Consider Context Separation

Decide whether a resource needs a reduced view (`Summary`) beside the full schema, for a list that returns
less than the get, or for nesting inside another resource.

**When to separate and how:** See the suffix table in "5. Schemas" of @.claude/skills/api-spec/SKILL.md and
"7. Schema families" in @.claude/skills/api-spec/references/rule-catalogue.md

### Step 7: Document External API Mapping

- Describe every schema and every property that is not self-evident
- Record how each property maps to the provider's field (name change, format change) in the design report,
  so the mapping engineer can use it

### Step 8: Validate Schema Quality

1. Run the checker script and `zbb validate`: see "Quick check" in @.claude/skills/api-spec/SKILL.md
2. No inline schema in a response or request body that would generate an `InlineResponse` type
3. No `nullable`; an absent property is the convention
4. Identifier types follow the provider
5. Formats applied

**What fails the lint:** See "9. What fails `validate`" in @.claude/skills/api-spec/SKILL.md

### Step 9: Review Against Rules

Check the spec against "5. Schemas", "6. Enums", "7. Formats" and "8. Responses" in
@.claude/skills/api-spec/SKILL.md:

- [ ] A paged list response is a plain array of the item schema, not an envelope
- [ ] No `nullable`
- [ ] Identifier types follow the provider
- [ ] Reused nested objects are named schemas
- [ ] Formats applied
- [ ] Reduced and full views where the provider has them
- [ ] `allOf` where properties are shared
- [ ] Schemas and properties described

Do not enforce the older rules listed in "15. Rules the specs do not support" of
@.claude/skills/api-spec/references/rule-catalogue.md.

## Common Decisions

| Decision | Where the answer is |
|---|---|
| Create a base schema? | `Base` row in "5. Schemas" of @.claude/skills/api-spec/SKILL.md |
| Separate a reduced view from the full schema? | `Summary` and `Info` rows in "5. Schemas"; "7. Schema families" in the rule catalogue |
| Use `allOf`? | "7. Schema families" in @.claude/skills/api-spec/references/rule-catalogue.md |
| Named enum or inline? | "6. Enums" in @.claude/skills/api-spec/SKILL.md |

## Output Format

Report the schema design decisions:

- **Schemas created:** all schemas, by kind (base, full, reduced, nested)
- **Composition strategy:** how schemas extend or compose
- **Validation results:** checker script and `zbb validate` output
- **External API mapping:** field-by-field mapping

## Success Criteria

- No inline schema that generates an `InlineResponse` type
- No duplicate schema definitions
- Formats applied
- Composition with `allOf` where properties are shared
- No `nullable` in the spec
- Identifier types follow the provider
- External API mapping documented in the report
- Checker script and `zbb validate` pass

## Tools Used

- `check-spec.sh` from the `api-spec` skill and `zbb validate`
- yq, grep
- API research findings (external API structure)

## Related Documentation

- @.claude/skills/api-spec/SKILL.md - schema, enum, format and response rules; checker script
- @.claude/skills/api-spec/references/rule-catalogue.md - YAML examples per rule
- @.claude/skills/mapping/SKILL.md - format-to-class table and mapper side
- @.claude/skills/gates/SKILL.md - where the spec check sits in the gate
