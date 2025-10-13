# Phase 5 Restart Prompt

## 🎯 Copy This Prompt for Clean Context

```
I'm continuing the agent consolidation project - starting Phase 5: Verification & Archive.

CURRENT STATUS:
✅ Phase 1-3: Rule extraction complete (21 rule files created in .claude/rules/)
✅ Phase 4: All 30 agents updated with @.claude/rules/ references (100% complete)
🔄 Phase 5: File-by-file verification and archive (STARTING NOW)

CONTEXT FILES TO READ:
1. .claude/PHASE-5-PLAN.md - Complete execution plan
2. .claude/PHASE-5-RESTART.md (this file) - Quick context
3. .claude/.consolidation-status.json - Current status

MY PREFERRED APPROACH:
Go through each file in claude/ directory one by one:
- Identify all rules in the file
- Verify each rule exists in .claude/rules/*.md
- Document verification results
- Only archive after verification

TASK: Start Phase 5, Stage 1 - Create inventory of all files in claude/ directory.

Please read the plan and begin with discovery.
```

---

## Quick Context Summary

### What We've Done (Phases 1-4)

**Phase 1-3: Rule Extraction**
- Created 21 rule files in `.claude/rules/`
- Extracted all critical rules from legacy system
- Organized by topic (API, testing, security, gates, etc.)

**Phase 4: Agent Updates**
- Updated all 30 agents in `.claude/agents/`
- Changed from embedding rules to referencing them
- Pattern: `@.claude/rules/rule-name.md - Description`
- All agents now reference rules instead of duplicating them

### Current State

**New System (.claude/):**
- ✅ 30 agent files with rule references
- ✅ 21 rule files with extracted content
- ✅ Commands/workflows in place
- ✅ Consistent pattern throughout

**Legacy System (claude/):**
- ⏳ Still exists, not yet verified
- ⏳ May contain rules not yet extracted
- ⏳ Needs file-by-file verification
- ⏳ Will be archived after verification

### Phase 5 Goal

**Verify zero content loss, then archive safely.**

File-by-file process:
1. Read legacy file
2. Identify all rules/requirements
3. Find each in new system
4. Document verification
5. Mark as verified
6. Archive when all verified

### Why This Approach

- **Thorough:** Every rule accounted for
- **Safe:** No bulk operations without verification
- **Documented:** Complete audit trail
- **Reversible:** Can restore if needed
- **Quality-focused:** Ensures nothing lost

---

## Directory Structure

### Current State
```
/Users/ctamas/code/zborg/module/
├── .claude/                      # New system (active)
│   ├── agents/                   # 30 agents with rule references ✅
│   ├── rules/                    # 21 rule files ✅
│   ├── commands/                 # Workflows
│   ├── .consolidation-status.json
│   ├── PHASE-4-PROGRESS.md
│   ├── PHASE-5-PLAN.md          # Detailed plan
│   └── PHASE-5-RESTART.md       # This file
│
└── claude/                       # Legacy system (to verify & archive)
    ├── agents/
    ├── create/
    ├── orchestration/
    └── workflow/
```

### After Phase 5
```
/Users/ctamas/code/zborg/module/
├── .claude/                      # Only active system
│   ├── agents/
│   ├── rules/
│   ├── commands/
│   └── CONSOLIDATION-COMPLETE.md
│
└── claude.backup-2025-10-13/    # Archived legacy
    └── [entire legacy system]
```

---

## Stage 1: Discovery (First Task)

**Objective:** Create complete inventory of files in `claude/`

**Commands to Run:**
```bash
# List all markdown files
find claude -type f -name "*.md" | sort

# Count by directory
find claude -type f -name "*.md" | xargs dirname | sort | uniq -c

# Total line count
find claude -type f -name "*.md" | xargs wc -l | tail -1
```

**Expected Output:**
- Complete list of files to verify
- File count by category
- Total lines of content

**Next Step:** After inventory, begin Stage 2 (file-by-file verification)

---

## Available Rule Files (.claude/rules/)

All extracted rules are in these 21 files:

