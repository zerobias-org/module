# Orchestration Guide for General Purpose Agent

This guide defines how the **general purpose agent** orchestrates specialist agents to execute workflows.

## Role of General Purpose Agent

The general purpose agent is the **conductor** who:
1. **Analyzes** user requests to identify task type
2. **Routes** requests to appropriate specialist agents
3. **Coordinates** multi-agent workflows
4. **Manages** context passing between agents
5. **Validates** progression through gates
6. **Handles** failures and escalations

## Core Orchestration Principles

### 1. Agent Selection Over Solo Work
```
❌ WRONG: General purpose agent implements operation directly
✅ CORRECT: General purpose agent invokes @operation-engineer
```

**Principle**: Delegate specialist work to specialist agents.

### 2. Sequential Workflow Coordination
```
Task: Add operation
↓
Phase 1: @credential-manager (MUST be first)
↓
Phase 2: @api-researcher + @operation-analyst
↓
Phase 3: @api-architect + @schema-specialist → @api-reviewer
↓
Phase 4: @build-validator
↓
Phase 5: @operation-engineer + @mapping-engineer
↓
Phase 6: @test-orchestrator (coordinates test engineers)
↓
Phase 7: @build-reviewer
↓
Phase 8: @gate-controller
```

**Principle**: Orchestrate agents in correct sequence through phases.

### 3. Context Passing
```
@api-researcher findings
  ↓ saved to memory
@api-architect reads findings
  ↓ designs spec
@api-reviewer validates spec
  ↓ passes Gate 1
@build-validator uses spec
```

**Principle**: Each agent's output becomes next agent's input.

### 4. Validation Gates
```
After each phase:
  ↓
Check gate status
  ↓
PASS → Continue to next phase
FAIL → Invoke specialist to fix → Re-validate
```

**Principle**: Gates are checkpoints, not obstacles.

### 5. Failure Handling
```
Agent fails or gate fails
  ↓
STOP workflow
  ↓
Report specific failure to user
  ↓
Invoke specialist to diagnose
  ↓
Fix issue
  ↓
Resume workflow from failed point
```

**Principle**: Fail fast, report clearly, fix specifically.

## Orchestration Decision Tree

### Step 1: Identify Request Type

```
User request
  ↓
Parse keywords: "add", "create", "fix", "update", etc.
  ↓
Classify:
  - Add single operation
  - Add multiple operations
  - Create new module
  - Fix issue
  - Update existing code
```

### Step 2: Select Workflow Template

```
Request type identified
  ↓
Load appropriate workflow template:
  - add-operation-workflow.md
  - create-module-workflow.md
  - fix-issue-workflow.md
  - update-module-workflow.md
```

### Step 3: Execute Workflow

```
For each phase in workflow:
  1. Invoke designated agents
  2. Wait for agent completion
  3. Validate phase output
  4. Check gate status
  5. If PASS → next phase
  6. If FAIL → handle failure
```

### Step 4: Task Completion Validation

```
All phases complete
  ↓
@gate-controller validates all 6 gates
  ↓
All PASS → Task complete ✅
Any FAIL → Report → Fix → Re-validate
```

## Agent Invocation Patterns

### Pattern 1: Single Agent Task
```
Simple, isolated task → Invoke one specialist

Example:
User: "Validate the API spec"
↓
@api-reviewer Validate api.yml
```

### Pattern 2: Collaborative Task
```
Task requires multiple perspectives → Invoke collaborating agents

Example:
User: "Design webhook schema"
↓
@api-architect @schema-specialist Design webhook schema
- Include proper composition
- Use $ref for nested objects
```

### Pattern 3: Sequential Workflow
```
Multi-phase task → Invoke agents in sequence

Example:
User: "Add listWebhooks operation"
↓
Phase 1: @credential-manager
Phase 2: @api-researcher
Phase 3: @api-architect + @schema-specialist
Phase 4: @api-reviewer
...
```

### Pattern 4: Parallel Execution (within phase)
```
Independent work in same phase → Invoke agents in parallel

Example:
Within implementation phase:
↓
@operation-engineer implements operation
@mapping-engineer creates mappers
(can work in parallel if specs clear)
```

## Context Management

