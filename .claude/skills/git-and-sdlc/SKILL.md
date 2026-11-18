---
name: git-and-sdlc
description: Git and pull-request rules for this module repository - branch from dev, conventional commits with a module scope, selective staging, committing gate-stamp.json, no version bumps, PRs that target dev. Use when creating a branch, staging, writing a commit message, committing, pushing, or opening a pull request for a module.
---

# Git and SDLC

## The flow

1. Branch from `origin/dev`: `git fetch origin && git switch -c <type>/<module>-<topic> origin/dev`.
   Branch names in use: `feat/nist-cmvp`, `feat/module-x12-x12`, `fix/x12-review-followups`.
   Module work lands on `dev`; the other environment branches (`qa`, `uat`, `main`) receive it
   through promotion PRs and the sync workflow, so a branch cut from `main` has the wrong base.
2. Do the work. Commit in small conventional commits (format below).
3. Stage the package, then run the final gate, then stage and commit `gate-stamp.json`.
4. Ask the owner before each commit and before the push. Show the files and the message first.
5. Push the branch and open the PR against `dev`. The owner reviews and merges.

## Commit messages

`.commitlintrc.json` extends `@commitlint/config-conventional`, and the publish workflow derives
version bumps from the commit type (`feat` → minor, `fix` → patch, `BREAKING CHANGE` → major), so
the type decides what gets released.

```
<type>(<scope>): <subject>
```

- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`,
  `revert`.
- Scope: the module, written `<vendor>-<product>` as in the package path. Repository-level changes
  use the area (`gitignore`, `bundle`).
- Subject: imperative, lower case, no trailing period.

Examples from `git log origin/dev`:

```
feat(nist-cmvp): add CMVP registry module (supersedes #37)
fix(x12-x12): default ackDurability to full
test(x12-x12): port the hub-sdk e2e suite so testDocker runs tests
build(x12-x12): publish to pkg.zerobias.org; package .npmrc; pin interface 2.2.6
docs(x12-x12): element key format in ObjectTree javadoc
chore(x12-x12): gate stamp for fix/x12-buffer-migration
```

`chore(release): …` and `chore(bundle): … [skip ci]` commits are written by automation; do not
write them by hand.

## Staging and the gate stamp

Stage files by path (`git add package/<vendor>/[<suite>/]<product>/src/mappers.ts …`). `git add .` and
`git add -A` pick up build output, captured logs and other people's untracked files.

The gate stamp hashes the package through `git ls-files`, so an untracked file contributes nothing
to the hash. A stamp written before the package was staged does not match what CI sees. The order
is therefore:

```bash
git add package/<vendor>/[<suite>/]<product>/<every source, test, spec and config file>
zbb --slot <slot> gate                      # from the module directory; writes gate-stamp.json
git add package/<vendor>/[<suite>/]<product>/gate-stamp.json
(cd <repo root> && ./gradlew :<vendor>:[<suite>:]<product>:gateCheck)   # confirms the stamp matches the tree
```

Re-run the gate after any later change to a hashed file, including a file added late; a stamp
that changes after staging is expected. Commit the stamp, either with the change or as its own
`chore(<scope>): gate stamp …` commit. What the gate runs and what the stamp contains is in the
`gates` skill.

Not committed: `generated/`, `dist/`, `build/`, `node_modules/`, anything under
`.claude/.localmemory/`, captured API responses, and credentials of any kind.

## No version bumps in a PR

Leave `version` in `package.json` alone. The root `README.md` states it: no version bumps inside
of pull requests; the `version` job of the publish workflow bumps every changed module in one
commit on push to `main` / `qa` / `dev` / `uat`. A manual bump collides with that job. The same
applies to git tags, which the publish creates.

The one documented exception is the migration rule in the root `README.md`: a module that
pre-dates the gradle migration moves to `2.0.0` when it gets its `build.gradle.kts` marker.

## Pull requests

- Base branch `dev`. A `Promotion Order Check` workflow runs on every PR.
- Title in the same conventional format as a commit; when the PR is squash-merged it becomes the
  commit on `dev` (`feat(nist-cmvp): add CMVP registry module (supersedes #37) (#70)`).
- Body: what changed and why, how it was verified (gate result), and anything the reviewer has to
  decide. For an enum change, state the case described in the `mapping` skill.
- Preview what a publish would do without publishing: `zbb publish --dry-run` from the module
  directory.

## What needs the owner

- Ask before `git commit` and before `git push`. A commit or push is visible to others and
  triggers automation; approval for one commit does not cover the next.
- Do not merge a PR, and do not push to `dev`, `qa`, `uat` or `main` directly. Pushes to those
  branches publish packages.
- Ask before anything that discards work: `git reset --hard`, `git clean`, deleting a branch,
  force-pushing, rewriting pushed history.
- Leave files you did not change out of the commit, and leave other people's uncommitted work in
  the tree untouched.
