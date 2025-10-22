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

