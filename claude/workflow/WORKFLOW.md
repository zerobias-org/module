# Unified Module Workflow

## Overview

This is the single, monolithic workflow that handles ALL module-related requests. It analyzes user intent, determines the appropriate action, and executes it with full quality enforcement.

## Workflow Entry Point

ALL requests start here. The workflow will:
1. Analyze the user's request
2. Determine the module and action needed
3. Execute the appropriate flow
4. Maintain quality throughout
5. Handle failures gracefully

## Primary Decision Tree

```
User Request
    ↓
[Analyze Intent]
    ↓
Module Exists? ──No──→ [Create Minimal Module] → [Add Requested Operations]
    ↓ Yes
What Action?
    ├─→ "add operation" → [Add Single Operation]
    ├─→ "fix issue" → [Fix Problem]
    ├─→ "update deps" → [Update Dependencies]
    └─→ "other" → [Ask for Clarification]
```

## Core Principles

### 1. Single Flow Philosophy
- ONE workflow handles everything
- Smart routing based on discovery
- No confusion about which flow to use
- Adapts to context automatically

### 2. Quality Gates
- Build must pass at checkpoints
- Tests must pass throughout
- Lint compliance required
- Git commits at subtask completion

### 3. Incremental Implementation
- Modules created with minimal operations first
- Operations added one at a time
- Each addition fully tested
- Dependencies determined automatically

### 4. Zero Hallucination
- Always verify against documentation
- Test code immediately after generation
- Flag assumptions for verification
- Maintain confidence scores
- Never guess names or patterns

## Execution Phases

### Phase 0: MANDATORY Rule Loading
**🚨 CRITICAL - NEVER SKIP THIS PHASE**
- **ALWAYS** read relevant rules BEFORE starting work
- **RE-READ** rules after context cleaning/compaction
- **RE-READ** rules when switching between topics
- Store critical rules in working memory
- Track rule compliance with TodoWrite

**Required Reading by Topic**:
```bash
# API Work → Read:
claude/rules/api-specification-critical.md
claude/rules/api-specification.md

# Implementation → Read:
claude/rules/implementation.md
claude/rules/error-handling.md
claude/rules/type-mapping.md

# Testing → Read:
claude/rules/testing.md

# Always → Read:
claude/rules/prerequisites.md
```

### Phase 1: Request Analysis
- Parse user intent
- Identify target module
- Determine requested operations
- Check existing state

### Phase 2: Module Discovery
- Run `npx lerna list --json`
- Find matching module
- Check module state if exists
- Plan execution path

### Phase 3: Action Execution
Based on discovery:
- **No module**: Create minimal, then add ops
- **Module exists + add op**: Add operation flow
- **Module exists + fix**: Fix flow
- **Module exists + other**: Appropriate flow

### Phase 4: Quality Validation
- Run build validation
- Execute tests
- Check lint compliance
- Verify no regressions

### Phase 5: Completion
- Create git commits
- Update documentation if needed
- Report success/failure
- Clean up temporary files

## Context Management

### Warning Thresholds
- 50% - First warning
- 60% - Second warning
- 70% - Strong recommendation to split
- >70% - Warning on every check

### Context Priority
**Essential (always keep)**:
- Current task objective
- Active errors/failures
- Test results
- API specification

**Optional (drop when needed)**:
- Historical decisions
- Previous task outputs
- Full file contents (can re-read)
- Similar patterns

## Persona Activation

Different phases activate different personas:

### Request Analysis
- **Product Specialist**: Understands domain

### Module Creation
- **Product Specialist**: Defines resources
- **API Architect + Security Auditor**: Design API
- **TypeScript Expert + Integration Engineer**: Implement
- **Testing Specialist**: Create tests
- **Documentation Writer**: Create guides

### Operation Addition
- **API Architect**: Update spec
- **TypeScript Expert + Integration Engineer**: Implement
- **Testing Specialist**: Test coverage

### Problem Fixing
- **Relevant specialist**: Based on issue type

## Failure Handling

### Immediate Stop Conditions
- Build failure
- Test regression
- Security violation
- Git conflicts

### Recovery Strategies
1. Try standard solution
2. If confidence <70%, check docs
3. If still <70%, ask user
4. Maximum 3 attempts before user input

### Rollback Capability
- Git checkpoints at each task
- Can rollback to last good state
- Preserve work in memory
- Document failure reason

## Task Coordination

### Sequential Tasks
Execute in order, each depends on previous:
```
Create Module → Add Connect → Add First Op → Test
```

### Parallel Opportunities
When independent, can parallelize:
```
Add Op1 ←→ Add Op2 ←→ Add Op3
(After API spec updated)
```

### Subtask Notation
- Main tasks: 1, 2, 3...
- Subtasks: 1.1, 1.2, 1.3...
- Sub-subtasks: 1.1.1, 1.1.2...

## Memory Management

### Storage Location
```
.claude/.localmemory/{action}-{module}/
├── _work/                    # Working directory
│   ├── .env                  # Test credentials
│   ├── product-model.md      # Product understanding
│   └── reasoning/            # Decision logs
├── task-01-output.json       # Task outputs
├── task-02-output.json
└── ...
```

### Information Flow
- Each task reads previous outputs
- Stores its results
- Next task continues from there
- Final cleanup optional

## Success Metrics

### Module Creation Success
- Connects to service ✓
- One operation works ✓
- Tests pass ✓
- Documentation complete ✓

### Operation Addition Success
- Operation implemented ✓
- Tests pass ✓
- No regressions ✓
- Build succeeds ✓

### Fix Success
- Issue resolved ✓
- Tests pass ✓
- No new issues ✓

## Related Documents

- [Decision Tree](./decision-tree.md) - Detailed flow logic
- [Context Management](./context-management.md) - Optimization strategies
- [Tasks](./tasks/) - Individual task definitions
- [Rules](../rules/) - Domain-specific rules
- [Personas](../personas/) - Expert definitions