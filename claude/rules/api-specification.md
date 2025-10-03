# API Specification Rules

Complete guide for creating OpenAPI specifications for module development.

## 🚨 CRITICAL RULES (Immediate Failure)

These rules are NON-NEGOTIABLE. ANY violation causes immediate task failure.

### 1. No Root Level Servers or Security
```yaml
# ❌ WRONG
servers:
  - url: https://api.example.com
security:
  - bearerAuth: []

# ✅ CORRECT - Clean root level, no servers or security
openapi: 3.0.0
info:
  title: Service API
```
**Rationale**: Module determines these dynamically from connection profile.

### 2. Resource Naming Consistency
- Choose ONE term for each resource type
- Use it EVERYWHERE consistently throughout the spec

```yaml
# ✅ CORRECT - Consistent naming
/users
/users/{userId}
/users/{userId}/profile
```

### 3. Complete Operation Coverage
- ALL operations from requirements MUST be implemented as API endpoints
- NO operations can be skipped
- If an operation cannot be implemented, STOP and report

### 4. Parameter Reuse via Components
If a parameter appears in 2+ operations → MUST go in `components/parameters`

```yaml
# ✅ CORRECT
components:
  parameters:
    limitParam:
      name: limit
      in: query
      schema:
        type: integer
paths:
  /users:
    get:
      parameters:
        - $ref: '#/components/parameters/limitParam'
```

### 5. Property Naming - camelCase ONLY
**NO EXCEPTIONS** - Even if external API uses snake_case

```yaml
# ✅ CORRECT
properties:
  userName:
    type: string
  createdAt:
    type: string
```

### 6. Sorting Parameters Standard Names
NEVER use sortBy, sortDir, sort, or variations - ALWAYS use:

```yaml
parameters:
  - name: orderBy
    schema:
      type: string
  - name: orderDir
    schema:
      type: string
      enum: [asc, desc]
```

### 7. Path Parameters - Descriptive Names
Path parameters MUST indicate resource type

```yaml
# ✅ CORRECT
/resources/{resourceId}
/items/{itemId}/subitems/{subitemId}
```

### 8. Resource Identifier Priority
When choosing the primary identifier:
1. `id` (if exists and unique)
2. `name` (if unique)
3. Other unique field

### 9. Parameters - No Connection Context
Operations MUST NOT include parameters available from ConnectionProfile or ConnectionState

```yaml
# ❌ FORBIDDEN - These come from connection
parameters:
  - name: apiKey           # NO! In ConnectionProfile
    in: header
  - name: token            # NO! In ConnectionProfile/State
    in: header
  - name: baseUrl          # NO! In ConnectionProfile
    in: query
  - name: organizationId   # NO! In ConnectionState
    in: path

# ✅ CORRECT - Only operation-specific parameters
/resources/{resourceId}:
  get:
    parameters:
      - name: resourceId   # YES! Operation-specific
        in: path
      - name: includeDetails  # YES! Operation option
        in: query
```

**WHY**: Connection context (apiKey, token, baseUrl, organizationId) is established during connect() and managed by the client. Operations should only define business parameters.

### 10. Tags - Lowercase Singular Nouns Only
```yaml
# ✅ CORRECT
tags:
  - user
  - group
  - account
  - access

# ❌ WRONG - Capitalized tags
tags:
  - User    # NO! Use lowercase
  - Access  # NO! Use lowercase
```

### 11. Method Naming Pattern - NEVER Use 'describe'
```yaml
# ✅ CORRECT
operationId: getAccessToken   # Full name (globally unique)
x-method-name: getToken       # Method name WITHOUT tag prefix

operationId: listAccessTokens # Full name (globally unique)
x-method-name: listTokens     # Method name WITHOUT tag prefix

# ❌ FORBIDDEN
operationId: describeItem     # NEVER use 'describe'
x-method-name: getAccessToken # NO! Remove tag name from method

# ❌ FORBIDDEN - Tag name in method name
x-method-name: getAccessToken # NO! 'Access' is the tag name
x-method-name: listUserItems  # NO! 'User' is the tag name
```

