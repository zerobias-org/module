# Claude Instructions

> [!IMPORTANT]
> **Do not hand-author modules in this repo.**
>
> Modules are **generated**, never written by hand. New modules for `zerobias-org` are
> built through the meta-repo:
>
> **https://github.com/zerobias-org/zerobias** -> `.claude/skills/create-connector/`
>
> That skill orchestrates `/create-product` -> `/create-module` -> `/create-collector`.
> Part 2 runs `/create-module` here, which invokes `yo @zerobias-org/module` and the
> specialist-agent pipeline described below. Do **not** bypass it with a manual `yo`
> call, and do not write `package.json` / `api.yml` by hand.
>
> Two things a hand-authored module always gets wrong:
>
> - **`moduleId`** is the id of the catalog's module record, issued by the platform.
>   Minting a UUID produces a module that resolves to nothing.
> - **`packageNames`** is not an authoring field. It appears in `store.Module.list`
>   *responses* only. Multi-product wiring is product dependencies + `x-product-infos`.
>
> Base branch is **`dev`**, not `main`.
>
> **Who contributes where.** `zerobias-org` is the contribution surface for the
> zerobias.com catalog; auditlogic is internal. Contributor location follows *who is
> contributing*, not whether a package is new — and content never moves from a private
> auditlogic repo into a public `zerobias-org` one.


## Architecture

**Flat, two-level structure:** You → Specialized worker agents (no intermediate orchestration)

## Core Principles

1. **NEVER work independently** - always invoke the appropriate agent
2. **DELEGATE planning** - use `api-researcher` + `product-specialist` for analysis
3. **COORDINATE simply** - run workflows and validation gates, but let agents do the work

## How It Works

1. User request → Identify workflow from `.claude/commands/`
2. Delegate planning to `api-researcher` + `product-specialist`
3. Invoke worker agents directly based on their analysis
4. Run validation gates sequentially

## Key Delegations

**Planning & Analysis:**
- API discovery, endpoint mapping → `api-researcher`
- Business requirements, priorities → `product-specialist`
- Operation prioritization → Both together
- Authentication research → `credential-manager`

**Validation Gates (run sequentially):**
1. API Spec → `api-reviewer`
2. Type Gen → `build-validator`
3. Implementation → Simple checks (grep)
4. Test Creation → `ut-reviewer` + `it-reviewer`
5. Test Execution → `npm test`
6. Build → `build-reviewer`

## Directory Structure

```
.claude/
├── agents/        # Worker agent definitions
├── commands/      # Workflow specifications
├── rules/         # Rule files (agents load what they need)
└── .localmemory/  # Temporary work storage (never commit)
```

## Key Rules

- Flat structure - no orchestration layers
- Each agent has exclusive responsibilities
- Agents load rules via `@.claude/rules/`
- Memory in `.claude/.localmemory/{workflow}-{module}/`
- When user updates a rule, update it FIRST

The agents handle everything else.
---

## Sessions, credentials & MCPs — slot-first

<!-- Synced section: identical in vendor, suite, product, module.
     The zerobias meta-repo's CLAUDE.md carries the same rules in its
     own words. Edit in one repo, copy to all. -->

All org credentials (platform ORG key, registry key, org/env identity)
live in a **zbb slot**; Claude Code sessions are launched THROUGH the
slot so the committed `.mcp.json` templates (`${VAR}` refs — no
secrets) and the zb `env` profile resolve that identity.

- **One-time setup (per org/env):** the user runs
  `./scripts/setup-org-credentials.sh` themselves in a normal terminal
  (never inside a Claude session). Check-first and re-runnable: it
  creates the slot (`<env>-<org-prefix>`), stores the keys, and wires
  `~/.npmrc` + the zb profile.
- **Launch:** `./scripts/setup-org-credentials.sh --launch [args…]`,
  or `zbb --slot <slot> --stack <stack> exec claude` from anywhere
  (`<stack>` = this repo's `zbb.yaml` `name:` short form, e.g.
  `vendor` in the vendor repo); from this repo's root plain
  `zbb --slot <slot> exec claude` works too (cwd infers the stack).
  NEVER launch stackless from outside a `zbb.yaml` directory: a slot
  holds NO user vars of its own (only `ZB_SLOT*` identity) — every
  credential is **stack-scoped**, and lives ONCE per slot on the
  shared `dev` stack (`@zerobias-org/dev-stack`); this repo's stack
  imports it (see `zbb.yaml` depends/imports), so the setup script
  seeds only the dev stack and every content stack resolves the same
  creds transitively. Never `env set` those vars on a content stack —
  a per-stack override shadows the import and rotation stops
  propagating there. `zbb --slot <slot> --stack dev exec claude`
  launches a creds-only session from anywhere (MCPs work; repo gates
  still need the repo's own stack). Add `--continue` to resume the
  previous session under another slot (sessions are keyed by cwd,
  not by slot).
- **Missing MCP tools / 401 / `MISSING_ENV_VAR` / `NOT SET`** means
  the session wasn't launched through a slot WITH a stack context.
  Check inside the session: `echo ${ZB_SLOT:-no-slot} ${ZB_ORG_ID:-no-stack}`
  (`no-slot` = not launched through zbb; `no-stack` = launched
  stackless). Fix the launch — exit and relaunch; `/mcp` reconnect can
  never pick up new env (it is captured once at claude startup). Do NOT
  register MCPs with pasted literal keys (a baked key silently
  overrides every slot identity, connecting as the wrong org) and do
  NOT export creds into the session as a workaround.
- **Multi-org / multi-env = one slot each**, chosen at launch time;
  switching identity means restarting claude through the other slot
  (env is read once at startup). A second IDENTITY (another API key)
  for the same org gets its own named slot too — a preset `SLOT` skips
  the reuse-by-content scan:
  `SLOT=<name> ZB_API_KEY=<other-key> ./scripts/setup-org-credentials.sh`.
  With several slots holding one org, always pass `--slot` explicitly —
  the auto-reuse scan just takes the first match.

Deep dive: the meta-repo's
[docs/MCPs.md](https://github.com/zerobias-org/zerobias/blob/main/docs/MCPs.md).

## Windows — WSL2 only

Everything here runs only on Ubuntu (`zbb` fails on native Windows).
On Windows, work inside WSL2 end-to-end — user walkthrough:
[docs/WindowsWSLSetup.md](https://github.com/zerobias-org/zerobias/blob/main/docs/WindowsWSLSetup.md).

- **If this session runs on NATIVE Windows** (prompt `PS C:\`, paths
  under `C:\` or `/mnt/c/...`): your ONLY job is getting WSL2 + Ubuntu
  installed. Refuse repo work — no cloning, editing, git, or builds —
  and point the user to their WSL session. Never relay work between a
  Windows agent and a WSL agent.
- **In WSL:** logins and credential setup happen in the Ubuntu
  terminal (`gh auth login`, claude's first-run login,
  `setup-org-credentials.sh`). Once setup is green, offer Remote
  Control (`/remote-control`, or `--launch --remote-control`) to
  continue from the Claude desktop / mobile app.
