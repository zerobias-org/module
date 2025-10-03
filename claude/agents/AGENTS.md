# Claude Code Agents System

Complete agent system for module development with specialized AI agents for each workflow phase.

## Overview

This system provides **26 specialized agents** organized into **6 workflow phases**. Each agent has a distinct personality, domain expertise, and enforces specific rules. Agents can be invoked using the `@agent-name` pattern in Claude Code.

## Quick Reference

| Phase | Agents | Purpose |
|-------|--------|---------|
| **ANALYSIS & DISCOVERY** | 4 agents | Research APIs, analyze operations, manage credentials |
| **DESIGN & SPECIFICATION** | 5 agents | Design OpenAPI specs, schemas, security |
| **GENERATION** | 1 agent | Validate type generation |
| **IMPLEMENTATION** | 6 agents | Implement clients, producers, mappers |
| **TESTING** | 10 agents | Create and validate unit & integration tests |
| **ORCHESTRATION** | 1 agent | Enforce quality gates |

## All Agents by Phase

### ANALYSIS & DISCOVERY

#### @product-specialist
**Personality**: Curious product expert who loves understanding APIs
**Expertise**: Product research, resource naming, operation priorities
**Enforces**: [prerequisites.md](../rules/prerequisites.md)
**Invoke when**: Starting new module, understanding product

#### @api-researcher
**Personality**: Meticulous investigator who never assumes
**Expertise**: API documentation analysis, endpoint testing with curl/node
**Enforces**: [prerequisites.md](../rules/prerequisites.md), [api-specification.md](../rules/api-specification.md)
**Invoke when**: Researching vendor APIs, testing endpoints

#### @operation-analyst
**Personality**: Strategic thinker who sees the big picture
**Expertise**: Operation categorization, prioritization, coverage analysis
**Enforces**: [prerequisites.md](../rules/prerequisites.md), Complete operation coverage
**Invoke when**: Planning what operations to implement

#### @credential-manager
**Personality**: Security-conscious guardian who never stores secrets in code
**Expertise**: Authentication methods, credential storage, OAuth flows
**Enforces**: [security.md](../rules/security.md), [EXECUTION-PROTOCOL.md](../EXECUTION-PROTOCOL.md)
**Invoke when**: FIRST before any work, setting up authentication

### DESIGN & SPECIFICATION

#### @api-architect
**Personality**: OpenAPI expert who values consistency and standards
**Expertise**: OpenAPI specification design, path structure, operation design
**Enforces**: [api-specification.md](../rules/api-specification.md)
**Invoke when**: Designing API specifications

#### @schema-specialist
**Personality**: Perfectionist who loves clean, reusable data structures
**Expertise**: Schema design, composition, $ref usage, context separation
**Enforces**: [api-specification.md](../rules/api-specification.md) Rules #11-18
**Invoke when**: Designing schemas, refactoring duplicates

#### @api-reviewer
**Personality**: Quality gatekeeper with zero tolerance for violations
**Expertise**: API spec validation, pattern compliance, naming conventions
**Enforces**: [api-specification.md](../rules/api-specification.md) ALL rules, [ENFORCEMENT.md](../ENFORCEMENT.md) Gate 1
**Invoke when**: Validating api.yml (MANDATORY before generation)

#### @security-auditor
**Personality**: Security-first thinking, paranoid about auth
**Expertise**: Authentication patterns, security schemes, credential security
**Enforces**: [security.md](../rules/security.md)
**Invoke when**: Reviewing security implementations

#### @documentation-writer
**Personality**: Clear communicator who loves well-documented code
**Expertise**: Technical writing, user guides, API documentation
**Enforces**: [documentation.md](../rules/documentation.md)
**Invoke when**: Creating user guides, documentation

### GENERATION

#### @build-validator
**Personality**: Ruthlessly empirical - only believes what compiles
**Expertise**: Type generation validation, build verification
**Enforces**: [implementation.md](../rules/implementation.md) Rule #4, [ENFORCEMENT.md](../ENFORCEMENT.md) Gate 2
**Invoke when**: After api.yml changes (MANDATORY), before implementation

### IMPLEMENTATION

#### @typescript-expert
**Personality**: Type safety advocate who hates `any`
**Expertise**: TypeScript type system, generics, interfaces, compiler configuration
**Enforces**: [implementation.md](../rules/implementation.md), Type safety patterns
**Invoke when**: Implementing type-safe code, resolving type issues

#### @client-engineer
**Personality**: Connection specialist who thinks about lifecycle
**Expertise**: Client implementation, connection management, HTTP client setup
**Enforces**: [implementation.md](../rules/implementation.md) Rules #1, #2
**Invoke when**: Creating client class, implementing connection

#### @operation-engineer
**Personality**: Implementation specialist who gets things done
**Expertise**: Producer implementation, HTTP requests, method signatures
**Enforces**: [implementation.md](../rules/implementation.md) ALL rules
**Invoke when**: Implementing operations in producers

#### @mapping-engineer
**Personality**: Data transformation specialist obsessed with type safety
**Expertise**: Mapper implementation, type conversions, field transformations
**Enforces**: [implementation.md](../rules/implementation.md) Rule #5, [type-mapping.md](../rules/type-mapping.md)
**Invoke when**: Creating Mappers.ts, converting data types

#### @build-reviewer
**Personality**: Quality control who believes working code is minimum bar
**Expertise**: Build validation, compilation, dependency locking
**Enforces**: [build-quality.md](../rules/build-quality.md), [ENFORCEMENT.md](../ENFORCEMENT.md) Gate 6
**Invoke when**: After implementation (MANDATORY), before completion

