# AI Module Development Workflow Guide

## 📍 Navigation
- **System Overview**: [CLAUDE.md](./CLAUDE.md) - START HERE
- **This Document**: User-friendly guide to the AI workflow
- **Technical Details**: [workflow/](./workflow/) directory

## 🎯 What This System Does

This is an AI-powered system for creating and maintaining TypeScript API client modules. It uses specialized AI personas, strict rules, and a unified workflow to ensure consistent, high-quality module development with zero hallucinations.

## 🚀 Quick Start for Users

### Creating a New Module

```bash
# Simple request - AI handles everything
"Create a GitHub module with basic user operations"

# Or with specific operations
"Create an Avigilon module with access control and user management"

# With credentials ready
"Create a Slack module, I have the API token in .env"
```

### Adding Operations to Existing Module

```bash
# Add a SINGLE operation
"Add listWebhooks to the GitHub module"
# → Uses add-operation task directly

# Add MULTIPLE operations
"Add webhook CRUD operations to the GitHub module"
# → Uses update-module task (which calls add-operation for each)

# Add related operations
"Add ability to create, update and delete users in the AWS IAM module"
# → Uses update-module task
```

### Fixing Issues

```bash
# When something's broken
"Fix the failing tests in the GitHub module"
"The build is failing for AWS IAM module"
```

## 📋 How It Works - The Complete Flow

### Step 1: Request Analysis
AI analyzes your request to determine:
- What module you're targeting
- What action is needed (create/add/fix)
- What operations you want
- What credentials you have

### Step 2: Module Discovery
System checks if module exists:
```bash
npx lerna list --json  # Lists all existing modules
```
- If exists → Update flow
- If not exists → Create flow
- If ambiguous → Ask for clarification

### Step 3: Task Execution
System follows numbered tasks in sequence:
```
Task 1 → Task 2 → Task 3 → ... → Task N
```

Each task:
- Has assigned personas (experts)
- Follows specific rules
- Stores output in memory
- Creates git commits

### Step 4: Quality Gates
Multiple checkpoints ensure quality:
- Build must pass
- Tests must pass
- Lint must pass
- No breaking changes (for updates)

### Step 5: Completion
- All operations implemented
- Tests comprehensive
- Documentation complete
- Ready for use

## 🗂️ Where Everything Lives

```
claude/                    # AI System Documentation
├── rules/                 # Domain-specific rules
├── personas/              # AI expert definitions
├── workflow/              # Task workflows
│   ├── tasks/            # Individual task definitions
│   └── schemas/          # Memory file schemas
└── templates/            # Reusable templates

.claude/
├── .localmemory/         # Session memory (not in git)
│   └── create-github-github/
│       ├── _work/        # Working directory
│       │   ├── .env      # Development credentials
│       │   └── product-model.md
│       ├── initial-request.json
│       ├── task-01-output.json
│       └── task-02-output.json
│
package/
└── github/
    └── github/           # Created module
        ├── src/
        │   ├── GitHubClient.ts
        │   ├── GitHubConnectorImpl.ts
        │   ├── UserProducer.ts
        │   ├── Mappers.ts
        │   └── util.ts
        ├── test/
        ├── generated/
        ├── api.yml
        ├── connectionProfile.yml
        └── USERGUIDE.md
```

## ⚠️ What to Pay Attention To

### Before Starting

1. **Clean Git State** (for updates)
   ```bash
   git status  # Must be clean
   ```

2. **Credentials Ready**
   ```bash
   # In .env file
   API_TOKEN=your_token_here

   # Or in .connectionProfile.json
   {
     "token": "your_token_here",
     "baseUrl": "https://api.example.com"
   }
   ```

3. **Clear Context** (for complex tasks)
   ```
   # If task is complex, clear AI context
   /clear
   # Or compact it
   /compact
   ```

### During Development

1. **Context Warnings**
   - At 50%, 60%, 70% - AI warns about context usage
   - Consider splitting task if warned
   - AI will ask, you decide

2. **Decision Points**
   - Multiple module matches → You choose
   - Unclear intent → AI asks for clarification
   - Low confidence → AI requests guidance

3. **Memory Persistence**
   - Everything saved in `.claude/.localmemory/`
   - Can stop anytime
   - Resume in new session
   - AI reads files, continues where left off

### After Completion

1. **Verify Success**
   ```bash
   npm run build       # Must pass
   npm run test        # Must pass
   npm run lint        # Must pass
   ```

2. **Check Implementation**
   ```bash
   # See what operations exist
   grep "operationId:" api.yml

   # Check what's implemented
   ls src/*Producer.ts
   ```

3. **Test with Real API**
   ```bash
   # Integration tests use real API
   npm run test:integration
   ```

## 🤖 The AI Personas Working for You

| Persona | What They Do |
|---------|--------------|
| **Product Specialist** | Researches API, determines operations |
| **API Architect** | Designs OpenAPI specifications |
| **Security Auditor** | Handles authentication, security |
| **TypeScript Expert** | Implements type-safe code |
| **Integration Engineer** | Builds HTTP clients, error handling |
| **Testing Specialist** | Creates comprehensive tests |
| **Documentation Writer** | Writes user guides |

## 📏 Rules Being Enforced

