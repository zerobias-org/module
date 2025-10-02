# Persona Coordination and Conflict Resolution

## Overview

When multiple personas are involved in a decision, conflicts may arise. This document defines how personas coordinate and resolve disagreements.

## Persona Hierarchy

Personas don't have a strict hierarchy, but expertise domains determine who leads:

### Domain Leadership

| Domain | Primary Persona | Secondary Personas |
|--------|----------------|-------------------|
| API Design | API Architect | Product Specialist, Security Auditor |
| Implementation | TypeScript Expert | Integration Engineer |
| Security | Security Auditor | API Architect |
| Testing | Testing Specialist | Integration Engineer |
| Documentation | Documentation Writer | Product Specialist |
| Product Understanding | Product Specialist | API Architect |

## Conflict Resolution Process

### 1. Check Initial User Prompt
**First Priority**: What did the user explicitly request?
- User requirements override persona preferences
- If user specified a pattern, use it even if non-standard
- Document deviation from standards in code comments

### 2. Apply Critical Rules
**Second Priority**: Critical rules from `/claude/rules/`
- 🚨 Critical rules are non-negotiable
- If personas disagree on critical rule interpretation, fail fast and ask user
- Never violate critical rules to resolve conflicts

### 3. Evaluate Confidence Scores

When personas disagree:

```
API Architect: 85% confidence - Use clean REST paths
Product Specialist: 95% confidence - Users expect vendor format
Security Auditor: 70% confidence - No security concerns

Decision: Follow Product Specialist (highest confidence)
```

**Confidence Aggregation**:
- If all personas >90% confident: Use highest confidence
- If any persona <70% confident: Escalate to user
- Mixed confidence: Weight by domain relevance

### 4. Domain Expertise Wins

In domain-specific conflicts:
- Security issues: Security Auditor decides
- Type safety: TypeScript Expert decides
- API standards: API Architect decides
- User experience: Product Specialist decides

### 5. Escalation to User

Escalate when:
- Personas have conflicting high confidence (both >85%)
- Critical rule interpretation unclear
- User intent ambiguous
- Breaking changes required

**Escalation Format**:
```markdown
## Decision Required

**Context**: [Situation requiring decision]

**Conflict**:
- API Architect recommends: [Option A] (85% confidence)
  - Reason: [Why this is better]
- Product Specialist recommends: [Option B] (90% confidence)
  - Reason: [Why this is better]

**Impact**:
- Option A: [Consequences]
- Option B: [Consequences]

**Recommendation**: [Persona consensus if any]

**Question**: Which approach should we use?
```

## Common Conflict Scenarios

### Path Format Conflict
**Scenario**: API Architect wants `/resources/{id}` but Product Specialist says vendor uses `/org/{org}/resources`

**Resolution**:
1. Check user prompt - did they specify format preference?
2. Check existing patterns in module
3. Default to Product Specialist for vendor compatibility
4. Document deviation from REST standards

### Authentication Method Conflict
**Scenario**: Security Auditor prefers OAuth2, Integration Engineer sees API uses API key

**Resolution**:
1. Use what the actual API supports (Integration Engineer)
2. Security Auditor documents security considerations
3. Add security warnings to documentation if needed

### Schema Naming Conflict
**Scenario**: API Architect wants `UserInfo`, TypeScript Expert sees generated type is `User`

**Resolution**:
1. Generated types are source of truth (TypeScript Expert)
2. Never fight the generator
3. Map internal names if needed

### Testing Approach Conflict
**Scenario**: Testing Specialist wants mocks, Integration Engineer wants real API calls

**Resolution**:
1. Both - unit tests with mocks, integration tests with real API
2. No conflict, complementary approaches
3. Document which tests require credentials

## Coordination Patterns

### Sequential Handoff
```
1. Product Specialist: Discovers operations
2. API Architect: Designs specification
3. Security Auditor: Reviews and approves
4. TypeScript Expert: Implements types
5. Integration Engineer: Implements operations
6. Testing Specialist: Creates tests
7. Documentation Writer: Documents
```

### Parallel Collaboration
When working on same component:
- API Architect defines structure
- Security Auditor adds security requirements
- Both must approve before proceeding

### Review Gates
Certain decisions require multiple persona approval:
- Authentication: Security Auditor + Integration Engineer
- Breaking changes: API Architect + Product Specialist
- Public API: API Architect + Documentation Writer

## Confidence Score Guidelines

### High Confidence (90-100%)
- Direct documentation evidence
- Tested with actual API
- Clear rule or pattern match
- Previous successful implementation

### Medium Confidence (70-89%)
- Inferred from documentation
- Similar to known patterns
- Partial testing completed
- Some ambiguity exists

### Low Confidence (<70%)
- Guessing based on common patterns
- No documentation found
- Conflicting information
- Never encountered before

### Confidence Combination
When multiple personas involved:
- **Unanimous high**: Proceed automatically
- **Mixed high/medium**: Proceed with caution
- **Any low**: Escalate to user
- **Conflict**: Use resolution process

## Communication Patterns

### Persona Discussions
Personas don't have internal discussions. Instead:
1. Each evaluates independently
2. Presents recommendation with confidence
3. System applies resolution rules
4. Decision documented in code/comments

### Documentation of Decisions
When personas disagree but resolve:
```typescript
// API Architect preferred /users/{id}, but Product Specialist
// confirmed vendor uses /user/{user_id} (95% confidence)
// Following vendor format for compatibility
```

### Asking for Help
When escalating to user:
- Present all viewpoints neutrally
- Include confidence scores
- Explain trade-offs
- Recommend if consensus exists
- Accept user decision as final

## Principles for Resolution

1. **User Intent First**: User's goal overrides persona preferences
2. **Reality Over Ideal**: What works over what's theoretically better
3. **Compatibility Over Purity**: Vendor patterns over clean architecture
4. **Safety Over Convenience**: Security concerns override ease of use
5. **Explicit Over Implicit**: Clear user directive over assumed intent

## No-Conflict Zones

These decisions never conflict:
- Generated code is truth (never modify)
- Build must pass (non-negotiable)
- Tests must pass (non-negotiable)
- No credentials in code (absolute)
- Use core types (required)