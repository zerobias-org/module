## Chapter 6: Phase 3 - API Specification Design

### 6.1 Phase Overview

**Goal**: Design OpenAPI specification and connection schemas that accurately model the API

**Duration**: 45-90 minutes

**Deliverables**:
- `api.yml` - Complete OpenAPI specification
- `connectionProfile.yml` - Connection credentials schema
- `connectionState.yml` - Connection state schema
- **Gate 1: API Specification** ✅
- **Gate 2: Type Generation** ✅

**Critical**: This phase drives EVERYTHING else. Get this right!

### 6.2 The 12 Critical API Specification Rules

**These 12 rules cause IMMEDIATE FAILURE if violated:**

#### Rule 1: Root Level Restrictions
**FORBIDDEN at root level:**
- `servers:`
- `security:`

```yaml
# ❌ WRONG
servers:
  - url: https://api.example.com
security:
  - bearerAuth:

# ✅ CORRECT
openapi: 3.0.0
info:
  title: Service API
paths:
  ...
components:
  securitySchemes:
    ...
```

#### Rule 2: Resource Naming - Singular Nouns
```yaml
# ✅ CORRECT
tags:
  - name: user
  - name: organization

components:
  schemas:
    User:
      ...
    Organization:
      ...

# ❌ WRONG
tags:
  - name: users    # NO - use singular
  - name: orgs     # NO - use full word
```

#### Rule 3: Operation Coverage
- ALL operations from API research MUST be in spec
- Zero operations may be skipped

#### Rule 4: Parameter Reuse
- If 2+ operations use same parameter → Move to `components/parameters`

```yaml
# ✅ CORRECT
components:
  parameters:
    userId:
      name: userId
      in: path
      required: true
      schema:
        type: string

paths:
  /users/{userId}:
    get:
      parameters:
        - $ref: '#/components/parameters/userId'
  /users/{userId}/profile:
    get:
      parameters:
        - $ref: '#/components/parameters/userId'
```

#### Rule 5: Property Naming - camelCase ONLY
```yaml
# ✅ CORRECT
User:
  properties:
    userId:
      type: string
    firstName:
      type: string
    createdAt:
      type: string
      format: date-time

# ❌ WRONG
User:
  properties:
    user_id:
      type: string      # NO - snake_case
    first_name:
      type: string      # NO - snake_case
```

**Validation:**
```bash
# Should return nothing
grep "_" api.yml | grep -v "x-" | grep -v "#"
```

#### Rule 6: Sorting Parameters - orderBy/orderDir
```yaml
# ✅ CORRECT
parameters:
  - name: orderBy
    in: query
    schema:
      type: string
      enum:
        - name
        - createdAt
  - name: orderDir
    in: query
    schema:
      type: string
      enum:
        - asc
        - desc
      default: asc

# ❌ WRONG
parameters:
  - name: sortBy     # NO - use orderBy
  - name: direction  # NO - use orderDir
```

#### Rule 7: Path Parameters - Descriptive
```yaml
# ✅ CORRECT
/users/{userId}
/organizations/{organizationId}

# ❌ WRONG
/users/{id}          # Not descriptive
/organizations/{org} # Abbreviation
```

#### Rule 8: Resource Identifier Priority
**Order: id > name > other unique fields**

```yaml
# ✅ CORRECT (preferred)
/users/{userId}

# ✅ ACCEPTABLE (if no id)
/templates/{templateName}

# ❌ WRONG
/users/{email}  # Email can change
```

#### Rule 9: Tags - Singular, Lowercase
```yaml
# ✅ CORRECT
paths:
  /users:
    get:
      tags:
        - user    # Singular, lowercase

# ❌ WRONG
paths:
  /users:
    get:
      tags:
        - users  # NO - use singular
        - Users  # NO - lowercase
```

#### Rule 10: Method Naming - operationId
**Use: get, list, search, create, update, delete**

```yaml
# ✅ CORRECT
paths:
  /users/{userId}:
    get:
      operationId: getUser
      x-method-name: getUser
  /users:
    get:
      operationId: listUsers
      x-method-name: listUsers

# ❌ WRONG
paths:
  /users/{userId}:
    get:
      operationId: describeUser  # NO - use 'get'
```

#### Rule 11: LIST Operations Must Have Pagination

**REQUIRED**: All LIST operations must include:
1. pageSizeParam (from core)
2. pageNumberParam OR pageTokenParam (from core)
3. links header in response (from core)
4. Response schema is array type

```yaml
# ✅ CORRECT - Define in components first, then reference
components:
  parameters:
    pageSizeParam:
      $ref: './node_modules/@auditmation/types-core/schema/params.yml#/pageSizeParam'
    pageNumberParam:
      $ref: './node_modules/@auditmation/types-core/schema/params.yml#/pageNumberParam'
  headers:
    linksHeader:
      $ref: './node_modules/@auditmation/types-core/schema/headers.yml#/linksHeader'

paths:
  /users:
    get:
      parameters:
        - $ref: '#/components/parameters/pageSizeParam'
        - $ref: '#/components/parameters/pageNumberParam'
      responses:
        '200':
          headers:
            links:
              $ref: '#/components/headers/linksHeader'
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/User'

# ❌ WRONG - Direct node_modules reference
parameters:
  - $ref: './node_modules/@auditmation/types-core/schema/params.yml#/pageSizeParam'  # NO! Define in components first

# ❌ WRONG - Custom pagination parameter names
parameters:
  - name: limit      # NO - use pageSizeParam from core
  - name: offset     # NO - use pageNumberParam from core
  - name: nextToken  # NO - use pageTokenParam from core
  - name: cursor     # NO - use pageTokenParam from core
```

