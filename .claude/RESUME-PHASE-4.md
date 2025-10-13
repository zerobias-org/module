# Resume Phase 4: Agent Rule Mapping

## Quick Start Command
```
Continue Phase 4 agent consolidation - 20 of 30 agents complete, 10 remaining
```

## Current Status: 67% Complete (20/30 Agents)

**Context at Compact:** 86% (172,000 / 200,000 tokens)
**Completed:** All CRITICAL and HIGH PRIORITY agents
**Remaining:** 10 lower-priority agents

## What Was Accomplished

### Phase 1-3 Summary (Completed)
- ✅ Created 11 new rule files in `.claude/rules/`
- ✅ Extracted critical rules from all documentation
- ✅ Established @.claude/rules/ reference notation

### Phase 4 Progress (20 agents updated)

**Pattern Used for All Agents:**
```markdown
## Rules to Load

**Critical/Primary Rules:**
- @.claude/rules/rule-name.md - Description

**Supporting Rules:**
- @.claude/rules/another-rule.md - Description

**Key Principles:**
- (Brief 3-5 bullet points - NOT full rules)
```

**✅ CRITICAL Agents (5/5) - ALL DONE:**
1. gate-controller - All 6 gate files + validation + failure-conditions
2. build-reviewer - gate-6 + build-quality + tool-requirements
3. api-architect - api-specification-critical-12-rules + gate-1
4. operation-engineer - mapper-field-validation + gate-3
5. mapping-engineer - mapper-field-validation (CRITICAL) + gate-3

**✅ HIGH PRIORITY Agents (5/5) - ALL DONE:**
6. testing-specialist - testing + gates 4-5
7. test-orchestrator - gates 4-5 (CRITICAL) + testing
8. typescript-expert - implementation + type-mapping + gate-2
9. integration-engineer - implementation + error-handling + gate-3
10. build-validator - gates 6+2 (CRITICAL) + build-quality

**✅ NEXT PRIORITY Agents (5/5) - ALL DONE:**
11. api-reviewer - api-specification-critical-12-rules + gate-1 (CRITICAL)
12. api-researcher - prerequisites + tool-requirements
13. schema-specialist - api-spec-schemas (CRITICAL) + 12-rules
14. client-engineer - implementation + connection-profile-design (CRITICAL)
15. security-auditor - security (CRITICAL) + connection-profile-design

**✅ MEDIUM PRIORITY Testing Agents (5/5) - ALL DONE:**
16. producer-ut-engineer - testing + gate-4
17. producer-it-engineer - testing + gate-5 (Rule #7: NO hardcoded)
18. connection-ut-engineer - testing + gate-4
19. connection-it-engineer - testing + gate-5 + security
20. mock-specialist - testing (CRITICAL Rule #3: ONLY nock) + gate-4

## Remaining Agents (10) - All Lower Priority

### Test Review Agents (3)
21. **ut-reviewer**
    - Add: @.claude/rules/testing.md, gate-4, failure-conditions

22. **it-reviewer**
    - Add: @.claude/rules/testing.md, gate-5, failure-conditions

23. **test-structure-validator**
    - Add: @.claude/rules/testing.md, gate-4, build-quality

### Other Support Agents (7)
24. **style-reviewer**
    - Add: @.claude/rules/implementation.md (code style sections)

25. **connection-profile-guardian** (IMPORTANT)
    - Add: @.claude/rules/connection-profile-design.md (CRITICAL)
    - Add: @.claude/rules/security.md

26. **module-scaffolder**
    - Add: @.claude/rules/tool-requirements.md
    - Add: @.claude/rules/prerequisites.md

27. **product-specialist**
    - Add: @.claude/rules/prerequisites.md
    - Add: @.claude/rules/api-spec-core-rules.md

28. **operation-analyst**
    - Add: @.claude/rules/api-spec-operations.md
    - Add: @.claude/rules/prerequisites.md

29. **credential-manager**
    - Add: @.claude/rules/security.md
    - Add: @.claude/rules/connection-profile-design.md
    - Add: @.claude/rules/execution-protocol.md

30. **documentation-writer**
    - Add: @.claude/rules/documentation.md

## Files to Update (Paths)

```bash
# Remaining agent files
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

## Update Pattern (Copy-Paste Ready)

For each agent, find the existing "## Rules They Enforce" or similar section and replace with:

```markdown
## Rules to Load

**Critical/Primary Rules:**
- @.claude/rules/[main-rule].md - [description]

**Supporting Rules:**
- @.claude/rules/[supporting-rule].md - [description]

**Key Principles:**
- [Keep existing 3-5 brief bullets]
```

## Rule File Distribution Status

**All 11 new rule files now referenced by at least one agent:**

| Rule File | Primary Users (20 agents use these) |
|-----------|-------------------------------------|
| failure-conditions.md | gate-controller, build-reviewer, api-architect, operation-engineer, etc. |
| gate-1-api-spec.md | gate-controller, api-architect, api-reviewer, schema-specialist |
| gate-2-type-generation.md | gate-controller, typescript-expert, build-validator |
| gate-3-implementation.md | gate-controller, operation-engineer, mapping-engineer, integration-engineer |
| gate-4-test-creation.md | gate-controller, testing-specialist, test-orchestrator, all UT engineers |
| gate-5-test-execution.md | gate-controller, testing-specialist, test-orchestrator, all IT engineers |
| gate-6-build.md | gate-controller, build-reviewer, build-validator |
| api-specification-critical-12-rules.md | api-architect, api-reviewer, schema-specialist |
| mapper-field-validation.md | operation-engineer, mapping-engineer |
| tool-requirements.md | build-reviewer, build-validator, api-researcher |
| validation-script.md | gate-controller |

**Still need distribution to remaining agents:**
- production-readiness.md - may not need agent distribution
- git-workflow.md - may not need agent distribution

## Next Steps After Compact

1. **Read this file** to restore context
2. **Read status file**: `.claude/.consolidation-status.json`
3. **Read progress file**: `.claude/PHASE-4-PROGRESS.md`
4. **Continue updating** remaining 10 agents with same pattern
5. **Update progress files** after each batch
6. **After all 30 agents done**: Move to Phase 5 (verification & archive)

## Phase 5 Preview (After Phase 4 Complete)

After completing all 30 agents:

1. **Verification**: Compare `claude/` and `.claude/` to ensure zero content loss
2. **Final check**: All agents have appropriate rules
3. **Archive legacy**: `mv claude claude.backup-2025-10-13`
4. **Test workflow**: Run one complete workflow to verify system works
5. **Completion report**: Document consolidation results

## Critical Success Criteria

✅ **Rules are IN rule files** (`.claude/rules/*.md`)
✅ **Agents REFERENCE rules** (via `@.claude/rules/` notation)
✅ **No rule duplication** between files
✅ **All critical agents updated** (gates, build, API, implementation, testing)
✅ **Pattern consistent** across all 20 updated agents

## Tracking Files

- `.claude/.consolidation-status.json` - JSON status
- `.claude/PHASE-4-PROGRESS.md` - Detailed progress
- `.claude/AGENT-RULE-MAPPING.md` - Complete mapping matrix

---

**Last Compact:** 2025-10-13
**Resume From:** 67% complete, 10 agents remaining
**Estimated Time:** 30-45 minutes to complete final 10 agents
