# How this was built

> New modules are **generated**, not hand-authored. Build via the meta-repo:
> https://github.com/zerobias-org/zerobias -> `.claude/skills/create-connector/`
> (`/create-product` -> `/create-module` -> `/create-collector`).

- [ ] : Built via `/create-module` (meta-repo `create-connector` flow)
- [ ] : Brief path: `.connector/<vendor>-[<suite>-]<product>.md`
- [ ] : Base branch is `dev`

<!-- If any box above is unchecked, explain here BEFORE spending more effort.
     Hand-authored modules are very likely to be sent back. -->

# Related Issue
- Jira Ticket Link goes here

# External APIs
- Links to external APIs used by this Pull Request

# Checklist: 
Please make good use of [conventional commits](https://www.conventionalcommits.org/en/v1.0.0-beta.4/). 
* feat: new feature xyz
* fix: bug fix
* For breaking changes (major updates), BREAKING CHANGE must be at the beginning of the body or footer of the offending commit.

- [ ] : Verify all checklist items have been checked in Jira, and the issue moved to Code Review.
- [ ] : If adding a new module, ensure `package/<vendor>/<product>/build.gradle.kts` exists (auto-discovered by `settings.gradle.kts`).
- [ ] : Run `zbb gate` locally and commit `gate-stamp.json` — CI validates the stamp, it does not re-run tests.
- [ ] : Dev Pull Requests that require a bump to `1.0.0` will needs to be labelled as `premajor` before merging

When doing a `premajor` on a PR, the pull_request workflow will automatically set the version to a release candidate version.
Please, use `dry-run` before labelling as `premajor`, this will flush out any possiblity that other module version may be altered by the process. (i.e. as a side effect of a `master` merge)

`Pre-Majored` modules, will be graduated to their major version when merged into master.

# Catalog identity

- [ ] : `moduleId` in `package.json` matches an existing catalog module record — **not** a newly minted UUID. Verify that `store.Module.get(<moduleId>)` resolves.
- [ ] : Product wiring uses product dependencies + `x-product-infos`. There is no `packageNames` authoring field — that name appears in `store.Module.list` *responses* only.
- [ ] : `api.yml` was designed by the `/create-module` pipeline, not written by hand.

# Content safety

- [ ] : No files copied from a private auditlogic repo into this public repo.
- [ ] : No credentials, tokens, or customer data anywhere in the diff.

# Notes:

Please add notes that might be relevant to this Pull Request, if any,
