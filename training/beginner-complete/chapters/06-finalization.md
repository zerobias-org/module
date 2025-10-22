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

