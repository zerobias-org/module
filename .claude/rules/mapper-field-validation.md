# Mapper Field Validation Process

Complete validation workflow for ensuring mappers correctly handle all interface fields.

## MANDATORY 3-STEP VALIDATION

Every mapper MUST follow this validation process:

### Step 1: Interface Analysis

**READ the target interface** from `generated/api/index.ts`:

```typescript
// Example: After running npm run generate
interface UserInfo {
  id: UUID;           // Required
  name: string;       // Required
  email?: Email;      // Optional
  createdAt: Date;    // Required
  status?: StatusEnum;// Optional
}
```

**DOCUMENT ALL FIELDS**:
- Count total fields: 5
- Required fields (3): id, name, createdAt
- Optional fields (2): email, status
- **CRITICAL**: Record the exact count

### Step 2: API Response Schema Validation

**EXAMINE the corresponding response schema** in api.yml:

```yaml
components:
  schemas:
    UserInfo:
      type: object
      required:
        - id
        - name
        - created_at
      properties:
        id:
          type: string
        name:
          type: string
        email:
          type: string
          format: email
        created_at:
          type: string
          format: date-time
        status:
          type: string
          enum: [active, inactive, pending]
```

**VERIFY**:
- Each interface field has corresponding API response field
- Required fields match between interface and schema
- Optional fields are correctly optional

### Step 3: Complete Mapping Implementation

**IMPLEMENT mapper with ALL fields**:

```typescript
export function toUserInfo(raw: any): UserInfo {
  // Validate required fields first
  if (!raw?.id || !raw?.name) {
    throw new InvalidInputError('userInfo', 'Missing required fields');
  }

  // 🚨 CRITICAL: Map ALL fields (must match interface count)
  const output: UserInfo = {
    // Required (3) - validated above
    id: map(UUID, raw.id),
    name: raw.name,
    createdAt: map(DateTime, raw.created_at)?.toDate(),

    // Optional (2) - MUST be mapped too
    email: map(Email, raw.email),
    status: toEnum(StatusEnum, raw.status)
  };

  return output;
}
```

**VALIDATION**:
- **ZERO MISSING FIELDS**: Interface field count = Mapped field count
- If interface has 5 fields → mapper MUST map 5 fields
- If interface has 10 fields → mapper MUST map 10 fields

## Field Count Validation

**MANDATORY CHECK**: Before considering mapper complete, count fields:

```typescript
// ✅ CORRECT - All 5 fields mapped
interface UserInfo {
  id: UUID;           // 1
  name: string;       // 2
  email?: Email;      // 3
  createdAt: Date;    // 4
  status?: StatusEnum;// 5
}

export function toUserInfo(raw: any): UserInfo {
  const output: UserInfo = {
    id: map(UUID, raw.id),           // 1 ✓
    name: raw.name,                   // 2 ✓
    email: map(Email, raw.email),    // 3 ✓
    createdAt: map(DateTime, raw.created_at)?.toDate(), // 4 ✓
    status: toEnum(StatusEnum, raw.status) // 5 ✓
  };
  return output;
}
// Field count: 5 = 5 ✅ PASS

// ❌ WRONG - Missing fields
export function toUserInfo(raw: any): UserInfo {
  const output: UserInfo = {
    id: map(UUID, raw.id),           // 1 ✓
    name: raw.name,                   // 2 ✓
    createdAt: map(DateTime, raw.created_at)?.toDate() // 3 ✓
    // Missing: email (field 4)
    // Missing: status (field 5)
  };
  return output;
}
// Field count: 3 ≠ 5 ❌ FAIL
```

**RULE**: Interface field count MUST EQUAL mapped field count.

## Nested Object Handling

For nested objects, use non-exported helper mappers:

```typescript
// Parent interface
interface Organization {
  id: UUID;
  name: string;
  address?: Address;  // Nested object
}

// Nested interface
interface Address {
  street: string;
  city: string;
  country?: string;
}

// ✅ CORRECT - Helper mapper for nested object
function toAddress(data: any): Address | undefined {
  if (!data) return undefined;

  const output: Address = {
    street: data.street || '',
    city: data.city || '',
    country: data.country || undefined
  };
  return output;
}

// Main mapper uses helper
export function toOrganization(data: any): Organization {
  if (!data?.id) {
    throw new InvalidInputError('organization', 'Missing required fields');
  }

  const output: Organization = {
    id: map(UUID, data.id),
    name: data.name,
    address: toAddress(data.address)  // Use helper
  };
  return output;
}
```

**RULES**:
- Helper mappers are NOT exported (no `export` keyword)
- Defined above the main mapper function
- Follow same validation rules as main mappers
- Handle optional nested objects with `| undefined`

## Optional Field Handling

Optional fields MUST still be mapped, returning `undefined` when absent:

