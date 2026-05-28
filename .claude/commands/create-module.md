---
description: Create a new ESM/gradle module from scratch (6 phases, gradle lifecycle gates, 3-4 hours)
argument-hint: <vendor> <service> [suite]
---

Execute the Create Module workflow.

**Arguments:**
- Vendor: $1
- Service: $2
- Suite: $3 (optional — embedded in the module package name)

**Module Identifier:**
- With suite: $1-$3-$2  (e.g. `amazon-aws-s3`)
- No suite: $1-$2       (e.g. `github-github`)

**Module path (derived by the generator from `modulePackage`):**
- With suite: `package/$1/$3/$2`
- No suite:   `package/$1/$2`

## Repository scope rule

We work on **one module at a time**. Do not edit files in sibling `package/*` directories. Every `npm` / `zbb` command in this workflow runs from the target module directory.

## Workflow Phases (6 phases)

Each phase ends with a checkpoint baked into the gradle lifecycle (`validate → generate → compile → test → buildImage`) plus a final `zbb gate` that writes `gate-stamp.json`. There are no separate "Type Gen" or "Build" phases — the lifecycle chains them in one run.

### Phase 1 — Discovery & Analysis

- Invoke @product-specialist
  - Check product bundle: `npm view @zerobias-org/product-bundle --json`
  - Install product package: `npm install @zerobias-org/product-$1-$2` (or `@zerobias-org/product-$1-$3-$2`)
  - Extract product metadata from `index.yml` / `index.ts` (display name, vendor URL, doc links)
  - Save findings to `.claude/.localmemory/create-{module-id}/phase-01-discovery.json`
- Invoke @api-researcher for **minimal-scope** API research
  - Auth endpoints + ONE primary operation
  - Test connection + that operation with real credentials
  - Document base URL, auth flow, rate limits, pagination style
  - Save sample responses for those two endpoints only
- Invoke @operation-engineer to prioritize operations (which one is the "first" operation for Phase 4)
- Invoke @credential-manager for auth requirements (token vs OAuth vs basic)

### Phase 2 — Scaffold (generator does it all)

