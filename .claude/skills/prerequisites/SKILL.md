---
name: prerequisites
description: Checks and explains the environment a module build needs in this repo - tools and where their versions are pinned, the npm registry and ZB_TOKEN, launching through a zbb slot and stack, what `zbb gate` requires (dataloader CLI, tokens, Docker), and the `package/<vendor>/[<suite>/]<product>` layout. Use as step 0 of /create-module, /add-operation, /fix-issue and /validate-gates, before starting module work, when setting up a machine, when asked "check prerequisites" or "am I set up", or when a build fails with 401, E401, "plugin not found", MISSING_ENV_VAR, a dataloader tool-check error, or a missing-tool error.
---

# Prerequisites

This repo is a gradle multi-project of ESM TypeScript (and a few Java) modules. `zbb` drives gradle and
injects credentials from a slot. There is no lerna, no npm workspace and no `npm run build`: each module
has its own `node_modules` and `package-lock.json`.

## Check first

Run the check from anywhere inside the repo. It only reads; it prints one line per item and exits
non-zero when a required item is missing.

```bash
bash "${CLAUDE_SKILL_DIR}/scripts/check-env.sh"
```

Fix what it reports before doing anything else, because every later failure (codegen, gate, publish) is
harder to read than a missing tool. The sections below explain each line of its output.

## A missing prerequisite pauses the work

When something required is missing there are two permitted actions: install or configure it (ask the
user first where consent or credentials are needed), or stop and wait for the user. Do not route
around it with substitute tooling, raw HTTP calls in place of the `zb` MCP, pasted keys, or a partial
continuation, because work done on a half-configured machine fails later in ways that hide the cause.

The same holds in the middle of a flow. A 401, an expired token, a refused org load or a tool that
disappears is a prerequisite regression: stop the phase, run the check again, and resume only when it
passes.

| Who you are | What applies |
|---|---|
| Platform path (you have a ZeroBias org) | Everything in this skill |
| External contributor (no ZeroBias account) | Tools, registry read access and the gate. The MCPs, the org-owner key and `publishOrg` do not apply: stop after the gate and open the PR; maintainers run the org verification. This is the one sanctioned reduction of scope. |

## Session: launch through a slot with a stack

All org credentials live in a zbb slot, on the shared `dev` stack, and this repo's stack (`zbb.yaml`,
name `module`) imports them. A session sees them only when it was started through the slot with a stack
context.

| Step | Command | Who runs it |
|---|---|---|
| One-time setup per org and environment | `./scripts/setup-org-credentials.sh` | The user, in a normal terminal, so keys never enter a Claude session |
| Start a session | `./scripts/setup-org-credentials.sh --launch [claude args]` or, from the repo root, `zbb --slot <slot> exec claude` | The user |
| Start from another directory | `zbb --slot <slot> --stack module exec claude` | The user |
| Check inside a session | `echo ${ZB_SLOT:-no-slot} ${ZB_ORG_ID:-no-stack}` | Claude |
| Check the MCPs (platform path) | `mcp__zb__*` and `mcp__zb-knowledge__*` tools are present; `zerobias_execute("meta.status")` reports the target org | Claude |

- `no-slot` means the session was not started through zbb; `no-stack` means it was started without a
  stack. In both cases MCP tools are missing or return 401, and builds cannot authenticate.
- The fix is to exit and relaunch. Environment is read once at startup, so reconnecting MCPs or
  exporting keys inside the session does not help, and pasting keys into the session leaks them into
  the transcript. Ask the user to relaunch instead.
- Do not `zbb env set` the imported variables (`ZB_TOKEN`, `ZB_API_KEY`, `ZB_ORG_ID`, `ZB_PLATFORM_URL`,
  `KNOWLEDGE_MCP_URL`, `NPM_CONFIG_TAG`) on the `module` stack: a per-stack value shadows the import, so
  a later key rotation on the `dev` stack stops reaching this repo.
- The setup script names the slot and reuses an existing slot that already holds the same org and
  environment. Use that slot; do not create ad-hoc ones such as `local`, because a second slot for the
  same org splits the credentials. `zbb slot list` shows what exists.
- `--slot` is a global zbb flag. It works before or after the subcommand (`zbb --slot <slot> gate`,
  `zbb gate --slot <slot>`); these skills write it before.

The full rules are in the root `CLAUDE.md`, section "Sessions, credentials & MCPs".