### Critical Rules (Task Fails if Violated)
- 🚫 **NEVER** push to git remote (no `git push` ever)
- 🚫 **NEVER** use `!` (non-null assertion) in TypeScript
- 🚫 **NO** environment variables in production code
- 🚫 **NO** Python scripts (use bash or Node.js)
- ✅ Use core error types only
- ✅ Build must pass at checkpoints
- ✅ All tests must pass
- ✅ No breaking changes in updates

### Quality Standards
- ✅ camelCase for ALL properties (never snake_case)
- ✅ Clean path formats (`/resources/{id}`)
- ✅ Consistent naming throughout
- ✅ `const output: Type` pattern in mappers
- ✅ Manual PagedResults assignment (not `ingest()`)
- ✅ 100% test coverage for new code
- ✅ Complete USERGUIDE.md mandatory

## 🔄 Session Management

### Stopping Mid-Task
```bash
# You can stop anytime
"I need to stop here"

# AI saves state in:
.claude/.localmemory/create-github-github/task-05-output.json
```

### Resuming Later
```bash
# New session, different day
"Continue with the GitHub module"

# AI checks memory, finds task 5 complete
# Continues from task 6
```

### Starting Fresh
```bash
# If something went wrong
rm -rf .claude/.localmemory/create-github-github/
"Start over with GitHub module"
```

## 📊 How to Track Progress

### Check Task Status
```bash
# See completed tasks
ls .claude/.localmemory/create-*/task-*-output.json

# Check last task output
cat .claude/.localmemory/create-github-github/task-05-output.json
```

### Check Implemented Operations
```bash
# In module directory
grep "operationId:" api.yml

# Or check generated code
grep "interface.*Api" generated/api/index.ts
```

### Review Git History
```bash
# See what was done
git log --oneline -10

# Each commit shows task completed
```

## 🚨 Common Issues & Solutions

### "Module not found"
- Check product packages are available
- Verify module name spelling
- Try vendor-service pattern (e.g., "github-github")

### "Build failing"
```bash
# First try clean and rebuild
npm run clean
npm run build
```
- Check api.yml for specification errors
- Verify TypeScript configuration
- Check for missing dependencies

### "Tests failing"
- Check credentials in .env
- Verify API endpoint is correct
- AI will debug with curl or Node.js script
- Integration tests may fail without credentials (that's OK)

### "Context getting large"
- Let AI complete current task
- Then start fresh session for next task
- Memory files maintain continuity

### "Persona Conflicts"
- User intent takes priority
- Critical rules are non-negotiable
- AI will escalate if uncertain

## ✅ Success Indicators

You know it's working when:
- Build passes without errors
- All tests pass (unit and integration)
- USERGUIDE.md is complete
- You can use the module immediately

## 🆘 Getting Help

If stuck:
1. Check the rules: `claude/rules/`
2. Check personas: `claude/personas/`
3. Check workflow: `claude/workflow/`
4. Check examples: Old tasks in `claude/create/tasks/`

## 💡 Pro Tips

1. **Provide credentials early** - AI can test while building
2. **Be specific about operations** - "list and create users" vs "user management"
3. **Let AI complete tasks** - Don't interrupt mid-implementation
4. **Trust the personas** - Each is expert in their domain
5. **Check memory files** - See what AI is thinking/planning
6. **Look in repo for examples** - Existing modules are best reference
7. **Use conventional commits** - Lerna uses them for versioning
8. **Never ask AI to push** - You control remote operations

## 📝 Example Full Session

```bash
User: "Create a GitHub module with repository operations"

AI: "I'll create a GitHub module. Starting with product discovery..."
# Task 1: Finds @auditlogic/product-github-github
# Task 2: Analyzes GitHub API with AI agent
# Task 3: Maps operations (listRepos, getRepo, createRepo)
# Task 4: Checks prerequisites
# Task 5: Scaffolds module
# Task 6: Creates API specification
# Task 7: Implements client and producers
# Task 8: Adds integration tests
# Task 9: Adds unit tests
# Task 10: Creates USERGUIDE.md
# Task 11: Updates README if needed
# Task 12: Final validation

AI: "GitHub module created successfully!
- Connected: ✓
- Operations: listRepos, getRepo, createRepo
- Tests: All passing
- Documentation: Complete"

User: "Great! Now add webhook operations"

AI: "I'll add webhook operations to the GitHub module..."
# Continues with update flow...
```

## 🔧 Recent Improvements

### Memory File Schemas
- All task outputs now have defined JSON schemas
- Located in `workflow/schemas/task-outputs.md`
- Ensures consistent task coordination

### Clearer Task Relationships
- **update-module**: Parent task for multiple operations
- **add-operation**: Child task for single operation
- Navigation updated to clarify when to use each

### Enhanced Rules
- No more `!` in TypeScript examples
- Git workflow prohibits all forms of push
- Persona conflict resolution documented
- Testing clarifications for integration tests

### Better Error Recovery
- Build failures: `npm run clean` then rebuild
- API mismatches: Check real responses
- Personas escalate when confidence <70%

## 🎯 Remember

- **AI handles complexity** - You provide intent
- **Everything is saved** - Can stop/resume anytime
- **Quality is enforced** - Multiple checkpoints
- **Learning system** - Gets better with use
- **You're in control** - AI asks when uncertain
- **No hallucinations** - Everything verified against docs

---

**Ready to build modules? Just tell the AI what you need!**