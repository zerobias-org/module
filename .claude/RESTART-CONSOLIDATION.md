# Restart Guide: Agent Consolidation Project

## 🎯 Quick Start - Copy This Prompt

```
I'm continuing the agent consolidation project. Current status:

PHASE 4: 20 of 30 agents updated (67% complete) - CRITICAL PATH COMPLETE
- All critical agents (gates, build, API, operations, testing) have rule references
- 10 lower-priority support agents remain

CONTEXT FILES TO READ:
1. .claude/RESTART-CONSOLIDATION.md (this file) - Full context
2. .claude/.consolidation-status.json - Status tracking
3. .claude/PHASE-4-PROGRESS.md - Detailed progress

TASK: Continue Phase 4 by updating the remaining 10 agents with rule references using the @.claude/rules/ notation pattern.

Please read the restart guide and confirm you understand the status and pattern to use.
```

---

## 📊 Project Status Summary

### ✅ What's Complete

**Phase 1-3: Rule Extraction (100% COMPLETE)**
- Created 11 new rule files in `.claude/rules/`
- Extracted all rules from legacy `claude/` documentation
- Established clean separation: rules IN files, agents REFERENCE them

**Phase 4: Agent Rule Mapping (67% COMPLETE - CRITICAL PATH DONE)**
- Updated 20 of 30 agents
- ALL critical functionality agents complete
- Remaining 10 are support/review roles

### 📋 Remaining Work

**10 Lower-Priority Agents to Update:**

1. **ut-reviewer** - Unit test review
2. **it-reviewer** - Integration test review
3. **test-structure-validator** - Test structure validation
4. **style-reviewer** - Code style review
5. **connection-profile-guardian** - Connection profile management
6. **module-scaffolder** - Module scaffolding
7. **product-specialist** - Product research
8. **operation-analyst** - Operation analysis
9. **credential-manager** - Credential management
10. **documentation-writer** - Documentation creation

**Estimated Time:** 45-60 minutes to complete all 10

---

## 🎨 The Pattern - Copy for Each Agent

### Standard Update Pattern

Find the agent's existing rules section (usually "## Rules They Enforce" or similar) and replace with:

```markdown
## Rules to Load

**Critical/Primary Rules:**
- @.claude/rules/[primary-rule].md - [Brief description]
- @.claude/rules/[another-primary].md - [Brief description]

**Supporting Rules:**
- @.claude/rules/[supporting-rule].md - [Brief description]

**Key Principles:**
- [Keep existing 3-5 brief bullet points from original]
- [These are summaries, NOT full rules]
```

### Rule Assignment Guide

**For Test Reviewers (ut-reviewer, it-reviewer, test-structure-validator):**
```markdown
**Primary Rules:**
- @.claude/rules/testing.md - All testing patterns and requirements
- @.claude/rules/gate-4-test-creation.md (for UT) or gate-5-test-execution.md (for IT)

**Supporting Rules:**
- @.claude/rules/failure-conditions.md - Test-related failures
```

**For style-reviewer:**
```markdown
**Primary Rules:**
- @.claude/rules/implementation.md - Code style and patterns sections
- @.claude/rules/build-quality.md - Quality standards

**Supporting Rules:**
- @.claude/rules/failure-conditions.md - Style violations
```

**For connection-profile-guardian:**
```markdown
**Critical Rules:**
- @.claude/rules/connection-profile-design.md - Connection profile patterns (CRITICAL - core responsibility)
- @.claude/rules/security.md - Secure credential handling

**Supporting Rules:**
- @.claude/rules/implementation.md - Connection implementation patterns
```

**For module-scaffolder:**
```markdown
**Primary Rules:**
- @.claude/rules/tool-requirements.md - All required tools and commands
- @.claude/rules/prerequisites.md - Setup requirements

**Supporting Rules:**
- @.claude/rules/execution-protocol.md - Workflow execution
```

**For product-specialist:**
```markdown
**Primary Rules:**
- @.claude/rules/prerequisites.md - Research requirements
- @.claude/rules/api-spec-core-rules.md - API pattern understanding

**Supporting Rules:**
- @.claude/rules/tool-requirements.md - Research tools
```