**Rule**: `x-method-name` must NOT contain the tag name, but `operationId` should be fully descriptive for global uniqueness.

**🚨 CRITICAL: Operation ID Consistency for Same Resource**

When you have multiple operations for the SAME resource, the operationId must use the SAME resource name consistently:

```yaml
# ✅ CORRECT - Consistent use of "Organization" (full word)
paths:
  /organizations/{organizationId}:
    get:
      operationId: getOrganization
  /organizations:
    get:
      operationId: listOrganizations

# ❌ WRONG - Inconsistent naming (getOrganization vs listOrgs)
paths:
  /organizations/{organizationId}:
    get:
      operationId: getOrganization  # Uses "Organization"
  /organizations:
    get:
      operationId: listOrgs         # Uses "Orgs" - INCONSISTENT!

# ❌ WRONG - Inconsistent naming (getUser vs listAccounts)
paths:
  /users/{userId}:
    get:
      operationId: getUser          # Uses "User"
  /users:
    get:
      operationId: listAccounts     # Uses "Accounts" - INCONSISTENT!
```

**Rationale**: Consistency in naming makes the API predictable and easier to understand. If one operation uses "Organization", ALL operations for that resource must use "Organization" (not "Org", not "Orgs", not "Organisation").

**Summary Text Rules**:
- `getItem` → Summary: "Retrieve an item"
- `listItems` → Summary: "Retrieve items"
- Use "Retrieve" for get/list operations

### 12. Pagination - Use Core pageTokenParam
```yaml
# ✅ CORRECT
parameters:
  - $ref: './node_modules/@auditmation/types-core/schema/params.yml#/pageTokenParam'
```

### 13. Response Codes - 200/201 Only
**NEVER add 40x or 50x error responses** - Framework handles ALL errors

```yaml
# ✅ CORRECT
responses:
  '200':
    description: Successful response
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/Resource'
```

**WHY**: Framework's error handling automatically converts HTTP errors to typed exceptions.

### 14. Response Schema - Main Business Object Only
**Response schemas MUST map directly to the main business object, NOT envelope/wrapper objects**

