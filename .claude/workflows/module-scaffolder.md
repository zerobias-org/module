# Module Scaffolder Workflow

## Purpose
Run `@zerobias-org/module` (Yeoman) for a new module, verify its output, and write the Phase 2 memory entry. The generator does install, gradle build, and symlinks itself — this workflow's job is *feeding it correct inputs and verifying afterwards*, not orchestrating each step.

## Input Required
- `workflow`: e.g. `create-module`
- `moduleId`: e.g. `github-github`, `amazon-aws-s3`
- `inputFile`: `phase-01-discovery.json` (Phase 1 output)
- `outputFile`: `phase-02-scaffolding.json` (this phase's output)

## Step-by-Step Process

### Step 1 — Read Phase 1 output

Read `.claude/.localmemory/{workflow}-{moduleId}/{inputFile}`.

Extract:
- `productPackage` — must start with `@zerobias-org/product-`
- Display name (becomes `--description`)
- Auth requirement (drives `--moduleType` = `connector` | `plain`)

### Step 2 — Derive remaining inputs

| Input            | Source                                                                 |
|------------------|------------------------------------------------------------------------|
| `modulePackage`  | `productPackage.replace('product-','module-').replace('@zerobias-org/','@zerobias-org/')` |
| `packageVersion` | `0.0.0` (always for new modules)                                       |
| `repository`     | `git config remote.origin.url`                                         |
| `author`         | `git config user.email`                                                |
| `moduleType`     | `connector` if auth required else `plain`                              |

### Step 3 — Compute paths

| Path           | Rule                                                                   |
|----------------|------------------------------------------------------------------------|
| `modulePath`   | `package/` + `modulePackage.split('/module-')[1].split('-').join('/')` |
| `gradleProject`| `:` + same path with `:` instead of `/`                                 |

Example: `@zerobias-org/module-amazon-aws-s3` → `package/amazon/aws/s3` → `:amazon:aws:s3`.

### Step 4 — Verify preconditions

Fail fast if any are missing:

1. CWD's `package.json.name` ends with `/module` (this repo)
2. `./gradlew` exists at CWD (zbb drives it; you should not invoke it directly)
3. `docker info` exits 0 (Docker Desktop is running)
4. `node -v` is `v22.*`
5. `which yo` succeeds and `@zerobias-org/module` is installed
6. `${modulePath}` does **not** already exist

### Step 5 — Invoke the generator

```bash
yo @zerobias-org/module \
  --productPackage "${productPackage}" \
  --modulePackage  "${modulePackage}" \
  --packageVersion '0.0.0' \
  --description    "${displayName}" \
  --repository     "$(git config remote.origin.url)" \
  --author         "$(git config user.email)" \
  --moduleType     "${moduleType}"
```

The generator will:
- Create `${modulePath}/`
- Emit the file set listed in `@.claude/skills/scaffolding/SKILL.md` ("What the generator does for you")
- Symlink `${modulePath}/.nvmrc` and `.npmrc` to repo root
- Run `zbb build` from `${modulePath}` (validate → generate → compile → test → buildImage)

If the generator exits non-zero, **stop**. Surface stderr to the caller. Do not retry; do not "fix" partial output.

### Step 6 — Verify output

Run the post-generator checklist from `@.claude/skills/scaffolding/SKILL.md`. In particular:

- Required scaffolded files exist
- Symlinks `.nvmrc` / `.npmrc` resolve
- `dist/`, `generated/`, `hub-sdk/generated/` populated (skip if generator was run with `--no-install`)
- `generated/api/manifest.json` exists
- `src/<Class>Impl.ts` exists and implements the `<Class>Connector` interface (TODO bodies are fine)
- `src/index.ts` re-exports the impl, factory, and generated model/api types
- `package.json` has only the `clean` script

### Step 7 — Write Phase 2 output

Write `.claude/.localmemory/{workflow}-{moduleId}/{outputFile}` in the shape documented in `@.claude/skills/scaffolding/SKILL.md` ("Memory output").

### Step 8 — Hand off

Do **not** commit. The first commit happens in Phase 6 after design + impl + tests + gate-stamp are all ready, so the module enters git as a coherent unit (or as a "scaffold baseline" if Phase 6 explicitly splits commits — that decision is owned by the workflow caller, not this agent).

## What this workflow does NOT do

- ❌ Create `.npmrc` / `.nvmrc` symlinks manually (generator does it)
- ❌ Create `connectionState.yml` (deferred to Phase 3, only if needed)
- ❌ Create `Dockerfile` (gradle `buildImage` doesn't need one)
- ❌ Create `test/integration/` (use `test/e2e/`)
- ❌ Touch files outside `${modulePath}`

## Success Criteria

- Generator exited 0
- All files listed in `scaffolding` skill's verification checklist exist
- Gradle auto-build succeeded (or `--no-install` was explicitly requested by the caller)
- `phase-02-scaffolding.json` written

## Troubleshooting

See `@.claude/skills/scaffolding/SKILL.md` § "When the generator fails".

## Handoff to Phase 3

Phase 3 agents pick up from a tree that contains:
- `api.yml` stub with empty `paths`
- `connectionProfile.yml` stub with a single `apiToken` (connector only)
- A compiled but functionally empty `src/<Class>Impl.ts`
- A passing `zbb build` (stub-level)
- Empty `test/unit/`, a `describeModule<T>` skeleton in `test/e2e/`

Phase 3 owns: real `api.yml`, real `connectionProfile.yml`, optional `connectionState.yml`, security review.

## Related

- @.claude/skills/scaffolding/SKILL.md — commands, file emission, verification, troubleshooting
- @.claude/skills/prerequisites/SKILL.md — environment requirements
- @.claude/skills/tool-requirements/SKILL.md — CLI tooling
- @.claude/skills/module-exports/SKILL.md — what `src/index.ts` should look like
- @.claude/skills/typescript-config/SKILL.md — `tsconfig.json` shape