#### Rule 12: Response Codes - 200/201 ONLY
```yaml
# ✅ CORRECT
responses:
  '200':
    description: Success
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/User'

# ❌ WRONG
responses:
  '200': ...
  '401': ...    # NO - remove all error responses
  '404': ...    # NO - remove all error responses
```

**Validation:**
```bash
# Should return nothing
grep -E "'40[0-9]'|'50[0-9]'" api.yml
```

### 6.3 Design api.yml

#### 6.3.1 Start with Template

```yaml
openapi: 3.0.0
info:
  title: GitHub API  # DO NOT EDIT - synced from package.json
  version: 0.0.0     # DO NOT EDIT - synced from package.json
  description: GitHub REST API
  x-product-infos:
    - $ref: './node_modules/@zerobias-org/product-github-github/index.yml'

paths: {}

components:
  schemas: {}
  parameters: {}
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
```

#### 6.3.2 Design First Operation Path

**Example: List Users**

Using your API research from Chapter 4:

```yaml
paths:
  /users:
    get:
      summary: List users
      description: Retrieve a paginated list of users
      operationId: listUsers
      x-method-name: listUsers
      tags:
        - user
      parameters:
        - $ref: '#/components/parameters/pageSizeParam'
        - $ref: '#/components/parameters/pageNumberParam'
        - name: name
          in: query
          description: Filter by user name
          required: false
          schema:
            type: string
      responses:
        '200':
          description: List of users
          headers:
            links:
              $ref: '#/components/headers/linksHeader'
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/User'
```

**Note**: The response schema is an array (not an object with data/total). The framework handles pagination metadata via the links header.

#### 6.3.3 Design User Schema

**Based on your sanitized API response:**

```yaml
components:
  schemas:
    User:
      type: object
      required:
        - id
        - email
        - firstName
        - createdAt
      properties:
        id:
          type: string
          description: User unique identifier
        email:
          type: string
          format: email
          description: User email address
        firstName:
          type: string
          description: User first name
        lastName:
          type: string
          description: User last name
        createdAt:
          type: string
          format: date-time
          description: User creation timestamp
```

**Critical Rules for Schemas:**
1. **required** array lists ONLY required fields (never remove fields just to make them optional!)
2. All property names in **camelCase** (no snake_case!)
3. No `nullable: true` (mappers handle null → undefined conversion)
4. Use `format` for special types (email, date-time, uri)

#### 6.3.4 Design Reusable Parameters and Headers

**Define pagination parameters in components** (reference core schemas):

```yaml
components:
  parameters:
    pageSizeParam:
      $ref: './node_modules/@auditmation/types-core/schema/params.yml#/pageSizeParam'
    pageNumberParam:
      $ref: './node_modules/@auditmation/types-core/schema/params.yml#/pageNumberParam'
    # Or for token-based pagination:
    # pageTokenParam:
    #   $ref: './node_modules/@auditmation/types-core/schema/params.yml#/pageTokenParam'

  headers:
    linksHeader:
      $ref: './node_modules/@auditmation/types-core/schema/headers.yml#/linksHeader'
```

**The 4 required elements for LIST operations:**
1. ✅ pageSize parameter (from core)
2. ✅ pageNumber OR pageToken parameter (from core)
3. ✅ links response header (from core)
4. ✅ Response schema is array type

**Why use core parameters:**
- Standardized across all modules
- Framework expects these exact parameter names
- Automatic pagination handling
- Type-safe in generated code

### 6.4 Design connectionProfile.yml

#### 6.4.1 Select Core Profile

**Based on your authentication research (Chapter 4):**

| Auth Method | Core Profile | Fields |
|-------------|--------------|--------|
| API Key / Bearer Token | `tokenProfile.yml` | token |
| OAuth Client Credentials | `oauthClientProfile.yml` | clientId, clientSecret |
| OAuth with Refresh | `oauthTokenProfile.yml` | accessToken, refreshToken |
| Username/Password | `basicConnection.yml` | username, password |
| Email/Password | Extend `basicConnection.yml` | email, password |

#### 6.4.2 Simple Token Example

```yaml
# connectionProfile.yml
$ref: './node_modules/@auditmation/types-core/schema/tokenProfile.yml'
```

**That's it!** The core profile provides: `token` (required), `url` (optional)

#### 6.4.3 Email/Password Example

```yaml
# connectionProfile.yml
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/basicConnection.yml'
  - type: object
    properties:
      username:
        type: string
        format: email
        description: User email for authentication
```

#### 6.4.4 Custom Fields Example

If API requires additional connection parameters:

