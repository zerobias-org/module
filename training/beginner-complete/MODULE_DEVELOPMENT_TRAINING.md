# Module Development Training Manual
## Complete Guide for TypeScript Developers

**Version**: 1.0
**Last Updated**: 2025-10-22
**Target Audience**: Junior to Mid-level TypeScript Developers
**Estimated Training Time**: 40-60 hours hands-on practice

---

## Table of Contents

### Part 1: Foundations
1. [Introduction & Course Overview](#chapter-1-introduction--course-overview)
2. [Understanding Module Architecture](#chapter-2-understanding-module-architecture)
3. [Development Environment Setup](#chapter-3-development-environment-setup)

### Part 2: The 8-Phase Module Creation Process
4. [Phase 1: API Research & Discovery](#chapter-4-phase-1-api-research--discovery)
5. [Phase 2: Module Scaffolding](#chapter-5-phase-2-module-scaffolding)
6. [Phase 3: API Specification Design](#chapter-6-phase-3-api-specification-design)
7. [Phase 4: Core Implementation](#chapter-7-phase-4-core-implementation)
8. [Phase 5: Testing](#chapter-8-phase-5-testing)
9. [Phase 6: Documentation](#chapter-9-phase-6-documentation)
10. [Phase 7: Build & Finalization](#chapter-10-phase-7-build--finalization)
11. [Phase 8: Quality Gates Validation](#chapter-11-phase-8-quality-gates-validation)

### Part 3: Advanced Topics
12. [Adding Operations to Existing Modules](#chapter-12-adding-operations-to-existing-modules)
13. [Troubleshooting & Debugging](#chapter-13-troubleshooting--debugging)
14. [Best Practices & Common Pitfalls](#chapter-14-best-practices--common-pitfalls)

### Part 4: Reference Materials
15. [Quick Reference Guide](#chapter-15-quick-reference-guide)
16. [Validation Checklists](#chapter-16-validation-checklists)
17. [Code Templates](#chapter-17-code-templates)

---

# Part 1: Foundations

## Chapter 1: Introduction & Course Overview

### 1.1 What You Will Learn

This comprehensive training manual will teach you how to build API integration modules from scratch. By the end of this course, you will be able to:

- **Research and analyze** third-party APIs systematically
- **Design OpenAPI specifications** that accurately model API behavior
- **Implement robust HTTP clients** with proper error handling
- **Create data mappers** that transform API responses to type-safe models
- **Write comprehensive tests** (both unit and integration)
- **Validate your work** through 6 quality gates
- **Build production-ready modules** that meet enterprise standards

### 1.2 Prerequisites

**Required Knowledge:**
- TypeScript fundamentals (types, interfaces, classes, async/await)
- Basic HTTP concepts (methods, status codes, headers)
- Git version control basics
- Command line/terminal usage
- NPM package management

**Helpful But Not Required:**
- OpenAPI/Swagger specification knowledge
- Experience with API testing tools (curl, Postman)
- Understanding of monorepos
- Testing frameworks (Mocha, Chai)

### 1.3 Course Structure

This course is organized into **17 chapters** across **4 parts**:

**Part 1 (Chapters 1-3)**: Foundation concepts and environment setup
**Part 2 (Chapters 4-11)**: Complete 8-phase module creation workflow
**Part 3 (Chapters 12-14)**: Advanced topics and troubleshooting
**Part 4 (Chapters 15-17)**: Reference materials and templates

Each chapter includes:
- ✅ **Concepts**: Theory and principles
- ✅ **Step-by-Step Instructions**: Detailed procedures
- ✅ **Code Examples**: Real-world patterns
- ✅ **Validation Scripts**: How to verify your work
- ✅ **Common Mistakes**: What to avoid
- ✅ **Exercises**: Hands-on practice

### 1.4 Learning Approach

**Hands-On Learning:** This course emphasizes learning by doing. You'll create a complete module from scratch.

**Incremental Progress:** Each phase builds on previous work with clear validation checkpoints.

**Real-World Focus:** All examples and patterns come from production modules.

**Quality First:** Learn to validate your work at every step through 6 quality gates.

### 1.5 Time Commitment

**Initial Module (Learning)**: 3-4 hours
**Subsequent Modules (Proficient)**: 45-70 minutes per operation
**Practice Recommendation**: Build 2-3 complete modules for proficiency

### 1.6 Success Criteria

You will know you've mastered module development when you can:

1. ✅ Create a new module passing all 6 quality gates
2. ✅ Add operations to existing modules independently
3. ✅ Debug and fix failing tests systematically
4. ✅ Design API specifications that match real API behavior
5. ✅ Write mappers that handle all edge cases correctly

---

## Chapter 2: Understanding Module Architecture

### 2.1 What is a Module?

A **module** is a TypeScript package that provides a type-safe interface to a third-party API. It acts as an adapter between the external REST API and your application's internal type system.

**Key Responsibilities:**
- **Authentication**: Manage connection lifecycle
- **HTTP Communication**: Make API requests with proper headers
- **Type Safety**: Convert untyped API responses to typed objects
- **Error Handling**: Translate HTTP errors to meaningful exceptions
- **Testing**: Provide both mocked and real API testing

### 2.2 Module Architecture Overview

```
Module Architecture (3 Layers)

┌─────────────────────────────────────────────┐
│           Public API Layer                   │
│  - newService()                              │
│  - ServiceConnector                          │
│  - Producer APIs (UserProducer, etc.)       │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│         Implementation Layer                 │
│  - ServiceClient (connection)                │
│  - Producers (business logic)                │
│  - Mappers (data transformation)            │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│          Infrastructure Layer                │
│  - HTTP Client (axios)                       │
│  - Error Handler                             │
│  - Generated Types                           │
└─────────────────────────────────────────────┘
```

### 2.3 Core Components

#### 2.3.1 Client (Connection Management)

**Responsibility**: ONLY connection lifecycle - connect, isConnected, disconnect

```typescript
class ServiceClient {
  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    // Authenticate with API
    // Store access token
    // Return state for framework
  }

  async isConnected(): Promise<boolean> {
    // Verify connection is still valid
  }

  async disconnect(): Promise<void> {
    // Clean up connection
  }
}
```

**Critical Rule**: Client NEVER implements business operations (list, get, create, etc.)

#### 2.3.2 Producers (Business Logic)

**Responsibility**: ALL API operations - list, get, create, update, delete

```typescript
class UserProducer {
  constructor(private client: ServiceClient) {}

  async listUsers(results: PagedResults<User>): Promise<void> {
    // Make HTTP request
    // Transform response with mapper
    // Populate results object
  }

  async getUser(userId: string): Promise<User> {
    // Make HTTP request
    // Transform response with mapper
    // Return typed object
  }
}
```

**Critical Rule**: Producers NEVER duplicate connection context (no apiKey, token, baseUrl parameters)

#### 2.3.3 Mappers (Data Transformation)

**Responsibility**: Transform untyped API responses to typed domain models

```typescript
export function toUser(raw: any): User {
  // 1. Validate required fields
  ensureProperties(raw, ['id', 'email']);

  // 2. Create typed output
  const output: User = {
    id: raw.id.toString(),
    email: map(Email, raw.email),
    firstName: optional(raw.firstName),
    createdAt: map(DateTime, raw.created_at)
  };

  return output;
}
```

**Critical Rule**: Mappers must handle EVERY field in the interface - zero missing fields

### 2.4 Repository Structure

```
module/                              # Repository root
├── package/                         # All modules
│   ├── {vendor}/
│   │   ├── {service}/              # Simple module
│   │   │   ├── api.yml             # OpenAPI spec
│   │   │   ├── connectionProfile.yml  # Auth schema
│   │   │   ├── connectionState.yml    # State schema
│   │   │   ├── package.json
│   │   │   ├── tsconfig.json
│   │   │   ├── src/
│   │   │   │   ├── index.ts        # Public exports
│   │   │   │   ├── {Service}Client.ts
│   │   │   │   ├── {Service}Impl.ts
│   │   │   │   ├── {Resource}Producer.ts
│   │   │   │   ├── Mappers.ts
│   │   │   │   └── util.ts
│   │   │   ├── generated/          # Generated types
│   │   │   │   └── api/
│   │   │   └── test/
│   │   │       ├── unit/
│   │   │       │   ├── Common.ts
│   │   │       │   ├── ConnectionTest.ts
│   │   │       │   └── {Resource}ProducerTest.ts
│   │   │       └── integration/
│   │   │           ├── Common.ts
│   │   │           ├── ConnectionTest.ts
│   │   │           └── {Resource}ProducerTest.ts
│   │   │
│   │   └── {suite}/                # Suite module
│   │       └── {service}/
│   │           └── (same structure)
├── .claude/                         # Development rules (ignore for now)
└── lerna.json                       # Monorepo config
```

### 2.5 The 8-Phase Creation Process

Every module goes through **8 phases** with **6 quality gates**:

**Phase 1: Discovery & Analysis** (Research)
- Research API documentation
- Identify authentication method
- Select first operation to implement
- Test with curl/Node.js

**Phase 2: Module Scaffolding** (Setup)
- Run Yeoman generator
- Create directory structure
- Install dependencies
- Verify scaffolding

**Phase 3: API Specification Design** (Modeling)
- Design api.yml (OpenAPI spec)
- Design connectionProfile.yml
- Design connectionState.yml
- **Gate 1: API Specification** ✅
- **Gate 2: Type Generation** ✅

**Phase 4: Core Implementation** (Coding)
- Implement Client (connection)
- Implement Producers (operations)
- Implement Mappers (transformation)
- **Gate 3: Implementation** ✅

**Phase 5: Testing** (Validation)
- Write unit tests (mocked)
- Write integration tests (real API)
- **Gate 4: Unit Test Creation & Execution** ✅
- **Gate 5: Integration Tests** ✅

**Phase 6: Documentation** (Communication)
- Write README.md
- Document setup and usage

**Phase 7: Build & Finalization** (Packaging)
- Run build
- Run shrinkwrap
- **Gate 6: Build** ✅

**Phase 8: Final Validation** (Quality Assurance)
- Validate all 6 gates
- Verify runtime mapping
- Ready for deployment

### 2.6 Quality Gates

**6 Mandatory Gates** - ALL must pass:

1. **API Specification** - Valid OpenAPI spec following 12 critical rules
2. **Type Generation** - TypeScript types generated successfully
3. **Implementation** - Zero `any` types, using generated types, ESLint passes
4. **Unit Tests** - All unit tests passing, nock mocking only
5. **Integration Tests** - All integration tests passing with real API
6. **Build** - Successful TypeScript compilation and dist/ creation

### 2.7 Key Principles

**Principle 1: Type Safety First**
- Use generated types from OpenAPI spec
- Never use `any` in function signatures
- Validate data at boundaries (mappers)

**Principle 2: Separation of Concerns**
- Client = Connection ONLY
- Producers = Operations ONLY
- Mappers = Transformation ONLY

**Principle 3: API Spec is Source of Truth**
- OpenAPI spec drives everything
- Never weaken spec to make implementation easier
- Validate spec against real API responses

**Principle 4: Test Everything**
- Unit tests (mocked HTTP, no credentials)
- Integration tests (real API, credentials required)
- 100% pass rate required

**Principle 5: Zero Tolerance for Failures**
- ANY test failure stops progress
- Fix implementation, never skip tests
- All gates must pass

---

## Chapter 3: Development Environment Setup

### 3.1 Required Tools

#### 3.1.1 Core Development Tools

**Node.js >= 16.0.0** (Recommended: 18.x LTS)
```bash
# Check version
node --version

# Install via nvm (recommended)
nvm install 18
nvm use 18
```

**npm >= 8.0.0** (Recommended: 9.x)
```bash
# Check version
npm --version

# Comes with Node.js
```

**Git >= 2.25.0**
```bash
# Check version
git --version

# Configure
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

#### 3.1.2 Module Generation Tools

**Yeoman >= 4.0.0**
```bash
# Install globally
npm install -g yo

# Verify
yo --version
```

**Module Generator**
```bash
# Install module generator
npm install -g @auditmation/generator-hub-module

# Verify
npm ls -g @auditmation/generator-hub-module
```

#### 3.1.3 Code Generation Tools

**Java >= 11.0.0** (Required for OpenAPI generator)
```bash
# Check version
java -version

# Install from https://adoptium.net/
```

**OpenAPI Generator** (Usually bundled with module setup)
```bash
# Alternative global install
npm install -g @openapitools/openapi-generator-cli
```

#### 3.1.4 YAML/JSON Processing Tools

**yq** (YAML processing)
```bash
# macOS
brew install yq

# Linux
snap install yq

# Verify
yq --version
```

**jq** (JSON processing)
```bash
# macOS
brew install jq

# Linux
apt-get install jq

# Verify
jq --version
```

### 3.2 Repository Setup

#### 3.2.1 Clone the Repository

```bash
# Clone the monorepo
git clone https://github.com/zerobias-org/module.git
cd module

# Verify you're in the right place
pwd
# Should show: /path/to/module
```

#### 3.2.2 Verify npm Registry Access

```bash
# Check current registry
npm config get registry

# Configure private registries (if needed)
npm config set @zerobias-org:registry https://npm.pkg.github.com/
npm config set @auditmation:registry https://npm.pkg.github.com/
npm config set @auditlogic:registry https://npm.pkg.github.com/
```

### 3.3 Prerequisites Validation Script

Create a script to validate your environment:

```bash
#!/bin/bash
# save as: check-prerequisites.sh

echo "Checking prerequisites..."

FAILED=0

# Node.js
NODE_VERSION=$(node --version 2>/dev/null | sed 's/v//')
if [ -z "$NODE_VERSION" ]; then
  echo "❌ Node.js not installed"
  FAILED=1
else
  MAJOR=$(echo $NODE_VERSION | cut -d. -f1)
  if [ "$MAJOR" -ge 16 ]; then
    echo "✅ Node.js $NODE_VERSION"
  else
    echo "❌ Node.js $NODE_VERSION (need >= 16.0.0)"
    FAILED=1
  fi
fi

# npm
NPM_VERSION=$(npm --version 2>/dev/null)
if [ -z "$NPM_VERSION" ]; then
  echo "❌ npm not installed"
  FAILED=1
else
  MAJOR=$(echo $NPM_VERSION | cut -d. -f1)
  if [ "$MAJOR" -ge 8 ]; then
    echo "✅ npm $NPM_VERSION"
  else
    echo "❌ npm $NPM_VERSION (need >= 8.0.0)"
    FAILED=1
  fi
fi

# Yeoman
YO_VERSION=$(yo --version 2>/dev/null)
if [ -z "$YO_VERSION" ]; then
  echo "❌ Yeoman not installed"
  FAILED=1
else
  MAJOR=$(echo $YO_VERSION | cut -d. -f1)
  if [ "$MAJOR" -ge 4 ]; then
    echo "✅ Yeoman $YO_VERSION"
  else
    echo "❌ Yeoman $YO_VERSION (need >= 4.0.0)"
    FAILED=1
  fi
fi

# Java
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ -z "$JAVA_VERSION" ]; then
  echo "❌ Java not installed"
  FAILED=1
else
  if [ "$JAVA_VERSION" -ge 11 ]; then
    echo "✅ Java $JAVA_VERSION"
  else
    echo "❌ Java $JAVA_VERSION (need >= 11)"
    FAILED=1
  fi
fi

# Git
GIT_VERSION=$(git --version 2>/dev/null | sed 's/git version //')
if [ -z "$GIT_VERSION" ]; then
  echo "❌ Git not installed"
  FAILED=1
else
  echo "✅ Git $GIT_VERSION"
fi

# yq
YQ_VERSION=$(yq --version 2>/dev/null)
if [ -z "$YQ_VERSION" ]; then
  echo "⚠️  yq not installed (required for some operations)"
else
  echo "✅ yq installed"
fi

# jq
JQ_VERSION=$(jq --version 2>/dev/null)
if [ -z "$JQ_VERSION" ]; then
  echo "⚠️  jq not installed (optional but recommended)"
else
  echo "✅ jq installed"
fi

echo ""
echo "Generator check:"
npm ls -g @auditmation/generator-hub-module 2>/dev/null || echo "❌ Module generator not installed"

if [ $FAILED -eq 0 ]; then
  echo ""
  echo "✅ All prerequisites met!"
  exit 0
else
  echo ""
  echo "❌ Some prerequisites missing. Install them before proceeding."
  exit 1
fi
```

**Usage:**
```bash
chmod +x check-prerequisites.sh
./check-prerequisites.sh
```

### 3.4 IDE Setup (Recommended)

**Visual Studio Code Extensions:**
- ESLint
- Prettier
- YAML
- OpenAPI (Swagger) Editor
- GitLens

**Settings (.vscode/settings.json):**
```json
{
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  },
  "typescript.tsdk": "node_modules/typescript/lib"
}
```

### 3.5 Your First Validation

Before proceeding to module creation, validate your environment:

```bash
# Run the prerequisites check
./check-prerequisites.sh

# Verify repository structure
ls -la

# Should see:
# - package/
# - lerna.json
# - .claude/
```

✅ **Checkpoint**: All prerequisites installed and verified

---

# Part 2: The 8-Phase Module Creation Process

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

**Note**: Response schema is an array (not wrapped in an object). The framework handles pagination metadata via the links header.

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

## Chapter 7: Phase 4 - Core Implementation

### 7.1 Phase Overview

**Goal**: Implement Client, Producers, and Mappers

**Duration**: 60-90 minutes

**Deliverables**:
- `{Service}Client.ts` - Connection management
- `{Resource}Producer.ts` - Business operations
- `Mappers.ts` - Data transformation
- `util.ts` - Helper functions
- **Gate 3: Implementation** ✅

### 7.2 Separation of Concerns

**Critical Architecture Rule:**

```
Client    = Connection ONLY (connect, isConnected, disconnect)
Producers = Operations ONLY (list, get, create, update, delete)
Mappers   = Transform ONLY (API response → typed objects)
```

**NEVER:**
- ❌ Client implements business operations
- ❌ Producers duplicate connection context (apiKey, token, baseUrl parameters)
- ❌ Mappers access environment variables

### 7.3 Implement Client (Connection Management)

#### 7.3.1 Create {Service}Client.ts

**Example: GitHubClient.ts**

```typescript
import axios, { AxiosInstance } from 'axios';
import { Email, DateTime } from '@auditmation/types-core-js';
import { handleAxiosError } from './util';
import {
  ConnectionProfile,
  ConnectionState
} from '../generated/api';

export class GitHubClient {
  private httpClient: AxiosInstance;
  private connectionProfile?: ConnectionProfile;
  private connectionState?: ConnectionState;

  constructor() {
    this.httpClient = axios.create({
      baseURL: 'https://api.github.com',
      timeout: 30000,
      headers: {
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28'
      }
    });

    // Add response interceptor for error handling
    this.httpClient.interceptors.response.use(
      response => response,
      error => handleAxiosError(error)
    );

    // Add request interceptor for authentication
    this.httpClient.interceptors.request.use(config => {
      if (this.connectionState?.accessToken) {
        config.headers['Authorization'] = `Bearer ${this.connectionState.accessToken}`;
      }
      return config;
    });
  }

  /**
   * Connect to GitHub API
   * Authenticates and stores connection state
   */
  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    this.connectionProfile = profile;

    // For GitHub, we use personal access token (already have token)
    // Some APIs require a login call here

    const response = await this.httpClient.get('/user', {
      headers: {
        'Authorization': `Bearer ${profile.token}`
      }
    });

    // GitHub tokens don't expire, but we set a long expiry for framework
    const state: ConnectionState = {
      accessToken: profile.token,
      expiresIn: 31536000 // 1 year in seconds
    };

    this.connectionState = state;
    return state;
  }

  /**
   * Check if connected to GitHub API
   * Makes a real API call to verify token validity
   */
  async isConnected(): Promise<boolean> {
    if (!this.connectionState?.accessToken) {
      return false;
    }

    try {
      await this.httpClient.get('/user');
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Disconnect from GitHub API
   * Clears connection state
   */
  async disconnect(): Promise<void> {
    this.connectionState = undefined;
    this.connectionProfile = undefined;
  }

  /**
   * Get HTTP client instance for producers
   */
  get apiClient(): AxiosInstance {
    return this.httpClient;
  }
}
```

**Key Points:**
- ✅ **Only 3 methods**: connect, isConnected, disconnect
- ✅ **connect()** returns ConnectionState
- ✅ **isConnected()** makes real API call
- ✅ **apiClient** getter provides HTTP client to producers
- ✅ Request interceptor adds authentication
- ✅ Response interceptor handles errors

#### 7.3.2 Alternative: OAuth Email/Password Authentication

**Example: Service requiring login**

```typescript
async connect(profile: ConnectionProfile): Promise<ConnectionState> {
  this.connectionProfile = profile;

  // POST to /auth/login
  const response = await this.httpClient.post('/auth/login', {
    email: profile.email,
    password: profile.password
  });

  // Calculate expiresIn from expiresAt if API returns timestamp
  const expiresAtTimestamp = new Date(response.data.expires_at).getTime();
  const nowTimestamp = Date.now();
  const expiresIn = Math.floor((expiresAtTimestamp - nowTimestamp) / 1000);

  const state: ConnectionState = {
    accessToken: response.data.access_token,
    refreshToken: response.data.refresh_token,
    expiresIn: expiresIn // MANDATORY - in SECONDS
  };

  this.connectionState = state;
  return state;
}
```

**Critical Rules:**
- ✅ Store ALL refresh-relevant data in ConnectionState
- ✅ expiresIn MUST be in SECONDS (not milliseconds or timestamp)
- ✅ Convert expiresAt to expiresIn: `Math.floor((expiresAt - now) / 1000)`
- ✅ Store refreshToken if API provides refresh capability

### 7.4 Implement util.ts (Helper Functions)

#### 7.4.1 Create util.ts

```typescript
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
  InvalidStateError
} from '@auditmation/types-core-js';

/**
 * Handle axios errors and convert to core error types
 */
export function handleAxiosError(error: any): never {
  const status = error.response?.status || error.status || 500;
  const data = error.response?.data || {};
  const message = data.message || data.error || error.message || 'Unknown error';

  switch (status) {
    case 401:
      throw new InvalidCredentialsError();

    case 403:
      if (message.toLowerCase().includes('rate') ||
          message.toLowerCase().includes('limit')) {
        throw new RateLimitExceededError();
      }
      throw new UnauthorizedError();

    case 404:
      throw new NoSuchObjectError('resource', 'unknown');

    case 400:
    case 422:
      const field = data.field || data.parameter || 'request';
      const value = data.value || message;
      throw new InvalidInputError(field, value);

    case 429:
      throw new RateLimitExceededError();

    default:
      if (status >= 500) {
        throw new UnexpectedError(`Server error: ${message}`, status);
      }
      throw new UnexpectedError(`API error: ${message}`, status);
  }
}

/**
 * Validate required properties exist in raw API data
 * Throws InvalidStateError if any property is null or undefined
 */
export function ensureProperties<K extends string>(
  raw: unknown,
  properties: readonly K[]
): asserts raw is Record<K, NonNullable<unknown>> {
  if (raw === null || raw === undefined) {
    throw new InvalidStateError(`Object is ${raw}`);
  }

  const obj = raw as Record<string, unknown>;

  for (const prop of properties) {
    const value = obj[prop];
    if (value === null || value === undefined) {
      throw new InvalidStateError(`Missing required field: ${prop}`);
    }
  }
}

/**
 * Normalize null to undefined while preserving all other falsy values
 * Preserves: 0, "", false, []
 * Converts: null → undefined
 */
export function optional<T>(value: T | null | undefined): T | undefined {
  return value ?? undefined;
}

/**
 * Apply a mapper function to a value, handling null/undefined at boundary
 * Works like map() but for custom mapper functions
 */
export function mapWith<T>(
  mapper: (raw: any) => T,
  value: any
): T | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  return mapper(value);
}

/**
 * Convert string value to enum with optional transformation
 * Default transformation: converts to snake_case
 */
export function toEnum<T extends Record<string, any>>(
  enumType: T,
  value: any,
  transform: (v: string) => string = toSnakeCase
): T[keyof T] | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }

  const transformed = transform(String(value));
  const enumValue = Object.values(enumType).find(
    v => String(v) === transformed
  );

  return enumValue as T[keyof T] | undefined;
}

/**
 * Convert string to snake_case
 */
function toSnakeCase(str: string): string {
  return str
    .replace(/([A-Z])/g, '_$1')
    .toLowerCase()
    .replace(/^_/, '');
}
```

### 7.5 Implement Mappers

#### 7.5.1 Create Mappers.ts

```typescript
import { map } from '@auditmation/util-hub-module-utils';
import { UUID, Email, DateTime } from '@auditmation/types-core-js';
import { User } from '../generated/api';
import { ensureProperties, optional } from './util';

/**
 * Convert raw API user data to User type
 */
export function toUser(raw: any): User {
  // 1. Validate required fields
  ensureProperties(raw, ['id', 'email', 'first_name', 'created_at']);

  // 2. Create typed output
  const output: User = {
    id: raw.id.toString(),
    email: map(Email, raw.email),
    firstName: raw.first_name,
    lastName: optional(raw.last_name),
    createdAt: map(DateTime, raw.created_at)
  };

  return output;
}
```

**Mapper Pattern Breakdown:**

1. **Validate Required Fields**
   ```typescript
   ensureProperties(raw, ['id', 'email', 'first_name', 'created_at']);
   ```
   - Checks all required fields exist
   - Throws InvalidStateError if missing
   - TypeScript knows fields exist after this

2. **Create Typed Output with const**
   ```typescript
   const output: User = { ... };
   ```
   - Declare with explicit type
   - All fields visible in one place
   - TypeScript catches missing fields

3. **Field Mappings**
   ```typescript
   id: raw.id.toString()                    // ID conversion
   email: map(Email, raw.email)             // Type conversion with map()
   firstName: raw.first_name                // snake_case → camelCase
   lastName: optional(raw.last_name)        // Optional field - null→undefined
   createdAt: map(DateTime, raw.created_at) // DateTime conversion
   ```

**Field Mapping Rules:**

| Type | Pattern | Example |
|------|---------|---------|
| ID (number) | `raw.id.toString()` | `id: raw.id.toString()` |
| String (required) | Direct mapping | `firstName: raw.first_name` |
| String (optional) | `optional()` | `lastName: optional(raw.last_name)` |
| Email | `map(Email, ...)` | `email: map(Email, raw.email)` |
| UUID | `map(UUID, ...)` | `userId: map(UUID, raw.user_id)` |
| DateTime | `map(DateTime, ...)` | `createdAt: map(DateTime, raw.created_at)` |
| URL | `map(URL, ...)` | `website: map(URL, raw.website)` |
| Date | `map(Date, ...)` | `birthDate: map(Date, raw.birth_date)` |
| Enum | `toEnum(EnumType, ...)` | `status: toEnum(UserStatus, raw.status)` |
| Nested Object | `mapWith(toNested, ...)` | `address: mapWith(toAddress, raw.address)` |
| Array | `raw.items?.map(toItem)` | `items: raw.items?.map(toItem)` |

#### 7.5.2 Helper Mappers (Nested Objects)

```typescript
// Helper - NOT exported, declared BEFORE main mapper
function toAddress(raw: any): Address {
  ensureProperties(raw, ['street']);

  const output: Address = {
    street: raw.street,
    city: optional(raw.city),
    zipCode: optional(raw.zip_code)
  };

  return output;
}

// Main mapper uses helper with mapWith
export function toUser(raw: any): User {
  ensureProperties(raw, ['id']);

  const output: User = {
    id: raw.id.toString(),
    address: mapWith(toAddress, raw.address) // mapWith handles null/undefined
  };

  return output;
}
```

#### 7.5.3 Critical Mapper Rules

**1. ZERO MISSING FIELDS**
- Interface has N fields → Mapper must map N fields
- Count MUST match exactly

**2. Use const output Pattern**
```typescript
const output: User = { ... };
return output;
```

**3. Validate Required Fields**
```typescript
ensureProperties(raw, ['id', 'email']);
```

**4. Prefer map() over constructors**
```typescript
// ✅ CORRECT
email: map(Email, raw.email)

// ⚠️ AVOID (use only if map() doesn't work)
email: new Email(raw.email)
```

**5. Use optional() for optional fields**
```typescript
lastName: optional(raw.last_name) // null→undefined, keeps "", 0, false
```

**6. NEVER use logical OR**
```typescript
// ❌ WRONG - destroys legitimate values
name: raw.name || undefined // Converts "" to undefined

// ✅ CORRECT
name: optional(raw.name) // Keeps ""
```

**7. NO fallbacks between different fields**
```typescript
// ❌ WRONG
phoneNumber: raw.mobilePhone || raw.phoneNumber || undefined

// ✅ CORRECT - map each field
mobilePhone: optional(raw.mobilePhone)
phoneNumber: optional(raw.phoneNumber)
```

**8. Enum conversion**
```typescript
status: toEnum(UserStatus, raw.status) // Default: snake_case
```

### 7.6 Implement Producers

#### 7.6.1 Create {Resource}Producer.ts

**Example: UserProducer.ts**

```typescript
import { PagedResults } from '@auditmation/types-core-js';
import { User } from '../generated/api';
import { GitHubClient } from './GitHubClient';
import { toUser } from './Mappers';
import { handleAxiosError } from './util';

export class UserProducer {
  constructor(private client: GitHubClient) {}

  /**
   * List users with pagination
   */
  async listUsers(
    results: PagedResults<User>,
    name?: string
  ): Promise<void> {
    const params: Record<string, string | number> = {};

    // Pagination: offset/limit from pageNumber/pageSize
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0; // MANDATORY: Always initialize offset
    }

    // Filter parameter
    if (name) {
      params.name = name;
    }

    const { data } = await this.client.apiClient
      .request({
        url: '/users',
        method: 'get',
        params
      })
      .catch(handleAxiosError);

    // Validate response structure before mapping
    if (!data || !Array.isArray(data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Manual assignment (NOT ingest)
    results.items = data.data.map(toUser);
    results.count = data.totalCount || 0;
  }

  /**
   * Get user by ID
   */
  async getUser(userId: string): Promise<User> {
    const { data } = await this.client.apiClient
      .request({
        url: `/users/${userId}`,
        method: 'get'
      })
      .catch(handleAxiosError);

    return toUser(data);
  }

  /**
   * Create new user
   */
  async createUser(input: UserCreateInput): Promise<User> {
    const { data } = await this.client.apiClient
      .request({
        url: '/users',
        method: 'post',
        data: input
      })
      .catch(handleAxiosError);

    return toUser(data);
  }

  /**
   * Delete user
   */
  async deleteUser(userId: string): Promise<void> {
    await this.client.apiClient
      .request({
        url: `/users/${userId}`,
        method: 'delete'
      })
      .catch(handleAxiosError);
  }
}
```

**Producer Pattern Breakdown:**

1. **Constructor receives Client**
   ```typescript
   constructor(private client: GitHubClient) {}
   ```
   - Client provides HTTP client instance
   - No connection context parameters!

2. **PagedResults Operations**
   ```typescript
   async listUsers(
     results: PagedResults<User>,
     name?: string
   ): Promise<void> {
     // Convert pageNumber/pageSize → API pagination
     // Make request
     // Apply mapper
     // Manual assignment: results.items = ...
     // Update count: results.count = ...
   }
   ```

3. **Single Resource Operations**
   ```typescript
   async getUser(userId: string): Promise<User> {
     const { data } = await this.client.apiClient.request(...);
     return toUser(data);
   }
   ```

**Critical Producer Rules:**

**1. NO Connection Context Parameters**
```typescript
// ❌ WRONG
async getUser(
  userId: string,
  apiKey: string,      // NO!
  baseUrl: string,     // NO!
  orgId: string        // NO!
): Promise<User>

// ✅ CORRECT
async getUser(userId: string): Promise<User>
```

**2. ALWAYS use handleAxiosError**
```typescript
.catch(handleAxiosError)
```

**3. PagedResults Manual Assignment**
```typescript
// ✅ CORRECT
results.items = data.map(toUser) || [];

// ❌ WRONG
results.ingest(data.map(toUser)); // Unreliable
```

**4. NEVER use Promise<any>**
```typescript
// ❌ WRONG
async getUser(userId: string): Promise<any>

// ✅ CORRECT
async getUser(userId: string): Promise<User>
```

**5. Update PagedResults count**
```typescript
if (data.total !== undefined) {
  results.count = data.total;
}
```

### 7.7 Implement ConnectorImpl

#### 7.7.1 Update {Service}Impl.ts

```typescript
import {
  ConnectionStatus,
  OperationSupportStatus,
  AccessApi
} from '@auditmation/types-core-js';
import { GitHubClient } from './GitHubClient';
import { UserProducer } from './UserProducer';
import type {
  ConnectionProfile,
  ConnectionState
} from '../generated/api';

export class GitHubConnector {
  private client: GitHubClient;
  private userProducer: UserProducer;

  constructor() {
    this.client = new GitHubClient();
    this.userProducer = new UserProducer(this.client);
  }

  /**
   * Connect to GitHub API
   */
  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    return await this.client.connect(profile);
  }

  /**
   * Check if connected to GitHub API
   */
  async isConnected(): Promise<boolean> {
    return await this.client.isConnected();
  }

  /**
   * Disconnect from GitHub API
   */
  async disconnect(): Promise<void> {
    return await this.client.disconnect();
  }

  /**
   * Get user producer API
   */
  getUserApi(): UserProducer {
    return this.userProducer;
  }

  /**
   * Connection status metadata
   */
  get metadata() {
    return {
      connectionStatus: ConnectionStatus.Down,
      isSupported: (operation: string) => {
        // For now, return Maybe for all operations
        return OperationSupportStatus.Maybe;
      }
    };
  }
}
```

#### 7.7.2 Update src/index.ts

```typescript
export { GitHubConnector } from './GitHubImpl';
export * from '../generated/api';

/**
 * Create new GitHub service instance
 */
export function newService(): GitHubConnector {
  return new GitHubConnector();
}
```

### 7.8 Validate Implementation (Gate 3)

#### 7.8.1 Run Build

```bash
# The build will catch most issues
npm run build

# If build succeeds, your implementation is good! ✓
```

**Gate 3 Pass Criteria:**
- ✅ `npm run build` exits with code 0 (no errors)
- ✅ Code follows the patterns from templates

**Note**: If the build succeeds, TypeScript has validated your types and imports are correct!

### 7.9 Phase 4 Validation Checklist

**Before proceeding to Phase 5:**

- [ ] ✅ Client implemented (connect, isConnected, disconnect)
- [ ] ✅ Client provides apiClient getter
- [ ] ✅ util.ts created with helper functions
- [ ] ✅ handleAxiosError implemented
- [ ] ✅ ensureProperties implemented
- [ ] ✅ optional() implemented
- [ ] ✅ mapWith() implemented
- [ ] ✅ toEnum() implemented
- [ ] ✅ Mappers.ts created
- [ ] ✅ All mappers follow `to<Model>` naming
- [ ] ✅ Mappers use const output pattern
- [ ] ✅ Mappers validate required fields
- [ ] ✅ Mappers handle ALL interface fields
- [ ] ✅ Producers implemented
- [ ] ✅ Producers receive only business parameters
- [ ] ✅ Producers use handleAxiosError
- [ ] ✅ ConnectorImpl updated
- [ ] ✅ index.ts exports updated
- [ ] ✅ **Gate 3: Implementation PASSED** ✅

---

## Chapter 8: Phase 5 - Testing

### 8.1 Phase Overview

**Goal**: Write comprehensive unit and integration tests

**Duration**: 60-90 minutes

**Deliverables**:
- Unit tests (mocked HTTP, no credentials)
- Integration tests (real API, credentials required)
- **Gate 4: Unit Test Creation & Execution** ✅
- **Gate 5: Integration Tests** ✅

**Testing Philosophy:**
- **Unit Tests**: Fast, offline, mocked HTTP with nock
- **Integration Tests**: Real API calls, verify actual behavior

### 8.2 Unit Tests (Mocked HTTP)

#### 8.2.1 Update test/unit/Common.ts

```typescript
import * as nock from 'nock';
import { Email } from '@auditmation/types-core-js';
import { newService } from '../../src';
import type { GitHubConnector } from '../../src';

/**
 * Get a connected instance for unit testing
 * Uses mocked HTTP - no real credentials needed
 * Unit tests should NEVER depend on environment variables
 */
export async function getConnectedInstance(): Promise<GitHubConnector> {
  nock('https://api.github.com')
    .get('/user')
    .reply(200, {
      id: '12345',
      login: 'testuser',
      email: 'test@example.com'
    });

  const connector = newService();

  await connector.connect({
    token: 'test-token-123'
  });

  return connector;
}
```

**Critical Rules for Unit Test Common.ts:**
- ✅ Uses nock to mock HTTP
- ✅ NO environment variables
- ✅ NO dotenv
- ✅ NO real credentials
- ✅ Works without .env file

#### 8.2.2 Create test/unit/ConnectionTest.ts

```typescript
import { expect } from 'chai';
import * as nock from 'nock';
import { InvalidCredentialsError } from '@auditmation/types-core-js';
import { newService } from '../../src';

describe('Connection', () => {
  describe('connect', () => {
    it('should successfully authenticate and store token', async () => {
      nock('https://api.github.com')
        .get('/user')
        .reply(200, {
          id: '12345',
          login: 'testuser'
        });

      const connector = newService();
      const connectionState = await connector.connect({
        token: 'test-token'
      });

      expect(connectionState).to.have.property('accessToken');
      expect(connectionState.accessToken).to.equal('test-token');
    });

    it('should handle authentication failure', async () => {
      nock('https://api.github.com')
        .get('/user')
        .reply(401, { message: 'Bad credentials' });

      const connector = newService();

      try {
        await connector.connect({
          token: 'invalid-token'
        });
        expect.fail('Should have thrown an error');
      } catch (error: any) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }
    });
  });

  describe('isConnected', () => {
    it('should return true when connected', async () => {
      nock('https://api.github.com')
        .get('/user')
        .twice()
        .reply(200, { id: '12345' });

      const connector = newService();
      await connector.connect({ token: 'test-token' });

      const isConnected = await connector.isConnected();
      expect(isConnected).to.be.true;
    });

    it('should return false when not connected', async () => {
      const connector = newService();
      const isConnected = await connector.isConnected();
      expect(isConnected).to.be.false;
    });
  });

  describe('disconnect', () => {
    it('should clear connection state', async () => {
      nock('https://api.github.com')
        .get('/user')
        .reply(200, { id: '12345' });

      const connector = newService();
      await connector.connect({ token: 'test-token' });

      await connector.disconnect();
      const isConnected = await connector.isConnected();
      expect(isConnected).to.be.false;
    });
  });
});
```

#### 8.2.3 Create test/unit/UserProducerTest.ts

```typescript
import { expect } from 'chai';
import * as nock from 'nock';
import { PagedResults, UUID, Email } from '@auditmation/types-core-js';
import { User } from '../../generated/api';
import { getConnectedInstance } from './Common';

describe('UserProducer', () => {
  describe('listUsers', () => {
    it('should retrieve paginated list of users', async () => {
      nock('https://api.github.com')
        .get('/users')
        .query({ offset: 0, limit: 10 })
        .reply(200, {
          data: [
            {
              id: '1',
              email: 'user1@example.com',
              first_name: 'John',
              last_name: 'Doe',
              created_at: '2025-01-01T00:00:00Z'
            },
            {
              id: '2',
              email: 'user2@example.com',
              first_name: 'Jane',
              last_name: 'Smith',
              created_at: '2025-01-02T00:00:00Z'
            }
          ],
          total: 100
        });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const results = new PagedResults<User>();
      results.pageNumber = 1;
      results.pageSize = 10;

      await userApi.listUsers(results);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(2);
      expect(results.count).to.equal(100);

      const firstUser = results.items[0];
      expect(firstUser.id).to.equal('1');
      expect(firstUser.email).to.be.instanceof(Email);
      expect(firstUser.firstName).to.equal('John');
    });

    it('should filter users by name', async () => {
      nock('https://api.github.com')
        .get('/users')
        .query({ offset: 0, limit: 10, name: 'John' })
        .reply(200, {
          data: [
            {
              id: '1',
              email: 'john@example.com',
              first_name: 'John',
              created_at: '2025-01-01T00:00:00Z'
            }
          ],
          total: 1
        });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const results = new PagedResults<User>();
      results.pageNumber = 1;
      results.pageSize = 10;

      await userApi.listUsers(results, 'John');

      expect(results.items).to.have.length(1);
      expect(results.items[0].firstName).to.equal('John');
    });
  });

  describe('getUser', () => {
    it('should retrieve user by ID', async () => {
      nock('https://api.github.com')
        .get('/users/123')
        .reply(200, {
          id: '123',
          email: 'user@example.com',
          first_name: 'John',
          last_name: 'Doe',
          created_at: '2025-01-01T00:00:00Z'
        });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const user = await userApi.getUser('123');

      expect(user).to.not.be.null;
      expect(user.id).to.equal('123');
      expect(user.email).to.be.instanceof(Email);
      expect(user.firstName).to.equal('John');
    });

    it('should handle user not found', async () => {
      nock('https://api.github.com')
        .get('/users/999')
        .reply(404, { message: 'Not found' });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      try {
        await userApi.getUser('999');
        expect.fail('Should have thrown an error');
      } catch (error: any) {
        expect(error).to.exist;
      }
    });
  });
});
```

#### 8.2.4 Unit Test Validation

```bash
# Run unit tests
npm run test:unit

# Expected output:
# Connection
#   connect
#     ✓ should successfully authenticate and store token
#     ✓ should handle authentication failure
#   isConnected
#     ✓ should return true when connected
#     ✓ should return false when not connected
#   disconnect
#     ✓ should clear connection state
#
# UserProducer
#   listUsers
#     ✓ should retrieve paginated list of users
#     ✓ should filter users by name
#   getUser
#     ✓ should retrieve user by ID
#     ✓ should handle user not found
#
# 9 passing (50ms)
```

**Gate 4 Pass Criteria:**
- ✅ Unit tests exist for Connection
- ✅ Unit tests exist for all Producers
- ✅ 3+ test cases per operation
- ✅ All tests use nock for HTTP mocking
- ✅ No forbidden mocking libraries (jest, sinon, fetch-mock)
- ✅ All unit tests passing (100% pass rate)

### 8.3 Integration Tests (Real API)

#### 8.3.1 Update test/integration/Common.ts

```typescript
import { config } from 'dotenv';
import { getLogger as getLoggerBase } from '@auditmation/util-logger';
import { newService } from '../../src';
import type { GitHubConnector } from '../../src';

// Load .env file explicitly
config();

// Export credentials
export const GITHUB_TOKEN = process.env.GITHUB_TOKEN || '';

// Export test data
export const GITHUB_TEST_USER_ID = process.env.GITHUB_TEST_USER_ID || '';

const LOG_LEVEL = (process.env.LOG_LEVEL || 'info') as string;

/**
 * Get a logger with configurable level from LOG_LEVEL env var
 * Usage: LOG_LEVEL=debug npm run test:integration
 */
export function getLogger(name: string) {
  return getLoggerBase(name, {}, LOG_LEVEL);
}

export function hasCredentials(): boolean {
  return !!GITHUB_TOKEN;
}

// Cached connector instance - connect once, reuse many times
let cachedConnector: GitHubConnector | null = null;

/**
 * Get a connected instance for integration testing
 * Connects once on first call, returns cached instance on subsequent calls
 * Uses real credentials from .env file
 * Makes real API calls
 */
export async function getConnectedInstance(): Promise<GitHubConnector> {
  if (cachedConnector) {
    return cachedConnector;
  }

  const connector = newService();

  await connector.connect({
    token: GITHUB_TOKEN
  });

  cachedConnector = connector;
  return connector;
}
```

**Critical Rules for Integration Test Common.ts:**
- ✅ Loads dotenv explicitly
- ✅ Exports credential constants
- ✅ Exports getLogger() helper
- ✅ Has hasCredentials() check
- ✅ Connection caching for performance
- ✅ Makes real API calls

#### 8.3.2 Create test/integration/ConnectionTest.ts

```typescript
import { expect } from 'chai';
import { newService } from '../../src';
import { GITHUB_TOKEN, hasCredentials, getLogger } from './Common';

const logger = getLogger('ConnectionTest');

describe('Connection Integration Tests', function () {
  this.timeout(30000);

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('connect', () => {
    it('should successfully connect to real API', async () => {
      const connector = newService();

      logger.debug('connector.connect(token)');
      const connectionState = await connector.connect({
        token: GITHUB_TOKEN
      });
      logger.debug('→', JSON.stringify(connectionState, null, 2));

      expect(connectionState).to.have.property('accessToken');
    });
  });

  describe('isConnected', () => {
    it('should return true when connected', async () => {
      const connector = newService();

      await connector.connect({
        token: GITHUB_TOKEN
      });

      logger.debug('connector.isConnected()');
      const isConnected = await connector.isConnected();
      logger.debug('→', JSON.stringify({ isConnected }, null, 2));

      expect(isConnected).to.be.true;
    });
  });

  describe('disconnect', () => {
    it('should successfully disconnect from API', async () => {
      const connector = newService();

      await connector.connect({
        token: GITHUB_TOKEN
      });

      logger.debug('connector.disconnect()');
      await connector.disconnect();
      logger.debug('→ disconnect completed');
    });
  });
});
```

#### 8.3.3 Create test/integration/UserProducerTest.ts

```typescript
import { expect } from 'chai';
import { PagedResults, Email } from '@auditmation/types-core-js';
import { User } from '../../generated/api';
import {
  getConnectedInstance,
  hasCredentials,
  getLogger,
  GITHUB_TEST_USER_ID
} from './Common';

const logger = getLogger('UserProducerTest');

describe('UserProducer Integration Tests', function () {
  this.timeout(30000);

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('listUsers', () => {
    it('should retrieve real users from API', async () => {
      logger.debug('getConnectedInstance()');
      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const results = new PagedResults<User>();
      results.pageNumber = 1;
      results.pageSize = 10;

      logger.debug('userApi.listUsers(results, pageSize=10)');
      await userApi.listUsers(results);
      logger.debug('→', JSON.stringify(results, null, 2));

      expect(results.items).to.be.an('array');
      expect(results.items.length).to.be.greaterThan(0);

      const firstUser = results.items[0];
      expect(firstUser.id).to.be.a('string');
      expect(firstUser.email).to.be.instanceof(Email);
    });
  });

  describe('getUser', () => {
    it('should retrieve user by ID from real API', async () => {
      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const userId = GITHUB_TEST_USER_ID;

      logger.debug(`userApi.getUser(${userId})`);
      const user = await userApi.getUser(userId);
      logger.debug('→', JSON.stringify(user, null, 2));

      expect(user).to.not.be.null;
      expect(user.id).to.equal(userId);
      expect(user.email).to.be.instanceof(Email);
    });
  });
});
```

#### 8.3.4 Integration Test Validation

```bash
# Run integration tests
npm run test:integration

# Or with debug logging
LOG_LEVEL=debug npm run test:integration

# Expected output:
# Connection Integration Tests
#   connect
#     ✓ should successfully connect to real API (500ms)
#   isConnected
#     ✓ should return true when connected (300ms)
#   disconnect
#     ✓ should successfully disconnect from API (200ms)
#
# UserProducer Integration Tests
#   listUsers
#     ✓ should retrieve real users from API (1200ms)
#   getUser
#     ✓ should retrieve user by ID from real API (800ms)
#
# 5 passing (3s)
```

**Gate 5 Pass Criteria:**
- ✅ Integration tests exist for Connection
- ✅ Integration tests exist for all Producers
- ✅ 3+ test cases per operation
- ✅ No mocking in integration tests
- ✅ No hardcoded test values (all from .env)
- ✅ All integration tests passing (100% pass rate)
- ✅ Debug logging present

### 8.4 Phase 5 Validation Checklist

**Before proceeding to Phase 6:**

- [ ] ✅ test/unit/Common.ts created (NO env vars, uses nock)
- [ ] ✅ test/unit/ConnectionTest.ts created
- [ ] ✅ test/unit/{Resource}ProducerTest.ts created
- [ ] ✅ All unit tests use nock ONLY
- [ ] ✅ No forbidden mocking libraries
- [ ] ✅ **Gate 4: Unit Test Creation & Execution PASSED** ✅
- [ ] ✅ test/integration/Common.ts created (loads dotenv, has getLogger)
- [ ] ✅ test/integration/ConnectionTest.ts created
- [ ] ✅ test/integration/{Resource}ProducerTest.ts created
- [ ] ✅ All integration tests have debug logging
- [ ] ✅ Test data from .env (not hardcoded)
- [ ] ✅ .env file has credentials and test values
- [ ] ✅ **Gate 5: Integration Tests PASSED** ✅

---

*[Training manual continues with Chapters 9-17 covering Documentation, Build, Quality Gates Validation, Adding Operations, Troubleshooting, Best Practices, Quick Reference, Validation Checklists, and Code Templates]*

---

**END OF EXCERPT**

*Note: This is Part 1 of the training manual (Chapters 1-8). The complete manual would continue with the remaining chapters covering documentation, build processes, advanced topics, and reference materials. Each chapter follows the same detailed, step-by-step approach with code examples, validation scripts, and hands-on exercises.*

---

## About This Training Manual

**Total Estimated Pages**: 350-400 pages
**Format**: Markdown for easy viewing and printing
**Updates**: Living document, updated as processes evolve

**For Questions or Updates**: Contact the module development team

## Chapter 9: Phase 6 - Documentation

### 9.1 Phase Overview

**Goal**: Document the module for end users

**Duration**: 15-30 minutes

**Deliverables**:
- USERGUIDE.md - Credential acquisition guide
- README.md updates (if special requirements exist)

### 9.2 USERGUIDE.md (Mandatory)

#### 9.2.1 Purpose

The USERGUIDE.md provides detailed instructions for:
- How to obtain API credentials
- Mapping credentials to connection profile fields
- Required permissions/scopes
- Step-by-step setup instructions

#### 9.2.2 Template Structure

```markdown
# {Service Name} Module User Guide

## Overview

This guide explains how to set up authentication for the {Service Name} module.

## Prerequisites

- Active {Service Name} account
- Required permissions: [list specific permissions]
- (If applicable) Admin access to organization

## Obtaining Credentials

### Step 1: Access API Settings

1. Log into [{Service Name}](https://service.example.com)
2. Navigate to **Settings** > **API** (or relevant path)
3. Click **Create API Credentials** or **Generate Token**

### Step 2: Generate API Key

1. Enter a name for your API key: "Integration API Key"
2. Select required permissions:
   - ☑ Read access to users
   - ☑ Read access to organizations
   - ☑ Write access (if creating/updating resources)
3. Click **Generate** or **Create**
4. **IMPORTANT**: Copy the API key immediately - it won't be shown again

### Step 3: Store Credentials Securely

Create a `.env` file in your project root:

```env
SERVICE_API_KEY=your-api-key-here
SERVICE_BASE_URL=https://api.service.com  # Optional, if custom endpoint
```

Add `.env` to your `.gitignore` file:
```
.env
.env.*
```

## Connection Profile Mapping

Map your credentials to the connection profile:

| Service Credential | Connection Profile Field | Required | Description |
|-------------------|-------------------------|----------|-------------|
| API Key | `token` | Yes | Your API key from Step 2 |
| Base URL | `baseUrl` | No | Custom API endpoint (default: https://api.service.com) |

## Required Permissions

Your API key must have these permissions:

- **Users**: Read access (for listUsers, getUser)
- **Organizations**: Read access (for listOrganizations)
- **Access Control**: Read/Write (for unlocking doors)

## Usage Example

```typescript
import { newServiceName } from '@auditmation/connector-service-name';

const connector = newServiceName();

await connector.connect({
  token: process.env.SERVICE_API_KEY!
});
```

## Troubleshooting

### Invalid Credentials Error

**Problem**: Getting 401 Unauthorized

**Solutions**:
1. Verify API key is copied correctly (no extra spaces)
2. Check API key hasn't expired
3. Ensure API key has required permissions

### Rate Limit Errors

**Problem**: Getting 429 Too Many Requests

**Solutions**:
1. Reduce request frequency
2. Implement retry logic with exponential backoff
3. Contact support to increase rate limits

## Support

For additional help:
- API Documentation: https://docs.service.com/api
- Support Email: support@service.com
```

### 9.3 Phase 6 Validation Checklist

**Before proceeding to Phase 7:**

- [ ] ✅ USERGUIDE.md created
- [ ] ✅ Prerequisites section included
- [ ] ✅ Step-by-step credential instructions
- [ ] ✅ Connection profile mapping table
- [ ] ✅ Required permissions listed
- [ ] ✅ Usage example included
- [ ] ✅ No real credentials in documentation
- [ ] ✅ Service-specific URLs updated
- [ ] ✅ Troubleshooting section added
- [ ] ✅ README.md updated (if special requirements exist)

---

## Chapter 10: Phase 7 - Build & Finalization

### 10.1 Phase Overview

**Goal**: Build the module and lock dependencies

**Duration**: 5-10 minutes

**Deliverables**:
- dist/ directory with compiled JavaScript
- npm-shrinkwrap.json with locked dependencies
- **Gate 6: Build** ✅

### 10.2 Build Process

```bash
# Navigate to module directory
cd package/{vendor}/{service}

# Clean previous build
npm run clean

# Build the module
npm run build

# Lock dependencies
npm run shrinkwrap

# Verify
ls -la dist/
ls -la npm-shrinkwrap.json
```

### 10.3 Phase 7 Validation Checklist

**Before proceeding to Phase 8:**

- [ ] ✅ npm run clean executed
- [ ] ✅ npm run build completed successfully
- [ ] ✅ Build exit code is 0
- [ ] ✅ No TypeScript compilation errors
- [ ] ✅ dist/ directory created
- [ ] ✅ JavaScript files in dist/
- [ ] ✅ Type declaration files (.d.ts) in dist/
- [ ] ✅ dist/index.js exists
- [ ] ✅ npm run shrinkwrap completed successfully
- [ ] ✅ npm-shrinkwrap.json created
- [ ] ✅ **Gate 6: Build PASSED** ✅

---

## Chapter 11: Phase 8 - Quality Gates Validation

### 11.1 Complete Module Validation

Run each gate command to verify everything passes:

```bash
# Gate 1: Validate API spec
npx swagger-cli validate api.yml

# Gate 2: Generate types
npm run clean && npm run generate

# Gate 3: Build implementation
npm run build

# Gate 4: Run unit tests
npm run test:unit

# Gate 5: Run integration tests
npm run test:integration

# Gate 6: Lock dependencies
npm run shrinkwrap

# If all commands succeed, all gates pass! ✅
```

### 11.2 Phase 8 Completion Checklist

**Module is complete when:**

- [ ] ✅ **Gate 1: API Specification** PASSED
- [ ] ✅ **Gate 2: Type Generation** PASSED
- [ ] ✅ **Gate 3: Implementation** PASSED
- [ ] ✅ **Gate 4: Unit Tests** PASSED
- [ ] ✅ **Gate 5: Integration Tests** PASSED
- [ ] ✅ **Gate 6: Build** PASSED
- [ ] ✅ All validation scripts pass
- [ ] ✅ Ready for commit and deployment

---

# Part 3: Advanced Topics

## Chapter 12: Adding Operations to Existing Modules

### 12.1 The Add Operation Workflow

Adding operations to existing modules follows a condensed 6-phase process (skip scaffolding):

**Phase 1: Research the Operation**
- Review API documentation
- Test with curl
- Document request/response

**Phase 2: Update API Specification**
- Add to api.yml
- Gate 1: Validate spec

**Phase 3: Generate Types**
- Run npm run clean && npm run generate
- Gate 2: Validate types

**Phase 4: Implement Operation**
- Add to Producer
- Update Mappers if needed
- Gate 3: Validate implementation

**Phase 5: Write Tests**
- Unit tests (mocked)
- Integration tests (real API)
- Gates 4 & 5: Validate tests

**Phase 6: Build & Finalize**
- Build and shrinkwrap
- Gate 6: Validate build

### 12.2 Add Operation Checklist

- [ ] ✅ Operation researched and tested with curl
- [ ] ✅ API specification updated (api.yml)
- [ ] ✅ Types regenerated
- [ ] ✅ Producer method implemented
- [ ] ✅ Mapper updated (if needed)
- [ ] ✅ Unit tests written (3+ cases)
- [ ] ✅ Integration tests written (2+ cases)
- [ ] ✅ Test data added to .env
- [ ] ✅ All 6 gates passed

---

## Chapter 13: Troubleshooting & Debugging

### 13.1 Common Build Failures

**TypeScript Compilation Errors:**
```bash
# Regenerate types
npm run clean && npm run generate
npm run build
```

**Module Not Found:**
```bash
# Install missing dependency
npm install <missing-package>
npm run build
```

**InlineResponse Types:**
```yaml
# Move inline schemas to components/schemas in api.yml
# Then regenerate
npm run clean && npm run generate
```

### 13.2 Common Test Failures

**Nock Not Matching:**
- Check path matches exactly
- Verify HTTP method matches
- Check headers if needed

**Integration Tests Skipping:**
- Verify .env file exists
- Check hasCredentials() returns true
- Ensure credentials are valid

**Invalid Credentials:**
- Test credentials with curl
- Regenerate API key if needed
- Update .env file

### 13.3 Debugging Techniques

1. Add debug logging to code
2. Run tests with LOG_LEVEL=debug
3. Inspect generated types
4. Test mapper in isolation
5. Compare API response to spec

---

## Chapter 14: Best Practices & Common Pitfalls

### 14.1 Best Practices

**API Specification:**
- Follow all 12 critical rules
- Use camelCase for properties
- Only 200/201 responses

**Implementation:**
- Separate concerns (Client/Producer/Mapper)
- Use generated types (no Promise<any>)
- Validate required fields in mappers

**Testing:**
- Write both unit and integration tests
- Test error cases
- Store test data in .env
- Use debug logging in integration tests

**Error Handling:**
- Use core error types
- Map HTTP status codes correctly

### 14.2 Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Using snake_case | Use camelCase for all properties |
| Including error responses | Remove all 4xx/5xx from spec |
| Using Promise<any> | Use generated types |
| Skipping type generation | Always run npm run clean && npm run generate |
| Not writing tests | Write both unit and integration |
| Hardcoding test values | Store in .env |
| Forgetting shrinkwrap | Always run npm run shrinkwrap |

---

# Part 4: Reference Materials

## Chapter 15: Quick Reference Guide

### 15.1 Common Commands

```bash
# Development Cycle
npm run clean && npm run generate  # After api.yml changes
npm run build                       # Compile TypeScript
npm run lint                        # Check code style
npm run test:unit                   # Run unit tests
npm run test:integration            # Run integration tests
npm run shrinkwrap                  # Lock dependencies
```

### 15.2 Pattern Quick Reference

**Client:**
```typescript
class ServiceClient {
  async connect(profile: ConnectionProfile): Promise<ConnectionState> {}
  async isConnected(): Promise<boolean> {}
  async disconnect(): Promise<void> {}
  get apiClient(): AxiosInstance {}
}
```

**Producer:**
```typescript
class ResourceProducer {
  constructor(private client: ServiceClient) {}
  async listResources(results: PagedResults<Resource>): Promise<void> {}
  async getResource(id: string): Promise<Resource> {}
}
```

**Mapper:**
```typescript
export function toResource(raw: any): Resource {
  ensureProperties(raw, ['id', 'name']);
  const output: Resource = {
    id: raw.id.toString(),
    name: raw.name,
    email: map(Email, raw.email),
    createdAt: map(DateTime, raw.created_at)
  };
  return output;
}
```

---

## Chapter 16: Validation Checklists

### 16.1 The 12 Critical API Rules

- [ ] **Rule 1**: No servers/security at root
- [ ] **Rule 2**: Singular nouns for tags/schemas
- [ ] **Rule 3**: All operations included
- [ ] **Rule 4**: Reusable parameters in components
- [ ] **Rule 5**: camelCase properties only
- [ ] **Rule 6**: orderBy/orderDir for sorting
- [ ] **Rule 7**: Descriptive path parameters
- [ ] **Rule 8**: id > name for identifiers
- [ ] **Rule 9**: Singular lowercase tags
- [ ] **Rule 10**: Correct operationId verbs
- [ ] **Rule 11**: pageTokenParam for pagination
- [ ] **Rule 12**: Only 200/201 responses

### 16.2 Pre-Commit Checklist

- [ ] All 6 quality gates pass
- [ ] All tests pass
- [ ] Build successful
- [ ] No ESLint errors
- [ ] Dependencies locked
- [ ] No hardcoded credentials
- [ ] .env file NOT committed

---

## Chapter 17: Code Templates

### 17.1 Complete File Templates

See the training manual for complete templates for:
- Client (ServiceClient.ts)
- Producer (ResourceProducer.ts)
- Mapper (Mappers.ts)
- Utilities (util.ts)
- Unit Test Common (test/unit/Common.ts)
- Integration Test Common (test/integration/Common.ts)

All templates include:
- Proper error handling
- Type safety
- Validation
- Best practices

---

## Conclusion

**Congratulations!** You now have complete knowledge of module development.

**What you've learned:**
- ✅ 8-phase module creation process
- ✅ All 6 quality gates
- ✅ API specification design (12 rules)
- ✅ Implementation patterns
- ✅ Testing strategies
- ✅ Troubleshooting techniques

**Your next steps:**
1. Create your first module from scratch
2. Add operations to existing modules
3. Build 2-3 modules for proficiency

**Remember:**
- API Spec is source of truth
- Client = Connection ONLY
- Producers = Operations ONLY
- Mappers = Transform ONLY
- All 6 gates must pass

**Good luck with your module development!** 🚀

---

**END OF TRAINING MANUAL**
