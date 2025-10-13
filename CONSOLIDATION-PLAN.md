# 🚨 CRITICAL: Rule Consolidation Plan

**Destination**: `.claude/` (active system)
**Sources**: `claude/` + `.claude/`
**Goal**: Zero rule loss, complete consolidation

---

## 📊 Analysis Summary

### Files Comparison

| Category | `claude/` | `.claude/` | Status |
|----------|-----------|------------|--------|
| **Total Files** | 11 | 28 | `.claude/` has more files |
| **Total Lines** | 5,088 | 6,693 | `.claude/` has 1,605 more lines |
| **Identical** | 8 files | 8 files | ✅ Can keep |
| **Different** | 1 file | 1 file | ⚠️ Minor fix needed |
| **Unique** | 2 files | 15+ files | ⚠️ Need to merge |

---

## ✅ Files That Are IDENTICAL (Keep .claude/ version)

These 8 files are byte-for-byte identical:
1. `implementation.md` (1,161 lines) ✅
2. `testing.md` (924 lines) ✅
3. `type-mapping.md` (420 lines) ✅
4. `error-handling.md` (365 lines) ✅
5. `build-quality.md` (142 lines) ✅
6. `security.md` (114 lines) ✅
7. `documentation.md` (110 lines) ✅

**Action**: Keep `.claude/` versions as-is. Delete `claude/` versions after consolidation.

---

## ⚠️ File With Trivial Difference

### `prerequisites.md` (348 lines)
**Difference**: One line reference (`claude/` vs `.claude/`)

```diff
< ├── claude/                      # This documentation
> ├── .claude/                    # Agent workspace (active system)
```

**Action**: Keep `.claude/` version (already has correct reference).

---

## 🚨 CRITICAL: Files ONLY in `claude/` - MUST PRESERVE

### 1. `production-readiness.md` (152 lines)
**Content**: Release readiness checklist
- Security requirements
- Code quality gates
- Documentation requirements
- Validation commands
- Sign-off criteria

**Action**: **COPY to `.claude/rules/production-readiness.md`**

### 2. `git-workflow.md` (145 lines)
**Content**: Git workflow and commit standards
- Never push to remote (critical!)
- Conventional commits format
- Commit strategy and examples
- Branch management
- Recovery procedures

**Action**: **COPY to `.claude/rules/git-workflow.md`**

---

## 🔍 CRITICAL INVESTIGATION: API Specification Split

### The Problem
- **Original** `claude/rules/api-specification.md`: **1,207 lines**
- **Split** `.claude/rules/api-spec-*.md` (4 files): **1,014 lines**
- **MISSING**: **193 lines**

### Split Files
1. `api-spec-core-rules.md` - 206 lines (Rules 1-10)
2. `api-spec-operations.md` - 262 lines (Rules 11-13)
3. `api-spec-schemas.md` - 277 lines (Rules 14-24)
4. `api-spec-standards.md` - 269 lines (Standards)

### Investigation Needed
**BEFORE CONSOLIDATION, we must:**
1. Compare original api-specification.md (24 rules) with split files
2. Identify what 193 lines contain
3. Verify if it's:
   - Whitespace/formatting difference
   - Duplicate content that was deduplicated
   - **ACTUAL MISSING CONTENT** ⚠️

**Action**: **DEEP COMPARE** required before proceeding.

---

## 📁 Files ONLY in `.claude/` - New Content

These files exist only in `.claude/` and should be kept:

### Orchestration Files (New - Created Today)
1. `orchestration-protocol.md` (21 lines) ✅
2. `agent-invocation.md` (38 lines) ✅
3. `memory-management.md` (49 lines) ✅
4. `workflow-execution.md` (46 lines) ✅
5. `repository-structure.md` (39 lines) ✅

### Gate Files
1. `gate-1-api-spec.md` (63 lines)
2. `gate-2-type-generation.md` (26 lines)
3. `gate-3-implementation.md` (127 lines)
4. `gate-4-test-creation.md` (55 lines)
5. `gate-5-test-execution.md` (22 lines)
6. `gate-6-build.md` (33 lines)

### Other Files
1. `agent-parameter-passing.md` (265 lines)
2. `output-file-naming.md` (377 lines)
3. `execution-protocol.md` (253 lines)
4. `completion-checklist.md` (270 lines)
5. `connection-profile-design.md` (411 lines)

**Action**: **KEEP ALL** - These are newer additions.

---

## 📋 STEP-BY-STEP CONSOLIDATION PLAN

### Phase 1: Investigation (DO THIS FIRST)
1. **Deep compare** `claude/rules/api-specification.md` with split files
2. **Identify missing 193 lines**
3. **Document findings**
4. **Get user approval** before proceeding

### Phase 2: Preserve Unique Content
1. **Copy** `claude/rules/production-readiness.md` → `.claude/rules/`
2. **Copy** `claude/rules/git-workflow.md` → `.claude/rules/`
3. **Verify** both files copied successfully

### Phase 3: Resolve API Specification (After Investigation)
**Option A**: If 193 lines are just formatting
- Keep split files as-is

**Option B**: If 193 lines contain unique rules
- **Merge missing content** into split files
- **Document** what was added

**Option C**: If split is incomplete
- **Replace split files** with original `api-specification.md`
- Or properly re-split with all content

### Phase 4: Verification
1. **Count total lines** in `.claude/rules/` after merge
2. **Should be**: 6,693 (current) + 152 (prod-readiness) + 145 (git-workflow) + [missing API spec content]
3. **Target**: >= 6,990 lines minimum (no content loss)

### Phase 5: Cleanup
1. **Archive** `claude/` directory (don't delete yet!)
2. **Move** to `claude.backup/` or `claude.old/`
3. **Test** with one workflow to ensure nothing breaks
4. **Delete backup** only after successful operation

---

## ✅ SUCCESS CRITERIA

Consolidation is successful when:
- ✅ All 8 identical files preserved in `.claude/`
- ✅ `production-readiness.md` exists in `.claude/rules/`
- ✅ `git-workflow.md` exists in `.claude/rules/`
- ✅ API specification content is COMPLETE (no missing rules)
- ✅ Total line count in `.claude/rules/` >= 6,990
- ✅ All existing functionality still works
- ✅ `claude/` archived as backup

---

## 🚨 CRITICAL NEXT STEP

**STOP and INVESTIGATE FIRST**

Before executing any consolidation:
1. Read and compare API specification files thoroughly
2. Document what the 193 missing lines contain
3. Get explicit approval for how to handle them

**DO NOT PROCEED** with consolidation until API spec investigation is complete.

---

## 📞 User Decision Points

1. **API Specification Split**: Keep split or revert to original?
2. **Gate Files**: Keep as separate files or merge into one?
3. **Backup Strategy**: Where to archive `claude/` directory?

---

**Created**: {{DATE}}
**Status**: Awaiting investigation and user approval
**Risk Level**: 🔴 HIGH (potential content loss if not careful)