```typescript
// ✅ CORRECT - Optional field mapped
const output: User = {
  id: map(UUID, data.id),              // Required
  name: data.name,                      // Required
  email: map(Email, data.email),       // Optional - returns Email | undefined
  website: map(URL, data.website)      // Optional - returns URL | undefined
};

// ❌ WRONG - Optional field missing
const output: User = {
  id: map(UUID, data.id),
  name: data.name,
  email: map(Email, data.email)
  // Missing: website - even though optional, must be mapped
};
```

**WHY**: TypeScript interface includes the field, mapper must provide it (even if undefined).

## Validation Checklist

Before completing a mapper:

- [ ] Read generated interface from `generated/api/index.ts`
- [ ] Counted total interface fields (required + optional)
- [ ] Documented required vs optional fields
- [ ] Checked API response schema in api.yml
- [ ] Validated required fields with error throwing
- [ ] Mapped ALL required fields
- [ ] Mapped ALL optional fields (with `| undefined`)
- [ ] Used `const output: Type` pattern
- [ ] Verified field count: interface count = mapper count
- [ ] No fields missing from mapper
- [ ] Helper mappers for nested objects (not exported)

## Common Mistakes

### Mistake 1: Skipping Optional Fields

```typescript
// ❌ WRONG - Skipping optional fields
interface User {
  id: UUID;
  name: string;
  email?: Email;      // Optional but must be mapped!
  phone?: string;     // Optional but must be mapped!
}

export function toUser(data: any): User {
  return {
    id: map(UUID, data.id),
    name: data.name
    // Missing email and phone - WRONG even if optional
  };
}
```

**FIX**: Map all fields, optional or not.

### Mistake 2: Not Counting Fields

```typescript
// ❌ WRONG - No validation of field count
// Developer maps 3 fields, interface has 5, doesn't notice
```

**FIX**: Explicitly count before and after.

### Mistake 3: Assuming Defaults

```typescript
// ❌ WRONG - Assuming undefined for missing fields
interface Resource {
  id: UUID;
  name: string;
  tags?: string[];  // Optional array
}

export function toResource(data: any): Resource {
  return {
    id: map(UUID, data.id),
    name: data.name
    // Missing tags - TypeScript might allow but it's wrong
  };
}
```

**FIX**: Explicitly map optional fields:
```typescript
return {
  id: map(UUID, data.id),
  name: data.name,
  tags: data.tags || undefined  // Explicit
};
```

## Nested Model Example

Complete example with nested objects:

```typescript
// Interfaces (from generated/api/index.ts)
interface Organization {
  id: UUID;
  name: string;
  address?: Address;
  owner?: User;
}

interface Address {
  street: string;
  city: string;
  postalCode?: string;
}

interface User {
  id: UUID;
  name: string;
  email?: Email;
}

// Helper mappers (not exported)
function toAddress(data: any): Address | undefined {
  if (!data) return undefined;

  const output: Address = {
    street: data.street || '',
    city: data.city || '',
    postalCode: data.postal_code || undefined  // All 3 fields mapped
  };
  return output;
}

function toUser(data: any): User | undefined {
  if (!data?.id) return undefined;

  const output: User = {
    id: map(UUID, data.id),
    name: data.name,
    email: map(Email, data.email)  // All 3 fields mapped
  };
  return output;
}

// Main mapper (exported)
export function toOrganization(data: any): Organization {
  if (!data?.id) {
    throw new InvalidInputError('organization', 'Missing required fields');
  }

  const output: Organization = {
    id: map(UUID, data.id),
    name: data.name,
    address: toAddress(data.address),  // Use helper
    owner: toUser(data.owner)           // Use helper
  };
  return output;  // All 4 fields mapped
}
```

**VALIDATION**:
- Organization interface: 4 fields → Mapper: 4 fields ✅
- Address interface: 3 fields → Helper mapper: 3 fields ✅
- User interface: 3 fields → Helper mapper: 3 fields ✅

## Discovery Process

### When implementing a new mapper:

1. **Run generation**:
   ```bash
   npm run generate
   ```

2. **Open generated interface**:
   ```typescript
   // generated/api/index.ts
   export interface NewResource {
     // Read all fields here
   }
   ```

3. **Count fields**:
   - Total: ?
   - Required: ?
   - Optional: ?

4. **Check API schema**:
   ```yaml
   # api.yml
   components:
     schemas:
       NewResource:
         required: [...]
         properties:
           ...
   ```

5. **Implement mapper**:
   ```typescript
   export function toNewResource(data: any): NewResource {
     const output: NewResource = {
       // Map ALL fields
     };
     return output;
   }
   ```

6. **Validate**:
   - Count fields in mapper
   - Compare with interface count
   - Must match exactly

## References

- Mapper Pattern: @.claude/rules/implementation.md (Mapper Pattern section)
- Type Mapping: @.claude/rules/type-mapping.md
- Error Handling: @.claude/rules/error-handling.md
- Operation Engineer agent: @.claude/agents/operation-engineer.md
- Mapping Engineer agent: @.claude/agents/mapping-engineer.md