**For operation-analyst:**
```markdown
**Primary Rules:**
- @.claude/rules/api-spec-operations.md - Operation patterns and priorities
- @.claude/rules/prerequisites.md - Analysis requirements

**Supporting Rules:**
- @.claude/rules/api-spec-core-rules.md - API fundamentals
```

**For credential-manager:**
```markdown
**Primary Rules:**
- @.claude/rules/security.md - Credential security patterns (CRITICAL)
- @.claude/rules/connection-profile-design.md - Credential storage patterns

**Supporting Rules:**
- @.claude/rules/execution-protocol.md - Workflow integration
- @.claude/rules/tool-requirements.md - Testing tools
```

**For documentation-writer:**
```markdown
**Primary Rules:**
- @.claude/rules/documentation.md - All documentation standards

**Supporting Rules:**
- @.claude/rules/implementation.md - Understanding code to document
```

---

## 📁 File Paths

### Agents to Update (10 files)
```bash
.claude/agents/ut-reviewer.md
.claude/agents/it-reviewer.md
.claude/agents/test-structure-validator.md
.claude/agents/style-reviewer.md
.claude/agents/connection-profile-guardian.md
.claude/agents/module-scaffolder.md
.claude/agents/product-specialist.md
.claude/agents/operation-analyst.md
.claude/agents/credential-manager.md
.claude/agents/documentation-writer.md
```

### Tracking Files to Update
```bash
.claude/.consolidation-status.json
.claude/PHASE-4-PROGRESS.md
```

---

## ✅ 20 Agents Already Complete

**CRITICAL (5):**
1. gate-controller - All 6 gates + validation + failure-conditions
2. build-reviewer - gate-6 + build-quality + tool-requirements
3. api-architect - api-specification-critical-12-rules + gate-1
4. operation-engineer - mapper-field-validation + gate-3
5. mapping-engineer - mapper-field-validation + gate-3

**HIGH PRIORITY (5):**
6. testing-specialist - testing + gates 4-5
7. test-orchestrator - gates 4-5 + testing + execution-protocol
8. typescript-expert - implementation + type-mapping + gate-2
9. integration-engineer - implementation + error-handling + gate-3
10. build-validator - gates 6+2 + build-quality + tool-requirements

**NEXT PRIORITY (5):**
11. api-reviewer - api-specification-critical-12-rules + gate-1
12. api-researcher - prerequisites + tool-requirements
13. schema-specialist - api-spec-schemas + api-specification-critical-12-rules
14. client-engineer - implementation + connection-profile-design + gate-3
15. security-auditor - security + connection-profile-design