### Memory Structure
```
.claude/.localmemory/{module}/
├── _work/
│   ├── api-research.md          # From @api-researcher
│   ├── operation-analysis.md    # From @operation-analyst
│   ├── credentials-status.md    # From @credential-manager
│   └── reasoning/
│       ├── api-decisions.md     # From @api-architect
│       └── type-decisions.md    # From @typescript-expert
├── initial-request.json         # User's original request
└── gate-status.json             # From @gate-controller
```

### Context Passing Protocol
1. **Agent outputs** → Save to memory files
2. **Next agent** → Read from memory files
3. **General purpose** → Ensure files exist before invoking dependent agents

## Error Handling & Recovery

### Gate Failure
```
Gate N fails
  ↓
General purpose agent:
  1. STOP workflow
  2. Report: "❌ Gate N FAILED: [specific reason]"
  3. Identify responsible agent
  4. Invoke agent to fix: "@agent-name Fix [specific issue]"
  5. Re-run gate validation
  6. If PASS → Resume workflow
  7. If FAIL → Escalate to user
```

### Agent Escalation
```
Agent confidence < 70% or unclear requirement
  ↓
Agent reports: "⚠️ Need clarification: [question]"
  ↓
General purpose agent:
  1. Present question to user
  2. Wait for user response
  3. Pass response to agent
  4. Resume workflow
```

### Build/Test Failure
```
npm run build or npm test fails
  ↓
General purpose agent:
  1. STOP workflow
  2. Capture error output
  3. Invoke diagnostic agent:
     - @build-reviewer for build errors
     - @test-orchestrator for test failures
  4. Agent diagnoses issue
  5. Invoke fix agent:
     - @typescript-expert for type errors
     - @operation-engineer for logic errors
  6. Re-run build/test
  7. If PASS → Resume workflow
```

## Workflow State Management

### Track Progress
```
current_phase: "implementation"
completed_gates: [1, 2]
pending_gates: [3, 4, 5, 6]
active_agents: ["@operation-engineer", "@mapping-engineer"]
```

### Resume After Interruption
```
If workflow interrupted:
  1. Load state from memory
  2. Identify last completed gate
  3. Resume from next phase
```

## Communication Patterns

### Starting Workflow
```
General purpose agent announces:
"🚀 Starting [workflow name]
📋 Phases: [list phases]
👥 Agents involved: [list agents]
🎯 Goal: [user's goal]"
```

### Phase Transitions
```
"✅ Phase N complete: [phase name]
   Validated by: @agent-name
   Status: [gate status]

📍 Starting Phase N+1: [next phase name]
   Invoking: @agent-name @agent-name"
```

### Completion
```
"✅ All gates passed
✅ Task complete: [task description]

Summary:
- Gates 1-6: All PASSED
- Tests: X passing
- Build: Successful
- Ready for commit"
```

## Best Practices

### DO:
✅ Always invoke @credential-manager FIRST
✅ Follow workflow templates for common tasks
✅ Validate gates after each phase
✅ Pass context through memory files
✅ Report progress clearly
✅ Fail fast on errors
✅ Delegate specialist work to specialists

### DON'T:
❌ Skip credential check
❌ Invoke agents out of sequence
❌ Skip gate validations
❌ Assume agent outputs without validation
❌ Continue on gate failures
❌ Do specialist work yourself
❌ Skip context saving

## Workflow Templates

See detailed templates in:
- [add-operation-workflow.md](./workflow-templates/add-operation-workflow.md)
- [create-module-workflow.md](./workflow-templates/create-module-workflow.md)
- [fix-issue-workflow.md](./workflow-templates/fix-issue-workflow.md)
- [update-module-workflow.md](./workflow-templates/update-module-workflow.md)

## Agent Routing

See detailed routing rules in:
- [agent-routing-rules.md](./agent-routing-rules.md)

## Task Breakdown

See decomposition patterns in:
- [task-breakdown-patterns.md](./task-breakdown-patterns.md)

## Success Metrics

General purpose agent orchestration is successful when:
- ✅ Correct agents invoked for task type
- ✅ Agents invoked in correct sequence
- ✅ All gates validated
- ✅ Context properly passed
- ✅ Failures handled gracefully
- ✅ User kept informed of progress
- ✅ Task completes successfully
