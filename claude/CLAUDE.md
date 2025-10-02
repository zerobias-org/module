# Claude Module Development System

## 📍 START HERE - Navigation Guide

### ⚠️ CRITICAL: EXECUTION PROTOCOL
**[EXECUTION-PROTOCOL.md](./EXECUTION-PROTOCOL.md)** - THE MISSING PIECE! Read this FIRST to understand HOW to execute workflows, not just what they are.

### What This Document Is
This is the **main entry point** and system overview for the Claude module development system. Start here to understand the overall structure and find your way to specific documentation.

### Quick Navigation

| I want to... | Go to... |
|-------------|----------|
| **Analyze an API / Create API diagram** | [workflow/tasks/analyze-api.md](./workflow/tasks/analyze-api.md) |
| **Create a new module** | [workflow/tasks/create-module.md](./workflow/tasks/create-module.md) |
| **Add ONE operation** | [workflow/tasks/add-operation.md](./workflow/tasks/add-operation.md) |
| **Add MULTIPLE operations** | [workflow/tasks/update-module.md](./workflow/tasks/update-module.md) |
| **Fix module issues** | [workflow/tasks/fix-module.md](./workflow/tasks/fix-module.md) (if exists) |
| **Understand the workflow** | [workflow/WORKFLOW.md](./workflow/WORKFLOW.md) |
| **Check rules for a domain** | [rules/](./rules/) - organized by topic |
| **Learn about personas** | [personas/](./personas/) - expert AI roles |
| **See legacy documentation** | [create/](./create/) or [update/](./update/) - reference only |

## 🎯 Overview

A comprehensive, structured system for developing TypeScript API client modules with AI assistance. This system uses specialized personas, strict rules, and a unified workflow to ensure consistent, high-quality module creation and maintenance.

## 🚨 MANDATORY PRE-EXECUTION PROTOCOL

**BEFORE starting ANY task, you MUST:**

### 1. Identify the Task Type
Map user request to specific workflow:
- "Create module" → `workflow/tasks/create-module.md`
- "Add operation" → `workflow/tasks/add-operation.md`
- "Add multiple operations" → `workflow/tasks/update-module.md`
- "Fix issue" → `workflow/tasks/fix-module.md`

### 2. Load Required Rules
Based on task type, load ONLY relevant rules:

**For API Specification Work:**
- `rules/api-specification.md` (all API rules consolidated)
- `ENFORCEMENT.md` (validation gates)

**For Implementation Work:**
- `rules/implementation.md` (patterns)
- `rules/error-handling.md` (error types)
- `rules/type-mapping.md` (type conversions)

**For Testing Work:**
- `rules/testing.md` (test requirements)

**Always Load:**
- `rules/prerequisites.md` (before any work)

### 3. Identify Active Personas
Determine which personas are relevant:
- API work → API Architect + Security Auditor
- Implementation → TypeScript Expert + Integration Engineer
- Testing → Testing Specialist
- Documentation → Documentation Writer

### 4. Create TodoWrite with ALL Steps
Map out complete workflow:
1. Update API specification
2. Run npm generate
3. Implement operation
4. Write tests (unit + integration)
5. Run tests
6. Build project
7. Complete

### 5. Execute with Constraints
Work ONLY within loaded rules and workflow steps:
- Apply rules at EACH decision point
- Validate at EACH gate
- Never assume - verify against rules
- Stop at any gate failure

**See `EXECUTION-PROTOCOL.md` for complete details.**

## 🚀 Quick Start

When working with modules, you express your intent:
- "Create a [service] module with [resource] and [resource] operations"
- "Add [operation type] operations to the [service] module"
- "Fix the failing tests in [vendor] [service] module"

The system will:
1. **Analyze request** → Identify task type
2. **Load rules** → Load only relevant rules
3. **Identify personas** → Activate appropriate experts
4. **Execute workflow** → Follow task steps with validation gates
5. **Maintain quality** → Enforce all rules throughout

## 📁 System Structure

```
claude/
├── CLAUDE.md                    # This file - system overview
├── rules/                       # Domain-specific rules and guidelines
├── personas/                    # AI persona definitions
├── workflow/                    # Unified workflow system
└── templates/                   # Reusable templates and helpers
```

## 🤖 Personas

The system uses specialized AI personas, each an expert in their domain:

| Persona | Expertise | Primary Responsibilities |
|---------|-----------|-------------------------|
| **[Product Specialist](./personas/product-specialist.md)** | Product & API research | Resource naming, operation priorities |
| **[API Architect](./personas/api-architect.md)** | OpenAPI design | API specifications, schemas |
| **[Security Auditor](./personas/security-auditor.md)** | Security patterns | Authentication, credentials |
| **[TypeScript Expert](./personas/typescript-expert.md)** | Type safety | Implementations, type mappings |
| **[Integration Engineer](./personas/integration-engineer.md)** | API integration | HTTP clients, error handling |
| **[Testing Specialist](./personas/testing-specialist.md)** | Test coverage | Unit & integration tests |
| **[Documentation Writer](./personas/documentation-writer.md)** | Technical writing | User guides, documentation |

### Collaboration Patterns
- **API Architect + Security Auditor**: Review each other's work for API security
- **TypeScript Expert + Integration Engineer**: Joint implementation reviews
- Personas document decisions when confidence < 90%

## 📋 Rules System

Rules are organized by domain for easy reference:

