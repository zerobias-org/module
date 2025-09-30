# Module Scaffolder Workflow

## Purpose
Scaffold new module structure using Yeoman generator, validate output, and prepare for Phase 3 design work.

## Input Required
- `workflow`: Workflow type (e.g., create-module, add-operation)
- `moduleId`: Module identifier (e.g., github-github)
- `inputFile`: Path to phase-01-discovery.json in localmemory
- `outputFile`: Path to write phase-02-scaffolding.json

## Step-by-Step Process

### Step 1: Read Input Parameters

- Read discovery output JSON from .claude/.localmemory/{workflow}-{moduleId}/{inputFile}
- Extract vendor and product names
- Extract suite (if applicable)
- Extract packageName and packageScope
- Determine target directory structure
- **Input format:** See "Discovery Output" in @.claude/rules/scaffolding-patterns.md

### Step 2: Determine Module Path

Calculate correct module path based on suite:
- With suite: package/{vendor}/{suite}/{product}
- Without suite: package/{vendor}/{product}
- **Path patterns:** See "Module Path Determination" in @.claude/rules/scaffolding-patterns.md

### Step 3: Execute Yeoman Generator

- Navigate to package directory
- Run Yeoman generator with correct parameters
- Use exact command format for vendor/product or vendor/suite/product
- Yeoman will prompt for package name, scope, description, author, license
- **Generator commands:** See "Yeoman Generator Execution" in @.claude/rules/scaffolding-patterns.md

### Step 4: Run sync-meta (CRITICAL)

**NEVER SKIP THIS STEP**

- Navigate to module directory
- Execute npm run sync-meta
- This syncs package.json metadata to api.yml (title and version)
- MUST run BEFORE npm install
- **Why critical:** See "sync-meta Usage" in @.claude/rules/scaffolding-patterns.md

### Step 5: Validate Yeoman Output

Run 11-point comprehensive validation checklist:
1. Core structure (src/, tests/, generated/ directories exist)
2. Key files exist (package.json, api.yml, connectionProfile.yml)
3. Source files present (Client.ts, Mappers.ts, index.ts)
4. Test structure (tests/unit/, tests/integration/)
5. TypeScript config (tsconfig.json)
6. api.yml title synced with package.json
7. api.yml version synced with package.json
8. connectionProfile.yml has stub
9. connectionState.yml exists
10. Build scripts present in package.json
11. Dependencies correct in package.json

**All validation scripts:** See "11-Point Validation Checklist" in @.claude/rules/scaffolding-patterns.md

### Step 6: Install Dependencies

- Navigate to module directory
- Run npm install
- Verify node_modules created
- Verify package-lock.json created
- **Installation:** See "Dependency Installation" in @.claude/rules/scaffolding-patterns.md

### Step 7: Validate TypeScript Configuration

**Do NOT build yet** - just validate tsconfig.json:
- Check tsconfig.json exists and is valid JSON
- Verify key settings present (target, module, outDir)
- Do NOT run build - that comes later in Phase 5

### Step 8: Create Required Symlinks

- Link .npmrc from root if not present
- Link .nvmrc from root if not present
- **Symlink commands:** See "Symlink Creation" in @.claude/rules/scaffolding-patterns.md

### Step 9: Validate Complete Structure

**Final verification** that all stub files are present:
- List all critical files in src/, tests/, root
- Verify stubs only (not design)
- Check for TODO markers in connectionProfile.yml
- Confirm api.yml paths are empty (ready for Phase 3 design)
- **Structure validation:** See "Structure Validation" in @.claude/rules/scaffolding-patterns.md

### Step 10: Write Output File

Create phase-02-scaffolding.json with:
- workflow and moduleId
- phase: "scaffolding"
- status: "success" or "failed"
- modulePath (absolute path to module)
- validation results (all checks with status)
- nextPhase: "design"
- notes about what's ready and what's deferred

**Output format:** See "Output File JSON Format" in @.claude/rules/scaffolding-patterns.md

### Step 11: Create Git Commit

- Navigate to module directory
- Stage all new files (git add .)
- Create commit with descriptive message
- Include module structure summary
- Use HEREDOC for multi-line commit message
- Add Claude Code attribution
- **Commit format:** See "Git Commit" in @.claude/rules/scaffolding-patterns.md

## Critical Sequence

**MUST follow this order:**

1. Yeoman generator
2. `npm run sync-meta` ← NEVER SKIP
3. `npm install`
4. Validation

**Violating this sequence causes:**
- api.yml title/version out of sync
- Build failures later
- Test failures later

## Success Criteria

- ✓ All 11 validation checks pass
- ✓ Module directory created at correct path
- ✓ All stub files present
- ✓ api.yml title/version synced with package.json
- ✓ Dependencies installed (node_modules present)
- ✓ No build errors (validation only, not full build)
- ✓ Git commit created
- ✓ Output JSON written to localmemory

## What NOT to Do

**DO NOT:**
- ❌ Design connectionProfile.yml schema (Phase 3)
- ❌ Design connectionState.yml schema (Phase 3)
- ❌ Design api.yml paths/operations (Phase 3)
- ❌ Make authentication decisions (Phase 3)
- ❌ Skip `npm run sync-meta`
- ❌ Run `npm run build` (premature)

**ONLY:**
- ✓ Scaffold structure
- ✓ Validate stubs exist
- ✓ Prepare for Phase 3

## Troubleshooting

See @.claude/rules/scaffolding-patterns.md for 6 common scenarios:
1. Generator not found → Install generator globally
2. Sync-meta fails → Check package.json validity
3. npm install fails → Check registry access
4. Missing directories → Re-run generator
5. api.yml not synced → Run sync-meta again
6. TypeScript errors → Check tsconfig.json

## Handoff to Next Phase

**Phase 3 (Design) agents will:**
- Design connectionProfile.yml (@connection-profile-guardian + @credential-manager)
- Design connectionState.yml (@credential-manager)
- Design api.yml operations (@api-architect + @schema-specialist)
- Select authentication methods (@security-auditor)

**You handed them:**
- ✓ Clean module structure
- ✓ Valid stub files
- ✓ Installed dependencies
- ✓ Ready for design work

## Related Documentation

**Primary Rules:**
- @.claude/rules/scaffolding-patterns.md - ALL technical patterns, commands, validation scripts, troubleshooting

**Supporting Rules:**
- @.claude/rules/tool-requirements.md - Required generator tools
- @.claude/rules/prerequisites.md - Setup requirements
- @.claude/rules/execution-protocol.md - Phase transitions
- @.claude/rules/implementation.md - Module structure standards