Preconditions (the generator will fail otherwise):
- CWD must be the module repo root (its `package.json.name` ends with `/module`)
- Docker Desktop running (gradle's `buildImage` requires it)
- Node 22.21.x active (`.nvmrc` at root)
- Generator installed: `npm i -g @zerobias-org/generator-module@0.25.2`

Invoke @module-scaffolder. It will:
1. Compute `productPackage`, `modulePackage`, `description`, `moduleType` (`connector` if auth required, `plain` otherwise) from Phase 1 output
2. Read `author` from `git config user.email` and `repository` from `git config remote.origin.url`
3. Run:
   ```bash
   yo @zerobias-org/module \
     --productPackage '@zerobias-org/product-<…>' \
     --modulePackage  '@zerobias-org/module-<…>' \
     --packageVersion '0.0.0' \
     --description    '<Display name>' \
     --repository     "$(git config remote.origin.url)" \
     --author         "$(git config user.email)" \
     --moduleType     'connector'   # or 'plain'
   ```
4. The generator emits the module tree, symlinks `.nvmrc` + `.npmrc` to repo root, and auto-runs `zbb build` (full lifecycle). **No manual `npm install` or `sync-meta`** — gone.
5. Verify scaffold output (see @.claude/skills/scaffolding/SKILL.md for the file checklist + auto-build verification)

**Files the generator scaffolds:** `build.gradle.kts`, `package.json` (ESM, `type=module`, only `clean` script), `tsconfig.json`, `.mocharc.json`, `.gitignore`, `api.yml`, `hub-sdk/package.json`, `src/<Class>Impl.ts`, `src/index.ts`, `test/e2e/constants.ts`, `test/e2e/<name>.test.ts`, `test/unit/.gitkeep`, `connectionProfile.yml` (connector only), `README.md`, plus `.nvmrc` / `.npmrc` symlinks.

**Files the generator does NOT scaffold (designed later if needed):** `connectionState.yml`, `Dockerfile`, `.env.example`, `test-profiles/`.

### Phase 3 — API Specification Design & Type Generation

- Invoke @credential-manager for authentication analysis (raw auth data → @api-architect)
- Invoke @security-auditor for security requirements
- Invoke @api-architect to design the spec
  - Replace `api.yml` stub: paths, operations, components, security schemes
  - If connector: replace `connectionProfile.yml` stub (extend `tokenProfile` / `oauthClientProfile` / etc. from `@zerobias-org/types-core`)
  - If token expiry tracking is needed: create `connectionState.yml` extending `baseConnectionState` (otherwise omit — design-phase decides)
  - Check for optional connection params (region, environment, etc.)
- Invoke @schema-specialist for complex resource schemas (`$ref` composition)
- Invoke @api-reviewer to validate against api-spec rules
- Invoke @connection-profile-guardian to review `connectionProfile.yml` / `connectionState.yml`
- Run: `zbb generate` from the module directory (chains `assembleSpec → bundleSpec → generate*`, writing codegen output into `generated/api/`, `generated/model/`, and `generated/api/manifest.json`)
- **Checkpoint** — `zbb generate` exits 0 and `generated/api/manifest.json` exists. If `bundleSpec` failed, the spec has unresolved `$refs` or invalid syntax — fix `api.yml` (or `connectionProfile.yml` / `connectionState.yml`) and rerun. The `hub-sdk/generated/` typed client is produced later by `buildHubSdk` as part of Gate 6's `build` chain — don't check for it here.

### Phase 4 — Core Implementation

- Invoke @client-engineer for HTTP client (if connector)
- Invoke @typescript-expert for shared interfaces
- Extend `src/<Class>Impl.ts` — replace TODOs with real `connect / disconnect / metadata / isConnected / isSupported`
- Add Producer classes for each API tag (`src/<Tag>ProducerImpl.ts`)
- Invoke @mapping-engineer for data mapping (`src/mappers/*.ts`)
- Update `src/index.ts` to export the Impl + factory function (`new<Class>()`) and the producers
- Run: `zbb compile`
- **Checkpoint** — TypeScript compiles cleanly. `dist/` populated.

### Phase 5 — Tests

- Invoke @mock-specialist for HTTP fixtures (nock 14 default-import style)
- Invoke @connection-ut-engineer + @producer-ut-engineer for `test/unit/`
- Invoke @connection-it-engineer + @producer-it-engineer for `test/e2e/` (NOT `test/integration/` — that path is gone)
  - Tests use `describeModule<T>` from `@zerobias-org/module-test-client`
  - Import the module type from `hub-sdk/generated/api/index.js`
  - Same test file is exercised by all three modes (direct / docker / hub)
- Run tests:
  ```bash
  zbb test       --slot local   # unit tests
  zbb testDirect --slot local   # e2e direct (in-process, fastest)
  zbb testDocker --slot local   # e2e docker (container — validates packaging)
  # testHub is blocked by upstream bugs — skip until announced otherwise
  ```
- **Checkpoint** — `zbb test` and `zbb testDirect` pass. For connectors, `zbb testDocker` also passes. Provide credentials via `zbb secret create` for e2e — never commit secrets or `.env`.

### Phase 6 — Gate, Docs, Commit

- Invoke @documentation-writer to flesh out `README.md` (the generator's stub is intentionally tiny)
- Run the full gate:
  ```bash
  zbb gate --slot local
  ```
  This runs `validate → generate → compile → test → buildImage` and writes `gate-stamp.json`.
- **Checkpoint** — `gate-stamp.json` exists in the module dir and reflects the current commit.
- Commit:
  - Stage only files inside `package/<vendor>/[<suite>/]<service>/` (plus `gate-stamp.json`)
  - **Never** `git add -A` or `git add .` from the repo root — would pick up unrelated changes
  - **Never** commit `node_modules/`, `dist/`, `generated/`, `hub-sdk/generated/`, `build/`, `npm-shrinkwrap.json`, secrets, or `.env`

## Success Criteria

- Generator ran cleanly and `zbb build` is green
- `zbb build` succeeds end-to-end
- `zbb test` + `zbb testDirect` (+ `zbb testDocker` for connectors) all green
- `gate-stamp.json` present and committed
- README documents auth + first operation
- Module package stays in `@zerobias-org/` scope
- `npm-shrinkwrap.json` absent; `package-lock.json` present
- Only files inside the target module path were touched

