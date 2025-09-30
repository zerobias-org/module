# Test Mode Guide - Adding and Updating Rules

## Overview

This guide explains how to safely add or update rules in the new agent-based system while testing changes before full deployment.

---

## Rule Update Workflow

### 1. Identify What Needs to Change

**Ask yourself:**
- Which rule file needs updating? (See `.claude/RULE-TO-AGENT-MAPPING.md`)
- Which agents use this rule?
- Is this a new rule or a modification?
- Does this affect multiple agents?

**Example:**
> "I need to add a new error handling pattern for rate limiting"
> → Update: `error-handling.md`
> → Affects: 6 agents (client-engineer, integration-engineer, mapping-engineer, operation-engineer, security-auditor, typescript-expert)

---

### 2. Update the Rule File

**Location:** `.claude/rules/[rule-file].md`

**Steps:**
1. Open the rule file
2. Make your changes (add/modify/remove rules)
3. Keep format consistent with existing rules
4. Use clear numbering if adding to numbered rules

**Example: Adding a rate limit error pattern to error-handling.md**

```markdown
### Rate Limiting Errors

#### RateLimitExceededError
Use for rate limit exceeded (429 errors):

```typescript
import { RateLimitExceededError } from '@auditmation/types-core-js';

// Basic usage
throw new RateLimitExceededError();

// With retry-after info
throw new RateLimitExceededError(new Date(), retryAfterSeconds, 'seconds');
```
```

---

### 3. Check Which Agents Are Affected

**Use the mapping document:**

```bash
# Open the mapping
cat .claude/RULE-TO-AGENT-MAPPING.md | grep -A 10 "^### error-handling.md"
```

**Output shows:**
```
### error-handling.md
**Summary:** Core error type usage...

**Used by:**
- client-engineer
- integration-engineer
- mapping-engineer
- operation-engineer
- security-auditor
- typescript-expert
```

---

### 4. Test Mode: Create a Test Branch

```bash
# Create test branch
git checkout -b test-rule-update-error-handling

# Make your changes
vim .claude/rules/error-handling.md

# Commit changes
git add .claude/rules/error-handling.md
git commit -m "test: add rate limit error pattern to error-handling.md

Testing new pattern with affected agents:
- client-engineer
- integration-engineer
- operation-engineer
- (3 more agents)

Will validate by creating test module."
```

---

### 5. Test the Rule Change

**Option A: Test with Existing Module**

If you have an existing module that uses the affected agents:

```bash
# Run operation that uses affected agents
/add-operation existing-module testOperation

# Watch for:
# 1. Do agents load the rule correctly?
# 2. Do they follow the new pattern?
# 3. Are there any conflicts?
```

**Option B: Create Test Module**

Create a minimal test module to validate the change:

```bash
# Create test module
/create-module test-vendor test-service

# This will invoke:
# - Many agents including some that use your changed rule
# - You can observe if they follow the new pattern

# After testing, you can delete:
rm -rf package/test-vendor/
```

**Option C: Targeted Agent Test**

If you want to test specific agents only:

```bash
# Create a test scenario that specifically uses the affected agents
# For error-handling.md, you'd want to test error handling code

# Example: Test with a module that has error handling
cd package/existing-module/
npm test  # Run tests to see if new error pattern is used correctly
```

---

### 6. Validate Agent Behavior

**Check if agents:**
1. **Load the rule** - Agents should reference `@.claude/rules/error-handling.md`
2. **Follow the rule** - Generated code should use the new pattern
3. **Pass validation** - Gates should still pass with new pattern

**What to look for:**

```bash
# Check if new pattern is used in generated code
grep -r "RateLimitExceededError" package/test-vendor/test-service/src/

# Check if tests pass
cd package/test-vendor/test-service/
npm test

# Check if build passes
npm run build
```

---

### 7. Verify No Breaking Changes

**Critical checks:**

```bash
# 1. All existing tests still pass
npm test

# 2. Build still succeeds
npm run build

# 3. Existing modules not affected negatively
# (if you changed a widely-used rule)
cd package/existing-module/ && npm test && npm run build
```

**If something breaks:**
- Review your rule change
- Check if it conflicts with other rules
- See if agents are interpreting it differently than intended

---

### 8. Update Documentation (Optional)

If your rule change is significant:

**Update RULE-TO-AGENT-MAPPING.md summary:**

```bash
vim .claude/RULE-TO-AGENT-MAPPING.md

# Find your rule and update the summary line
# Before: "**Summary:** Core error type usage, constructor patterns..."
# After:  "**Summary:** Core error type usage, rate limit patterns, constructor patterns..."
```

---

### 9. Commit and Merge (When Satisfied)

```bash
# If test passed, commit final version
git add .claude/rules/error-handling.md
git add .claude/RULE-TO-AGENT-MAPPING.md  # if updated
git commit -m "feat: add rate limit error handling pattern

Added RateLimitExceededError usage pattern to error-handling.md.

Validated with:
- Test module creation: ✅
- Affected agents: 6 agents tested
- Existing modules: No breaking changes
- All tests passing: ✅

Agents affected:
- client-engineer
- integration-engineer
- operation-engineer
- mapping-engineer
- security-auditor
- typescript-expert"

# Merge back to main branch
git checkout new-claude-approach
git merge test-rule-update-error-handling
git branch -d test-rule-update-error-handling

# Push
git push origin new-claude-approach
```

