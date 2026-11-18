# Module Scaffolder Workflow

## Purpose
Run the module generator (`yo @zerobias-org/module`) for a new module, repair and verify its output, and
write the Phase 2 memory entry. Arguments, the fix list, the verification script and the failure table are
in @.claude/skills/scaffolding/SKILL.md; this workflow supplies the inputs and the order.

## Input Required
- `workflow`: e.g. `create-module`
- `moduleId`: e.g. `nist-cmvp`, `avigilon-alta-access`
- `inputFile`: `phase-01-discovery.json` (Phase 1 output)
- `outputFile`: `phase-02-scaffolding.json` (this phase's output)

## Step-by-Step Process

### Step 1 — Read Phase 1 output

Read `.claude/.localmemory/{workflow}-{moduleId}/{inputFile}`.

Extract:
- `productPackage` — starts with `@zerobias-org/product-`
- Display name (becomes `--description`)
- Auth requirement (drives `--moduleType`)

### Step 2 — Derive remaining inputs

| Input            | Source                                                     |
|------------------|------------------------------------------------------------|
| `modulePackage`  | `productPackage` with `/product-` replaced by `/module-`   |
| `packageVersion` | `0.0.0` (the publish workflow bumps versions)              |
| `repository`     | `git config remote.origin.url`                             |
| `author`         | `git config user.email`                                    |
| `moduleType`     | `connector` if the source needs credentials, else `plain`  |

Connector or plain: see "1. Decide connector or plain" in @.claude/skills/scaffolding/SKILL.md.

### Step 3 — Compute paths

| Path           | Rule                                                                   |
|----------------|------------------------------------------------------------------------|
| `modulePath`   | `package/` + `modulePackage.split('/module-')[1].split('-').join('/')` |
| `gradleProject`| `:` + same path with `:` instead of `/`                                 |

Example: `@zerobias-org/module-avigilon-alta-access` → `package/avigilon/alta/access` → `:avigilon:alta:access`.

### Step 4 — Verify preconditions

Stop and report if any item of "2. Before running" in @.claude/skills/scaffolding/SKILL.md is not met
(repo root, generator installed and current, product package published, `${modulePath}` absent, feature
branch). Tool versions are checked with the `prerequisites` skill's check script, not from memory.

### Step 5 — Invoke the generator

Run the command from "3. Run the generator" in @.claude/skills/scaffolding/SKILL.md with the inputs from
Steps 1–2, all seven arguments, and `--no-install` (that section explains why).

If the generator exits non-zero, stop and pass stderr to the caller. Look the message up in "When the
generator fails" in the skill; do not rescaffold over partial output.

### Step 6 — Fix and verify output

1. Apply "4. Post-scaffold fix list" in @.claude/skills/scaffolding/SKILL.md for the module type, including
   the single build at the end of that list.
2. Run the check script from "5. Verify" in the same skill; exit code 0 means the scaffold is ready.

The file set the generator writes is under "What it writes" in the skill and in
@.claude/skills/scaffolding/references/generated-files.md.

### Step 7 — Write Phase 2 output

Write `.claude/.localmemory/{workflow}-{moduleId}/{outputFile}` with: `modulePackage`, `modulePath`,
`gradleProject`, `moduleType`, the fixes applied, the check script result and the build result.

### Step 8 — Hand off

Do not commit in this phase. The first commit happens in Phase 6, after design, implementation, tests and
the gate stamp are ready, and the workflow caller decides how commits are split. Staging the module before
a gate run is covered by fix-list item 10 and the `gates` skill.

## What this workflow does not do

- Create `.npmrc` / `.nvmrc` symlinks by hand (the generator links them)
- Create `connectionState.yml` (Phase 3, only when a token expires)
- Create a `Dockerfile` (the build generates the image for a connector)
- Create `test/integration/` (see "`test/integration/`" in @.claude/skills/testing/SKILL.md)
- Touch files outside `${modulePath}`

## Success Criteria

- Generator exited 0
- Fix list applied for the module type
- `check-scaffold.sh` exits 0
- The build from the fix list succeeded
- `phase-02-scaffolding.json` written

## Troubleshooting

See "When the generator fails" in @.claude/skills/scaffolding/SKILL.md.

## Handoff to Phase 3

Phase 3 agents pick up from a tree that contains:
- `api.yml` stub with empty `paths`
- Connector: `connectionProfile.yml` stub with a single `apiToken`, a lifecycle stub in `src/<Class>Impl.ts`,
  a `describeModule` skeleton in `test/e2e/`
- Plain: no profile, `"interface": true`, an Impl without lifecycle, no `test/e2e/`
- `test/unit/<Class>ApiTest.ts`
- A passing stub-level build

Phase 3 owns: real `api.yml`, real `connectionProfile.yml`, optional `connectionState.yml`, security review.

## Related

- @.claude/skills/scaffolding/SKILL.md — command, fix list, verification, troubleshooting
- @.claude/skills/scaffolding/references/generated-files.md — file table, `src/index.ts`, `tsconfig.json`, `package.json`
- @.claude/skills/prerequisites/SKILL.md — environment and CLI tooling
- @.claude/skills/build-lifecycle/SKILL.md — what the build produces
