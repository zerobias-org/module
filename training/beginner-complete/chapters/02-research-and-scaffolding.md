## Chapter 4: Phase 1 - API Research & Discovery

### 4.1 Phase Overview

**Goal**: Understand the API's authentication and identify the first operation to implement

**Duration**: 30-60 minutes

**Deliverables**:
- Authentication method identified
- Test credentials obtained
- First operation selected
- Real API call tested and documented

### 4.2 Research Checklist

#### 4.2.1 Find API Documentation

**Where to look:**
- Official API documentation site
- Developer portal
- GitHub repository
- Postman collections
- OpenAPI/Swagger specs (if available)

**What to find:**
- Base URL (e.g., `https://api.example.com`)
- Authentication method
- Available endpoints
- Request/response examples
- Rate limits
- API versioning

#### 4.2.2 Identify Authentication Method

**Common Authentication Types:**

**1. API Key / Bearer Token**
```bash
# Header-based
Authorization: Bearer sk_live_abc123
# or
X-API-Key: abc123
```

**2. Basic Authentication**
```bash
# Username + Password
Authorization: Basic base64(username:password)
```

**3. OAuth 2.0 Client Credentials**
```bash
# POST to /oauth/token
{
  "client_id": "...",
  "client_secret": "...",
  "grant_type": "client_credentials"
}
```

**4. OAuth 2.0 with Email/Password**
```bash
# POST to /auth/login
{
  "email": "user@example.com",
  "password": "password"
}
# Returns: access_token, refresh_token, expires_in
```

**Document your findings:**
```
API: Example Service
Base URL: https://api.example.com
Auth Method: OAuth 2.0 (email/password)
Auth Endpoint: POST /auth/login
Token Location: Authorization: Bearer {accessToken}
Token Expiry: Yes (expires_in field in response)
Refresh: Yes (refresh_token provided)
```

#### 4.2.3 Select First Operation

**Priority order for first operation:**

1. **Connection/Token Info** (if available)
   - `GET /auth/me`
   - `GET /auth/token`
   - Simple, verifies connection works

2. **List Operation** (most common)
   - `GET /users`
   - `GET /organizations`
   - Tests pagination, filtering

3. **Get Operation** (simple fallback)
   - `GET /users/{id}`
   - `GET /organizations/{id}`
   - Simple single-resource retrieval

**Selection Criteria:**
- ✅ Simple request structure
- ✅ Minimal required parameters
- ✅ Clear response format
- ✅ Uses authentication
- ✅ Can be tested with test account

### 4.3 Test API with curl

Before writing any code, test the API directly.

#### 4.3.1 Test Authentication

```bash
# Example: OAuth Email/Password
curl -X POST "https://api.example.com/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "testpassword"
  }'

# Expected Response:
{
  "access_token": "eyJhbGc...",
  "refresh_token": "def502...",
  "expires_in": 3600,
  "token_type": "Bearer"
}

# Save the access_token for next steps
ACCESS_TOKEN="eyJhbGc..."
```

#### 4.3.2 Test First Operation

```bash
# Example: List Users
curl -X GET "https://api.example.com/users?limit=10" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json"

# Save the response
curl -X GET "https://api.example.com/users?limit=10" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  > api-response-users-list.json

# Pretty print
cat api-response-users-list.json | jq .
```

#### 4.3.3 Document API Response Structure

**Create a research document:**

```markdown
# API Research: Example Service

## Authentication
- Method: OAuth 2.0 (email/password)
- Endpoint: POST /auth/login
- Request:
  ```json
  {
    "email": "string",
    "password": "string"
  }
  ```
- Response:
  ```json
  {
    "access_token": "string",
    "refresh_token": "string",
    "expires_in": 3600
  }
  ```

## First Operation: List Users
- Endpoint: GET /users
- Parameters:
  - limit (query): integer, optional, default 10, max 100
  - offset (query): integer, optional, default 0
- Response:
  ```json
  {
    "data": [
      {
        "id": "string",
        "email": "string",
        "first_name": "string",
        "last_name": "string",
        "created_at": "2025-10-22T10:00:00Z"
      }
    ],
    "total": 100
  }
  ```

## Notes
- Rate limit: 1000 requests/hour
- Pagination: offset/limit based
- Timestamps: ISO 8601 format
```

### 4.4 Sanitize Test Data

**CRITICAL**: Remove all PII from saved responses before committing

```bash
# Create sanitized version
cp api-response-users-list.json api-response-users-list.sanitized.json

# Edit and replace:
# - Real names → "Jane Doe", "John Smith"
# - Real emails → "user@example.com"
# - Real phone numbers → "+1-555-0123"
# - Any personal information
```

### 4.5 Phase 1 Validation

**Before proceeding to Phase 2, ensure:**

- [ ] ✅ API documentation reviewed
- [ ] ✅ Authentication method identified
- [ ] ✅ Test credentials obtained
- [ ] ✅ Authentication tested with curl
- [ ] ✅ First operation tested with curl
- [ ] ✅ Response structure documented
- [ ] ✅ Test data sanitized
- [ ] ✅ Research notes saved

---

## Chapter 5: Phase 2 - Module Scaffolding

### 5.1 Phase Overview

**Goal**: Create the module directory structure and install dependencies

**Duration**: 10-15 minutes

**Deliverables**:
- Module directory structure created
- Dependencies installed
- Stub files generated
- Ready for Phase 3 (API specification design)

### 5.2 Module Identifier Format

**Format**: `{vendor}-{service}` or `{vendor}-{suite}-{service}`

**Examples:**
- `github-github` (simple)
- `amazon-aws-s3` (with suite)
- `bitbucket-bitbucket` (simple)
- `avigilon-alta-access` (with suite)