## Tools and where their versions are pinned

Read the version from the file named here rather than from memory; the pins move.

| Tool | Version source | Install / note |
|---|---|---|
| Node.js (your shell) | `.nvmrc` at the repo root | `nvm install && nvm use` in the repo root. Root `package.json` `engines.node` states the minimum. Use the pinned version for any `npm` command you run by hand, such as regenerating a `package-lock.json`. |
| Node.js (gradle tasks) | `nodeVersion` in `gradle.properties` | Gradle provisions this itself, so gradle tasks do not depend on the shell's Node. It can differ from `.nvmrc` in the patch number; that is expected. |
| Java | 21 | `zbb --slot <slot> ...` points `JAVA_HOME` at a Java 21 when the current one is not 21. Calling `./gradlew` directly needs Java 21 on your own `JAVA_HOME`. |
| zbb | latest published | `npm i -g @zerobias-org/zbb`; `zbb --version`, `zbb --help` |
| dataloader CLI | `tools.dataloader.version` in `zbb.yaml` (a minimum) | `npm i -g @zerobias-com/platform-dataloader` |
| Gradle | wrapper in the repo (`./gradlew`) | Nothing to install. Plugins `zb.*` resolve as `1.+` (see `settings.gradle.kts`). |
| Docker | running daemon | Needed for connector modules (image build, container tests) and for the generator's automatic build. |
| Yeoman + module generator | latest published: `npm view @zerobias-org/generator-module version` | `npm i -g yo @zerobias-org/generator-module`. Only for new modules; see the `scaffolding` skill. |
| jq | any | Used by the check scripts in these skills. |
| git | any recent | Commits are linted by commitlint (`.commitlintrc.json`); see the `git-and-sdlc` skill. |

The unpinned ZeroBias CLIs move fast and version skew fails in confusing ways. For `@zerobias-org/zbb`,
`@zerobias-com/zerobias-mcp` (the `zb` command) and `@zerobias-com/platform-dataloader`, compare the
installed version with `npm view <pkg>@latest version` (write `@latest`: the slot sets `NPM_CONFIG_TAG`,
which changes what a bare `npm view` resolves). When one is behind, update it with the user's consent
(`npm i -g <pkg>@latest`) or stop and wait. A version pinned in a repo file wins over this rule.

Global npm tools belong to one Node installation. If you switch Node with nvm, `zbb`, `dataloader` and
`yo` installed under another version disappear from `PATH`, and zbb's tool check for `dataloader` fails
even though it is installed. Install the globals under the Node version you run zbb with.

## Registry and tokens

Two different tokens are involved, and they fail in different places.

| What | Where it is configured | Used for | Symptom when missing |
|---|---|---|---|
| `ZB_TOKEN` (ZeroBias API key) | `.npmrc`: every `@zerobias-org`, `@zerobias-com`, `@auditlogic`, `@auditmation`, `@devsupply` scope points at `https://pkg.zerobias.org` with `_authToken=${ZB_TOKEN}` | `npm install`, `npm view`, publishing, and the dataloader step of the gate | `E401` / `ENEEDAUTH` from npm; `testDataloader` 401 |
| GitHub token with `read:packages` | Your shell profile. `settings.gradle.kts` takes the first one that is set of `READ_TOKEN`, `NPM_TOKEN`, `GITHUB_TOKEN` | Reading the `zb.*` gradle plugins from GitHub Packages Maven | Gradle fails at plugin resolution before any task runs |

- `ZB_TOKEN` comes from the slot; it is set by `setup-org-credentials.sh`, not by `npm login`.
- The GitHub token is personal and is not stored in the slot. `zbb exec` passes the host environment
  through, so it has to be exported in the shell that launches the session. Because the first variable
  that is set wins, an expired `READ_TOKEN` or `NPM_TOKEN` hides a valid `GITHUB_TOKEN`.
- Each module has `.npmrc` as a symlink to the repo root file (generator-made modules also link
  `.nvmrc`), so `npm` commands run inside a module use the same registry.
- Do not write tokens into `.npmrc`, `gradle.properties`, a module file or a skill. `gradle-ci.properties`
  holds vault references for CI only.

## What a gate needs

`zbb gate` in this repo is declared in `zbb.yaml` (`lifecycle.gate`, with `tools: [dataloader]`). Before
running it, all of these have to be true:

1. The session runs through a slot with the `module` stack (lifecycle commands need `zbb.yaml`, a
   loaded slot and the stack).
