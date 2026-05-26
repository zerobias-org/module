# Build Validation Workflow

## Overview

This workflow covers two critical validation gates:
- **Gate 2**: Type Generation Validation (after API spec changes)
- **Gate 6**: Final Build Validation (before task completion)

Both gates are MANDATORY and must pass to proceed.

## Gate 2: Type Generation Validation

### When to Run
- **MANDATORY** after ANY api.yml changes
- Before implementation starts
- After API Reviewer passes spec

### Process

1. **Clean previous generation**
   - Remove generated/ directory
   - **Commands:** See "Gate 2 Validation" in @.claude/skills/build-quality/SKILL.md

2. **Run type generation**
   - Execute `zbb generate` from the module directory
   - Verify exit code is 0
   - If fails: BLOCK and report error

3. **List generated files**
   - Check generated/api/ directory
   - Check generated/model/ directory
   - Count total TypeScript files created

4. **Check for InlineResponse types**
   - Search all generated files for InlineResponse/InlineRequestBody
   - **Detection script:** See "InlineResponse Detection" in @.claude/skills/build-quality/SKILL.md
   - If found: FAIL - API spec has inline schemas that must be moved to components/schemas

5. **Validate TypeScript compilation**
   - Run `zbb compile` to verify types compile
   - If fails: Report type errors and BLOCK

6. **Report results**
   - Status: PASSED/FAILED
   - File count
   - Any InlineResponse types found
   - Compilation result

### Success Criteria

- ✅ Generation exit code 0
- ✅ Generated directory exists
- ✅ TypeScript files created
- ✅ NO InlineResponse types
- ✅ Build succeeds

### Common Failures

**InlineResponse types found:**
- Cause: Inline schema in api.yml responses
- Fix: Move schema to components/schemas and use $ref

**Generation failed:**
- Cause: Invalid $ref or malformed YAML
- Fix: Check api.yml syntax and references

## Gate 6: Final Build Validation

### When to Run
- **MANDATORY** before marking task complete
- After all implementation finished
- Before creating git commit

### Process

1. **Source code linting** (MANDATORY)
   - Execute `zbb lint`
   - **MUST** have 0 errors
   - Warnings are acceptable if documented
   - If errors found: BLOCK and fix immediately

2. **Clean previous build**
   - Remove dist/ directory
   - **Commands:** See "Gate 6 Validation" in @.claude/skills/build-quality/SKILL.md

3. **Run build**
   - Execute `zbb build`
   - Verify exit code is 0
   - If fails: BLOCK and report errors

4. **Validate artifacts**
   - Check dist/ directory exists
   - Count JavaScript files
   - Verify entry point (dist/index.js)
   - Count declaration files (.d.ts)
   - **Validation script:** See "Artifact Validation" in @.claude/skills/build-quality/SKILL.md

5. **Verify gate stamp**
   - Confirm gate-stamp.json exists (produced by `zbb gate`)
   - npm-shrinkwrap.json is FORBIDDEN in the gradle/zbb world

6. **Type check**
   - Run `zbb compile`
   - Report any TypeScript errors

7. **Report results**
   - Lint status: errors/warnings count
   - Build status: PASSED/FAILED
   - Artifact count (JS files, .d.ts files)
   - gate-stamp.json status
   - Any TypeScript errors

### Success Criteria

- ✅ Build exit code 0
- ✅ dist/ directory created
- ✅ JavaScript files generated
- ✅ Declaration files (.d.ts) generated
- ✅ gate-stamp.json exists (no npm-shrinkwrap.json)
- ✅ NO TypeScript errors
- ✅ NO linting errors

### Common Failures

**TypeScript compilation errors:**
- Cause: Type mismatches, missing imports
- Fix: Check type definitions and imports

**Missing artifacts:**
- Cause: Build didn't complete
- Fix: Check build output for errors

**Missing gate-stamp.json:**
- Cause: `zbb gate` not run after build
- Fix: Run `zbb gate` from the module directory

## Output Format

### Gate 2 Output

Report should include:
- **Status:** PASSED or FAILED
- **Generation results:** Exit code and output
- **Files generated:** Count by directory (api/, model/)
- **Type quality checks:** InlineResponse detection results
- **Compilation:** Build success/failure
- **Decision:** Whether to proceed with implementation

**Format template:** See "Gate 2 Output Format" in @.claude/skills/build-quality/SKILL.md

### Gate 6 Output

Report should include:
- **Status:** PASSED or FAILED
- **Build execution:** Exit code, errors if any
- **Artifacts generated:** File tree and counts
- **Gate stamp:** gate-stamp.json status
- **Decision:** Whether task is ready for completion

**Format template:** See "Gate 6 Output Format" in @.claude/skills/build-quality/SKILL.md

## Tools Used

- `zbb generate`
- `zbb build`
- `zbb lint`
- `zbb compile`
- `zbb gate --slot local` (produces gate-stamp.json)
- grep (for InlineResponse detection)
- find (for file counting)
- ls (for verification)

## Related Documentation

**Primary Rules:**
- @.claude/skills/build-quality/SKILL.md - All validation scripts, commands, and output formats
- @.claude/skills/gate-build/SKILL.md - Gate 6 specification
- @.claude/skills/gate-type-generation/SKILL.md - Gate 2 specification

**Supporting Rules:**
- @.claude/skills/failure-conditions/SKILL.md - Build-related failure conditions
- @.claude/skills/implementation-core/SKILL.md - TypeScript compilation standards