---

## Common Rule Update Scenarios

### Scenario 1: Add New Rule to Existing File

**Example:** Add a new validation rule to api-spec-core-rules.md

```markdown
1. Open `.claude/rules/api-spec-core-rules.md`
2. Add new rule at the end (or in appropriate section)
3. Number it sequentially (e.g., Rule 11 if last was 10)
4. Update any agents that should enforce this rule
5. Test with module creation
```

### Scenario 2: Create Entirely New Rule File

**Example:** Create rules for async/streaming operations

```bash
# 1. Create new rule file
vim .claude/rules/async-operations.md

# 2. Write rules following existing format:
# - Clear headings
# - Numbered rules (if applicable)
# - Examples
# - Rationale

# 3. Decide which agents need this rule
# (e.g., operation-engineer, integration-engineer)

# 4. Update agents to reference the new rule
vim .claude/agents/operation-engineer.md

# Add to "Rules" section:
# - @.claude/rules/async-operations.md

# 5. Update mapping document
vim .claude/RULE-TO-AGENT-MAPPING.md

# Add new section for async-operations.md

# 6. Test with streaming operation
/add-operation test-module streamData
```

### Scenario 3: Modify Existing Critical Rule

**Example:** Change a critical API spec rule

**⚠️ CAUTION:** Critical rules affect many agents!

```bash
# 1. Check blast radius
cat .claude/RULE-TO-AGENT-MAPPING.md | grep -A 15 "api-spec-core-rules.md"

# Shows: 7 agents affected - HIGH IMPACT

# 2. Create detailed test plan
# - Test module creation
# - Test operation addition
# - Test all 6 gates pass
# - Test existing modules still build

# 3. Make change incrementally
# Don't change multiple rules at once

# 4. Test thoroughly before merging
# Create 2-3 test modules to validate

# 5. Document breaking changes (if any)
# Update CLAUDE.md or create BREAKING-CHANGES.md if needed
```

### Scenario 4: Deprecate a Rule

**Example:** Remove outdated rule

```bash
# 1. Mark as deprecated first (don't delete immediately)
### Rule X: [Deprecated] Old Pattern

**Status:** DEPRECATED - Use Rule Y instead

**Rationale:** This pattern is no longer recommended...

# 2. Test that agents don't rely on it

# 3. After validation period (e.g., 1 week), remove it

# 4. Update any agents that referenced it
```

---

## Test Mode Checklist

Before merging any rule change:

- [ ] Rule file updated with clear, consistent format
- [ ] Identified all affected agents (use mapping document)
- [ ] Created test branch
- [ ] Tested with module creation OR specific scenario
- [ ] Validated agent behavior (they follow the new rule)
- [ ] No breaking changes to existing modules
- [ ] All tests pass
- [ ] Build succeeds
- [ ] Documentation updated (if needed)
- [ ] Clear commit message explaining change
- [ ] Blast radius understood and acceptable

---

## Quick Commands Reference

```bash
# View rule file
cat .claude/rules/[rule-name].md

# See which agents use a rule
grep -l "@.claude/rules/[rule-name]" .claude/agents/*.md

# See all rules an agent uses
grep "@.claude/rules/" .claude/agents/[agent-name].md

# View mapping document
cat .claude/RULE-TO-AGENT-MAPPING.md

# Create test branch
git checkout -b test-rule-[rule-name]

# Create test module
/create-module test-vendor test-service

# Clean up test module
rm -rf package/test-vendor/

# Run tests
cd package/[module]/ && npm test

# Run build
cd package/[module]/ && npm run build
```

---

## Tips for Safe Rule Updates

1. **Start small** - Test one rule change at a time
2. **Test locally** - Always test before pushing
3. **Check blast radius** - Know which agents are affected
4. **Use test modules** - Create disposable test modules
5. **Document changes** - Clear commit messages
6. **Validate incrementally** - Don't change multiple critical rules at once
7. **Keep format consistent** - Follow existing rule formatting
8. **Update mapping** - Keep RULE-TO-AGENT-MAPPING.md current

---

## Emergency Rollback

If a rule change causes problems:

```bash
# Revert specific rule file
git checkout HEAD~1 -- .claude/rules/[rule-name].md

# Or revert entire commit
git revert [commit-hash]

# Or checkout previous version
git show HEAD~1:.claude/rules/[rule-name].md > .claude/rules/[rule-name].md

# Commit rollback
git add .claude/rules/[rule-name].md
git commit -m "revert: rollback rule change to [rule-name]

Reason: [explain issue]"
```

---

## Getting Help

**If you're unsure about a rule change:**

1. Check `.claude/RULE-TO-AGENT-MAPPING.md` for impact
2. Look at how other similar rules are formatted
3. Test thoroughly before merging
4. Start with non-critical rules to gain confidence

**Questions to ask:**
- Does this conflict with existing rules?
- Which agents will this affect?
- Is this a breaking change?
- Can I test this safely?

---

**Last Updated:** 2025-10-13
**Related Documents:**
- `.claude/RULE-TO-AGENT-MAPPING.md` - Rule usage mapping
- `.claude/ARCHIVE-ACCESS.md` - Access old system if needed
- `/CLAUDE.md` - System overview
