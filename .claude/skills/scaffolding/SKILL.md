---
name: scaffolding
description: Scaffolds a new module with the Yeoman generator (`yo @zerobias-org/module`) and repairs its output - arguments, connector versus plain (profile-less) module, the files it writes, and the post-scaffold fix list (publishConfig registry, hub-sdk package scope, nock, `"interface": true` and the Impl stub for plain modules). Use when creating a new module, when asked to "scaffold" or "generate" a module, when the generator fails, or when a fresh module does not compile or its `tsconfig.json`, `package.json` or `src/index.ts` needs review.
---

# Scaffolding a module

The generator `@zerobias-org/generator-module` (run as `yo @zerobias-org/module`) writes the module
skeleton. Its output is close to, but not exactly, what this repo's gate accepts, so scaffolding is two
steps: run the generator, then apply the fix list below. Do both before designing the API.

## 1. Decide connector or plain

| The source API | `moduleType` | Result |
|---|---|---|
| needs credentials (token, OAuth, basic) | `connector` | `zb.typescript-connector`, `connectionProfile.yml`, connect lifecycle, image and container tests. Example: `package/readyplayerme/readyplayerme` |
| is public, no credentials | `plain` | `zb.typescript`, no profile, no lifecycle, no image. Example: `package/nist/cmvp` |

The `connection-profile` skill owns this decision and the profile design. The choice matters here because
the generator's plain output needs more repair than its connector output.

## 2. Before running

- The environment passes the `prerequisites` check, and the installed generator is the latest published
  one (`npm view @zerobias-org/generator-module version`). Older versions write outdated dependency
  lines.
- The current directory is the repo root. The generator refuses to run unless the `package.json` there
  has a name ending in `/module`.
- The product package is published: `npm view @zerobias-org/product-<vendor>-[<suite>-]<product> version`.
  The scaffold depends on it at `latest`, and `api.yml` references its `catalog.yml`.
- `package/<vendor>/[<suite>/]<product>` does not exist yet. The generator aborts on an existing path
  rather than merging.
- You are on a feature branch cut from `dev` (see the `git-and-sdlc` skill).

## 3. Run the generator

```bash
yo @zerobias-org/module \
  --productPackage '@zerobias-org/product-<vendor>-[<suite>-]<product>' \
  --modulePackage  '@zerobias-org/module-<vendor>-[<suite>-]<product>' \
  --packageVersion '0.0.0' \
  --description    '<Display name>' \
  --repository     "$(git config remote.origin.url)" \
  --author         "$(git config user.email)" \
  --moduleType     'connector' \
  --no-install
```

| Argument | Rule |
|---|---|
| `productPackage` | Contains `/product-`; products live in `@zerobias-org`. |
| `modulePackage` | Contains `/module-`. Pass it explicitly with the `@zerobias-org` scope: every module in this repo is `@zerobias-org/module-*`, while the generator's default is another scope. It prints a warning about the scope; that warning does not apply to this repo. |
| `packageVersion` | `0.0.0`. Versions are bumped by the publish workflow, never in a pull request. |
| `description` | The product's display name; it becomes the `description` in `package.json` and `api.yml`. |
| `repository`, `author` | Read from git config as shown, so nothing personal is typed into a command or a skill. |
| `moduleType` | `connector` or `plain`. |
| `--no-install` | Skips the automatic gradle build (and the Docker check that goes with it). |
| `--no-links` | Skips the `.nvmrc` / `.npmrc` symlinks. Rarely wanted. |

Any argument left out is asked interactively, which blocks a headless run; pass all seven.

The directory, class name, gradle project and package ids are derived from `modulePackage`: every
hyphen after `module-` becomes a path segment, and the last segment becomes `<name>` and `<Class>`.
`@zerobias-org/module-nist-cmvp` gives `package/nist/cmvp`, `:nist:cmvp`, `CmvpImpl`.

Why `--no-install`: without it the generator runs `./gradlew :<vendor>:[<suite>:]<product>:build` from
the repo root as its last step and requires a running Docker daemon. For a plain module that build
cannot pass, because the emitted stub does not compile until fix 5 and 6 are applied. For a connector
it can pass, but it runs before the fixes. Scaffold without it, fix, then build once.

## 4. Post-scaffold fix list

Apply these by hand right after the generator returns. Each one is a known gap between the generator
template and this repo.

All modules:

1. `package.json` → `publishConfig.registry`: change to `https://pkg.zerobias.org/`. The template writes
   the GitHub Packages URL, but this org publishes to and installs from `pkg.zerobias.org` (see the root
   `.npmrc`).
2. `hub-sdk/package.json` → `name`: change the scope to `@zerobias-org/hub-sdk-<vendor>-[<suite>-]<product>`.
   The template writes another org's scope, and the SDK is published under the name found here.
