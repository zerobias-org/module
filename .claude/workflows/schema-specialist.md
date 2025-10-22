# Schema Specialist Workflow

## Overview

This workflow covers designing OpenAPI schemas in api.yml with focus on reusability, composition, and clean data modeling.

## Input

- API research findings (external API responses)
- Resource definitions
- Business requirements

## Output

- Complete schemas in components/schemas
- Base schemas for composition
- Context-separated schemas (Summary vs Full)
- Validated schema structure

## Detailed Steps

### Step 1: Identify Unique Objects

Review API research findings and identify all unique business objects:

1. **List main resources:**
   - User, Webhook, Repository, Organization, etc.
   - These become top-level schemas

2. **List nested objects:**
   - WebhookConfig, RepositoryPermissions, etc.
   - These become supporting schemas

3. **Identify common properties:**
   - Properties like id, createdAt, updatedAt that appear across resources
   - Consider creating base schemas for these

### Step 2: Design Base Schemas

Create base schemas for common properties when:
- 3+ schemas share the same properties
- Properties are truly identical (same type, format, meaning)

**When to create base:** See "Base Schema Pattern" in @.claude/rules/api-spec-schemas.md
**Example structure:** See "ResourceBase Pattern" in @.claude/rules/api-spec-schemas.md

### Step 3: Create Full Schemas with Composition

Use allOf for composition:
- Extend base schemas with additional properties
- Compose Summary + additional fields to create Full schemas
- Reuse common property sets

**Composition patterns:** See "Schema Composition with allOf" in @.claude/rules/api-spec-schemas.md
**When to use allOf:** See "Composition Strategy" in @.claude/rules/api-spec-schemas.md

### Step 4: Create Nested Object Schemas

**CRITICAL:** ALL nested objects MUST use $ref, never inline

For each nested object:
- Create separate schema in components/schemas
- Define properties with appropriate types
- Mark required fields
- Reference using $ref from parent schema

**Nested patterns:** See "Nested Object References" in @.claude/rules/api-spec-schemas.md

### Step 5: Apply Formats

Apply appropriate formats to properties:
- **uuid**: For ID fields (when API returns UUID format)
- **date-time**: For timestamps (ISO 8601)
- **uri**: For URLs
- **email**: For email addresses

**All formats:** See @.claude/rules/type-mapping.md for complete format reference
**Format application:** See "Format Usage" in @.claude/rules/api-spec-schemas.md

### Step 6: Consider Context Separation

If schema is used in BOTH nested and direct contexts AND has 10+ properties:

**Create two versions:**
- **Summary schema:** Minimal fields for nested usage (id, name, key properties)
- **Full schema:** Extends summary with all additional fields

**When to separate:** See "Summary vs Full Decision" in @.claude/rules/api-spec-schemas.md
**Pattern:** See "Context Separation Pattern" in @.claude/rules/api-spec-schemas.md

### Step 7: Document External API Mapping

Add comments documenting how each field maps to external API:
- Map internal camelCase names to external snake_case
- Document any transformations or conversions
- Note format changes (string → date-time, etc.)

**Mapping comments:** See "External API Mapping" in @.claude/rules/api-spec-schemas.md

### Step 8: Validate Schema Quality

Run validation scripts:
1. Check no inline schemas in responses
2. Verify no nullable usage (CRITICAL - forbidden)
3. Confirm all IDs are type: string
4. Ensure nested objects use $ref (not inline)
5. Verify formats applied correctly

**All validation scripts:** See "Schema Validation" in @.claude/rules/api-spec-schemas.md

### Step 9: Review Against Rules

Check compliance with schema rules (Rules 14-24 in @.claude/rules/implementation.md):

- [ ] Responses reference main business object only (not envelope)
- [ ] NO nullable anywhere
- [ ] ALL IDs use type: string
- [ ] Nested objects use $ref
- [ ] Formats applied appropriately
- [ ] Context separation where needed (10+ properties)
- [ ] Composition with allOf where appropriate
- [ ] External API mapping documented

**Complete checklist:** See "Schema Quality Checklist" in @.claude/rules/api-spec-schemas.md

## Common Decisions

### When to Create Base Schema?

**Create base when:**
- 3+ schemas share the same properties
- Properties are truly identical (same type, format, meaning)

**Don't create base when:**
- Only 1-2 schemas share properties
- Shared properties have different meanings

**Decision guide:** See "Base Schema Decision" in @.claude/rules/api-spec-schemas.md

### When to Separate Summary vs Full?

**Separate when:**
- Schema used in BOTH nested AND direct contexts
- Schema has 10+ properties
- Nested usage doesn't need all fields

**Don't separate when:**
- Schema only used in one context
- Schema has fewer than 10 properties
- All fields needed in nested usage

**Decision guide:** See "Context Separation Decision" in @.claude/rules/api-spec-schemas.md

### When to Use allOf?

**Use allOf for:**
- Extending base schemas
- Composing Summary + additional fields → Full
- Reusing common property sets

**Don't use allOf for:**
- Simple schemas without extension
- When properties are unique to one schema

**Decision guide:** See "allOf Usage" in @.claude/rules/api-spec-schemas.md

## Output Format

Document schema design decisions:

**Report should include:**
- **Schemas Created:** List of all schemas (Base, Full, Summary, Nested)
- **Composition Strategy:** How schemas extend/compose
- **Validation Results:** All checks passed
- **External API Mapping:** Field-by-field mapping

**Template:** See "Schema Design Output Format" in @.claude/rules/api-spec-schemas.md

## Success Criteria

Schema design is complete when:

- ✅ Zero inline schemas in responses
- ✅ No duplicate schema definitions
- ✅ Proper format application
- ✅ Clean composition with allOf
- ✅ Context-appropriate schemas (Summary vs Full)
- ✅ All nested objects use $ref
- ✅ **No nullable in spec** (CRITICAL)
- ✅ All IDs use type: string
- ✅ External API mapping documented
- ✅ All validation scripts pass

## Tools Used

- yq (for YAML querying and validation)
- grep (for pattern checking)
- API research findings (external API structure)
- Type mapping reference (formats)

## Related Documentation

**Primary Rules:**
- @.claude/rules/api-spec-schemas.md - ALL schema patterns, composition, validation scripts

**Supporting Rules:**
- @.claude/rules/type-mapping.md - Format reference and core type mappings
- @.claude/rules/gate-api-spec.md - Schema validation requirements for Gate 1
- @.claude/rules/implementation.md - Schema rules (Rules 14-24)