**Rules:**
- All lowercase
- Hyphen-separated
- Vendor name first
- Suite optional (for multi-product vendors)
- Service name last

### 5.3 Product Package Discovery

Before scaffolding, check if a product package exists:

```bash
# Search for product package
npm view @zerobias-org/product-{vendor}-{product} --json

# Example
npm view @zerobias-org/product-github-github --json

# If exists, install it
npm install @zerobias-org/product-github-github
```

**If product package exists:**
```bash
# Extract product metadata
cd node_modules/@zerobias-org/product-github-github

# Look for index.yml or index.ts
cat index.yml
# or
cat index.ts
```

### 5.4 Run Yeoman Generator

#### 5.4.1 Determine Module Path

```bash
# For simple module: package/{vendor}/{service}
# Example: package/github/github

# For suite module: package/{vendor}/{suite}/{service}
# Example: package/amazon/aws/s3
```

#### 5.4.2 Execute Generator

**From repository root:**

```bash
# Make sure you're in the repo root
pwd
# Should show: /path/to/module

# Run Yeoman generator
yo @auditmation/hub-module \
  --productPackage '@zerobias-org/product-github-github' \
  --modulePackage '@zerobias-org/module-github-github' \
  --packageVersion '0.0.0' \
  --description 'GitHub' \
  --repository 'https://github.com/zerobias-org/module' \
  --author 'your.email@company.com'
```

**Parameter Guide:**
- `--productPackage`: Product package name (from discovery step)
- `--modulePackage`: Replace 'product-' with 'module-'
- `--packageVersion`: Start at `0.0.0`
- `--description`: Service display name (e.g., 'GitHub', 'Jira', 'S3')
- `--repository`: Fixed: `https://github.com/zerobias-org/module`
- `--author`: Your work email

**Wait for completion:**
```
✔ Creating module structure...
✔ Generating files...
✔ Module created successfully!
```

### 5.5 Critical Post-Generation Steps

#### 5.5.1 Navigate to Module Directory

```bash
# Example for github-github
cd package/github/github

# Verify you're in the right place
pwd
# Should show: /path/to/module/package/github/github

ls -la
# Should see: package.json, api.yml, src/, test/, etc.
```

#### 5.5.2 Run sync-meta (CRITICAL!)

**This step is MANDATORY:**

```bash
# Sync package.json metadata to api.yml
npm run sync-meta
```

**What this does:**
- Reads `title` and `version` from package.json
- Writes them to api.yml
- Ensures api.yml matches package.json

**Why this is critical:**
- api.yml title/version MUST match package.json
- Validation will fail if not synced
- sync-meta runs automatically on version bumps
- NEVER manually edit api.yml title/version

**That's it!** If sync-meta completes without errors, it worked correctly.

#### 5.5.3 Install Dependencies

```bash
# Install all dependencies
npm install

# This will take 1-2 minutes
# Should complete without errors
```

### 5.6 Verify Scaffolding

Quick visual check:

```bash
# Check the key files were created
ls -la

# You should see:
# - package.json
# - api.yml
# - connectionProfile.yml
# - connectionState.yml
# - src/
# - test/
# - node_modules/

# If you see these, scaffolding succeeded! ✓
```

### 5.7 Understanding Generated Files

#### 5.7.1 Key Files Created

**Configuration Files:**
- `package.json` - Module metadata and dependencies
- `tsconfig.json` - TypeScript compiler configuration
- `.eslintrc` - Linting rules
- `.mocharc.json` - Test runner configuration
- `.gitignore` - Git ignore patterns

**Schema Files (Stubs - will be designed in Phase 3):**
- `api.yml` - OpenAPI specification stub
- `connectionProfile.yml` - Connection profile schema stub
- `connectionState.yml` - Connection state schema stub

**Source Files (Stubs - will be implemented in Phase 4):**
- `src/index.ts` - Public exports
- `src/{Service}Impl.ts` - Connector implementation stub
- `src/{Service}Client.ts` - HTTP client stub (may not exist yet)

**Test Files (Stubs - will be written in Phase 5):**
- `test/unit/Common.ts` - Unit test helpers
- `test/unit/ConnectionTest.ts` - Connection unit tests
- `test/integration/Common.ts` - Integration test helpers
- `test/integration/ConnectionTest.ts` - Connection integration tests

### 5.8 Create .env File

**Important**: Create `.env` file with your test credentials NOW:

```bash
# Create .env in module root
touch .env

# Add credentials (customize for your API)
cat > .env <<EOF
# Example Service Credentials
SERVICE_EMAIL=your-test-email@example.com
SERVICE_PASSWORD=your-test-password
SERVICE_API_KEY=your-api-key

# Test Data (add as you identify test values)
SERVICE_TEST_USER_ID=
SERVICE_TEST_ORGANIZATION_ID=
EOF
```

**Note**: The generator already added `.env` to `.gitignore` for you.

### 5.9 Phase 2 Validation Checklist

**Before proceeding to Phase 3:**

- [ ] ✅ Module directory created
- [ ] ✅ Yeoman generator completed successfully
- [ ] ✅ **npm run sync-meta executed** (CRITICAL!)
- [ ] ✅ npm install completed
- [ ] ✅ package.json has correct metadata
- [ ] ✅ api.yml title/version synced from package.json
- [ ] ✅ Stub files exist (api.yml, connectionProfile.yml, connectionState.yml)
- [ ] ✅ Source stubs exist (src/ files)
- [ ] ✅ Test stubs exist (test/ files)
- [ ] ✅ Dependencies installed (node_modules/ exists)
- [ ] ✅ TypeScript configuration valid
- [ ] ✅ .env file created with credentials

**Note**: Build validation is deferred to Phase 7 (stubs are not yet fully implemented)

---

