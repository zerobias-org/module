# What the generator writes

Reference for the `scaffolding` skill. Read it when reviewing a fresh scaffold, when a module's
`tsconfig.json`, `package.json` or `src/index.ts` looks different from its siblings, or when deciding
whether an edit to one of those files is a deviation.

## Contents

- [Names derived from the module package](#names-derived-from-the-module-package)
- [File list](#file-list)
- [package.json](#packagejson)
- [hub-sdk/package.json](#hub-sdkpackagejson)
- [tsconfig.json](#tsconfigjson)
- [src/index.ts](#srcindexts)
- [Not scaffolded](#not-scaffolded)

## Names derived from the module package

Everything is computed from `modulePackage`. Example: `@zerobias-org/module-nist-cmvp`.

| Value | Rule | Example |
|---|---|---|
| Directory | text after `module-`, every hyphen becomes a path segment, under `package/` | `package/nist/cmvp` |
| Gradle project | the same segments joined with `:` | `:nist:cmvp` |
| Module name | text after `module-` | `nist-cmvp` |
| `<name>` | last hyphen segment | `cmvp` |
| `<Class>` | `<name>` with the first letter upper-cased | `Cmvp` |
| `zerobias.package` | segments joined with `.` plus `.module` | `nist.cmvp.module` |
| `moduleId` | a new random UUID | - |
| `x-impl-name` in `api.yml` | `<name>` | `cmvp` |

Because every hyphen is a path separator, a product name cannot contain a hyphen of its own.
`<Class>` comes from one lower-case segment, so a multi-word product gets a flat class name
(`readyplayerme` gives `Readyplayerme`). `package/readyplayerme/readyplayerme` uses `ReadyPlayerMe`
instead, through `x-impl-name` in its `api.yml`; the `api-spec` skill owns that field.

## File list

| File | Content | Connector | Plain |
|---|---|---|---|
| `build.gradle.kts` | one plugin line | `zb.typescript-connector` | `zb.typescript` |
| `package.json` | see below | `files` includes `connectionProfile.yml` | no profile entry |
| `hub-sdk/package.json` | typed client package manifest | yes | yes |
| `api.yml` | OpenAPI 3.0.3, empty `paths`, `x-product-infos` `$ref` into the product package's `catalog.yml` | yes | yes |
| `connectionProfile.yml` | plain `type: object` with one `apiToken` property | yes | not written |
| `tsconfig.json`, `.mocharc.json`, `.gitignore` | see below; mocha runs TypeScript through `tsx/esm` | yes | yes |
| `src/<Class>Impl.ts` | stub that `implements <Class>Connector` with the five lifecycle methods | yes | written identically, which is wrong for a plain module |
| `src/index.ts` | factory + generated re-exports | yes | yes |
| `test/unit/<Class>ApiTest.ts` | one test: `new<Class>()` returns an instance | yes | yes |
| `test/e2e/constants.ts`, `test/e2e/<name>.test.ts` | `describeModule<...>` skeleton, no tests inside | yes | written, to be removed |
| `README.md` | short Authentication / Usage / Test page | yes | yes, without the credentials block |
| `.nvmrc`, `.npmrc` | symlinks to the repo root files, depth computed from the directory | yes | yes |

`.gitignore` covers `node_modules/`, `dist/`, `generated/`, `hub-sdk/generated/`, `hub-sdk/dist/`,
`hub-sdk/tsconfig.json`, `build/`, `docs/` and the transient build files (`module-*.yml`, `full.yml`,
`eslint.config.*`, `test-results.json`).

## package.json

The shape to keep (compare with `package/nist/cmvp/package.json`):

- `"type": "module"` and `"main": "dist/src/index.js"`. The path contains `src/` because `outDir` is
  `dist` and the compile includes `src/`, `test/` and `generated/`.
- `scripts` has only `clean`. Build, test and publish are gradle tasks, so there is nothing to run
  through `npm run`.
- `files`: `dist`, `generated/api/manifest.json`, `api.yml`, plus `connectionProfile.yml` for a
  connector (and `connectionState.yml` when the module has one, as in `package/avigilon/alta/access`).
  The dataloader reads the manifest and the spec from the published package.
- `zerobias`: `package`, `dataloader-version`, `import-artifact: "module"`. Together with `moduleId`
  this identifies the module to the dataloader; do not regenerate `moduleId`.
- `dependencies`: the product package at `latest`, `@zerobias-org/logger`, `util-connector`,
  `util-hub-module-utils`. `peerDependencies`: `types-core` and `types-core-js`. The generator writes
  the current version lines; do not copy version numbers from an older module.
- `"interface": true` is not written by the generator. Add it for a plain module only (see the
  post-scaffold list in `SKILL.md`).
- No top-level `exports`; the module is consumed through `main`.

## hub-sdk/package.json

The typed client other code uses to call the module. It has its own name, `exports` map (`.`, `./api`,
`./model`) and peer dependencies. Its `generated/`, `dist/` and `tsconfig.json` are produced by the
build (the `build-lifecycle` skill says which task), so they do not exist right after a
`--no-install` scaffold and are ignored by git.

## tsconfig.json

The generator's file is the one in `package/nist/cmvp/tsconfig.json`. The settings that matter:

| Setting | Why |
|---|---|
| `module` and `moduleResolution`: `NodeNext` | The package is ESM. Relative imports must end in `.js` (`./CmvpImpl.js`, `../generated/api/index.js`); package imports do not. |
| `target` / `lib`: `ES2022` | Matches the Node runtime; no down-levelling. |
| `strict: true`, `noImplicitAny: false` | Strict checks on module code, relaxed for the loosely typed generated code. |
| `useUnknownInCatchVariables: false` | `catch (e)` stays `any`, so caught HTTP errors can be inspected without casts. |
| `declaration: true`, `outDir: dist` | Types ship with the package; `main` points into `dist/src/`. |
| `include`: `src`, `test`, `generated` | Tests and generated code compile with the sources, so a missing generated type fails the compile. |
| `exclude`: `dist`, `node_modules` | `package/readyplayerme/readyplayerme` and `package/avigilon/alta/access` also exclude `hub-sdk`; both forms pass the gate. |

Leave the file as generated. If a module needs another option, add only that option and say why in the
pull request, because every module is compiled by the same gradle plugin with the same expectations.

## src/index.ts

```typescript
import { <Class>Impl } from './<Class>Impl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

export function new<Class>() {
  return new <Class>Impl();
}
```

- The factory `new<Class>()` is the entry point: the scaffolded unit test and consumers call it.
  Keep the name.
- The two wildcard re-exports give consumers the generated API interfaces and models. Replacing them
  with a hand-picked list breaks consumers each time the spec gains a model.
- Named exports only.
- An explicit return type on the factory is optional: `package/readyplayerme/readyplayerme/src/index.ts`
  declares `ReadyPlayerMeConnector`, `package/nist/cmvp/src/index.ts` lets it be inferred.
- `package/nist/cmvp` exports nothing else from `src/` (no client, producers or mappers). Tests that
  need an internal file import it by path, as `test/unit/CertificateProducerTest.test.ts` does with
  `../../src/CmvpClient.js`.

## Not scaffolded

Added later, only when the design calls for them: `connectionState.yml`, `Dockerfile`, producers,
`src/<Class>Client.ts`, `src/mappers.ts`, `src/util.ts`, fixtures, `package-lock.json`,
`gate-stamp.json`. The `implementation`, `mapping`, `testing` and `gates` skills own those.
