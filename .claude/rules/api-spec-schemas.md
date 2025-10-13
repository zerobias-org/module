# API Specification - Schemas (Rules 14-24)

**Schema design, response patterns, and data modeling**

## Rule 14: Response Schema - Main Business Object Only

**Response schemas MUST map directly to the main business object, NOT envelope/wrapper objects**

```yaml
# ❌ WRONG - Includes envelope (status, data, meta)
responses:
  '200':
    schema:
      type: object
      properties:
        status: { type: string }
        data: { $ref: '#/components/schemas/User' }
        meta: { type: object }

# ✅ CORRECT - Direct reference to main business object
responses:
  '200':
    schema:
      $ref: '#/components/schemas/User'

# ✅ CORRECT - Array of main business objects
responses:
  '200':
    schema:
      type: array
      items:
        $ref: '#/components/schemas/User'
```

**WHY**: Framework handles status/metadata separately. Mappers extract main object from API envelope.

## Rule 15: No nullable in API Specification

**🚨 CRITICAL: NEVER use nullable in api.yml**

```yaml
# ❌ FORBIDDEN
properties:
  name:
    type: string
    nullable: true       # NO! Never use nullable

# ✅ CORRECT - Optional without nullable
properties:
  name:
    type: string         # Optional = not in required array
```

**WHY**: TypeScript uses `| undefined` for optional fields. Mappers convert null → undefined.

## Rule 16: ID Fields - String Type Only

**ALL ID fields MUST use `type: string`** even if external API uses numbers

```yaml
# ✅ CORRECT
properties:
  id: { type: string }
  userId: { type: string }
  organizationId: { type: string }

# ❌ WRONG
properties:
  id: { type: integer }    # NO! Always use string
```

**Rationale**: IDs are identifiers not quantities - prevents precision/overflow issues.

## Rule 17: Path Plurality Matches Operation Type

```yaml
# ✅ CORRECT - List operation uses plural path
/resources:
  get:
    operationId: listResources

# ✅ CORRECT - Get operation uses singular path
/resources/{resourceId}:
  get:
    operationId: getResource

# ❌ WRONG - Mismatch
/resource:
  get:
    operationId: listResources    # List but singular path!
```

## Rule 18: Clean Path Structure - Resource-Based Only

```yaml
# ✅ CORRECT - Clean resource paths
/users
/users/{userId}
/users/{userId}/repositories
/users/{userId}/repositories/{repositoryId}

# ❌ WRONG - External API style paths
/api/v1/users                    # NO! No /api/v1 prefix
/{owner}/{repo}/issues           # NO! Use /repositories/{repositoryId}/issues
/users/{username}/repos          # NO! Use userId not username in path
```

**Rationale**: Module creates clean, consistent resource-based paths regardless of external API structure.

## Rule 19: Nested Objects Must Use $ref

```yaml
# ✅ CORRECT - Nested object uses $ref
properties:
  owner:
    $ref: '#/components/schemas/User'
  repository:
    $ref: '#/components/schemas/Repository'

# ❌ WRONG - Inline nested object
properties:
  owner:
    type: object
    properties:
      id: { type: string }
      name: { type: string }
```

**WHY**: Reusability, maintainability, and type safety.

## Rule 20: Schema Name Conflict Check When Adding Operations

When adding new operations, check if response schemas already exist in the spec. If YES and schemas differ:

```yaml
# Scenario: Adding "getAccessToken" but "AccessToken" schema exists
# with different structure than new operation needs

# Option 1: Use existing if compatible
responses:
  '200':
    schema:
      $ref: '#/components/schemas/AccessToken'

# Option 2: Create operation-specific schema if incompatible
components:
  schemas:
    AccessTokenDetails:    # New schema for this specific operation
      properties:
        token: { type: string }
        expiresIn: { type: integer }
        # ... different from existing AccessToken
```

**WHY**: Prevents breaking existing operations when adding new ones.

## Rule 21: Never Update Connection Profile During Operation Implementation

**🚨 CRITICAL**: When implementing operations, NEVER modify connectionProfile.yml or connectionState.yml

```
# ❌ WRONG Flow
1. Implementing getAccessToken operation
2. Notice we need to store refreshToken
3. Add refreshToken to connectionState.yml    # NO! STOP!

# ✅ CORRECT Flow
1. Implementing getAccessToken operation
2. Notice we need refreshToken
3. STOP and report: "connectionState needs refreshToken field"
4. User reviews and updates connection schemas
5. Resume operation implementation
```

**WHY**: Connection profile changes affect ALL operations. Must be reviewed separately.

## Rule 22: Enum Fields with Descriptions

```yaml
# ✅ CORRECT - Enum with descriptions
status:
  type: string
  enum: [active, pending, suspended]
  description: |
    Account status:
    - active: Account is active and can be used
    - pending: Account is awaiting activation
    - suspended: Account has been suspended

# ❌ INCOMPLETE - Enum without descriptions
status:
  type: string
  enum: [active, pending, suspended]
```

## Rule 23: Complete Response Model Mapping

Every response schema property must map to external API field:

```yaml
# ✅ COMPLETE - All fields documented
User:
  properties:
    id:
      type: string          # Maps to: id
    name:
      type: string          # Maps to: display_name
    email:
      type: string          # Maps to: email_address
    createdAt:
      type: string          # Maps to: created_at
```

## Rule 24: Schema Context Separation - Summary vs Full

When a schema is used in BOTH nested contexts AND as direct response, consider separating:

```yaml
# ✅ GOOD - Separate Summary and Full schemas
components:
  schemas:
    UserSummary:        # For nested usage (10+ properties)
      properties:
        id: { type: string }
        name: { type: string }
        email: { type: string }

    User:               # For direct response (full details)
      allOf:
        - $ref: '#/components/schemas/UserSummary'
        - properties:
            bio: { type: string }
            location: { type: string }
            # ... 20+ more properties

# Usage:
/users/{userId}:
  get:
    responses:
      '200':
        schema:
          $ref: '#/components/schemas/User'    # Full details

/organizations/{orgId}:
  get:
    responses:
      '200':
        schema:
          properties:
            owner:
              $ref: '#/components/schemas/UserSummary'  # Nested = summary
```

**WHEN**: Schema used in BOTH contexts AND has 10+ properties
**WHY**: Avoids circular references, reduces payload size for nested usage

---

## Schema Design Checklist

Before finalizing schemas:

- [ ] Responses reference main business object (NOT envelope)
- [ ] NO `nullable` anywhere in api.yml
- [ ] ALL IDs use `type: string`
- [ ] Path plurality matches operation type
- [ ] Paths are clean resource-based (no /api/v1, no external patterns)
- [ ] Nested objects use $ref (NOT inline)
- [ ] Checked for schema name conflicts with existing schemas
- [ ] NOT modifying connectionProfile/connectionState during operation work
- [ ] Enums have descriptions for each value
- [ ] All schema properties documented with external API mapping
- [ ] Large schemas (10+ props) separated into Summary/Full when used in both contexts

---

**These schema rules ensure clean, reusable, and maintainable data models.**
