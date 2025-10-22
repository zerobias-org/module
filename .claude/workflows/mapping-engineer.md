# Mapping Engineer Workflow

## Purpose
Implement data mappers that transform external API responses to internal typed objects using core types and the map() utility.

## Input Required
- API specification (api.yml) with response schemas
- Generated TypeScript types from api.yml
- Core type definitions (@zborg/module-utils)
- Target interface definitions

## Step-by-Step Process

### Step 1: Analyze API Response Schema

- Use yq or Read tool to view operation's response schema in api.yml
- Identify response structure (single object, array, or nested)
- Note required vs optional fields
- Document field names (typically snake_case in API)
- List data types (string, number, boolean, object, array)
- Identify candidates for core types (UUID, Email, URL, DateTime)
- **Schema patterns:** See @.claude/rules/api-spec-schemas.md

### Step 2: Review Target Interface

- Read generated TypeScript interface from generated/api.ts
- Note interface field names (camelCase)
- Review type annotations
- Identify optional (?) vs required fields
- Note any nested interfaces
- **Type reference:** See @.claude/rules/type-mapping.md

### Step 3: Create Mapper Function Signature

- Use naming convention: `mapApi{Type}To{Type}` for single objects
- Use naming convention: `mapApi{Type}ArrayTo{Type}Array` for arrays
- Accept ApiType parameter
- Return internal Type
- **Signature patterns:** See "Single Object Mapper" in @.claude/rules/mapper-patterns.md

### Step 4: Validate Required Fields

- Check api.yml for required fields in schema
- Add validation checks at start of mapper function
- Throw InvalidInputError for missing required fields
- Document which fields are required
- **Validation workflow:** See @.claude/rules/mapper-field-validation.md

### Step 5: Implement Field Mappings

- Use const output pattern for type safety
- Map required fields first
- Handle optional fields with ternary operators
- Apply core type conversions using map() utility
- Convert snake_case to camelCase
- Map nested objects recursively
- Map arrays using Array.map()
- **Field mapping patterns:** See "Field Mapping" in @.claude/rules/mapper-patterns.md
- **Core type usage:** See @.claude/rules/type-mapping.md

### Step 6: Handle Core Type Conversions

- Use map() utility for all core type conversions
- Convert string to UUID using map(input.id, UUID)
- Convert string to Email using map(input.email, Email)
- Convert string to URL using map(input.url, URL)
- Convert string to DateTime using map(input.created_at, DateTime)
- Handle optional core types with ternary operators
- **All conversions:** See "Core Type Conversions" in @.claude/rules/type-mapping.md

### Step 7: Handle Nested Objects

- Map single nested objects with recursive mapper calls
- Map arrays of nested objects with Array.map()
- Handle deeply nested structures
- Use optional chaining for nested optionals
- **Nested patterns:** See "Nested Object Mapping" in @.claude/rules/mapper-patterns.md

### Step 8: Handle Optional Fields

Use appropriate strategy for each case:
- Ternary operator for single optional fields
- Nullish coalescing (??) for defaults
- Optional chaining + map for nested optionals
- **Optional handling:** See "Optional Field Handling" in @.claude/rules/mapper-patterns.md

### Step 9: Validate Field Coverage

CRITICAL: Ensure zero missing fields
- Count fields in generated interface
- Count fields in mapper const output
- Numbers MUST match exactly
- Run field coverage validation script
- **Validation:** See "Field Coverage Validation" in @.claude/rules/mapper-field-validation.md

### Step 10: Add Array Mapper (if needed)

- Create array mapper function if operation returns arrays
- Use naming: `mapApi{Type}ArrayTo{Type}Array`
- Simply map over array calling single object mapper
- **Array pattern:** See "Array Mapper" in @.claude/rules/mapper-patterns.md

### Step 11: Document and Export

- Add JSDoc comment describing transformation
- Document parameters and return type
- Note any thrown errors (InvalidInputError)
- Export function from Mappers.ts
- **Documentation:** See "Mapper Documentation" in @.claude/rules/mapper-patterns.md

## Field Validation Workflow

Follow complete validation from @.claude/rules/mapper-field-validation.md:

1. Read API spec → Identify required fields
2. Check generated interface → Count fields
3. Validate each required field → Add checks in mapper
4. Map ALL interface fields → No missing fields
5. Count mapped fields → Must equal interface field count
6. Test with real data → Verify transformations work

## Common Patterns

All patterns with code examples in @.claude/rules/mapper-patterns.md:

- **Pattern 1:** Simple Field Rename (snake_case → camelCase)
- **Pattern 2:** Type Conversion (string → UUID)
- **Pattern 3:** Optional Field (with ternary)
- **Pattern 4:** Nested Object (recursive mapping)
- **Pattern 5:** Array (Array.map with mapper)
- **Pattern 6:** Nullable + Optional (nullish coalescing)

## Error Handling

- Import InvalidInputError from @zborg/module-utils
- Throw for missing required fields
- map() utility throws automatically for invalid type conversions
- **Error patterns:** See @.claude/rules/error-handling.md

## Testing Strategy

- Create unit test for each mapper function
- Test all required fields map correctly
- Test optional fields handled properly
- Test missing required field throws InvalidInputError
- Test invalid formats throw appropriate errors
- **Test patterns:** See @.claude/rules/nock-patterns.md and @.claude/rules/gate-test-creation.md

## Success Criteria

- ✅ All interface fields mapped (zero missing)
- ✅ Required fields validated
- ✅ Core types applied correctly
- ✅ snake_case → camelCase conversions
- ✅ Optional fields handled gracefully
- ✅ Nested objects mapped recursively
- ✅ Arrays mapped correctly
- ✅ Error handling for invalid input
- ✅ JSDoc documentation
- ✅ Exported from Mappers.ts
- ✅ Unit tests pass

## What NOT to Do

**DO NOT:**
- ❌ Use constructors instead of map() utility
- ❌ Leave fields unmapped
- ❌ Skip required field validation
- ❌ Return any type
- ❌ Weaken API spec requirements
- ❌ Use generic Error (use InvalidInputError)
- ❌ Trust external data without validation

**ALWAYS:**
- ✓ Use map() utility for core types
- ✓ Validate required fields first
- ✓ Map ALL interface fields
- ✓ Use const output pattern
- ✓ Handle optional fields explicitly
- ✓ Apply snake_case → camelCase
- ✓ Throw InvalidInputError for validation failures

## Reference Files

**Primary Rules:**
- @.claude/rules/mapper-patterns.md - All mapper implementation patterns, examples, validation
- @.claude/rules/mapper-field-validation.md - Complete field validation workflow
- @.claude/rules/type-mapping.md - All core type usage and conversion reference

**Supporting Rules:**
- @.claude/rules/error-handling.md - InvalidInputError for missing required fields
- @.claude/rules/implementation.md - Rule #5 (API spec is source of truth)
- @.claude/rules/gate-implementation.md - Implementation validation
