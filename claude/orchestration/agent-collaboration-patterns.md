# Agent Collaboration Patterns

Common patterns for how agents work together to accomplish tasks.

## Collaboration Types

### 1. Sequential Handoff
One agent completes → passes output → next agent starts

### 2. Parallel Collaboration
Multiple agents work simultaneously on independent tasks

### 3. Review Partnership
One agent creates → another reviews → first revises if needed

### 4. Coordinated Team
One agent orchestrates → multiple agents execute → orchestrator validates

### 5. Escalation Chain
Agent identifies issue → escalates to specialist → specialist resolves

## Common Agent Pairs

### Design Duo: @api-architect + @schema-specialist

**Pattern:** Collaborative Design
```
@api-architect designs paths and operations
@schema-specialist designs schemas and composition
Both work together on api.yml

Workflow:
1. @api-architect defines operations and paths
2. @schema-specialist designs schemas
3. @api-architect integrates schemas into operations
4. Both review complete spec

Coordination:
- Shared file: api.yml
- Sequential sections: paths (architect) → schemas (specialist)
- Joint review before @api-reviewer validation
```

**Use When:** Designing API specification with complex schemas

---

### Security Review: @api-architect + @security-auditor

**Pattern:** Create → Review → Approve
```
@api-architect designs authentication approach
@security-auditor reviews for security implications
@api-architect adjusts based on feedback

Workflow:
1. @api-architect proposes authentication design
2. @security-auditor reviews:
   - Credential storage approach
   - Token expiration handling
   - Security scheme configuration
3. @api-architect incorporates feedback
4. @security-auditor approves

Coordination:
- @security-auditor has veto power on security
- @api-architect implements approved design
```

**Use When:** Setting up authentication, designing security-sensitive operations

---

### Implementation Team: @operation-engineer + @mapping-engineer

**Pattern:** Parallel Development
```
@operation-engineer implements operation methods
@mapping-engineer creates mapper functions
Both work in parallel (different files)

Workflow:
1. Both start after types generated
2. @operation-engineer:
   - Creates Producer method
   - Defines signature using generated types
   - Builds HTTP request
3. @mapping-engineer:
   - Creates mapper functions
   - Handles type conversions
   - Validates required fields
4. @operation-engineer calls mappers

Coordination:
- Different files: *Producer.ts vs Mappers.ts
- Shared dependency: generated types
- Integration point: Operation calls mapper
```

**Use When:** Implementing any operation

---

### Type Safety Pair: @typescript-expert + @operation-engineer

**Pattern:** Guided Implementation
```
@typescript-expert provides type guidance
@operation-engineer implements with type safety

Workflow:
1. @typescript-expert reviews generated types
2. @operation-engineer implements operation
3. @typescript-expert reviews for type safety
4. @operation-engineer fixes type issues

Coordination:
- @typescript-expert has final say on types
- @operation-engineer owns business logic
```

**Use When:** Complex type requirements, type safety concerns

---

### Test Coordination: @test-orchestrator + Test Engineers

**Pattern:** Orchestrated Team
```
@test-orchestrator coordinates all testing
@mock-specialist, @producer-ut-engineer, @producer-it-engineer execute
@ut-reviewer, @it-reviewer validate quality

Workflow:
1. @test-orchestrator creates test plan
2. @mock-specialist sets up HTTP mocks
3. @producer-ut-engineer creates unit tests (using mocks)
4. @producer-it-engineer creates integration tests (using real API)
5. @ut-reviewer reviews unit tests
6. @it-reviewer reviews integration tests
7. @test-orchestrator runs all tests
8. @test-orchestrator reports results

Coordination:
- @test-orchestrator coordinates all
- Test engineers work in parallel
- Reviewers validate before execution
```

**Use When:** Creating comprehensive test coverage

---

### Quality Assurance Chain: Reviewers + @gate-controller

**Pattern:** Multi-Stage Validation
```
Sequence of reviewers validate different aspects
@gate-controller performs final validation

Workflow:
1. @api-reviewer validates specification
2. @build-validator validates generation
3. @style-reviewer validates code quality
4. @ut-reviewer + @it-reviewer validate tests
5. @build-reviewer validates build
6. @gate-controller validates all gates

Coordination:
- Each reviewer has specific domain
- Sequential validation (can't skip)
- @gate-controller aggregates all results
```

**Use When:** Final quality validation before task completion

---

## Collaboration by Workflow Phase

### Phase 0: Credential Check
**Agents:** @credential-manager (solo)
**Pattern:** Independent check
```
@credential-manager checks and reports
No collaboration needed (first step, blocks all others)
```

---

### Phase 1: Research & Analysis
**Agents:** @api-researcher + @operation-analyst
**Pattern:** Sequential handoff
```
@api-researcher tests API and documents
  ↓ Saves findings
@operation-analyst uses findings to map operations
  ↓ Saves operation plan
```

---

