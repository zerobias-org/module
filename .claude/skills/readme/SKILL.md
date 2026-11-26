---
name: readme
description: Writes or updates the README.md of a module package from a template modelled on the NIST CMVP module README. Use when creating a module README, documenting a module's authentication, usage, operations or test commands, or reviewing a README before the PR.
---

# Module README

Every module has a `README.md` in its package directory; the generator writes a three-section
stub (Authentication and authorization, Usage, Test) and this skill turns it into something a
consumer can use. The model is `package/nist/cmvp/README.md`.

## Steps

1. Read the module first: `package.json` (`name`), `api.yml` (operations, tags, `x-method-name`),
   `src/index.ts` (the factory name, for example `newCmvp`), and whether `connectionProfile.yml`
   exists. The README describes what these files say, so every name in it comes from them.
2. Copy `${CLAUDE_SKILL_DIR}/assets/README.template.md` over the generated stub and fill the
   placeholders in angle brackets.
3. Keep one Authentication variant and delete the other (see below).
4. Delete template comments and any section that has nothing to say. An empty heading is worse
   than a missing one.
5. Stage the README together with the rest of the package (see the `git-and-sdlc` skill).

## Sections

| Section | Content | Source of truth |
|---|---|---|
| Title and intro | Package name in backticks and what the module reads or controls, in two or three sentences | `package.json` `name` and `description` |
| Installation | `npm install <package name>` plus the Node and registry line | `package.json`; root `README.md` for `ZB_TOKEN` |
| Authentication | "None" for a plain module, or the profile fields for a connector | `connectionProfile.yml` |
| Usage | One short runnable snippet: factory, API getter, one list call, one get call | `src/index.ts`, generated API |
| Operations | One table row per operation: `operationId`, the method a caller uses, where the data comes from | `api.yml` |
| Examples | Optional; only what Usage does not show, typically error handling | |
| Testing | The zbb commands that work for this module and what they run against | the `testing` skill |

The package name is `@zerobias-org/module-<vendor>-<product>`, built from the path under
`package/`: `package/nist/cmvp` is `@zerobias-org/module-nist-cmvp`, and a three-level path such
as `package/avigilon/alta/access` is `@zerobias-org/module-avigilon-alta-access`. Copy it from
`package.json` instead of deriving it.

## Authentication: connector or plain

A connector (it has `connectionProfile.yml` and `build.gradle.kts` applies
`zb.typescript-connector`) documents each profile field in a table: name, whether it is required,
what it is and where the user obtains it. Take the fields from `connectionProfile.yml`; see
`package/readyplayerme/readyplayerme/connectionProfile.yml` for a two-field example. Show
`connect()` with placeholder values.

A plain / profile-less module (no `connectionProfile.yml`, `zb.typescript`, like
`package/nist/cmvp`) writes "None" and says why in one sentence: the source is public, there is no
connection profile and no `connect()` lifecycle. Do not leave the section out; a reader looking
for credentials needs the explicit answer.

For credential setup that takes more than a few lines (creating an API key in a vendor console,
OAuth app registration), a separate `USERGUIDE.md` next to the README is an option, linked from
the Authentication section. It is not required: a module without credentials has nothing to put
in one.

## Testing section

Use the commands as they are run from the module directory:

```bash
zbb --slot <slot> test         # unit tests
zbb --slot <slot> testDirect   # e2e, in-process        (connectors with test/e2e)
zbb --slot <slot> testDocker   # e2e, against the image (connectors with test/e2e)
zbb --slot <slot> gate         # full gate (writes gate-stamp.json; commit it)
```

List only the commands that do something for this module. A plain module without `test/e2e`
keeps `zbb test` and `zbb gate`, and says how to verify against the live source by hand, as the
cmvp README does. Say what the tests need (fixtures and nock for unit tests; which slot secret the
e2e suite reads), and leave the how-to of secrets to the `testing` skill.

## Rules

- No credentials and no `.env` instructions. Secrets live in the zbb slot; a README that tells the
  reader to put a token in a file teaches the pattern the repository avoids. Snippets use
  placeholders, not `process.env` reads of real secrets.
- Snippets use real names from this module: the factory from `src/index.ts`, API getters and
  method names from the generated API, model properties from `api.yml`. A snippet with invented
  names is the most common README defect because nothing compiles it.
- A list call returns `PagedResults`; read `page.items` and `page.count`, not `.length` on the
  result.
- Do not state the license, the Node patch version or dependency versions; they live in
  `package.json` and `.nvmrc` and drift in prose.
- Do not hand-edit generated API reference text. Some READMEs (`package/readyplayerme/readyplayerme`,
  `package/avigilon/alta/access`) carry a doctoc-marked "Apis / Models" reference below the
  hand-written part; leave it as it is and write above it.
- Write the README when the module is created or its public surface changes (new operation,
  changed authentication). Other documentation files are written only when the owner asks for them.

## Before the PR

- [ ] Package name in the title, the install line and every import matches `package.json`.
- [ ] Authentication says "None" or lists every field of `connectionProfile.yml`.
- [ ] Every `operationId` in `api.yml` has a row in Operations, and no row names an operation that
      is gone.
- [ ] Testing lists only commands that run for this module.
- [ ] No token, password, personal e-mail address or `.env` reference anywhere in the file.
