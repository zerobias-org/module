# Migration Map: From Old Task Structure to New System

## Overview
This document maps how content from the old task files has been redistributed to the new structure of rules, personas, and workflow tasks.

## Key Principles of Migration

1. **Rules** go to `claude/rules/*.md` - Hard requirements, validation criteria, patterns
2. **Personas** go to `claude/personas/*.md` - Responsibilities, expertise, decision authority
3. **Workflows** go to `claude/workflow/tasks/*.md` - Process steps, sequencing, outputs
4. **Context/Memory** patterns stay in workflow tasks - What to store, where, format

## Content Distribution Map

### From: `claude/create/tasks/task-01-product-discovery-and-setup.md`

**→ Rules** (`rules/api-specification.md`):
- Module identifier derivation rules
- Product package naming patterns

**→ Personas** (`personas/product-specialist.md`):
- Product discovery responsibilities
- Matching product packages
- Basic information extraction

**→ Workflow** (`workflow/tasks/create-module.md#1-product-discovery`):
- Process steps
- NPM commands
- Memory folder creation
- Output format

### From: `claude/create/tasks/task-02-external-api-analysis.md`

**→ Rules** (`rules/api-specification.md`):
- Entity discovery checklist
- Commonly missed resource types
- Operation mapping requirements
- Completeness verification

**→ Personas** (`personas/product-specialist.md`):
- AI agent usage for analysis
- Mermaid diagram creation
- Resource type verification

**→ Workflow** (`workflow/tasks/create-module.md#2-external-api-analysis`):
- AI agent task specification
- Output requirements
- Context management

### From: `claude/create/tasks/task-05-scaffold-module-with-yeoman.md`

**→ Rules** (`rules/implementation.md`):
- File organization structure
- Package naming conventions

**→ Personas** (`personas/typescript-expert.md`):
- Module scaffolding responsibility
- Dependency management

**→ Workflow** (`workflow/tasks/create-module.md#5-scaffold-module`):
- Yeoman generator usage
- Configuration steps
- Build validation

### From: `claude/create/tasks/task-06-api-definition-create-spec.md`

**→ Rules** (`rules/api-specification.md`):
- Path format standards
- Property naming (camelCase)
- Parameter reuse requirements
- Security scheme configuration
- Metadata configuration

**→ Personas** (`personas/api-architect.md` + `personas/security-auditor.md`):
- API specification design
- Authentication method selection
- Schema definition
- Security configuration

**→ Workflow** (`workflow/tasks/create-module.md#6-define-api-specification`):
- Research process
- Validation steps
- Output format

### From: `claude/create/tasks/task-07-implementation-module-logic.md`

**→ Rules** (`rules/implementation.md`):
- No environment variables in client
- Core error usage requirements
- Interface name compliance
- Build gate compliance
- Mapper validation requirements
- Enum mapping rules
- Type import requirements

**→ Personas** (`personas/typescript-expert.md` + `personas/integration-engineer.md`):
- Client implementation
- Mapper creation
- Producer implementation
- Error handling

**→ Workflow** (`workflow/tasks/create-module.md#7-implement-module-logic`):
- Implementation sequence
- Build validation checkpoints
- File creation order

### From: `claude/create/tasks/task-08-integration-tests.md`

**→ Rules** (`rules/testing.md`):
- Integration test patterns
- Credential discovery
- Test skipping rules

**→ Personas** (`personas/testing-specialist.md`):
- Integration test creation
- Fixture management
- Credential handling

**→ Workflow** (`workflow/tasks/create-module.md#8-integration-tests`):
- Test environment setup
- Test creation steps

### From: `claude/create/tasks/task-09-add-unit-tests.md`

**→ Rules** (`rules/testing.md`):
- Unit test patterns
- Mock strategies
- Coverage requirements

**→ Personas** (`personas/testing-specialist.md`):
- Unit test implementation
- Mock creation
- Assertion patterns

**→ Workflow** (`workflow/tasks/create-module.md#9-unit-tests`):
- Test file organization
- Mock setup

### From: `claude/create/tasks/task-10-create-user-guide.md`

**→ Rules** (`rules/documentation.md`):
- USERGUIDE.md structure
- Content requirements
- Focus on authentication only

**→ Personas** (`personas/documentation-writer.md`):
- User guide creation
- Credential documentation
- Permission mapping

**→ Workflow** (`workflow/tasks/create-module.md#10-create-user-guide`):
- Documentation steps
- Output format

### From: `claude/update/tasks/*`

Similar distribution pattern:
- Validation rules → `rules/build-quality.md`
- Fail-fast rules → `rules/testing.md`
- Update processes → `workflow/tasks/update-module.md`
- Backward compatibility → `rules/api-specification.md`

## Critical Rules Extracted

### Always Enforced (from all tasks)

**Build Gates**:
- Must pass before proceeding
- Stop immediately on failure
- Multiple checkpoints

**Context Management**:
- Clear context at task start
- Read original user intent
- Store in memory files

**No Breaking Changes** (updates):
- Preserve all existing functionality
- Add only, never modify
- Fail fast on regression

## Memory File Patterns (Preserved)

All tasks maintain the same memory structure:
```
.claude/.localmemory/{action}-{module}/
├── _work/
├── task-XX-output.json
└── initial-request.json
```

## Git Commit Patterns (Preserved)

Conventional commits with task notation:
- After each major step
- Include task number in body
- Clear checkpoint commits

## What Stays in Old Location

The detailed task files in `claude/create/tasks/` and `claude/update/tasks/` can remain as reference but the new workflow tasks should be used for execution. They contain:
- Specific command examples
- Detailed error scenarios
- Edge case handling

## How to Use This Map

1. **For execution**: Use `workflow/tasks/*.md`
2. **For rules**: Check `rules/*.md`
3. **For expertise**: Consult `personas/*.md`
4. **For reference**: Old task files have detailed examples

## Benefits of New Structure

1. **No duplication**: Each rule in one place
2. **Clear ownership**: Personas own their domain
3. **Simplified flow**: One workflow adapts to needs
4. **Easy updates**: Add rules without touching workflows
5. **Better context**: Focused, relevant information

## Migration Status

✅ Rules extracted and organized
✅ Personas defined with responsibilities
✅ Core workflow tasks created
✅ Context management defined
✅ Mapping documented

⏳ Remaining: Additional workflow tasks (fix, deprecate, etc.) as needed