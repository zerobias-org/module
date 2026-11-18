---
name: build-lifecycle
description: Explains the gradle build of a module as it really runs and which zbb or gradlew command drives each step. Use when running or debugging `zbb generate`, `zbb compile`, `zbb lint`, `zbb validate`, `zbb build` or `zbb test`, when regenerating types after an `api.yml` edit, when a redocly or eslint step fails, when asking what `build` and `gate` actually run, where `generated/` and `hub-sdk/generated` come from, or what `"interface": true` changes.
---

# Build lifecycle

Every module is a gradle subproject built by the `build-tools` plugins and driven through `zbb`.
The lifecycle is not in `package.json`: the only script the gate-green modules keep there is `clean`.
This skill owns the task graph and the commands. The `gates` skill owns the done-checklist, the
stamp rules and what to do when the gate fails.

## How to run a task

- From the module directory: `zbb <task>`. zbb finds the gradle root, works out the subproject
  from the current directory and runs that project's task. Any gradle task name passes through
  (`zbb generate`, `zbb testDirect`, `zbb buildHubSdk`).
- From the repo root: `./gradlew :<vendor>:[<suite>:]<product>:<task>`. The project path mirrors
  the path under `package/` (`package/nist/cmvp` is `:nist:cmvp`, `package/avigilon/alta/access`
  is `:avigilon:alta:access`); `settings.gradle.kts` discovers every directory that has a
  `build.gradle.kts`.
- `--slot <name>` is a global zbb flag and may sit anywhere on the line:
  `zbb --slot <slot> gate`. Tasks that install packages, run tests or the dataloader need the
  slot's environment (`ZB_TOKEN` at least); see the `prerequisites` skill.
- To validate a committed stamp without building, run `./gradlew :<project>:gateCheck` from the repo
  root.

The plugin line in the module's `build.gradle.kts` selects the behaviour:
`zb.typescript-connector` (module with a `connectionProfile.yml`), `zb.typescript` (plain,
profile-less module such as `package/nist/cmvp`), `zb.java-module` (`package/x12/x12`).

## The task graph

Each line depends on the ones above it; indented names are the tasks that do the work, and are the
names you see in a failure.

```
validate        validateSpec        redocly lint of api.yml
                validateConnector   connector plugin only: connectionProfile.yml must exist
generate        assembleSpec → npmInstallModule → bundleSpec → dereferenceProductInfos
                → copyDistributionSpec → generateApi → postGenerate
lint            lintExec            eslint on src/ (needs node_modules, not dist/)
compile         transpile           tsc; depends on generate and on lint
  ├─ test             testUnit         mocha --recursive test/unit/         if the directory exists
  │                   testIntegration  mocha --recursive test/integration/  if the directory exists
  ├─ testDirect       mocha test/e2e/**/*.test.ts, in-process               if test/e2e exists
  ├─ testDocker       the same e2e suite against the module's container     if test/e2e exists
  ├─ testDataloader   dataloaderExec: loads the package through the dataloader CLI
  └─ buildArtifacts   buildHubSdk, buildOpenApiSdk, buildImage
```

Consequences worth knowing:

- Lint runs before `tsc`. A lint error is reported first and no `dist/` is produced.
- `compile` triggers `generate`, and `generate` triggers `validate`, so `zbb compile` after a spec
  edit regenerates by itself.
- `test` is unit plus integration only. The e2e suite runs in `testDirect` and `testDocker`.
- `syncMeta` (copies package.json metadata into the `api.yml` info block) is a separate task and is
  not part of `generate`.

## What `build` and `gate` run

| Command | Runs | Does not run |
|---|---|---|
| `zbb build` | `buildArtifacts` and what it depends on: validate, generate, lint, compile, hub-sdk, image | any test, the dataloader, the stamp |
| `zbb test` | `test` (unit + integration), after a dataloader tool check | e2e, dataloader load, artifacts |
| `zbb gate` | `verifyNoLocalRegistry`, `validate`, `lint`, `compile`, `test`, `testDirect`, `testDocker`, `testDataloader`, `buildArtifacts`, then `writeGateStamp` | publish |
| `./gradlew :<project>:gateCheck` | re-hashes the tree against the committed `gate-stamp.json` | every build and test task |
| `zbb publish --dry-run` | rehearses the publish graph without publishing | |

A green `zbb build` therefore says nothing about tests. Only `zbb gate` writes `gate-stamp.json`;
follow the `gates` skill for the order (stage first, then gate).

## `validate`: the redocly ruleset

`validateSpec` runs `@redocly/cli lint api.yml` with a config that build-tools writes to
`generated/redocly.yaml` on every run, the same for all modules:

- extends redocly's `minimal` ruleset
- errors: `struct`, `no-unresolved-refs`, `no-invalid-media-type-examples`
- switched off: `no-empty-servers`, `security-defined`, `operation-4xx-response`,
  `no-unused-components`

So a spec without servers, security schemes or declared 4xx responses passes; a `$ref` that does
not resolve, a structurally invalid document, or an example that does not match its schema fails.
A `.redocly.yaml` inside a module (`package/readyplayerme/readyplayerme/.redocly.yaml`) is not the
config the gate uses; it only matters if you run redocly by hand. There is no spectral step and no
`.spectral.yml`. The spec conventions the linter does not enforce are in the `api-spec` skill.