| Rule Category | Description | Critical Rules |
|--------------|-------------|----------------|
| **[API Specification](./rules/api-specification.md)** | OpenAPI standards | Consistent naming, clean paths |
| **[Implementation](./rules/implementation.md)** | TypeScript patterns | No env vars, use core errors |
| **[Testing](./rules/testing.md)** | Test requirements | 100% coverage for new code |
| **[Build & Quality](./rules/build-quality.md)** | Build gates | Must pass before proceeding |
| **[Git Workflow](./rules/git-workflow.md)** | Version control | Clean state, conventional commits |
| **[Security](./rules/security.md)** | Secure coding | No committed secrets |
| **[Documentation](./rules/documentation.md)** | Doc standards | USERGUIDE.md mandatory |
| **[Type Mapping](./rules/type-mapping.md)** | Core type usage | UUID, Email, URL types |
| **[Error Handling](./rules/error-handling.md)** | Error patterns | Core error types only |

### Rule Compliance
- 🚨 **CRITICAL RULES**: Immediate task failure if violated
- 🟡 **STANDARD RULES**: Should be followed, document exceptions
- 🟢 **GUIDELINES**: Best practices and recommendations

## 🔄 Unified Workflow

### Single Entry Point
ALL requests go through the [unified workflow](./workflow/WORKFLOW.md):

```
User Request → Analyze → Route → Execute → Validate → Complete
```

### Key Features
- **Smart Routing**: Automatically determines action needed
- **Quality Gates**: Multiple validation checkpoints
- **Incremental**: Operations added one at a time
- **Git Safety**: Checkpoint commits for rollback
- **Context Aware**: Monitors and optimizes context usage

### Workflow Documentation
- **[Main Workflow](./workflow/WORKFLOW.md)** - Orchestration and phases
- **[Decision Tree](./workflow/decision-tree.md)** - Routing logic
- **[Context Management](./workflow/context-management.md)** - Memory optimization
- **[Task Definitions](./workflow/tasks/)** - Individual task specs

## 💾 Memory Management

### Structure
```
.claude/.localmemory/{action}-{module}/
├── _work/                    # Working directory
│   ├── .env                  # Development credentials
│   ├── product-model.md      # Product understanding
│   └── reasoning/            # Decision logs
├── initial-request.json      # Original user request
├── task-01-output.json       # Task outputs
└── recovery-context.json     # Error recovery state
```

### Module Naming
- `create-{vendor}-{module}` - Creating base module
- `update-{vendor}-{suite}-{module}` - Updating module (adding operations)
- `fix-{vendor}-{module}` - Fixing module issues

### Module Path Structure
**Yeoman generator determines paths**:
- With suite: `package/{vendor}/{suite}/{service}/` (vendor/suite/service)
- Without suite: `package/{vendor}/{service}/` (vendor/service)
- Format: Always use forward slashes, no hyphens in path segments

## 🎯 Quality Enforcement

### Zero Hallucination Strategy
1. Always verify against documentation
2. Test code immediately after generation
3. Flag all assumptions
4. Maintain confidence scores
5. Never guess names or patterns

### Build Gates
- Pre-implementation validation
- Post-generation checks
- Mid-task verification
- Final quality validation

### Testing Requirements
- ALL new operations need tests
- Integration tests with real API
- Unit tests with mocks
- Existing tests must keep passing

## 🔧 Common Operations

### Create Minimal Module
```
1. Discover product → Set up environment
2. Create scaffold → Design minimal API
3. Implement connection → Add one operation
4. Test everything → Document
```

### Add Operation
```
1. Research API → Update specification
2. Generate types → Implement operation
3. Add mappers → Create tests
4. Validate → Commit
```

### Fix Issue
```
1. Diagnose problem → Plan solution
2. Apply fix → Test thoroughly
3. Verify no regression → Commit
```

## 📊 Context Management

### Monitoring
- 50% - First warning
- 60% - Consider structure
- 70% - Strong split recommendation
- >70% - Continuous warnings

### Optimization
- Keep essential, drop optional
- Summarize completed work
- Reference instead of repeat
- Split at natural boundaries

## 🛠️ Templates

Available templates in [`templates/`](./templates/):
- `curl-test.sh` - API endpoint testing
- `node-test.js` - Node.js API testing
- `recovery-context.json` - Error recovery tracking

## 🚦 Success Criteria

### Module Success
✅ Connects to service
✅ One operation works
✅ All tests pass
✅ Documentation complete

### Operation Success
✅ Fully implemented
✅ Tests comprehensive
✅ No regressions
✅ Build passes

## 🆘 Getting Help

- Check relevant [rules](./rules/) for domain guidance
- Review [persona](./personas/) expertise for decisions
- Follow [workflow](./workflow/) for process
- Use [templates](./templates/) for common tasks

## 🎓 Key Principles

1. **One Flow**: Single workflow adapts to all needs
2. **Expert Personas**: Each owns their domain completely
3. **Strict Rules**: Organized, enforced, documented
4. **Quality First**: Multiple gates, zero tolerance
5. **No Guessing**: Verify everything, assume nothing
6. **Incremental**: Small, tested, committed steps
7. **Context Aware**: Monitor and optimize continuously

## 🔄 Continuous Improvement

When encountering new patterns or issues:
1. System flags for user decision
2. User approves rule addition/change
3. Rules updated immediately
4. Future runs use new knowledge

---

**Remember**: The goal is autonomous, high-quality module development with zero hallucinations and consistent patterns. When in doubt, check the rules, consult the personas, and follow the workflow.