**TESTING SPECIALISTS (5):**
16. producer-ut-engineer - testing + gate-4
17. producer-it-engineer - testing + gate-5
18. connection-ut-engineer - testing + gate-4
19. connection-it-engineer - testing + gate-5 + security
20. mock-specialist - testing (Rule #3: ONLY nock) + gate-4

---

## 📚 Available Rule Files

All rules are in `.claude/rules/`:

**Gate Validation Rules:**
- `gate-1-api-spec.md` - API specification validation
- `gate-2-type-generation.md` - Type generation validation
- `gate-3-implementation.md` - Implementation validation
- `gate-4-test-creation.md` - Test creation validation
- `gate-5-test-execution.md` - Test execution validation
- `gate-6-build.md` - Build validation

**Core Rules:**
- `api-specification-critical-12-rules.md` - The 12 CRITICAL API rules
- `api-spec-core-rules.md` - API rules 1-10 (foundation)
- `api-spec-operations.md` - API rules 11-13 (operations)
- `api-spec-schemas.md` - API rules 14-24 (schemas)
- `api-spec-standards.md` - API standards and validation
- `implementation.md` - TypeScript implementation patterns
- `testing.md` - All testing requirements
- `error-handling.md` - Error type patterns
- `type-mapping.md` - Core type usage
- `security.md` - Security patterns
- `connection-profile-design.md` - Connection schema patterns
- `documentation.md` - Documentation standards
- `build-quality.md` - Build requirements
- `tool-requirements.md` - Required tools and commands
- `prerequisites.md` - Setup requirements
- `execution-protocol.md` - Workflow execution
- `failure-conditions.md` - All failure conditions
- `validation-script.md` - Complete validation script
- `mapper-field-validation.md` - Field mapping validation
- `production-readiness.md` - Release readiness
- `git-workflow.md` - Git workflow standards

---

## 🔄 After Completing Phase 4

When all 30 agents have rule references, move to **Phase 5: Verification & Archive**

### Phase 5 Steps:

1. **Verify Zero Content Loss**
   - Compare `claude/` and `.claude/` directories
   - Ensure all rules extracted to `.claude/rules/`
   - Confirm all agents have appropriate references

2. **Validation Checks**
   ```bash
   # All agents reference rules
   grep -r "@.claude/rules/" .claude/agents/

   # No rule duplication
   # (Rules should be in .claude/rules/, not repeated in agents)

   # All 11 rule files exist
   ls .claude/rules/*.md | wc -l  # Should be 21+
   ```

3. **Archive Legacy System**
   ```bash
   # Create backup
   mv claude claude.backup-2025-10-13

   # Update CLAUDE.md if needed to remove references to claude/
   ```

4. **Test Complete Workflow**
   - Run one operation through complete system
   - Verify agents load rules correctly
   - Ensure no broken references

5. **Create Completion Report**
   - Document consolidation results
   - List all changes made
   - Archive tracking files

---

## 🎯 Success Criteria

### Must Have (Already Achieved)
✅ Rules are IN rule files (`.claude/rules/*.md`)
✅ Agents REFERENCE rules (via `@.claude/rules/` notation)
✅ No rule content duplication between files
✅ All critical agents updated (20/30 done)
✅ Consistent pattern across all updated agents

### To Complete
- [ ] Update remaining 10 agents with rule references
- [ ] Update progress tracking files
- [ ] Verify zero content loss
- [ ] Archive legacy `claude/` directory
- [ ] Test complete workflow
- [ ] Create completion report

---

## 📝 Update Workflow (For Each Agent)

1. **Read agent file**
   ```bash
   Read .claude/agents/[agent-name].md
   ```

2. **Find rules section** (usually "## Rules They Enforce")

3. **Replace with new format** using pattern above

4. **Keep brief principles** (3-5 bullets, NOT full rules)

5. **Update progress** after each batch:
   ```bash
   Edit .claude/PHASE-4-PROGRESS.md
   Edit .claude/.consolidation-status.json
   ```

---

## 🚨 Critical Principles

**DO:**
- ✅ Put rules IN `.claude/rules/*.md` files
- ✅ Have agents REFERENCE rules with `@.claude/rules/` notation
- ✅ Keep "Key Principles" brief (3-5 bullets)
- ✅ Use consistent pattern across all agents

**DON'T:**
- ❌ Duplicate full rule content in agent files
- ❌ Create new rule files (use existing 21 rule files)
- ❌ Change agent personalities or expertise
- ❌ Remove existing working patterns from agents

---

## 📞 Context Files

**Primary Documentation:**
- `.claude/RESTART-CONSOLIDATION.md` (this file)
- `.claude/.consolidation-status.json` - Machine-readable status
- `.claude/PHASE-4-PROGRESS.md` - Detailed progress log
- `.claude/AGENT-RULE-MAPPING.md` - Complete mapping matrix

**Rule Files:**
- `.claude/rules/*.md` - All 21 rule files

**Agent Files:**
- `.claude/agents/*.md` - All 30 agent definitions

---

## 🎬 Ready to Continue

**Your restart prompt is at the top of this file.**

Copy the "🎯 Quick Start" prompt into a fresh conversation and you'll have everything needed to complete the consolidation.

**Estimated completion time:** 45-60 minutes for remaining 10 agents + Phase 5 verification.

---

**Last Updated:** 2025-10-13
**Status:** Phase 4 Critical Path Complete (20/30 agents)
**Next:** Complete final 10 agents, then Phase 5 verification