`$ref`s into `node_modules` (product catalog, `@zerobias-org/types-core` schemas) resolve only
after an install, which `generate` performs through `npmInstallModule`.

## Regenerating after an `api.yml` edit

1. Finish the edit pass, then run `zbb generate` from the module directory (or go straight to
   `zbb compile`, which includes it).
2. Read the new signatures in `generated/api/` and the models in `generated/model/`, then fix
   `src/` and `test/` to match. `generated/` is gitignored and rewritten on every run, so a
   hand-edit there is lost and hides the real problem in the spec.
3. Run `zbb compile`, then `zbb test`.
4. The stamp is now stale (`api.yml` is a hashed input); finish with the `gates` skill.

Three files decide the generated shape, so adding or removing one is a regeneration too:

| File | Effect on codegen |
|---|---|
| `connectionProfile.yml` present | generator runs with `isConnector=true`: `<Class>Connector` interface with the connect lifecycle, plus a `ConnectionProfile` model |
| `connectionProfile.yml` absent | plain `<Class>` interface, no lifecycle, no `ConnectionProfile` model (see `package/nist/cmvp/src/CmvpImpl.ts`) |
| `connectionState.yml` present | generator runs with `hasState=true` |

If the output looks stale (a removed operation still appears, a `$ref` you deleted still
resolves), clear it and regenerate: `zbb clean` removes `dist/`, `generated/` and `build/`; the
module's `npm run clean` also removes `hub-sdk/generated`, `hub-sdk/dist` and the bundled spec
files at the module root.

When `generateApi` fails, the message names the schema or operation; the fix is in `api.yml`.
When `bundleSpec` cannot resolve a `$ref`, check that the package it points into is a dependency
and is published.

## Where `hub-sdk/generated` comes from

Not from `generate`. `buildHubSdk` (part of `buildArtifacts`, so part of `build` and `gate`) runs
the generator a second time into `hub-sdk/generated/`, writes `hub-sdk/tsconfig.json`, and compiles
to `hub-sdk/dist/` when `hub-sdk/package.json` exists. After `zbb generate` or `zbb compile` alone
the directory is absent, and that is correct. Only `hub-sdk/package.json` is tracked.

## `"interface": true` and server generation

For a module that ships a Docker image, the build generates a REST server around the Impl
(`generateServerApi`, `generateServerEntry`, `compileServer`, `generateDockerfile`) and then
`buildImage`. Those server tasks run only when all three hold:

- package.json does not contain `"interface": true`;
- `buildImage` or `testDocker` is in the requested task graph (always true for `gate` and `build`);
- the module has no `Dockerfile` of its own (`package/x12/x12` ships one and owns its runtime).

`"interface": true` in package.json switches off the Docker side: server generation, `buildImage`,
starting and stopping the module container, and image publishing. It does not switch off the npm
package, the hub-sdk or the dataloader step. A plain, profile-less module needs it, because the
generated server expects a `ConnectionProfile` model that such a module does not have
(`package/nist/cmvp/package.json`). The limits this puts on e2e tests are in the `gates` skill.

## What is on disk

| Path | Produced by | In git |
|---|---|---|
| `generated/api/`, `generated/model/` | `generate` | no |
| `generated/api/manifest.json` | `generate`; listed in package.json `files`, so it is published | no |
| `dist/` (`dist/src/index.js` is package.json `main`) | `compile` | no |
| `hub-sdk/generated/`, `hub-sdk/dist/`, `hub-sdk/tsconfig.json` | `buildHubSdk` | no |
| `build/` | gradle | no |
| `eslint.config.js`, `module-*.yml`, `full.yml` at the module root | written temporarily by lint and the spec tasks | no |
| `gate-stamp.json` | `gate` | yes |
| `package-lock.json` | npm install (the `npmInstallModule` task) | yes |

The ignore list is the module's `.gitignore` (see `package/nist/cmvp/.gitignore`).

## Command reference

Run from the module directory. Add `--slot <slot>` unless the session was started through the slot.

| Goal | Command |
|---|---|
| Lint the spec | `zbb validate` |
| Regenerate types | `zbb generate` |
| Lint the source | `zbb lint` |
| Compile (includes generate and lint) | `zbb compile` |
| Unit and integration tests | `zbb test` |
| Unit tests only | `zbb testUnit` |
| e2e in-process | `zbb testDirect` |
| e2e against the container (connector) | `zbb testDocker` |
| Dataloader load test | `zbb testDataloader` |
| Hub SDK only | `zbb buildHubSdk` |
| All artifacts, no tests | `zbb build` |
| Full gate and stamp | `zbb gate` |
| Is the committed stamp still valid | `./gradlew :<project>:gateCheck` (repo root) |
| Remove build output | `zbb clean` |
| Sync package.json metadata into `api.yml` | `zbb syncMeta` |
| Rehearse a publish | `zbb publish --dry-run` |
| Same task from the repo root | `./gradlew :<vendor>:[<suite>:]<product>:<task>` |
| What zbb itself offers | `zbb --help` |

Secrets, slots and environment commands are in the `prerequisites` and `testing` skills; publishing
and `publishOrg` are in the `git-and-sdlc` skill.