### Phase 2: API Specification Design
**Agents:** @api-architect + @schema-specialist + @api-reviewer
**Pattern:** Collaborative creation → Review
```
@api-architect + @schema-specialist collaborate on api.yml
  ↓ Complete spec
@api-reviewer validates
  ↓ If PASS → next phase
  ↓ If FAIL → back to architects
```

---

### Phase 3: Type Generation
**Agents:** @build-validator (solo)
**Pattern:** Independent validation
```
@build-validator runs generation and validates
Reports results (blocks if failed)
```

---

### Phase 4: Implementation
**Agents:** @operation-engineer + @mapping-engineer + @style-reviewer
**Pattern:** Parallel development → Review
```
@operation-engineer implements operation
(parallel)
@mapping-engineer creates mappers
  ↓ Both complete
@style-reviewer reviews both
```

---

### Phase 5: Testing
**Agents:** @test-orchestrator coordinates 8+ agents
**Pattern:** Orchestrated team (largest collaboration)
```
@test-orchestrator
  ├─ @mock-specialist (setup mocks)
  ├─ @producer-ut-engineer (create unit tests)
  ├─ @producer-it-engineer (create integration tests)
  ├─ @ut-reviewer (review unit tests)
  ├─ @it-reviewer (review integration tests)
  └─ Run tests and report
```

---

### Phase 6: Build & Finalization
**Agents:** @build-reviewer + @gate-controller
**Pattern:** Sequential validation
```
@build-reviewer validates build
  ↓ If PASS
@gate-controller validates all gates
```

---

## Complex Collaboration Scenarios

### Scenario 1: API Spec Changes Ripple Through

```
@api-architect changes schema
  ↓ Triggers
@build-validator regenerates types
  ↓ Triggers
@mapping-engineer updates mappers (types changed)
  ↓ Triggers
@producer-ut-engineer updates test expectations
  ↓ Triggers
@build-reviewer re-validates build

Coordination:
- Chain reaction of updates
- Each agent knows their trigger
- General purpose agent manages sequence
```

---

### Scenario 2: Gate Failure Recovery

```
@test-orchestrator reports Gate 5 failure (tests failing)
  ↓ Escalates to
@test-orchestrator diagnoses issue (mock problem)
  ↓ Routes to
@mock-specialist fixes mocks
  ↓ Triggers
@producer-ut-engineer updates tests
  ↓ Triggers
@test-orchestrator re-runs tests
  ↓ Reports to
@gate-controller (validates Gate 5 now passes)

Coordination:
- Diagnostic → Fix → Validate loop
- Multiple agents in recovery chain
```

---

### Scenario 3: New Module Creation (Full Collaboration)

```
Phase 1: Discovery Team
  @product-specialist + @api-researcher + @credential-manager
  All research different aspects in parallel

Phase 2: Design Team
  @api-architect + @schema-specialist + @security-auditor
  Collaborate on secure API design

Phase 3: Generation
  @build-validator (solo)

Phase 4: Implementation Team
  @client-engineer + @operation-engineer + @mapping-engineer
  Work in parallel on different components

Phase 5: Testing Team (largest)
  @test-orchestrator coordinates 8 agents

Phase 6: Quality Team
  @documentation-writer + @build-reviewer + @gate-controller
  Sequential validation

Total: 20+ agents collaborating across 9 phases
```

---

## Collaboration Protocols

### Protocol 1: Blocking vs Non-Blocking

**Blocking Agents:**
- @credential-manager (blocks all work if no credentials + no approval)
- @api-reviewer (blocks generation if spec invalid)
- @build-validator (blocks implementation if generation fails)
- @gate-controller (blocks completion if gates fail)

**Non-Blocking Agents:**
- @style-reviewer (provides feedback, doesn't block)
- Documentation agents (can be done after main work)

---

### Protocol 2: Veto Power

**Agents with veto:**
- @security-auditor (can veto insecure approaches)
- @api-reviewer (can veto invalid specs)
- @gate-controller (can veto task completion)

**Agents without veto:**
- @style-reviewer (suggests, doesn't block)
- Most diagnostic agents (advise, don't block)

---

### Protocol 3: Conflict Resolution

**When agents disagree:**
```
1. Check user intent (user requirement wins)
2. Apply critical rules (non-negotiable)
3. Defer to domain expert (architect for API design, etc.)
4. Escalate to user if high-stakes decision
```

**Example:**
```
@api-architect wants clean REST paths (/resources)
@product-specialist says vendor uses different format (/api/v1/resources)

Resolution:
- Vendor API dictates format (reality over ideal)
- Follow @product-specialist (domain expert on vendor)
- @api-architect documents deviation
```

---

## Success Indicators

Agent collaboration is effective when:
- ✅ Clear handoffs between agents
- ✅ No duplicate work
- ✅ Parallel work where possible
- ✅ Proper escalation when blocked
- ✅ Quality maintained through reviews
- ✅ Conflicts resolved smoothly
- ✅ Context properly passed
- ✅ Dependencies respected
