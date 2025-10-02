# Task: Analyze Request

## Overview

This is the entry point for ALL module-related requests. It analyzes user intent, identifies the target module, and determines the appropriate action path.

## Responsible Personas
- **Product Specialist** (primary)
- **All personas** (consultation as needed)

## Input
- User request/prompt (natural language)
- Current repository state

## Process Steps

### 0. Context Management and Goal Reminder
**MANDATORY FIRST STEP - CONTEXT CLEARING**:
- IGNORE all previous conversation context
- CLEAR mental context
- REQUEST: User should run `/clear` or `/compact` command

**MANDATORY SECOND STEP**: Capture user intent:
1. Store original user request in `initial-request.json`
2. Parse for action, module, operations
3. This becomes the north star for all subsequent tasks

### 1. Parse User Intent

```javascript
// Extract key information from request
const intent = {
  action: detectAction(request),      // create|add|fix|update
  module: extractModuleName(request), // e.g., 'github', 'aws-iam'
  operations: extractOperations(request),
  credentials: detectCredentials(request),
  specialRequirements: extractRequirements(request)
};
```

### 2. Module Discovery

```bash
# List all existing modules
npx lerna list --json
```

Match request to module:
- Exact match: `github` → `@zerobias-org/module-github-github`
- Vendor match: `aws iam` → `@zerobias-org/module-amazon-aws-iam`
- Fuzzy match: Ask user to confirm

### 3. Determine Action Type

**Create Module** indicators:
- "create module for..."
- "new module..."
- Module doesn't exist
- "implement GitHub API module"

**Add Operation** indicators:
- "add ability to..."
- "implement listWebhooks"
- "add webhook operations"
- Module exists

**Fix Issue** indicators:
- "fix the build"
- "tests are failing"
- "error when..."
- "not working"

**Update Dependencies** indicators:
- "update dependencies"
- "upgrade packages"
- "security updates"

### 4. Store Initial Context

Save to `.claude/.localmemory/{action}-{module}/initial-request.json`:

```json
{
  "timestamp": "2024-01-01T10:00:00Z",
  "originalRequest": "Full user request text",
  "analysis": {
    "action": "add",
    "module": "github-github",
    "operations": ["listWebhooks", "createWebhook"],
    "confidence": 95
  },
  "decisions": {
    "modulePath": "package/github/github",
    "workDir": ".claude/.localmemory/add-github-github/_work"
  }
}
```

### 5. Validate Prerequisites

For existing modules:
```bash
cd package/{vendor}/{module}
git status --porcelain  # Must be clean
npm run build          # Must pass
npm run test           # Must pass
```

## Decision Points

### Multiple Module Matches
```
Found multiple possible modules:
1. @zerobias-org/module-github-github
2. @zerobias-org/module-github-github-issues

Which module do you want to work with? [1/2]:
```

### Unclear Intent
```
I need clarification on your request.

Are you trying to:
1. Create a new module
2. Add operations to existing module
3. Fix an issue
4. Something else

Please choose [1/2/3/4]:
```

### Missing Information
```
To add operations, I need to know which operations.

The GitHub API has many endpoints. Which specific operations do you need?
- User management (listUsers, getUser, etc.)
- Repository operations (listRepos, createRepo, etc.)
- Webhook operations (listWebhooks, createWebhook, etc.)
- Other (please specify)
```

## Output Format

```json
{
  "status": "analyzed",
  "confidence": 85,
  "action": "add-operation",
  "module": {
    "name": "@zerobias-org/module-github-github",
    "path": "package/github/github",
    "exists": true,
    "state": "valid"
  },
  "plan": {
    "operations": ["listWebhooks", "createWebhook", "deleteWebhook"],
    "order": ["listWebhooks", "createWebhook", "deleteWebhook"],
    "dependencies": {
      "deleteWebhook": ["listWebhooks"]
    }
  },
  "nextTask": "add-operation"
}
```

## Success Criteria
- User intent correctly identified
- Module accurately discovered/planned
- Action type determined
- Prerequisites validated
- Context properly stored

## Error Conditions
- Ambiguous request → Ask for clarification
- Module not found → Suggest alternatives
- Dirty git state → Request cleanup
- Build/test failures → Switch to fix flow

## Confidence Scoring
- Exact module match: +30%
- Clear action words: +30%
- Specific operations named: +20%
- Credentials provided: +10%
- Similar past requests: +10%

## Context Usage
- Light: ~5% of context
- Mostly analyzing text
- No file loading yet
- Quick decision making