3. `package.json` → `devDependencies`: add `nock` (exact version, as in `package/nist/cmvp/package.json`).
   The template has none, and unit tests mock HTTP with it; the `testing` skill covers its use.
4. `package.json` → `repository.url`: confirm it is this repo's URL and not a fork's, since it is
   published with the package.

Plain modules only (connector modules skip these):

5. `package.json`: add `"interface": true` (next to `moduleId`, as in `package/nist/cmvp/package.json`).
   It is the switch that tells the build not to generate a server or build an image; without it the
   build tries to compile a server entry that expects a connection profile.
6. `src/<Class>Impl.ts`: replace the stub. The generator writes the connector version for both types:
   it `implements <Class>Connector`, imports `ConnectionProfile` and defines `connect`, `disconnect`,
   `metadata`, `isConnected`, `isSupported`. A plain module's generated interface is `<Class>` with no
   lifecycle. Model the file on `package/nist/cmvp/src/CmvpImpl.ts`.
7. Delete `test/e2e/`. The skeleton uses `describeModule`, which works through a connection profile,
   and the gate's container test step fails when `test/e2e` exists in an `"interface": true` module.
   A plain module is covered by unit tests that mock the real host; see the `testing` skill.
8. `README.md`: the Authentication section should say that none is needed; see the `readme` skill.

Connector modules keep `connectionProfile.yml` (a stub with one `apiToken` property, to be designed
with the `connection-profile` skill), the lifecycle stub (to be implemented with the `implementation`
skill) and `test/e2e/`.

Then, for both types:

9. Build once from the repo root: `./gradlew :<vendor>:[<suite>:]<product>:build`. It needs the tokens
   and Java version described in the `prerequisites` skill; the `build-lifecycle` skill lists the zbb
   forms and what the build produces (`generated/`, `dist/`, `hub-sdk/generated/`).
10. `git add package/<vendor>/[<suite>/]<product>` before any gate run that is meant to be committed.
    The gate stamp hashes only files git tracks, so a stamp produced while the new module is untracked
    cannot pass the stamp check later. The `gates` skill explains the stamp.

## 5. Verify

```bash
bash "${CLAUDE_SKILL_DIR}/scripts/check-scaffold.sh" package/<vendor>/[<suite>/]<product>
```

It checks that the generator's files exist, the symlinks resolve, and every item of the fix list is
applied for the module's type, and it reports untracked files. Lines starting with `FIX` need an edit;
exit code 0 means the scaffold is ready for API design. The script only reads.

The scaffold is done at this point. The stub `api.yml` has empty `paths` and the Impl has no producers;
those belong to the `api-spec` and `implementation` skills.

## When the generator fails

| Message | Cause and action |
|---|---|
| `You must run this generator from the root of a package` | Not at the repo root. Change directory and rerun. |
| `Docker is not running...` | The automatic build was requested. Rerun with `--no-install`, or start Docker. |
| `Path ... already exists, aborting.` | The module directory exists. Work in the existing module; do not delete someone else's work to rescaffold. |
| `Product package must be in the format...` / `Module package must be in the format...` | The name lacks `/product-` or `/module-`. |
| `No gradlew at ...` | Wrong repository. |
| `Gradle build failed (exit N)` | The files were written; only the automatic build failed. Do not rescaffold. Apply the fix list, then build and read the gradle output. |

## What it writes

Thirteen files, plus `connectionProfile.yml` for a connector and two symlinks: `build.gradle.kts`,
`package.json`, `hub-sdk/package.json`, `api.yml`, `tsconfig.json`, `.mocharc.json`, `.gitignore`,
`README.md`, `src/<Class>Impl.ts`, `src/index.ts`, `test/unit/<Class>ApiTest.ts`,
`test/e2e/constants.ts`, `test/e2e/<name>.test.ts`, and `.nvmrc` / `.npmrc` links to the repo root.

Three of them are configuration that stays as generated for the life of the module:

- `tsconfig.json`: ESM with `NodeNext`, so every relative import ends in `.js`; output goes to `dist/`.
- `src/index.ts`: the `new<Class>()` factory plus wildcard re-exports of `generated/api` and
  `generated/model`.
- `package.json`: `"type": "module"`, `"main": "dist/src/index.js"`, one script (`clean`), the `files`
  list and the `zerobias` block with `moduleId`.

Read `references/generated-files.md` for the full file table, how names are derived, the reason for
each `tsconfig.json` setting, the `src/index.ts` and `package.json` export shape, and what is
deliberately not scaffolded. Read it before changing any of those three files.

## Related skills

- `prerequisites` - tools, tokens, slot launch, repo layout.
- `connection-profile` - connector or plain, and the profile itself.
- `api-spec`, `implementation`, `testing` - what fills the skeleton.
- `build-lifecycle`, `gates` - building, the gate and the stamp.