```yaml
# connectionProfile.yml
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/tokenProfile.yml'
  - type: object
    properties:
      organizationId:
        type: string
        description: Organization identifier for API access
      region:
        type: string
        description: Optional region for multi-region deployments
        enum:
          - us-east-1
          - eu-west-1
          - ap-southeast-1
```

**Important Rules:**
- ✅ ALWAYS extend a core profile
- ✅ Check what parent profile provides BEFORE adding fields
- ✅ Include environment-specific optional parameters (region, environment, etc.)
- ✅ NO scope-limiting fields (organizationId for operations, not connection)

### 6.5 Design connectionState.yml

#### 6.5.1 Core Rule: MUST Extend baseConnectionState

**MANDATORY**: All connectionState schemas MUST extend baseConnectionState for `expiresIn`

```yaml
# connectionState.yml
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/baseConnectionState.yml'
  - type: object
    required:
      - accessToken
    properties:
      accessToken:
        type: string
        format: password
        description: Current access token
```

**Why expiresIn is mandatory:**
- Server uses `expiresIn` to schedule automatic token refresh cronjobs
- Must be in **SECONDS** (integer) until token expires
- Calculation: `expiresIn = Math.floor((expiresAt - now) / 1000)`

#### 6.5.2 Simple Token State Example

```yaml
# connectionState.yml
$ref: './node_modules/@auditmation/types-core/schema/tokenConnectionState.yml'
```

**Provides**: `accessToken`, `expiresIn` (from baseConnectionState)

#### 6.5.3 OAuth with Refresh Example

```yaml
# connectionState.yml
$ref: './node_modules/@auditmation/types-core/schema/oauthTokenState.yml'
```

**Provides**: `tokenType`, `accessToken`, `refreshToken`, `expiresIn`, `scope`, `url`

#### 6.5.4 Custom State Example

```yaml
# connectionState.yml
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/baseConnectionState.yml'
  - type: object
    required:
      - accessToken
    properties:
      accessToken:
        type: string
        format: password
        description: Current access token
      refreshToken:
        type: string
        format: password
        description: Token used to refresh access token
      scope:
        type: string
        description: OAuth scope for this token
# expiresIn comes from baseConnectionState.yml
```

### 6.6 Validate API Specification (Gate 1)

#### 6.6.1 Simple Validation

```bash
# Validate OpenAPI syntax
npx swagger-cli validate api.yml

# Should say: "api.yml is valid"
# If valid, your spec is good! ✓
```

#### 6.6.2 Visual Check

Open your files and verify:

**In api.yml:**
- ✅ operationId uses `getUser` (not `describeUser`)
- ✅ Properties are `camelCase` (not `snake_case`)
- ✅ Only `'200':` response (no `'404':` or `'500':`)
- ✅ Tags are singular: `user` (not `users`)

**In connectionState.yml:**
- ✅ First line extends baseConnectionState:
  ```yaml
  allOf:
    - $ref: './node_modules/@auditmation/types-core/schema/baseConnectionState.yml'
  ```

**Gate 1 Pass Criteria:**
- ✅ `npx swagger-cli validate api.yml` passes
- ✅ connectionState extends baseConnectionState
- ✅ No obvious violations in api.yml

### 6.7 Generate Types (Gate 2)

#### 6.7.1 Clean and Generate

```bash
# Clean previous generated files
npm run clean

# Generate TypeScript types from OpenAPI spec
npm run generate

# This will:
# 1. Validate api.yml
# 2. Run OpenAPI generator
# 3. Create generated/api/ directory with TypeScript types
```

#### 6.7.2 Verify Generation Success

```bash
# Check generated files exist
ls generated/api/

# You should see TypeScript files (.ts)
# If you see files, generation succeeded! ✓
```

**Gate 2 Pass Criteria:**
- ✅ `npm run generate` exits with code 0
- ✅ `generated/api/` directory created with .ts files

**Note**: If generation succeeded without errors, the types are valid and ready to use!

### 6.8 Phase 3 Validation Checklist

**Before proceeding to Phase 4:**

- [ ] ✅ api.yml follows all 12 critical rules
- [ ] ✅ OpenAPI syntax validates successfully
- [ ] ✅ connectionProfile.yml extends core profile
- [ ] ✅ connectionState.yml extends baseConnectionState
- [ ] ✅ expiresIn field present (via baseConnectionState)
- [ ] ✅ All properties in camelCase
- [ ] ✅ No error responses (4xx/5xx) in spec
- [ ] ✅ Tags are singular lowercase
- [ ] ✅ Operation IDs use correct verbs (get/list/create/update/delete)
- [ ] ✅ **Gate 1: API Specification PASSED** ✅
- [ ] ✅ Types generated successfully
- [ ] ✅ No inline types
- [ ] ✅ TypeScript compilation successful
- [ ] ✅ **Gate 2: Type Generation PASSED** ✅

**Common Mistakes to Avoid:**
- ❌ Forgetting to extend baseConnectionState
- ❌ Using snake_case in property names
- ❌ Including error responses in spec
- ❌ Using plural tags
- ❌ Adding connection context to operation parameters

---

