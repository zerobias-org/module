# Context Management Strategy

## Overview

Effective context management prevents hallucinations, maintains focus, and ensures quality output. This document defines how to optimize context usage throughout the workflow.

## Context Window Monitoring

### Usage Thresholds

```
0-50%   : Green zone - Full context available
50%     : First warning - Consider structure
60%     : Second warning - Plan for split
70%     : Third warning - Strong recommendation
>70%    : Red zone - Warning on every operation
```

### Warning Format

```
⚠️ Context Usage: 60% (36,000 / 60,000 tokens)
Current task: Implementing UserProducer
Recommendation: Consider splitting after API spec generation

Continue? [y/n/split]:
```

### Detailed Usage (on request)

```
📊 Context Breakdown:
- Current task instructions: 2,500 tokens (4%)
- API specification: 8,000 tokens (13%)
- Generated interfaces: 5,000 tokens (8%)
- Previous task outputs: 3,500 tokens (6%)
- File contents: 15,000 tokens (25%)
- Conversation history: 2,000 tokens (3%)

Suggestions for optimization:
- Clear conversation history
- Summarize completed tasks
- Drop non-essential file contents
```

## Context Priority System

### Essential Context (Never Drop)

```yaml
Priority 1 - Critical:
  - Current task objective
  - Active errors and stack traces
  - Test failure details
  - User's original request

Priority 2 - Core Working Data:
  - API specification (current section)
  - Generated interfaces (relevant ones)
  - File being edited
  - Connection profile
```

### Optional Context (Can Drop)

```yaml
Priority 3 - Helpful:
  - Previous task outputs (summarized)
  - Similar code patterns
  - Related documentation
  - Historical decisions

Priority 4 - Nice to Have:
  - Full file contents (can re-read)
  - Complete conversation history
  - Detailed reasoning logs
  - Example implementations
```

## Task Splitting Strategy

### When to Suggest Split

```javascript
function shouldSuggestSplit(context) {
  // Hard threshold
  if (context.usage > 0.7) {
    return {
      suggest: true,
      reason: 'Approaching context limit',
      confidence: 'high'
    };
  }

  // Complex task threshold
  if (context.usage > 0.5 && taskComplexity > 'medium') {
    return {
      suggest: true,
      reason: 'Complex task with growing context',
      confidence: 'medium'
    };
  }

  // Multiple operations
  if (context.usage > 0.6 && remainingOps > 3) {
    return {
      suggest: true,
      reason: 'Multiple operations remaining',
      confidence: 'high'
    };
  }

  return { suggest: false };
}
```

### Natural Split Points

```yaml
Good split points:
  - After API specification complete
  - After code generation
  - Before starting implementation
  - After each operation added
  - Before test implementation
  - After fixing each major issue

Poor split points:
  - Middle of implementation
  - During error resolution
  - While updating schemas
  - During test execution
```

## Context Optimization Techniques

### 1. Summarization

```javascript
// Instead of full task output
const fullOutput = {
  task: 'API Analysis',
  operations: [/* 50 operations */],
  schemas: [/* 30 schemas */],
  details: '... 5000 tokens ...'
};

// Use summary
const summary = {
  task: 'API Analysis',
  operationCount: 50,
  schemaCount: 30,
  keyOperations: ['listUsers', 'getUser'],
  status: 'completed'
};
```

### 2. Selective Loading

```javascript
// Load only needed sections
function loadApiSpec(operation) {
  return {
    paths: {
      [operation.path]: spec.paths[operation.path]
    },
    components: {
      schemas: getReferencedSchemas(operation)
    }
  };
}
```

### 3. Incremental Updates

```javascript
// Don't reload entire file
const currentContent = await readFile(file);

// Just track changes
const changes = {
  added: ['function newMethod() {...}'],
  modified: [{ line: 42, content: 'updated' }],
  deleted: []
};
```

### 4. Reference Compression

```javascript
// Instead of repeating
const user1 = { id: 'uuid', name: 'John', email: 'john@example.com', ... };
const user2 = { id: 'uuid', name: 'Jane', email: 'jane@example.com', ... };

// Use references
const userSchema = { type: 'object', properties: {...} };
const users = [
  { $ref: '#userSchema', id: '1' },
  { $ref: '#userSchema', id: '2' }
];
```

## Memory File Strategy

### What to Store

```yaml
Always store:
  - Task completion status
  - Critical decisions made
  - Generated artifacts paths
  - Test results summary

Sometimes store:
  - Detailed reasoning (if complex)
  - API responses (for fixtures)
  - Error patterns encountered

Never store:
  - Full file contents
  - Redundant information
  - Temporary calculations
  - Debug output
```

### Storage Format

```json
{
  "task": "05-scaffold-module",
  "status": "completed",
  "summary": {
    "modulePath": "/package/{vendor}/{service}",
    "packageName": "@zerobias-org/module-{vendor}-{service}",
    "dependencies": ["axios", "@auditmation/types-core-js"]
  },
  "criticalInfo": {
    "httpClient": "axios",
    "authMethod": "token"
  },
  "nextTask": {
    "requires": ["modulePath", "httpClient", "authMethod"]
  }
}
```

## Zero Hallucination Strategy

### Prevention Techniques

```javascript
function preventHallucination(action) {
  const checks = {
    // Always verify against source
    verifyAgainstDocs: async () => {
      const docs = await fetchDocumentation();
      return validateAgainst(action, docs);
    },

    // Never generate without testing
    testBeforeCommit: async () => {
      const result = await testCode(action.code);
      return result.success;
    },

    // Flag all assumptions
    flagAssumptions: () => {
      return action.assumptions.map(a => ({
        assumption: a,
        confidence: calculateConfidence(a),
        needsVerification: true
      }));
    },

    // Maintain confidence score
    checkConfidence: () => {
      return action.confidence >= 70;
    }
  };

  return Promise.all(Object.values(checks));
}
```

### Verification Points

```yaml
Must verify:
  - Operation names from API docs
  - Parameter types from spec
  - Required fields from responses
  - Authentication methods from testing
  - Error codes from actual API

Never guess:
  - Resource names
  - Property names
  - Type definitions
  - Authentication flows
  - Error handling
```

## Context Recovery

### After Context Clear

```javascript
async function recoverContext() {
  // Reload essential information
  const context = {
    // From memory files
    taskStatus: await loadTaskStatus(),
    lastOutput: await loadLastOutput(),

    // From current state
    currentModule: await findModule(),
    gitStatus: await getGitStatus(),

    // From original request
    userIntent: await loadOriginalRequest()
  };

  return context;
}
```

### Checkpoint System

```yaml
Checkpoints:
  - Before: Major implementation
  - After: Successful test run
  - Before: API spec changes
  - After: Code generation
  - Before: Complex refactoring
```

## Best Practices

### Do's
- Monitor context usage proactively
- Summarize completed work
- Store only essential information
- Use references over repetition
- Clear context at natural boundaries
- Verify instead of assuming

### Don'ts
- Don't store full file contents in memory
- Don't repeat information across tasks
- Don't keep outdated context
- Don't guess when context is unclear
- Don't continue with high usage warnings
- Don't ignore split recommendations

## Context Budget per Task

### Recommended Allocations

```yaml
Create Module:
  - Product discovery: 10%
  - API design: 30%
  - Implementation: 40%
  - Testing: 15%
  - Documentation: 5%

Add Operation:
  - Analysis: 15%
  - API update: 25%
  - Implementation: 35%
  - Testing: 20%
  - Validation: 5%

Fix Issue:
  - Diagnosis: 30%
  - Solution planning: 20%
  - Implementation: 30%
  - Verification: 20%
```