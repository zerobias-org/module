# Agent-Rule Mapping

Complete mapping of which agents need which rules.

## New Rule Files Created (Phase 3)

1. `failure-conditions.md` - Immediate failure conditions
2. `validation-script.md` - Complete validation script for all gates
3. `production-readiness.md` - Release readiness checklist
4. `git-workflow.md` - Git workflow and commit standards
5. `tool-requirements.md` - All required tools and commands
6. `api-specification-critical-12-rules.md` - The 12 critical API spec rules
7. `mapper-field-validation.md` - Complete field validation workflow

## Agent-to-Rules Matrix

### Core Specification Agents

#### api-architect
**Current rules:** api-spec-core-rules.md, api-spec-operations.md, api-spec-schemas.md, api-spec-standards.md, connection-profile-design.md

**Add:**
- `api-specification-critical-12-rules.md` - Must know the 12 critical rules
- `gate-1-api-spec.md` - Gate 1 validation
- `failure-conditions.md` - Know immediate failures (Rules 1, 2, 12)

#### api-researcher
**Current rules:** None visible

**Add:**
- `tool-requirements.md` - API testing tools (curl, node scripts)

#### api-reviewer
**Current rules:** None visible

**Add:**
- `api-specification-critical-12-rules.md` - Review against 12 rules
- `gate-1-api-spec.md` - Validation checklist

#### schema-specialist
**Current rules:** None visible

**Add:**
- `api-spec-schemas.md` - Schema design rules
- `api-specification-critical-12-rules.md` - Rules 5, 8, 11 (schema-related)

### Implementation Agents

#### operation-engineer
**Current rules:** implementation.md

**Add:**
- `mapper-field-validation.md` - Critical for ensuring complete field mapping
- `gate-3-implementation.md` - Implementation validation
- `failure-conditions.md` - Know failures (Rule 8: Promise<any>)

#### mapping-engineer
**Current rules:** None visible

**Add:**
- `mapper-field-validation.md` - CRITICAL - core responsibility
- `type-mapping.md` - Type conversion rules
- `implementation.md` - Mapper pattern section

#### integration-engineer
**Current rules:** None visible

**Add:**
- `implementation.md` - Implementation patterns
- `error-handling.md` - Error handling patterns
- `gate-3-implementation.md` - Implementation validation

#### typescript-expert
**Current rules:** None visible

**Add:**
- `implementation.md` - All TypeScript patterns
- `type-mapping.md` - Core type usage
- `gate-2-type-generation.md` - Type generation validation

#### client-engineer
**Current rules:** None visible

**Add:**
- `implementation.md` - Client class patterns
- `error-handling.md` - Error handling utilities
- `connection-profile-design.md` - Connection handling

### Testing Agents

#### testing-specialist
**Current rules:** None visible

**Add:**
- `testing.md` - CRITICAL - all testing rules
- `gate-4-test-creation.md` - Test creation validation
- `gate-5-test-execution.md` - Test execution validation
- `failure-conditions.md` - Know failures (Rules 4, 5, 11: tests)

#### producer-ut-engineer
**Current rules:** None visible

**Add:**
- `testing.md` - Unit testing section
- `gate-4-test-creation.md` - Test creation validation

#### producer-it-engineer
**Current rules:** None visible

**Add:**
- `testing.md` - Integration testing section
- `gate-4-test-creation.md` - Test creation validation
- `gate-5-test-execution.md` - Test execution validation

#### connection-ut-engineer
**Current rules:** None visible

**Add:**
- `testing.md` - Connection testing patterns
- `gate-4-test-creation.md` - Test creation validation

#### connection-it-engineer
**Current rules:** None visible

**Add:**
- `testing.md` - Integration testing section
- `gate-5-test-execution.md` - Test execution validation

#### mock-specialist
**Current rules:** None visible

**Add:**
- `testing.md` - Mock strategies (nock only)

#### test-orchestrator
**Current rules:** None visible

