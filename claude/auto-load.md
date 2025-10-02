# Auto-Load Instructions for Claude

## MANDATORY ON EVERY REQUEST

When you receive ANY module-related request, IMMEDIATELY:

1. **Identify the task type** from the request
2. **Load ONLY these files**:
   - `claude/EXECUTION-PROTOCOL.md` (ALWAYS)
   - `claude/workflow/tasks/[identified-task].md`
   - Specific rules for current phase only

## Task Identification Matrix

| If user says... | Task Type | Load These Files |
|----------------|-----------|------------------|
| "add operation" | add-operation | EXECUTION-PROTOCOL, workflow/tasks/add-operation, rules/api-specification-critical |
| "create module" | create-module | EXECUTION-PROTOCOL, workflow/tasks/create-module, rules/prerequisites |
| "add operations" (plural) | update-module | EXECUTION-PROTOCOL, workflow/tasks/update-module, rules/api-specification-critical |
| "fix" or "debug" | fix-module | EXECUTION-PROTOCOL, workflow/tasks/fix-module, rules/error-handling |

## Phase-Based Loading

### DO NOT load all rules at once!

```python
# WRONG - Loading everything
load_all_rules()  # ❌ NO!

# RIGHT - Phase-based loading
if current_phase == "api_specification":
    load("rules/api-specification-critical.md")
    load("rules/api-specification.md")
elif current_phase == "implementation":
    load("rules/implementation.md")
    load("rules/type-mapping.md")
elif current_phase == "testing":
    load("rules/testing.md")
```

## Re-Load Trigger Events

MUST reload relevant rules when:
1. ✅ New user request arrives
2. ✅ Returning from context cleanup
3. ✅ Moving to next phase
4. ✅ After any interruption

## Verification Commands

After loading, verify with:
```bash
echo "Loaded for task: [task-type]"
echo "Current phase: [phase]"
echo "Active rules: [list]"
```