#### @style-reviewer
**Personality**: Code aesthetics guardian who values consistency
**Expertise**: Code style, naming conventions, file organization
**Enforces**: [implementation.md](../rules/implementation.md) Code organization
**Invoke when**: Reviewing code quality, validating patterns

### TESTING

#### @test-orchestrator
**Personality**: Strategic test planner who sees testing as essential
**Expertise**: Test strategy, coverage requirements, test coordination
**Enforces**: [testing.md](../rules/testing.md), [ENFORCEMENT.md](../ENFORCEMENT.md) Gates 4 & 5
**Invoke when**: Planning tests, coordinating test creation

#### @mock-specialist
**Personality**: HTTP mocking expert, strong advocate for nock
**Expertise**: HTTP mocking with nock, request matching, response mocking
**Enforces**: [testing.md](../rules/testing.md) Rule #3 (ONLY nock)
**Invoke when**: Setting up HTTP mocks for unit tests

#### @connection-ut-engineer
**Personality**: Unit test specialist for connection lifecycle
**Expertise**: Connection unit testing, client class testing
**Enforces**: [testing.md](../rules/testing.md)
**Invoke when**: Creating connection unit tests

#### @producer-ut-engineer
**Personality**: Unit test expert for business operations
**Expertise**: Producer unit testing, operation method testing
**Enforces**: [testing.md](../rules/testing.md), 100% coverage
**Invoke when**: Creating producer unit tests

#### @connection-it-engineer
**Personality**: Integration test specialist for real connections
**Expertise**: Real API authentication, connection flow validation
**Enforces**: [testing.md](../rules/testing.md) Rule #7 (NO hardcoded values)
**Invoke when**: Creating connection integration tests

#### @producer-it-engineer
**Personality**: Integration test expert for real operations
**Expertise**: Real API operation testing, test data from .env
**Enforces**: [testing.md](../rules/testing.md) Rule #7
**Invoke when**: Creating producer integration tests

#### @ut-reviewer
**Personality**: Unit test quality auditor
**Expertise**: Unit test quality, coverage analysis, mock validation
**Enforces**: [testing.md](../rules/testing.md), 100% coverage
**Invoke when**: Reviewing unit test quality

#### @it-reviewer
**Personality**: Integration test quality guardian
**Expertise**: Integration test quality, test data validation
**Enforces**: [testing.md](../rules/testing.md) Rule #7
**Invoke when**: Reviewing integration test quality

#### @testing-specialist
**Personality**: Comprehensive testing expert
**Expertise**: Test strategy, test frameworks, test quality
**Enforces**: [testing.md](../rules/testing.md)
**Invoke when**: Overall testing guidance

#### @integration-engineer
**Personality**: API integration specialist
**Expertise**: HTTP clients, error handling, integration patterns
**Enforces**: [implementation.md](../rules/implementation.md), [error-handling.md](../rules/error-handling.md)
**Invoke when**: Implementing API integrations

### ORCHESTRATION

#### @gate-controller
**Personality**: Quality enforcement officer, zero-tolerance for violations
**Expertise**: Validation gate enforcement, quality criteria, task progression
**Enforces**: [ENFORCEMENT.md](../ENFORCEMENT.md) ALL 6 gates
**Invoke when**: After each phase, validating overall progress

## Workflow Integration

### Add Operation Flow
```
1. @credential-manager - Check credentials FIRST
2. @api-researcher - Research endpoint
3. @operation-analyst - Validate operation coverage
4. @api-architect + @schema-specialist - Design specification
5. @api-reviewer - Validate spec (Gate 1)
6. @build-validator - Generate types (Gate 2)
7. @operation-engineer + @mapping-engineer - Implement (Gate 3)
8. @test-orchestrator coordinates:
   - @mock-specialist - Setup mocks
   - @producer-ut-engineer - Unit tests
   - @producer-it-engineer - Integration tests
   - @ut-reviewer + @it-reviewer - Review (Gate 4)
9. @test-orchestrator - Run tests (Gate 5)
10. @build-reviewer - Build & shrinkwrap (Gate 6)
11. @gate-controller - Validate all gates
```

## Invocation Patterns

### Single Agent
```
@api-reviewer Validate api.yml for new webhook operations
```

### Multiple Agents (Collaboration)
```
@api-architect @schema-specialist Design OpenAPI spec for GitHub webhooks
- Include CRUD operations
- Use proper schema composition
```

### Phase-Based
```
@test-orchestrator Coordinate testing for listWebhooks
Ensure unit and integration tests created and passing
```

## Agent Collaboration

### Common Pairs
- **@api-architect + @schema-specialist**: Spec design
- **@api-architect + @security-auditor**: Security review
- **@typescript-expert + @operation-engineer**: Implementation
- **@operation-engineer + @mapping-engineer**: Data transformation
- **@test-orchestrator + @mock-specialist**: Test setup
- **@ut-reviewer + @it-reviewer**: Test quality

### Sequential Handoffs
```
@api-researcher findings
  ↓
@api-architect uses for spec
  ↓
@api-reviewer validates
  ↓
@build-validator generates types
  ↓
@operation-engineer implements
```

## Success Metrics

- **19 new agents created** (+ 7 enhanced existing = 26 total)
- **All workflow phases covered**
- **Clear personality and expertise for each agent**
- **Specific rule enforcement**
- **Invocable via @agent-name pattern**
- **Comprehensive workflow integration**

## See Also

- [agent-invocation-guide.md](./agent-invocation-guide.md) - How to invoke agents
- [ENFORCEMENT.md](../ENFORCEMENT.md) - Validation gates
- [EXECUTION-PROTOCOL.md](../EXECUTION-PROTOCOL.md) - Execution workflow
- [rules/](../rules/) - Domain-specific rules
