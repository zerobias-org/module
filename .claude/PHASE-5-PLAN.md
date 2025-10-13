# Phase 5: Verification & Archive - Execution Plan

## Objective

Systematically verify that all rules from the legacy `claude/` directory have been properly extracted to `.claude/rules/`, then safely archive the legacy system.

## Approach: File-by-File Verification

For each file in `claude/`, we will:
1. Read the file completely
2. Identify all rules, requirements, and critical information
3. Verify each piece exists in `.claude/rules/*.md`
4. Document where each rule is now located
5. Mark file as verified and ready for archive
6. Only after verification, archive the file

## Phase 5 Structure

### Stage 1: Discovery & Inventory
**Goal:** Understand what files exist in `claude/` directory

**Tasks:**
- List all files in `claude/` directory recursively
- Categorize files by type (agents, rules, tasks, workflows, meta-docs)
- Create inventory checklist
- Estimate verification effort

**Output:** Complete file inventory with categories

### Stage 2: File-by-File Verification
**Goal:** Verify zero content loss for each file

**Process for Each File:**
1. **Read Legacy File**
   - Read complete contents
   - Extract all rules (numbered, bulleted, principles)
   - Extract all requirements, patterns, standards
   - Note any unique information

2. **Locate in New System**
   - Search `.claude/rules/*.md` for each rule
   - Search `.claude/agents/*.md` for references
   - Verify exact match or semantic equivalent
   - Document location mapping

3. **Verification Checklist**
   - [ ] All numbered rules located
   - [ ] All principles/patterns located
   - [ ] All examples preserved (if critical)
   - [ ] No unique information lost
   - [ ] Agent references correct

4. **Document Results**
   - Create verification record
   - Note any discrepancies
   - Flag any content not found
   - Mark file as verified or needs-attention

**Output:** Verification record for each file

### Stage 3: Gap Analysis
**Goal:** Identify any content not yet extracted

**Tasks:**
- Review all "needs-attention" flags
- Identify missing rules or content
- Decide: extract now or document as intentionally excluded
- Create extraction tasks if needed
- Re-verify after extraction

**Output:** Complete gap analysis report

### Stage 4: Archive Execution
**Goal:** Safely archive verified legacy system

**Tasks:**
- Verify all files marked as "verified"
- Create archive directory: `claude.backup-2025-10-13/`
- Move entire `claude/` directory to archive
- Verify archive is complete
- Test that system works without `claude/`
- Document archive location

**Output:** Archived legacy system, clean `.claude/` only

### Stage 5: Validation & Testing
**Goal:** Confirm new system works correctly

**Tasks:**
- Verify all 30 agents load without errors
- Check all rule references resolve
- Run validation script
- Test agent invocation
- Document any issues

**Output:** System validation report

### Stage 6: Completion Documentation
**Goal:** Document the consolidation project

**Tasks:**
- Create completion summary
- Document what was consolidated
- List all rule files created
- List all agents updated
- Archive tracking files
- Create final report

**Output:** CONSOLIDATION-COMPLETE.md report

## File Categories to Verify

Based on known structure:

### Category 1: Agent Definitions
**Files:** `claude/agents/*.md`
**Strategy:**
- These may have rules embedded in descriptions
- Cross-reference with `.claude/agents/*.md`
- Rules should now be in `.claude/rules/`
- Agent personalities/expertise can stay in `.claude/agents/`

### Category 2: Rule Documents
**Files:** `claude/create/*.md`, `claude/orchestration/*.md`, `claude/workflow/*.md`
**Strategy:**
- These are pure rule content
- Should be fully extracted to `.claude/rules/`
- Verify each rule has a home in new system

### Category 3: Workflow Templates
**Files:** `claude/orchestration/workflow-templates/*.md`
**Strategy:**
- Workflow logic belongs in `.claude/commands/`
- Rules belong in `.claude/rules/`
- Verify workflows updated to reference new locations