**Add:**
- `testing.md` - Test strategy
- `gate-4-test-creation.md` - Test creation validation
- `gate-5-test-execution.md` - Test execution validation

### Build and Quality Agents

#### build-reviewer
**Current rules:** None visible

**Add:**
- `gate-6-build.md` - CRITICAL - core responsibility
- `build-quality.md` - Build gates
- `tool-requirements.md` - Build commands
- `failure-conditions.md` - Know failures (Rule 6, 7: build)

#### build-validator
**Current rules:** None visible

**Add:**
- `gate-6-build.md` - Build validation
- `tool-requirements.md` - Validation commands

#### gate-controller
**Current rules:** None visible

**Add:**
- `gate-1-api-spec.md` through `gate-6-build.md` - ALL gates
- `failure-conditions.md` - All immediate failures
- `validation-script.md` - Complete validation script

#### style-reviewer
**Current rules:** None visible

**Add:**
- `implementation.md` - Code style guidelines

#### ut-reviewer
**Current rules:** None visible

**Add:**
- `testing.md` - Unit test review criteria
- `gate-4-test-creation.md` - Test validation

#### it-reviewer
**Current rules:** None visible

**Add:**
- `testing.md` - Integration test review criteria
- `gate-5-test-execution.md` - Test execution validation

#### test-structure-validator
**Current rules:** None visible

**Add:**
- `testing.md` - Test structure requirements
- `gate-4-test-creation.md` - Test validation

### Orchestration and Management Agents

#### module-scaffolder
**Current rules:** None visible

**Add:**
- `tool-requirements.md` - Yeoman and scaffolding tools
- `prerequisites.md` - Prerequisites before scaffolding

#### product-specialist
**Current rules:** None visible

**Add:**
- `prerequisites.md` - Prerequisites for discovery
- `tool-requirements.md` - NPM and discovery commands

#### operation-analyst
**Current rules:** None visible

**Add:**
- `api-spec-operations.md` - Operation patterns

#### credential-manager
**Current rules:** None visible

**Add:**
- `execution-protocol.md` - Credential discovery patterns (Step 1.5)
- `security.md` - Security patterns

#### security-auditor
**Current rules:** None visible

**Add:**
- `security.md` - Security patterns
- `connection-profile-design.md` - Secure credential handling

#### documentation-writer
**Current rules:** None visible

**Add:**
- `documentation.md` - Documentation standards

#### connection-profile-guardian
**Current rules:** None visible

**Add:**
- `connection-profile-design.md` - CRITICAL - core responsibility
- `security.md` - Secure credential handling

## Priority Updates

### CRITICAL (Update First)
1. **gate-controller** - Add all gate files + validation-script.md
2. **build-reviewer** - Add gate-6 + tool-requirements.md
3. **testing-specialist** - Add testing.md + gates 4-5
4. **operation-engineer** - Add mapper-field-validation.md
5. **mapping-engineer** - Add mapper-field-validation.md
6. **api-architect** - Add api-specification-critical-12-rules.md

### HIGH PRIORITY
7. **test-orchestrator** - Add testing rules
8. **typescript-expert** - Add implementation rules
9. **integration-engineer** - Add implementation + error handling
10. **build-validator** - Add gate-6 + tool-requirements.md

### MEDIUM PRIORITY
11-20. Testing-related agents (ut/it engineers, reviewers)
21-25. Schema and API agents
26-30. Scaffolding and orchestration agents

## Update Pattern

For each agent, add rules in this format:

```markdown
## Rules to Load

**Critical Rules:**
- @.claude/rules/rule-name.md - Description
- @.claude/rules/another-rule.md - Description

**Supporting Rules:**
- @.claude/rules/optional-rule.md - Description
```

## Validation

After updates:
- [ ] All agents have appropriate rules
- [ ] No agent has conflicting rules
- [ ] All new rule files referenced by at least one agent
- [ ] Gate-controller has all gate files
- [ ] Critical agents updated first
