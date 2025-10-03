# Slash Commands System

Quick-access commands for executing complete workflows with agent orchestration.

## Available Commands

### Module Development

```bash
/create-module <vendor> <service> [suite]
```
Create a new module from scratch with MVP (connection + one operation).
- **Duration**: 3-4 hours
- **Agents**: 24 agents across 9 phases
- **Output**: Working module ready for production

**Examples:**
```bash
/create-module github github
/create-module amazon s3 aws
/create-module avigilon access alta
```

---

```bash
/add-operation <module-identifier> <operation-name>
```
Add a single operation to existing module.
- **Duration**: 45-70 minutes
- **Agents**: 18 agents across 7 phases
- **Gates**: All 6 gates validated

**Examples:**
```bash
/add-operation github-github listWebhooks
/add-operation amazon-aws-s3 listBuckets
/add-operation avigilon-alta-access unlockDoor
```

---

```bash
/add-operations <module-identifier> <operation1,operation2,operation3>
```
Add multiple operations sequentially.
- **Duration**: ~45-70 min per operation
- **Strategy**: One at a time, validate before next
- **Gates**: All 6 gates per operation

**Examples:**
```bash
/add-operations github-github listWebhooks,getWebhook,createWebhook,updateWebhook,deleteWebhook
/add-operations amazon-aws-s3 listBuckets,createBucket,deleteBucket
```

---

### Analysis & Planning

```bash
/analyze-api <vendor> <product>
```
Analyze vendor API and create entity relationship diagram.
- **Duration**: 30-60 minutes
- **Output**: Mermaid ERD diagram (external-api.mmd)
- **No code generation**: Analysis only

**Examples:**
```bash
/analyze-api github webhooks
/analyze-api amazon s3
/analyze-api avigilon alta
```

---

### Maintenance

```bash
/fix-issue <module-identifier> [description]
```
Diagnose and fix issues in existing module.
- **Duration**: 15 min - 2 hours
- **Strategy**: Diagnose → Fix → Validate
- **Gates**: Re-validate affected gates only

**Examples:**
```bash
/fix-issue github-github "type error in WebhookProducer"
/fix-issue amazon-aws-s3 "build failing"
/fix-issue avigilon-alta-access
```

---

```bash
/validate-gates <module-identifier>
```
Validate all 6 quality gates for a module.
- **Duration**: 5-10 minutes
- **Validator**: @gate-controller
- **Output**: Gate status report

**Examples:**
```bash
/validate-gates github-github
/validate-gates amazon-aws-s3
```

---

## Module Identifier Format

**Pattern**: `{vendor}-{service}` or `{vendor}-{suite}-{service}`

**Examples:**
- `github-github` → package/github/github/
- `amazon-aws-s3` → package/amazon/aws/s3/
- `avigilon-alta-access` → package/avigilon/alta/access/
- `gitlab-gitlab` → package/gitlab/gitlab/

## Command Structure

All commands follow consistent pattern:
```
/command-name <required-arg> <required-arg> [optional-arg]
```

### Argument Types

- **module-identifier**: Format `vendor-service` or `vendor-suite-service`
- **operation-name**: camelCase, starts with verb (list, get, create, update, delete, search)
- **operations**: Comma-separated list (no spaces): `op1,op2,op3`
- **vendor**: Lowercase alphanumeric
- **service**: Lowercase alphanumeric
- **suite**: Lowercase alphanumeric (optional)

## Workflow Execution

Each command triggers a complete workflow with:
1. **Agent orchestration**: Right agents invoked in correct sequence
2. **Phase execution**: Sequential phases with validation
3. **Gate validation**: Quality gates enforced
4. **Error handling**: Failures caught and reported
5. **Context management**: Information passed between agents

## Command Files

Each command has a JSON definition:
- `add-operation.json` - Single operation workflow
- `add-operations.json` - Multiple operations workflow
- `create-module.json` - Module creation workflow
- `analyze-api.json` - API analysis workflow
- `fix-issue.json` - Issue resolution workflow
- `validate-gates.json` - Gate validation

## Workflow Templates

Detailed execution steps in:
- `claude/orchestration/workflow-templates/add-operation-workflow.md`
- `claude/orchestration/workflow-templates/update-module-workflow.md`
- `claude/orchestration/workflow-templates/create-module-workflow.md`
- `claude/orchestration/workflow-templates/fix-issue-workflow.md`

## Agent System

Commands orchestrate specialized agents:
- **26 agents** across 6 workflow phases
- Each agent has specific expertise and rules
- Agents enforce quality through validation gates

See [claude/agents/AGENTS.md](../agents/AGENTS.md) for complete agent reference.

## Validation Gates

All module workflows validate through 6 gates:
1. **Gate 1**: API Specification (@api-reviewer)
2. **Gate 2**: Type Generation (@build-validator)
3. **Gate 3**: Implementation (@typescript-expert + engineers)
4. **Gate 4**: Test Creation (@test-orchestrator + reviewers)
5. **Gate 5**: Test Execution (@test-orchestrator)
6. **Gate 6**: Build (@build-reviewer)

See [claude/ENFORCEMENT.md](../ENFORCEMENT.md) for gate details.

## Error Handling

Commands handle errors gracefully:
- **Module not found**: Clear error with suggestions
- **Gate failure**: Stop, report, guide to fix
- **Credentials missing**: Prompt user for decision
- **Build/test failure**: Diagnose and suggest fixes

## Success Criteria

Each command completes successfully when:
- ✅ All required phases executed
- ✅ All validation gates passed
- ✅ Tests passing
- ✅ Build successful
- ✅ Ready for commit

## Examples by Use Case

### Starting a New Module
```bash
# Step 1: Analyze API first (optional but recommended)
/analyze-api github webhooks

# Step 2: Create module with MVP
/create-module github github

# Step 3: Add more operations
/add-operations github-github createWebhook,updateWebhook,deleteWebhook
```

### Expanding Existing Module
```bash
# Add single operation
/add-operation github-github searchRepositories

# Or add multiple at once
/add-operations github-github searchRepositories,searchUsers,searchIssues
```

### Fixing Issues
```bash
# Diagnose and fix
/fix-issue github-github "tests failing after update"

# Validate after fix
/validate-gates github-github
```

### Pre-Commit Validation
```bash
# Before committing
/validate-gates github-github
```

## Command Development

To add a new slash command:
1. Create JSON definition in `.slashcommands/`
2. Create workflow template in `orchestration/workflow-templates/`
3. Update this README
4. Test command execution

See existing commands for structure and patterns.

## Related Documentation

- [Orchestration Guide](../orchestration/ORCHESTRATION-GUIDE.md) - How workflows are orchestrated
- [Agent Routing Rules](../orchestration/agent-routing-rules.md) - How agents are selected
- [Agent System](../agents/AGENTS.md) - Complete agent reference
- [Enforcement](../ENFORCEMENT.md) - Validation gates
- [Execution Protocol](../EXECUTION-PROTOCOL.md) - Execution sequence