2. `dataloader` is on `PATH` and satisfies the minimum in `zbb.yaml`; zbb refuses to start `test` and
   `gate` otherwise.
3. `ZB_TOKEN` resolves. The gate's dataloader step authenticates with it against
   `DATALOADER_SERVICE_URL`. Leave that variable on its default from `zbb.yaml` even when the target org
   is on another environment: the token is issued by production, and an override makes it fail with 401.
4. The GitHub `read:packages` token is exported (plugin resolution).
5. Docker is running, for a connector module. A plain, profile-less module such as `package/nist/cmvp`
   (`"interface": true` in `package.json`) builds no image and runs no container test, so it needs no
   Docker.
6. The module's files are staged in git before the final gate run. The stamp hashes tracked files only;
   the `gates` skill explains this.

What the gate runs and how to verify a stamp belong to the `build-lifecycle` and `gates` skills.

Publishing privately to an org (`zbb publishOrg`) additionally needs `ZB_API_KEY` to be an org-owner key
(Organization Admin) of the target environment. A member key authenticates but cannot load artifacts, so
the flow would fail late, at the org load. The setup script verifies ownership (`isAdmin` on the
platform's who-am-I response) and refuses `--launch` when the check fails; inside a session,
`zerobias_execute("dana.Me.whoAmI")` shows the same field. Keys are per environment: a key issued by
one environment returns 401 on another.

## Repository layout

```
<repo root>                          package.json name "@zerobias-org/module"
├── zbb.yaml                         stack "module": imports, lifecycle, required tools
├── settings.gradle.kts              includes every package/** dir that has build.gradle.kts
├── build.gradle.kts, gradle.properties, gradle-ci.properties, gradlew
├── .npmrc, .nvmrc                   registry + Node pin (modules symlink to these)
├── scripts/setup-org-credentials.sh
├── .github/workflows/               publish.yml and branch sync; publishing and version bumps happen here
└── package/<vendor>/[<suite>/]<product>/
    ├── build.gradle.kts             one plugin line; makes the directory a gradle project
    ├── package.json                 "@zerobias-org/module-<vendor>-[<suite>-]<product>", ESM
    ├── package-lock.json            per module
    ├── api.yml                      OpenAPI spec, source of truth
    ├── connectionProfile.yml        connector modules only
    ├── hub-sdk/package.json         typed client package; its generated/ and dist/ are build output
    ├── src/                         flat; index.ts, <Class>Impl.ts, client, producers, mappers
    ├── test/unit/, test/e2e/        e2e only where the module can run it (see the `testing` skill)
    ├── gate-stamp.json              written by the gate, committed
    └── generated/, dist/, build/, node_modules/     ignored build output
```

- Depth is two or three segments under `package/`: `package/nist/cmvp`,
  `package/readyplayerme/readyplayerme`, `package/avigilon/alta/access`.
- The gradle project path mirrors the directory: `package/nist/cmvp` is `:nist:cmvp`,
  `package/avigilon/alta/access` is `:avigilon:alta:access`. Use it for a single-module task from the
  root: `./gradlew :nist:cmvp:gateCheck`.
- The plugin line in `build.gradle.kts` decides the module kind:

| Plugin | Kind | Example |
|---|---|---|
| `zb.typescript-connector` | TypeScript module with a connection profile | `package/readyplayerme/readyplayerme` |
| `zb.typescript` | Plain TypeScript module, no profile, `"interface": true` | `package/nist/cmvp` |
| `zb.java-module` | Java HTTP module | `package/x12/x12` |

- `package/interface/*` hold shared interface definitions (`zb.typescript-connector` together with
  `"interface": true`). Treat them as their own kind; they are not the model for a new product module.
- `connectionState.yml` exists only in modules whose tokens expire; `Dockerfile` only where a module
  brings its own image. Neither is part of a fresh scaffold.
- Modules do not import each other. Shared types come from the `@zerobias-org/types-core*` packages
  through npm, not from a directory in this repo.

## Related skills

- `scaffolding` - creating a module with the generator and fixing its output.
- `build-lifecycle` - the gradle tasks, `zbb <task>` pass-through, `build` versus `gate`.
- `gates` - the gate checklist, staging before the gate, the stamp.
- `testing` - `zbb secret` and e2e credentials.
- `git-and-sdlc` - branches, commits, pull requests to `dev`.