### Category 4: Meta Documentation
**Files:** `claude/agents/AGENTS.md`, `claude/orchestration/*.md`
**Strategy:**
- Organizational documentation
- May contain rules disguised as process description
- Extract any rules, then archive

### Category 5: Task Files
**Files:** `claude/workflow/tasks/*.md`
**Strategy:**
- May contain embedded rules
- Workflow logic should be in `.claude/commands/`
- Rules should be in `.claude/rules/`

## Verification Checklist Template

For each file:

```markdown
## File: claude/path/to/file.md

### Rules Identified
1. Rule: [Description]
   - Located in: `.claude/rules/[file].md` line [X]
   - Status: ✅ Verified | ⚠️ Partial | ❌ Missing

2. Rule: [Description]
   - Located in: `.claude/rules/[file].md` line [X]
   - Status: ✅ Verified

### Key Principles/Patterns
- Pattern: [Description]
  - Located in: `.claude/rules/[file].md` section [X]
  - Status: ✅ Verified

### Unique Content
- [Any unique information not found elsewhere]
  - Action: Extract to [location] | Document as excluded | Already handled

### Verification Result
- [ ] All rules accounted for
- [ ] All patterns preserved
- [ ] No unique content lost
- [ ] Ready for archive

**Overall Status:** ✅ VERIFIED | ⚠️ NEEDS ATTENTION | ❌ BLOCKED
```

## Success Criteria

**Must Have:**
- [ ] Every file in `claude/` has verification record
- [ ] All rules located in `.claude/rules/`
- [ ] Zero content loss documented
- [ ] All gaps either filled or documented as intentional
- [ ] `claude/` directory safely archived
- [ ] System works without `claude/` directory

**Quality Metrics:**
- 100% file verification coverage
- Zero missing rules (all extracted or documented)
- Complete audit trail
- Reversible archive process

## Risk Mitigation

**Backup Strategy:**
- Git tracks all changes
- Archive preserves original structure
- Verification records document mappings
- Can restore from archive if needed

**Validation Strategy:**
- Verify before archive
- Test after archive
- Document all changes
- Keep detailed records

## Estimated Effort

Based on file count (to be determined in Stage 1):
- **Stage 1 (Discovery):** 15-20 minutes
- **Stage 2 (Verification):** 2-4 hours (depends on file count)
- **Stage 3 (Gap Analysis):** 30-60 minutes
- **Stage 4 (Archive):** 15-20 minutes
- **Stage 5 (Validation):** 20-30 minutes
- **Stage 6 (Documentation):** 30-45 minutes

**Total:** 4-6 hours for thorough verification

## Tools & Commands

### Discovery
```bash
# List all files in claude/
find claude -type f -name "*.md" | sort

# Count files by directory
find claude -type f -name "*.md" | xargs dirname | sort | uniq -c

# Get total line count
find claude -type f -name "*.md" | xargs wc -l
```

### Verification
```bash
# Search for rule in new system
grep -r "Rule #[0-9]" .claude/rules/

# Find references to specific patterns
grep -r "Pattern: " .claude/rules/

# Check agent references
grep -r "@.claude/rules/" .claude/agents/
```

### Archive
```bash
# Create archive
mv claude claude.backup-2025-10-13

# Verify archive
ls -la claude.backup-2025-10-13/

# Check size
du -sh claude.backup-2025-10-13/
```

## Output Files

This phase will create:
- `.claude/PHASE-5-VERIFICATION-LOG.md` - Detailed verification records
- `.claude/PHASE-5-GAP-ANALYSIS.md` - Any gaps found
- `.claude/CONSOLIDATION-COMPLETE.md` - Final completion report
- `claude.backup-2025-10-13/` - Archived legacy directory

## Next Phase

After Phase 5 completion:
- System is fully consolidated
- Only `.claude/` directory contains orchestration files
- All rules in `.claude/rules/*.md`
- All agents in `.claude/agents/*.md`
- All workflows in `.claude/commands/*.md`
- Legacy system safely archived

---

**Created:** 2025-10-13
**Status:** Ready to Execute
**Prerequisite:** Phase 4 Complete ✅
