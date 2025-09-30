---
description: Diagnose and fix module issues (15 min - 2 hours)
argument-hint: <module-identifier> [issue-description]
---

Execute the Fix Issue workflow for module: $1

**Issue Description:** $2 (optional - will diagnose if not provided)

**Workflow:**

1. **Phase 1: Diagnosis**
   - Run `npm test` to identify failing tests
   - Run `npm run build` to identify compilation errors
   - Check TypeScript diagnostics
   - Invoke @build-reviewer to analyze errors
   - Categorize issue: API spec, implementation, tests, or build

2. **Phase 2: Root Cause Analysis**
   - If API spec issue: Invoke @api-reviewer
   - If type issue: Invoke @typescript-expert
   - If implementation issue: Invoke @operation-engineer
   - If test issue: Invoke @test-orchestrator
   - If build issue: Invoke @build-validator

3. **Phase 3: Fix Implementation**
   - Route to appropriate specialist agent:
     - API changes → @api-architect + regenerate types
     - Code fixes → @operation-engineer or @mapping-engineer
     - Test fixes → @mock-specialist or test engineers
     - Build fixes → @build-reviewer

4. **Phase 4: Validation**
   - Re-run affected tests
   - Verify fix doesn't break other tests
   - Run full test suite
   - Run build
   - Invoke @gate-controller to validate all gates

5. **Phase 5: Regression Check**
   - Ensure no new issues introduced
   - Verify all gates still pass
   - Confirm fix is complete

**Common Issue Types:**

**Type Errors:**
- Missing type generation → Run `npm run clean && npm run generate`
- Using `any` types → Use generated types
- Import errors → Fix import paths

**Test Failures:**
- Mock configuration → Invoke @mock-specialist
- Integration test credentials → Check .env
- Test data issues → Update test fixtures

**Build Errors:**
- TypeScript compilation → Invoke @typescript-expert
- Missing dependencies → Run `npm install`
- Configuration issues → Check tsconfig.json

**Success Criteria:**
- Issue identified and fixed
- All tests passing
- Build successful
- No regressions introduced
- All 6 gates still pass

**Example:**
```
/fix-issue github-github "getWebhook returns undefined"
/fix-issue amazon-aws-s3
```