**Gate Rules (6):**
- gate-1-api-spec.md
- gate-2-type-generation.md
- gate-3-implementation.md
- gate-4-test-creation.md
- gate-5-test-execution.md
- gate-6-build.md

**API Rules (5):**
- api-specification-critical-12-rules.md
- api-spec-core-rules.md
- api-spec-operations.md
- api-spec-schemas.md
- api-spec-standards.md

**Implementation Rules (5):**
- implementation.md
- testing.md
- error-handling.md
- type-mapping.md
- build-quality.md

**Security & Connection (2):**
- security.md
- connection-profile-design.md

**Workflow & Process (3):**
- prerequisites.md
- execution-protocol.md
- tool-requirements.md

**Validation (3):**
- failure-conditions.md
- validation-script.md
- mapper-field-validation.md

**Meta (3):**
- documentation.md
- production-readiness.md
- git-workflow.md

---

## File Categories in claude/

Based on known structure:

**Agents:** `claude/agents/*.md`
- May have embedded rules in descriptions
- Compare with `.claude/agents/*.md`

**Rules/Create:** `claude/create/*.md`
- Pure rule content
- Should be in `.claude/rules/*.md`

**Orchestration:** `claude/orchestration/*.md`
- Process and workflow rules
- Should be in `.claude/rules/execution-protocol.md` or `.claude/commands/`

**Workflows:** `claude/orchestration/workflow-templates/*.md`
- Workflow logic + embedded rules
- Workflows → `.claude/commands/`
- Rules → `.claude/rules/`

**Tasks:** `claude/workflow/tasks/*.md`
- Task-specific rules
- Should be extracted to appropriate rule files

---

## Verification Record Template

For each file, create this record:

```markdown
## File: claude/path/to/file.md
**Category:** [agents/rules/workflow/meta]
**Lines:** [count]

### Rules Found
1. [Rule description]
   - ✅ Located: .claude/rules/[file].md:[line]
2. [Rule description]
   - ✅ Located: .claude/rules/[file].md:[line]

### Unique Content
- [Any content not found elsewhere]
  - Action: [Extract/Document/Already handled]

### Result: ✅ VERIFIED | ⚠️ NEEDS ATTENTION | ❌ MISSING CONTENT
```

---

## Success Criteria

**Stage 1 (Discovery):**
- [ ] Complete file inventory created
- [ ] Files categorized
- [ ] Effort estimated

**Stage 2 (Verification):**
- [ ] Every file has verification record
- [ ] All rules located in new system
- [ ] Zero content loss documented

**Stage 3 (Gap Analysis):**
- [ ] All gaps identified
- [ ] Extraction plan for missing content (if any)

**Stage 4 (Archive):**
- [ ] Legacy system archived
- [ ] Archive verified complete

**Stage 5 (Validation):**
- [ ] System works without claude/
- [ ] All agents load correctly

**Stage 6 (Documentation):**
- [ ] Completion report created
- [ ] Project documented

---

## Time Estimate

- **Stage 1:** 15-20 minutes
- **Stage 2:** 2-4 hours (file-by-file)
- **Stage 3:** 30-60 minutes
- **Stage 4:** 15-20 minutes
- **Stage 5:** 20-30 minutes
- **Stage 6:** 30-45 minutes

**Total:** 4-6 hours for thorough verification

---

## Important Notes

**Quality Over Speed:**
- This is final verification - be thorough
- Document everything
- Don't archive until verified
- Flag anything unclear

**What to Look For:**
- Numbered rules (Rule #1, Rule #2, etc.)
- Bulleted requirements
- "MUST", "NEVER", "ALWAYS" statements
- Critical patterns or standards
- Validation criteria
- Failure conditions

**What Can Be Skipped:**
- Duplicate content already verified
- Example code (unless it's a critical pattern)
- General descriptions (if rule extracted)
- Process documentation (if in execution-protocol.md)

---

## Ready to Start

Copy the prompt at the top of this file into a new conversation to begin Phase 5 with full context.

All preparation is complete - just need to execute the file-by-file verification.

---

**Created:** 2025-10-13
**Purpose:** Clean context restart for Phase 5
**Prerequisites:** Phase 4 complete ✅