**RULE**: If external API returns `{status, data, meta, response, result, caller, token, ...}`, the OpenAPI response schema should reference ONLY the main business object (the thing you're actually retrieving), not the envelope.

**FORBIDDEN PATTERNS:**
```yaml
# ❌ WRONG - Includes envelope fields (status, data, meta)
responses:
  '200':
    content:
      application/json:
        schema:
          type: object
          properties:
            status:
              type: string
            data:
              $ref: '#/components/schemas/User'
            meta:
              type: object

# ❌ WRONG - Includes wrapper (response, result)
responses:
  '200':
    content:
      application/json:
        schema:
          type: object
          properties:
            response:
              $ref: '#/components/schemas/User'

# ❌ WRONG - Includes caller/token/metadata alongside main object
responses:
  '200':
    content:
      application/json:
        schema:
          type: object
          properties:
            user:
              $ref: '#/components/schemas/User'
            caller_id:
              type: string
            expires_at:
              type: string
```

**ONLY VALID PATTERNS:**
```yaml
# ✅ CORRECT - Direct reference to main business object
responses:
  '200':
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/User'

# ✅ CORRECT - Array of main business objects
responses:
  '200':
    content:
      application/json:
        schema:
          type: array
          items:
            $ref: '#/components/schemas/User'
```

**WHY**:
- **Status handling**: Framework maps HTTP status to typed exceptions (200=success, 4xx/5xx=errors)
- **Metadata separation**: Framework handles pagination/caller/token metadata separately
- **Mapper simplicity**: Mappers extract main object from API envelope
- **Domain focus**: API spec reflects actual domain model, not transport wrapper

**IMPLEMENTATION NOTE**: Mappers extract the main object from API response:
```typescript
// API returns: {status: "ok", response: {id: "123", name: "John"}}
// Mapper extracts the main business object
export function toUser(data: any): User {
  const main = data.response || data.data || data.result || data;
  return {
    id: map(UUID, main.id),
    name: main.name
  };
}
```

### 15. No nullable in API Specification
**🚨 CRITICAL: NEVER use nullable in api.yml**

```yaml
# ❌ FORBIDDEN - nullable in API spec
properties:
  name:
    type: string
    nullable: true       # NO! Never use nullable

  email:
    type: string
    nullable: true       # NO! Never use nullable

# ✅ CORRECT - Optional without nullable
properties:
  name:
    type: string         # Optional = not in required array
  email:
    type: string         # Optional = not in required array
```

**RULE**:
- **NEVER add `nullable: true` to any property in api.yml**
- **Optional vs Required**: Use `required` array to mark required fields
- **Null handling**: Done in implementation phase (mappers convert null → undefined)

**WHY**:
- TypeScript uses `| undefined` for optional fields, not `| null`
- Keeps API spec clean and simple
- Mappers handle null values by converting to undefined
- Generated types are cleaner without null union types

**Implementation Pattern**:
```typescript
// If API returns null, mapper converts to undefined
export function toUser(data: any): User {
  const output: User = {
    name: data.name || undefined,           // null → undefined
    email: data.email || undefined,         // null → undefined
    createdAt: map(DateTime, data.created_at)  // map() handles null → undefined
  };
  return output;
}
```

### 16. ID Fields - String Type Only
**ALL ID fields MUST use `type: string`** even if external API uses numbers

```yaml
# ✅ CORRECT
properties:
  id:
    type: string
  userId:
    type: string
```

**Rationale**: IDs are identifiers not quantities - prevents precision/overflow issues.

### 16. Path Plurality Matches Operation Type

**Path Pattern Rules:**

| Operation | Path Pattern | Example |
|-----------|--------------|---------|
| Get specific/current (no ID) | `/{resource}` (singular) | `/profile`, `/session`, `/configuration` |
| List collection | `/{resources}` (plural) | `/users`, `/items`, `/reports` |
| Get by ID | `/{resources}/{resourceId}` | `/users/{userId}`, `/items/{itemId}` |
| Create | `/{resources}` POST | `POST /users`, `POST /items` |
| Update | `/{resources}/{resourceId}` PUT/PATCH | `PUT /users/{userId}` |
| Delete | `/{resources}/{resourceId}` DELETE | `DELETE /users/{userId}` |

```yaml
# ✅ CORRECT - Singular for specific/current resource (no ID parameter)
/profile:
  get:
    operationId: getProfile
    summary: Retrieve current user profile

/configuration:
  get:
    operationId: getConfiguration
    summary: Retrieve system configuration

# ✅ CORRECT - Plural for collection
/users:
  get:
    operationId: listUsers
    summary: Retrieve users
  post:
    operationId: createUser
    summary: Create a user

# ✅ CORRECT - Plural + {id} for specific resource by ID
/users/{userId}:
  get:
    operationId: getUser
    summary: Retrieve a user
  put:
    operationId: updateUser
    summary: Update a user
  delete:
    operationId: deleteUser
    summary: Delete a user
```

**Decision Tree:**

1. **Does the operation require an ID parameter?**
   - NO → Is it a list operation?
     - YES → Use plural: `/resources`
     - NO → Use singular: `/resource`
   - YES → Use plural with ID: `/resources/{resourceId}`

2. **Examples:**
   - Current/specific (implicit): `/profile`, `/session`, `/status`, `/configuration`
   - List: `/users`, `/reports`, `/notifications`
   - By ID: `/users/{userId}`, `/reports/{reportId}`, `/notifications/{notificationId}`

### 17. Clean Path Structure - Resource-Based Only

```yaml
# ✅ CORRECT - Clean, resource-based paths
/profile                      # Singular for current/specific
/session                      # Singular for current/specific
/users                        # Plural for collection
/users/{userId}               # Plural + ID for get by ID
/users/{userId}/permissions   # Hierarchical nesting
/items/{itemId}/details       # Hierarchical nesting

# ❌ FORBIDDEN - Unclean paths
/auth/session                 # No namespace prefixes (auth/, api/, etc.)
/users/current                # Use singular /user instead of /users/current
/api/v1/users/{id}            # No version in path
/{orgId}/resources/{id}       # No context IDs in path (comes from connection)
/get_user/{id}                # No operation name in path
```

**RULE**: Paths must be:
- **Resource-based**: Start with resource name only
- **Clean**: No namespace prefixes (`/auth/`, `/api/`), no version numbers, no `/current` suffix
- **Simple**: Singular for specific, plural for collections
- **Hierarchical**: Parent before child (e.g., `/users/{userId}/permissions`)
- **No context IDs**: Organization, account, or tenant IDs come from connection, not path

**Why these rules:**
- **No namespace prefixes**: Resource is the identity, not the domain
- **No `/current` suffix**: Use singular resource name instead (e.g., `/session` not `/sessions/current`)
- **No version in path**: Version managed at API level, not individual operations
- **No context in path**: Organization/tenant comes from authentication/connection state

### 18. Nested Objects Must Use $ref
**ALL nested object properties MUST be extracted as separate schemas**

```yaml
# ✅ CORRECT
components:
  schemas:
    User:
      properties:
        id:
          type: string
        address:
          $ref: '#/components/schemas/UserAddress'
    UserAddress:
      properties:
        street:
          type: string
        city:
          type: string
```

**WHY**: Prevents InlineResponse/InlineObject types in generated code.

### 19. Schema Name Conflict Check When Adding Operations
**🚨 CRITICAL: When adding a new operation, ALWAYS check if response schema name conflicts with existing schemas**

**MANDATORY WORKFLOW when adding new operation:**

1. **Identify the response schema name** from external API documentation
2. **Check if schema already exists** in api.yml components/schemas
3. **If exists, COMPARE fields** between existing and external API documentation
4. **Determine if rename needed** based on comparison

**DECISION TREE:**

```
Adding operation with response schema "User"
  ↓
Does "User" exist in api.yml?
  ↓ YES
Compare fields: existing vs external API docs
  ↓
Are they identical?
  ↓ NO → Different contexts
Rename existing based on its usage:
  - If nested/minimal → UserSummary
  - If extended → UserInfo
  - If parent-scoped → ParentUser
Then add new schema with correct name
```

**EXAMPLE SCENARIO:**

```yaml
# BEFORE: Existing api.yml has minimal User schema
User:
  type: object
  required:
    - id
  properties:
    id:
      type: string
    opal:
      type: string

# You're adding listUsers operation
# External API docs show User has 50+ fields

# ❌ WRONG: Just add fields to existing User
User:
  type: object
  required:
    - id
  properties:
    id: string
    opal: string
    # ... 50 more fields  # This breaks nested references!

# ✅ CORRECT: Rename existing, add full schema
UserSummary:  # Renamed - used in nested contexts
  type: object
  required:
    - id
  properties:
    id: string
    opal: string

User:  # New full schema for direct API responses
  type: object
  required:
    - id
  properties:
    id: string
    opal: string
    # ... 50 more fields from API docs

# Update nested references
TokenScope:
  properties:
    user:
      $ref: '#/components/schemas/UserSummary'  # Updated reference
```

**NAMING PATTERNS:**
- **Summary**: Minimal version for nested references (e.g., `UserSummary`, `OrganizationSummary`)
- **Info**: Extended version with additional details (e.g., `UserInfo`)
- **ParentResource**: When scoped to parent context (e.g., `OrgUser`, `SiteEntry`)

**WHY THIS MATTERS:**
- Prevents accidentally breaking nested references by expanding schemas
- Ensures schemas accurately reflect API responses in each context
- Maintains type safety across different usage patterns
- Avoids validation errors in parent mappers

**VALIDATION:**
```bash
# Before adding operation, check if schema exists
yq eval '.components.schemas | keys' api.yml | grep "^User$"

# If found, compare field counts
existing_fields=$(yq eval '.components.schemas.User.properties | length' api.yml)
echo "Existing schema has $existing_fields fields"
# Compare with external API documentation field count
```

### 20. Never Update Connection Profile During Operation Implementation
**🚨 CRITICAL: Connection profiles are configuration, NOT operation parameters**

**RULE**: When implementing operations, NEVER add fields to connectionProfile.yml

**WRONG APPROACH**:
```yaml
# ❌ Adding organizationId to connectionProfile during operation implementation
connectionProfile.yml:
  properties:
    organizationId:  # NO! Don't add this during operation work
      type: string
```

**CORRECT APPROACHES**:

**Option 1: Operation Parameter** (if varies per call)
```yaml
# ✅ Add as operation parameter if it changes per call
paths:
  /users:
    get:
      parameters:
        - name: organizationId
          in: path
          required: true
```

**Option 2: Connection State** (if obtained during connect)
```yaml
# ✅ Add to connectionState if API returns it during authentication
connectionState.yml:
  properties:
    organizationId:  # Stored after connect()
      type: string
```

**Option 3: Existing Setup** (if already configured)
```yaml
# ✅ Use what's already in connectionProfile
# If organizationId not in profile, it shouldn't be added during operation work
```

**WHY THIS MATTERS**:
- Connection profiles are set up ONCE during module initialization
- Operations should NOT require profile changes
- Changing profile during operation work breaks existing connections
- Profile changes require regeneration of ALL operations

**WHEN TO UPDATE CONNECTION PROFILE**:
- ✅ During initial module scaffolding
- ✅ When fixing connection authentication
- ✅ When user explicitly requests profile changes
- ❌ NEVER during "add operation" tasks

### 21. Enum Fields with Descriptions
**🚨 CRITICAL: String fields with known values MUST use enums with descriptions**

**RULE**: When a string field has known possible values, define as enum with x-enum-descriptions

**PATTERN**:
```yaml
status:
  type: string
  description: User status
  enum:
    - I
    - A
    - S
  x-enum-descriptions:
    - Inactive
    - Active
    - Suspended
```

**EXAMPLES**:

```yaml
# ✅ CORRECT - Status field with enum
userStatus:
  type: string
  description: Current status of the user
  enum:
    - active
    - inactive
    - suspended
    - pending
  x-enum-descriptions:
    - User is active and can access the system
    - User account is temporarily inactive
    - User is suspended due to policy violation
    - User registration is pending approval

# ✅ CORRECT - Role field with enum
role:
  type: string
  description: User role
  enum:
    - admin
    - user
    - viewer
  x-enum-descriptions:
    - Administrator with full access
    - Regular user with standard permissions
    - Read-only viewer access

# ❌ WRONG - Known values but no enum
status:
  type: string
  description: Status (can be 'A', 'I', or 'S')  # Don't describe in text!
```

**BENEFITS**:
- Type-safe code generation
- Better documentation
- IDE autocomplete
- Validation at API boundary
- Self-documenting schemas

**WHEN TO USE**:
- ✅ Status fields (active/inactive/pending)
- ✅ Types (admin/user/guest)
- ✅ Categories with fixed set
- ✅ API returns specific codes
- ❌ Free-text fields
- ❌ Dynamic/unlimited values

### 22. Complete Response Model Mapping
**🚨 CRITICAL: When external API documentation/spec provides full response model, ALL properties MUST be defined in api.yml**

**RULE**: Never leave out properties from external API documentation - map complete response models

**PROCESS**:
1. Review external API documentation or OpenAPI spec
2. Identify ALL properties in response model
3. Map EVERY property to our api.yml (no omissions)
4. Extract nested objects to components/schemas and reference them

**EXAMPLE**:

```yaml
# ❌ WRONG - Missing properties from external API
User:
  type: object
  properties:
    id: string
    name: string
    # Missing: email, status, createdAt, roles, etc.

# ✅ CORRECT - Complete mapping from external API
User:
  type: object
  required:
    - id
  properties:
    id:
      type: string
      description: Unique identifier
    name:
      type: string
      description: User's full name
    email:
      type: string
      format: email
      description: User's email address
    status:
      type: string
      enum: [active, inactive, suspended]
      x-enum-descriptions:
        - User is active
        - User is inactive
        - User is suspended
    createdAt:
      type: string
      format: date-time
      description: Account creation timestamp
    roles:
      type: array
      items:
        $ref: '#/components/schemas/Role'
      description: User's assigned roles
    profile:
      $ref: '#/components/schemas/UserProfile'
    # ... ALL other properties from external API

# Extract nested objects
UserProfile:
  type: object
  properties:
    avatarUrl:
      type: string
      format: url
    bio:
      type: string
    location:
      type: string
```

**VALIDATION**:
```bash
# Compare property counts
external_props=$(jq '.components.schemas.User.properties | length' external-openapi.json)
our_props=$(yq eval '.components.schemas.User.properties | length' api.yml)
echo "External: $external_props, Ours: $our_props"
# Must match!
```

**WHY THIS MATTERS**:
- Ensures complete data access for module users
- Prevents "missing field" bugs in production
- Maintains consistency with external API
- Enables proper type safety in generated code

**WHEN TO USE**:
- ✅ External API spec available
- ✅ Documentation shows complete model
- ✅ Adding new operation with documented response
- ❌ External API undocumented (map what you observe)

### 23. Paged Results Pattern
**🚨 CRITICAL: List operations returning multiple items MUST use PagedResults pattern with correct schema structure**

**RULE**: For paginated endpoints, response schema MUST be `array` type, NOT object wrapper

**CORRECT PATTERN**:

```yaml
paths:
  /organizations/{organizationId}/users:
    get:
      operationId: listUsers
      x-method-name: list
      parameters:
        - name: organizationId
          in: path
          required: true
          schema:
            type: string
        - $ref: '#/components/parameters/pageNumberParam'
        - $ref: '#/components/parameters/pageSizeParam'
      responses:
        '200':
          description: Successful response
          headers:
            Link:
              $ref: '#/components/headers/pagedLinkHeader'
          content:
            application/json:
              schema:
                type: array  # ✅ MANDATORY - Direct array, not object!
                items:
                  $ref: '#/components/schemas/User'

components:
  parameters:
    pageNumberParam:
      $ref: './node_modules/@auditmation/types-core/schema/pageNumberParam.yml'
    pageSizeParam:
      $ref: './node_modules/@auditmation/types-core/schema/pageSizeParam.yml'
  headers:
    pagedLinkHeader:
      $ref: './node_modules/@auditmation/types-core/schema/pagedLinkHeader.yml'
```

**❌ WRONG PATTERNS**:

```yaml
# ❌ DON'T wrap in object
schema:
  type: object
  properties:
    data:
      type: array
      items:
        $ref: '#/components/schemas/User'

# ❌ DON'T use nested structure
schema:
  type: object
  properties:
    results:
      type: array
    totalCount: integer
    page: integer
```

**WHY DIRECT ARRAY**:
- Framework generates `PagedResults<T>` automatically
- `PagedResults` provides: `items`, `count`, `pageNumber`, `pageSize`, `pageToken`
- Wrapping in object breaks type generation
- Framework handles pagination metadata from Link headers

**IF EXTERNAL API USES WRAPPER**:
```yaml
# External API returns: { data: [...], totalCount: 123, page: 1 }

# In api.yml - Still use array!
responses:
  '200':
    content:
      application/json:
        schema:
          type: array  # Direct array in spec
          items:
            $ref: '#/components/schemas/User'

# In Producer implementation - Extract array
async list(results: PagedResults<User>): Promise<void> {
  const response = await this.client.get('/users');

  // Extract items from wrapper
  const items = response.data.data || response.data.items || response.data.results;

  // Apply mappers
  results.items = items.map(toUser);

  // Set count if available
  if (response.data.totalCount !== undefined) {
    results.count = response.data.totalCount;
  }
}
```

**PAGINATION PARAMETERS**:
- Use `pageNumberParam` and `pageSizeParam` from `@auditmation/types-core`
- NEVER create custom pagination parameters
- Use `pageTokenParam` for cursor-based pagination if API supports it

**BENEFITS**:
- Type-safe `PagedResults<T>` in generated code
- Automatic pagination handling
- Consistent interface across all modules
- Framework-level pagination support

### 24. Schema Context Separation - Summary vs Full
**🚨 CRITICAL: When a schema is used in MULTIPLE contexts, create separate schemas for each context**

**PROBLEM**: A schema used in different contexts often has different field requirements:
- **Direct API Response** (e.g., `GET /organizations/{id}`): Returns ALL fields (60+ properties)
- **Nested Reference** (e.g., in `TokenScope.org`): Returns MINIMAL fields (4 properties)

**RULE**: If a schema appears in BOTH contexts, create TWO separate schemas:

```yaml
# ✅ CORRECT - Separate schemas for different contexts

# Minimal version for nested references
OrganizationSummary:
  type: object
  required:
    - id
  properties:
    id:
      type: string
      description: Unique identifier
    name:
      type: string
      description: Organization name
    opal:
      type: string
      description: OPAL identifier
    isUnified:
      type: boolean
      description: Whether unified

# Full version for direct API responses
Organization:
  type: object
  required:
    - id
  properties:
    id:
      type: string
    name:
      type: string
    opal:
      type: string
    isUnified:
      type: boolean
    # ... 60+ additional fields
    status:
      type: string
    billingStatus:
      type: string
    createdAt:
      type: string
      format: date-time
    # etc.

# TokenScope uses the minimal version
TokenScope:
  type: object
  properties:
    org:
      $ref: '#/components/schemas/OrganizationSummary'  # ✅ Minimal
    user:
      $ref: '#/components/schemas/UserSummary'          # ✅ Minimal

# Direct API response uses full version
paths:
  '/organizations/{organizationId}':
    get:
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Organization'  # ✅ Full
```

```yaml
# ❌ WRONG - Single schema for both contexts

# This schema started small but grew to 60+ fields for getOrganization
Organization:
  type: object
  required:
    - id
  properties:
    id:
      type: string
    name:
      type: string
    # ... 60+ fields added later

# TokenScope now references the bloated schema
# But API only returns 4 fields in this context!
TokenScope:
  type: object
  properties:
    org:
      $ref: '#/components/schemas/Organization'  # ❌ Wrong! Too many fields
```

**NAMING CONVENTION**:
- **Summary/Minimal**: `{Resource}Summary` (e.g., `OrganizationSummary`, `UserSummary`)
- **Full**: `{Resource}` (e.g., `Organization`, `User`)
- **Alternative**: `{Resource}Info`, `{Resource}Details` for extended versions

**WHEN TO SEPARATE**:
1. **Different field counts**: Direct response has 10+ fields, nested has <5 fields
2. **Different requirements**: Direct response requires fields that nested doesn't have
3. **Multiple usages**: Schema referenced in 2+ different endpoint contexts
4. **API behavior differs**: Same schema name but different fields returned by API

**IMPLEMENTATION IMPACT**:
```typescript
// Separate mapper functions for each schema

export function toOrganizationSummary(data: any): OrganizationSummary | undefined {
  if (!data) return undefined;
  if (!data.id && data.id !== 0) {
    throw new InvalidInputError('organizationSummary', 'Missing required field: id');
  }
  return {
    id: String(data.id),
    name: data.name || undefined,
    opal: data.opal || undefined,
    isUnified: data.isUnified,
  };
}

export function toOrganization(data: any): Organization | undefined {
  if (!data) return undefined;
  if (!data.id && data.id !== 0) {
    throw new InvalidInputError('organization', 'Missing required field: id');
  }
  return {
    id: String(data.id),
    name: data.name || undefined,
    opal: data.opal || undefined,
    isUnified: data.isUnified,
    status: data.status || undefined,
    billingStatus: data.billingStatus || undefined,
    // ... 50+ more fields
  };
}

// Parent mapper uses the appropriate version
export function toTokenScope(data: any): TokenScope {
  let org: OrganizationSummary | undefined;  // ✅ Uses Summary
  if (data.org) {
    try {
      org = toOrganizationSummary(data.org);   // ✅ Calls Summary mapper
    } catch (error) {
      org = undefined;
    }
  }
  // ...
}
```

**WHY THIS MATTERS**:
- **Type Safety**: Generated types match actual API responses
- **Clarity**: Developers know exactly what fields to expect in each context
- **Maintainability**: Changes to full schema don't affect nested usages
- **Correctness**: Prevents validation errors when nested objects have fewer fields

**RED FLAGS** - Signs you need to separate schemas:
1. You're expanding a schema with 20+ new fields
2. That schema is referenced by other schemas (nested usage)
3. API documentation shows different fields in different contexts
4. You're catching validation errors in parent mappers for "incomplete" nested objects

**VALIDATION**:
```bash
# Check if schemas are used in multiple contexts
yq eval '.components.schemas | to_entries[] | select(.value.properties.*.["$ref"] != null) | .key' api.yml

# If Organization appears in results AND has its own endpoint, consider separation
```

## 🟡 STANDARD RULES

### Operation Naming Conventions

Use ONLY these prefixes:

| Operation Type | Name | HTTP Method | Example |
|---------------|------|-------------|---------|
| Get single | `get{Resource}` | GET | `getUser` |
| List multiple | `list{Resources}` | GET | `listUsers` |
| Search with filters | `search{Resources}` | POST | `searchUsers` |
| Create new | `create{Resource}` | POST | `createUser` |
| Update existing | `update{Resource}` | PUT/PATCH | `updateUser` |
| Delete resource | `delete{Resource}` | DELETE | `deleteUser` |

**Forbidden prefixes**: describe, fetch, retrieve, find, query, add, remove, modify, patch

### Descriptions - Always From Documentation

**MANDATORY**: When creating/updating specs:
1. Check vendor documentation for operation descriptions
2. Include descriptions when available
3. Copy accurately from official documentation

```yaml
paths:
  /users/{userId}:
    get:
      operationId: getUser
      summary: Retrieve a user
      description: |
        Returns detailed information about a specific user including
        their profile, permissions, and account status.
      parameters:
        - name: userId
          description: The unique identifier of the user
          required: true
```

When descriptions are not available:
- Create minimal factual description
- Document the gap

### Schema Organization

**Model Definition Source Priority**:
1. **Official Documentation** - Definitive source, include ALL documented fields
2. **API Responses** - Only when significantly mismatches docs
3. **Field Inclusion** - Include ALL documented fields (may be optional)

**Schema Naming Patterns**:
```yaml
User              # Main resource
UserInfo          # Extended version
UserBase          # Minimal version
UserCreateInput   # Create request
UserUpdateInput   # Update request
UserSummary       # Abbreviated
```

## 🟢 GUIDELINES

### Security Schemes
- ALWAYS use OAuth2 type with scopes
- Even for API Key/PAT/Basic Auth, define as OAuth2
- Scopes define required permissions per operation

### API Metadata Configuration
- Set `x-impl-name` to service name only
- Do NOT set title, description, or version (sync-meta script handles)
- Configure server base URL from service configuration

### Schema Validation
- Run `npx swagger-cli validate api.yml` after any changes
- Specification must validate without errors

## Validation Quick Reference

| Rule | Check Command |
|------|--------------|
| No root servers/security | `yq eval '.servers, .security' api.yml` |
| camelCase only | `grep -r '_' api.yml` in properties |
| No 'describe' | `grep -E "describe[A-Z]" api.yml` |
| 200 only | `yq eval '.paths.*.*.responses \| keys' api.yml` |
| No InlineResponse | `grep -r "InlineResponse" generated/` |

## Failure Recovery

If ANY rule is violated:

1. **STOP all work immediately**
2. **Document the violation**
3. **Fix the violation**
4. **Re-validate ALL rules**
5. **Only proceed when 100% compliant**

## Remember

These rules ensure:
- Consistent API design across all modules
- Proper code generation
- Clean, maintainable specifications
- Compatibility with the platform

When in doubt, ALWAYS err on the side of compliance.
