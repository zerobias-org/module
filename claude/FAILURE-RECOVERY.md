# 🔧 FAILURE RECOVERY GUIDE

## Common Failures & Fixes

### 1. "Claude added error responses"
**Symptom**: 401, 403, 404, 500 in api.yml
**Fix**:
```bash
# Remove ALL error responses
# Keep ONLY 200/201
# Re-run generate
```

### 2. "Claude used 'any' type"
**Symptom**: `Promise<any>` in implementation
**Fix**:
```bash
# 1. Check if generation was run
ls generated/api/
# 2. If not, run it
npm run generate
# 3. Import proper type
import { GeneratedType } from '../generated/api'
```

### 3. "Claude skipped generation"
**Symptom**: Types don't exist, compilation errors
**Fix**:
```bash
# ALWAYS after api.yml changes:
npm run generate
# THEN check what was generated:
ls generated/
```

### 4. "Claude loaded all rules"
**Symptom**: Context overflow, irrelevant information
**Fix**:
```
# Clear context
# Reload ONLY:
- EXECUTION-PROTOCOL.md
- Specific workflow task
- Phase-specific rules
```

### 5. "Claude didn't use TodoWrite"
**Symptom**: No task tracking, forgot steps
**Fix**:
```
# Create todos immediately:
- [ ] Update API spec
- [ ] Run generate
- [ ] Implement with types
- [ ] Write tests
- [ ] Validate build
```

## Recovery Commands

```bash
# Reset to clean state
git status
git diff

# Verify no rule violations
./claude/validate-rules.sh

# Check generation state
ls -la generated/

# Verify build
npm run build

# Run tests
npm test
```

## When to Start Over

Start fresh if:
- ❌ Multiple error responses added
- ❌ Implementation done before generation
- ❌ Wrong workflow followed
- ❌ Context contaminated with unrelated rules

## Clean Restart Protocol

1. Note current progress
2. Git stash or commit WIP
3. Clear Claude context
4. Start with: "Fresh start: [specific task]"
5. Load ONLY needed files
6. Follow workflow step-by-step