---
name: gates
description: Checks a module against the six quality gates and produces a gate-stamp.json that CI accepts. Use when asked to validate gates, run or fix `zbb gate`, check whether a module is done or PR-ready, refresh or debug a stale or invalid gate stamp, or do the pre-publish check. Covers the single six-checkpoint checklist, the stage-before-the-final-gate rule, stamp fields, gate prerequisites, plain (profile-less) module exceptions and the static check script.
---

# Gates

A module is done when `zbb gate` passes on the staged tree and the resulting `gate-stamp.json` is
committed with it. The six "gates" are checkpoints inside that one gradle run, not six separate
tools. This skill owns the checklist and the stamp rules; the `build-lifecycle` skill owns what each
gradle task does and the command reference.

## The order that produces a valid stamp

The stamp hashes its inputs through `git ls-files`. A file git does not track hashes as nothing, so
a gate run on an unstaged module writes a stamp CI will reject. Follow this order every time:

1. Run the static check (seconds, no gradle) and fix every `FAIL`:
   `${CLAUDE_SKILL_DIR}/scripts/check-module.sh package/<vendor>/[<suite>/]<product>`
2. Stage the whole module directory by path: `git add package/<vendor>/[<suite>/]<product>`
   (the module's `.gitignore` keeps `dist/`, `generated/`, `node_modules/` out).
3. From the module directory run the gate: `zbb --slot <slot> gate`
4. Stage the stamp it wrote: `git add gate-stamp.json`
5. Confirm from the repo root: `./gradlew :<vendor>:[<suite>:]<product>:gateCheck`
6. Run the script again with `--final`; it now fails on a missing stamp or untracked files.

Any edit to a hashed file after step 3, and any late `git add` of a new file, invalidates the stamp:
repeat from step 2. A stamp that changes after staging is expected, not an error. Commit and push
rules (branch, message, approval) belong to the `git-and-sdlc` skill.

## The six checkpoints

| # | Checkpoint | Gradle task inside `gate` | Passes when |
|---|---|---|---|
| 1 | API spec | `validate` | redocly accepts `api.yml`; on the connector plugin `connectionProfile.yml` exists |
| 2 | Type generation | `generate` | codegen writes `generated/api` and `generated/model` from the spec |
| 3 | Implementation | `lint`, then `compile` | eslint passes on `src/`; `tsc` compiles `src/`, `test/`, `generated/` |
| 4 | Tests exist | none (counted into the stamp) | at least one test under `test/unit`, `test/e2e` or `test/integration` |
| 5 | Tests pass | `test`, `testDirect`, `testDocker`, `testDataloader` | every suite that exists passes; the dataloader accepts the package |
| 6 | Build and stamp | `buildArtifacts`, then `writeGateStamp` | hub-sdk (and image, for connectors) build; `gate-stamp.json` written |

`gate` also runs `verifyNoLocalRegistry` first, which stops when `package-lock.json` carries
`localhost` registry URLs left by local-registry development.

Work the checkpoints in order while developing, using the single tasks (`zbb validate`,
`zbb generate`, `zbb lint`, `zbb compile`, `zbb test`, `zbb testDirect`) so a failure is cheap. They
are not a substitute for the final `zbb gate`: `zbb build` runs only `buildArtifacts`, with no tests
and no stamp.

What to verify by reading, per checkpoint (the script covers the greppable part):

1. **Spec** — content rules live in the `api-spec` skill. Here: operationIds unique, the plugin in
   `build.gradle.kts` matches the module kind (see "Connector or plain").
2. **Generation** — `generated/` is never edited and never committed. `hub-sdk/generated/` does not
   exist after `zbb generate`; it appears at checkpoint 6. A missing `hub-sdk/generated/` is not a
   generation failure.
3. **Implementation** — relative imports end in `.js`; no `process.env` in `src/`; the Impl
   implements the generated interface for its kind. Code rules live in the `implementation` and
   `mapping` skills.
4. **Tests exist** — unit tests run as `mocha --recursive test/unit/`, so file names are free; e2e
   tests must match `test/e2e/**/*.test.ts`. A module may be gate-green with only one of the two
   (`package/readyplayerme/readyplayerme` has no `test/unit`, `package/nist/cmvp` has no
   `test/e2e`). Test shape lives in the `testing` skill.
5. **Tests pass** — a suite whose directory is absent is recorded as `skipped`, which is fine; a
   suite that is present must run. Do not skip or delete a failing test to get the stamp.
6. **Build and stamp** — read the stamp after the run (next section).

## Reading the stamp

`gate-stamp.json` (see `package/nist/cmvp/gate-stamp.json`) has exactly these fields:

| Field | Meaning |
|---|---|
| `version`, `branch` | written by gradle at gate time; do not edit by hand |
| `sourceHash` | hash of the tracked `api.yml`, `tsconfig.json`, `src/` and the paths in package.json `files` |
| `testHash` | hash of the tracked files under `test/` |
| `tasks` | result per task: `validate`, `lint`, `compile`, `test`, `testDirect`, `testDocker`, `testDataloader`, `buildArtifacts` (some stamps add `dataloaderExec`) |
| `tests` | `unit`, `integration`, `e2e`, each `{ expected, ran, status }` |

There is no timestamp field; freshness is decided by the hashes, not by date. Check after a gate:

- `tests.*.expected` equals `ran` for every suite, and the counts are what you wrote. `expected: 0`
  with `status: skipped` on a suite you believe exists means the files are in the wrong place.
- `sourceHash` does not start with `e3b0c442`. That value is the hash of an empty input: the gate
  ran before `git add`.
- `./gradlew :<vendor>:[<suite>:]<product>:gateCheck` passes. It re-hashes the tree and fails when the source hash, the test hash or
  the test counts moved, when a recorded task did not succeed, or when a path listed in package.json
  `files` is neither tracked nor ignored.

`package.json` and `package-lock.json` are not part of either hash, so a dependency change does not
invalidate the stamp by itself. Run the gate again anyway: the stamp is a claim that this tree
passed.

## Prerequisites the gate adds

Tool installation and slot setup belong to the `prerequisites` skill. The gate specifically needs:

- **The dataloader CLI.** `zbb gate` and `zbb test` refuse to start without it; the minimum version
  and the install command are in the `tools.dataloader` block of the root `zbb.yaml`. It must be on
  the `PATH` of the Node version active when `zbb` runs; a global install under a different Node
  version is not found.
- **A slot that resolves `ZB_TOKEN`.** The root `.npmrc` authenticates every scope with
  `${ZB_TOKEN}`, and `testDataloader` authenticates with the same token. Leave
  `DATALOADER_SERVICE_URL` on its default; `zbb.yaml` explains why an override breaks the step.
- **Docker, for connectors.** `buildImage` and `testDocker` run when the module has a
  `connectionProfile.yml` and is not marked `"interface": true`.
- **Secrets for e2e, for connectors.** `testDirect` and `testDocker` find the module's `zbb secret`;
  see the `testing` skill.

The `gateCheck` task needs none of this: no slot and no tokens, only Java 21 on `JAVA_HOME` because it
is called through `./gradlew` directly (see the `prerequisites` skill).

## Connector or plain

Connector-only items are marked; a plain module is one with no credentials and no
`connectionProfile.yml`, like `package/nist/cmvp`.

| | Connector (`package/readyplayerme/readyplayerme`) | Plain (`package/nist/cmvp`) |
|---|---|---|
| `build.gradle.kts` plugin | `zb.typescript-connector` | `zb.typescript` |
| `connectionProfile.yml` | required by `validate` | absent |
| package.json | no `interface` key | `"interface": true` |
| Generated interface the Impl implements | `<Class>Connector` with the connect lifecycle | `<Class>`, no lifecycle methods |
| `test/e2e` | expected; runs in `testDirect` and `testDocker` | absent; unit tests mock the real host |
| Docker image, `testDocker` | built and run | skipped |
| `testDataloader` | runs | runs |

Two plain-module traps that end in a failed gate rather than a wrong stamp:

- Without `"interface": true` the gate generates a server for the Docker image, and that server
  expects a `ConnectionProfile` the module does not have, so `compileServer` fails.
- With `"interface": true` and a `test/e2e` directory, `testDocker` still runs and stops with
  "module-container.json not found", because no container was started. Keep the live check outside
  `test/e2e` until the tooling supports profile-less modules.

Other plugins exist (`package/x12/x12` uses `zb.java-module` and declares its own hashed inputs in
`build.gradle.kts`); the checklist is the same, the implementation checks are not.

## When the gate fails

| Failing task or message | Checkpoint | Look at |
|---|---|---|
| `verifyNoLocalRegistry` | before 1 | `package-lock.json` resolves from `localhost`; regenerate the lockfile against the real registry |
| `validateSpec` | 1 | the redocly message; rules in the `build-lifecycle` and `api-spec` skills |
| `validateConnector` | 1 | connector plugin without `connectionProfile.yml` |
| `npmInstallModule`, `bundleSpec` | 2 | `ZB_TOKEN` not resolved, or a `$ref` into a package that is not installed |
| `generateApi` | 2 | fix `api.yml`, never `generated/` |
| `lintExec` | 3 | eslint output; lint runs before `tsc`, so this is the first code failure you see |
| `transpile` | 3 | TypeScript errors, often stale code after a spec change |
| `testUnitExec`, `testIntegrationExec` | 5 | mocha output; `testing` skill |
| `testDirectExec`, `testDockerExec` | 5 | secret missing or wrong, or the plain-module trap above |
| `dataloaderExec` | 5 | the dataloader CLI or `ZB_TOKEN` (see prerequisites above), then the dataloader's own message |
| `compileServer` | 6 | plain module without `"interface": true` |
| `buildImageExec` | 6 | Docker daemon |
| "Gate stamp invalid — untracked published file(s)" | stamp | stage the files named in the message |
| `gateCheck` fails right after a green gate | stamp | a hashed file changed or was staged after the gate; repeat from step 2 |

Fix the cause and run the gate again. Do not edit `gate-stamp.json`, and do not weaken a test, a
lint rule or the spec to get past a task.

## Pre-publish extras

The gate proves the module builds and tests; these are the conventions it does not check. The script
reports them as `WARN` because older gate-green modules do not all follow them.

- `publishConfig.registry` is `https://pkg.zerobias.org/` (the scaffold writes a different registry).
- `hub-sdk/package.json` is named `@zerobias-org/hub-sdk-<vendor>-<product>`; the module package is
  `@zerobias-org/module-<vendor>-<product>`.
- `repository.url` points at `zerobias-org/module`, not a fork; no `file:` dependencies.
- No version bump in the PR: the publish workflow bumps versions. The PR targets `dev`. See the
  `git-and-sdlc` skill.
- No `console.*`, raw `throw new Error(`, or temporary debug markers left in `src/`.
- No credentials, tokens or personal data in `src/`, `test/`, fixtures or the README; no `.env` file.
- `README.md` matches the module (see the `readme` skill).
- A new module implements the DataProducer interface before it ships (`dataproducer-implementation`
  skill): `src/impl/dataproducer/DataProducerImpl.ts` exists and the six getters are wired on the Impl.
  The gate does not check this.
- The module was published to the user's org (`zbb publishOrg`) and the user signed it off before the
  push; see "Org first, then the pull request" in the `git-and-sdlc` skill.

## The static check script

`${CLAUDE_SKILL_DIR}/scripts/check-module.sh <module-dir> [--final]`

- Reads files and asks git read-only questions. It does not run gradle, zbb or npm, so it passes on
  a fresh checkout with no `generated/` directory.
- `FAIL` (exit 1) is reserved for things that are wrong in every module: missing `.js` on relative
  imports, `process.env` in `src/`, a connector plugin without a profile, a `<Class>Connector`
  implementation in a profile-less module, no tests, `.only(` in tests, tracked build output or
  `.env`, `file:` dependencies, local-registry URLs in the lockfile, a stamp with a failed task or
  an empty `sourceHash`.
- `WARN` covers toolchain-version behaviour (the plain-module traps) and the pre-publish extras.
- `--final` turns a missing stamp and untracked files into `FAIL`. Use it at step 6.
- It prints the plugin, the detected kind and the test counts, so compare those with what you
  expect before reading the rest.

A clean script run is a reason to start the gate, not evidence that the gate passes. Report gate
status from the gate's exit code and the stamp, never from the script alone.
