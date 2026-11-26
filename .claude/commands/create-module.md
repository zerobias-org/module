---
description: Create a new ESM/gradle module from scratch (prerequisites gate, 6 build phases, required DataProducer phase, org-first publish and sign-off before the PR)
argument-hint: <vendor> <service> [suite]
---

Execute the Create Module workflow.

**Arguments:**
- Vendor: $1
- Service: $2
- Suite: $3 (optional — embedded in the module package name)

**Module Identifier:**
- With suite: $1-$3-$2  (e.g. `avigilon-alta-access`)
- No suite: $1-$2       (e.g. `nist-cmvp`)

**Module path (derived by the generator from `modulePackage`):**
- With suite: `package/$1/$3/$2`
- No suite:   `package/$1/$2`

## Repository scope rule

Work on one module at a time and leave sibling `package/*` directories untouched. `zbb <task>` runs from the target module directory; the generator and `./gradlew :<project>:<task>` run from the repo root.

## Phase 0 — Prerequisites

Run the `prerequisites` skill (`bash .claude/skills/prerequisites/scripts/check-env.sh`) and fix what it reports before anything else, interactive or headless. A missing prerequisite is installed (with the user's consent) or waited for; it is not worked around. The same applies later in the flow: on a 401, an expired token, a refused org load or a vanished tool, stop the phase, re-run the check and resume only when it passes.

## Branch first

```bash
git fetch origin
git switch -c feat/module-$1-$2 origin/dev    # with suite: feat/module-$1-$3-$2
```

Module work is never committed on `dev` or `main` directly; see the `git-and-sdlc` skill.

## Headless runs (`claude -p "create module …"`)

The flow is the same, with three differences:

- Phase 0 runs before anything is touched. When something is missing, print the exact setup instructions and exit.
- The run ends after Phase 7 (org load). Print what was created, where to verify it, and that sign-off and the PR continue interactively. Phases 8 and 9 need a person.
- A decision fork stops the run: the module already exists, an API design choice is ambiguous, or a gate conflicts. Print the state and the decision needed, and change nothing further.

## Workflow Phases

Each phase ends with a checkpoint that is one task of the gradle lifecycle; the final `zbb gate` runs all of them and writes `gate-stamp.json`. The task graph and the commands are in the `build-lifecycle` skill, the checklist and the stamp rules in @.claude/skills/gates/SKILL.md.

Memory hand-off between phases: `.claude/.localmemory/create-module-{module-id}/`.

### Phase 1 — Discovery & Analysis

- Invoke @product-specialist
  - Check product bundle: `npm view @zerobias-org/product-bundle --json`
  - Confirm the product package is published: `npm view @zerobias-org/product-$1-$2 version` (or `@zerobias-org/product-$1-$3-$2`)
  - Extract product metadata (display name, vendor URL, doc links)
  - Save findings to `.claude/.localmemory/create-module-{module-id}/phase-01-discovery.json`
- Invoke @api-researcher for **minimal-scope** API research
  - Auth endpoints + ONE primary operation
  - Test that operation against the real API (with credentials from the slot when the API needs them)
  - Document base URL, auth flow, rate limits, pagination style
  - Save sanitized sample responses for those endpoints only
  - List the full API surface (operation names only, no samples) in the analysis document. Implementation stays incremental: this first operation now, the rest in per-tag batches through `/add-operations`.
- Invoke @operation-engineer to prioritize operations (which one is the "first" operation for Phase 4)
- Invoke @credential-manager for auth requirements. The outcome decides the module kind for the rest of the workflow, per the `connection-profile` skill: **connector** (the API needs credentials) or **plain** (public source, no profile).

### Phase 2 — Scaffold

Invoke @module-scaffolder. It follows @.claude/skills/scaffolding/SKILL.md, which holds the preconditions, the argument rules and the failure table:

1. Compute `productPackage`, `modulePackage`, `description`, `moduleType` (`connector` or `plain`) from Phase 1 output
2. Run the generator from the repo root with all seven arguments and `--no-install`, so it does not build before the fixes are applied
3. Apply the skill's post-scaffold fix list (a plain module has more fixes than a connector)
4. Build once from the repo root: `./gradlew :<vendor>:[<suite>:]<service>:build`
5. `git add` the new module directory, so a later gate stamp hashes it
6. Verify: `bash .claude/skills/scaffolding/scripts/check-scaffold.sh package/<vendor>/[<suite>/]<service>`

**Checkpoint** — the check script exits 0 and the build is green. No commit yet.

### Phase 3 — API Specification Design & Type Generation

- Invoke @api-architect to design the spec per the `api-spec` skill: replace the `api.yml` stub (paths, operations, components)
- Invoke @schema-specialist for complex resource schemas (`$ref` composition)
- Invoke @api-reviewer to validate against the `api-spec` skill (`scripts/check-spec.sh`, then `zbb validate`)
- **Connector only** (a plain module has no profile, no state and no OAuth; skip these):
  - Invoke @credential-manager for authentication analysis (raw auth data → @api-architect) and @security-auditor for security requirements
  - Replace the `connectionProfile.yml` stub, and add `connectionState.yml` only when `connect()` mints a token that expires. Profile shape, fields and state are decided with the `connection-profile` skill.
  - Invoke @connection-profile-guardian to review `connectionProfile.yml` / `connectionState.yml`
  - **If OAuth `authorization_code` (click-to-Connect):** follow @.claude/skills/oauth-click-to-connect/SKILL.md. It is a three-layer capability (module, provider package in `zerobias-org/oauth`, external app and secret). Check whether the provider exists with the skill's `scripts/list-oauth-providers.sh`, and carry `requiresNewOAuthProvider` + `requiresPlatformOAuthRegistration` forward to Phase 6.
- Run: `zbb generate` from the module directory
- **Checkpoint** — `zbb generate` exits 0 and `generated/api/manifest.json` exists. When `bundleSpec` fails, a `$ref` does not resolve or the YAML is invalid: fix `api.yml` (or the profile / state file) and rerun. `hub-sdk/generated/` is absent at this point; `buildHubSdk` produces it during `build` and `gate`.

### Phase 4 — Core Implementation

File layout, the Impl, the Client, producers and `src/index.ts` follow the `implementation` skill; mappers follow the `mapping` skill.

- Invoke @client-engineer for the HTTP client
- Invoke @typescript-expert for shared interfaces
- Complete `src/<Class>Impl.ts`: a connector implements the connect lifecycle of `<Class>Connector`; a plain module implements `<Class>`, which has no lifecycle methods
- Add a producer per API tag (`src/<Tag>ProducerApiImpl.ts`)
- Invoke @mapping-engineer for data mapping (`src/mappers.ts`)
- Run: `zbb compile`
- **Checkpoint** — lint and TypeScript pass, `dist/` is populated, and the `implementation` skill's `scripts/check-src.sh` has no findings.

### Phase 5 — Tests

Test shape, fixtures and credentials follow the `testing` skill.

- Invoke @mock-specialist for HTTP fixtures (nock)
- Invoke @connection-ut-engineer + @producer-ut-engineer for `test/unit/`. A plain module gets no connection test, because it has no lifecycle.
- **Connector only:** invoke @connection-it-engineer + @producer-it-engineer for `test/e2e/` (`describeModule<T>`, one test body for direct and docker mode). Credentials are a `zbb secret` in the slot; ask the user to create it when it is missing. A plain module keeps unit tests only and has no `test/e2e`.
- Run tests from the module directory:
  ```bash
  zbb --slot <slot> test         # unit tests
  zbb --slot <slot> testDirect   # connector: e2e in-process
  zbb --slot <slot> testDocker   # connector: e2e against the container
  # testHub is blocked by upstream bugs — skip until announced otherwise
  ```
- **Checkpoint** — `zbb test` passes; for a connector `zbb testDirect` and `zbb testDocker` pass and the mocha output shows the e2e tests ran (with no secret the suite is skipped and the task still exits 0).

### Phase 6 — Docs, DataProducer, Gate, Commit

- Invoke @documentation-writer to complete `README.md` per the `readme` skill
- **DataProducer (required for every new module):** follow @.claude/skills/dataproducer-implementation/SKILL.md before the final gate. It wraps the tag producers from Phase 4, so it exposes what exists now; later `/add-operations` batches extend both together. Done when the six DataProducer getters work under `zbb testDirect` and the skill's Step 11 (gate) is complete.
- Run the gate in the order the `gates` skill gives ("The order that produces a valid stamp"): static check `bash .claude/skills/gates/scripts/check-module.sh package/<vendor>/[<suite>/]<service>`, stage the module directory, `zbb --slot <slot> gate`, stage `gate-stamp.json`, `./gradlew :<vendor>:[<suite>:]<service>:gateCheck` from the repo root, then the script again with `--final`.
- **Checkpoint** — the gate exits 0, `gateCheck` passes, and the stamp's test counts match the tests written.
- Commit per the `git-and-sdlc` skill: stage by path inside the module directory only, show the owner the files and the message, and ask before committing. Do not push yet.
- **If OAuth `authorization_code` (`requiresPlatformOAuthRegistration`):** finish steps 3 and 4 of the `oauth-click-to-connect` skill.
  - **If `requiresNewOAuthProvider`:** open a second PR against `zerobias-org/oauth` that adds the provider package. It is a deliverable of this workflow, because the module cannot offer OAuth until the provider loads.
  - Tell the user that the Hub "Connect" button stays hidden until a platform insider registers the external OAuth app and stores its secret. Hand them the task text from the skill's `references/insider-task.md`, and name both the oauth-repo PR (if any) and that task in the module PR body.

### Phase 7 — Publish to the user's org

Follow "Org first, then the pull request" in the `git-and-sdlc` skill: `zbb --slot <slot> publishOrg` from the module directory, then verify that the module landed in the org. Iterate here (edit, gate, publish again) until the user is satisfied. No PR and no shared catalog are involved yet. An external contributor without a ZeroBias org skips Phases 7 and 8.

### Phase 8 — User verification and sign-off

Show the user the org-loaded module and wait for an explicit confirmation. Silence, or a request for another change, is not a sign-off; ask when it is unclear.

### Phase 9 — Push and PR

After sign-off only: push the branch and open the PR against `dev`, with no version bump. Ask the owner before the push.

## Success Criteria

- `check-scaffold.sh` and `check-module.sh --final` exit 0
- `zbb gate` green; `zbb test` (+ `zbb testDirect` / `zbb testDocker` for connectors) ran and passed
- `gate-stamp.json` present, valid under `gateCheck`, and committed
- README documents authentication (or that none is needed) and the first operation
- DataProducer implemented: six getters wired, object IDs round-trip, unsupported writes throw
- Module loaded into the user's org with `publishOrg`, and the user's explicit sign-off received before the push
- Module package stays in `@zerobias-org/` scope
- Only files inside the target module path were touched
