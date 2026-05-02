# Module mono-repo
Monorepo of modules. 

<!-- For module development, see [the SDK documentation]( link to hub docs). -->

## NPM Packages authentication
**ZB_TOKEN**

Set `ZB_TOKEN` in your environment variables to authenticate with npm registry.
`ZB_TOKEN` needs to be an API key from [ZeroBias](https://app.zerobias.com).

## Getting Started

## Commit conventions and Version management

### Versioning: gradle + zbb

Module versions are managed by **gradle** through **zbb**. Lerna and `lerna.json` have been removed — the `version` job in the reusable publish workflow handles all bumps.

* Per-module marker: `package/<vendor>/<product>/build.gradle.kts` (one-line `plugins { id("zb.module") }`).
* Auto-discovery: `settings.gradle.kts` walks `package/**` and includes any directory with the marker.
* Bumps are conventional-commit driven (feat → minor, fix → patch, BREAKING CHANGE → major).
* The `version` job in the reusable workflow bumps every changed module's version and pushes ONE commit before the per-module matrix fans out — eliminates the historical pushVersion race.
* No version bumps inside of pull requests; let CI do it on push to `main`/`qa`/`dev`/`uat`.

#### Migration major-bump rule

Modules that pre-date the gradle migration are on `1.x.x` (or older). When migrating a module to gradle (adding the `build.gradle.kts` marker), bump to `2.0.0` so the version line is unambiguous about which lifecycle owns the package. Skip the bump for modules already on `2.x`. Same rule applied across `org/vendor`, `org/suite`, `zerobias-com/tag`.

#### Local dry-run

```bash
# From the module dir
zbb publish --dry-run
# Or from repo root
./gradlew :<vendor>:<product>:publish -PdryRun=true
```

### GitHub Actions workflows

The repo has **one** workflow — `.github/workflows/publish.yml` — a thin caller that delegates to the reusable `zerobias-org/devops/.github/workflows/zbb-publish-reusable.yml@main`.

**Job graph** (from the reusable):

1. **`detect`** — diffs `package/**/build.gradle.kts` against the base ref → list of changed modules.
2. **`version`** — single-writer: bumps every changed module's version and pushes ONE commit before the matrix fans out.
3. **`publish`** — matrix per module: `zbb publish` runs the gate preflight (validate + tests + dataloader + image build/test), publishes the npm package, publishes the docker image (if applicable), creates the git tag, posts a release announcement.
4. **`sync`** — propagates `main → uat → qa → dev` after a successful main publish.

**zbb decides per-module** whether to publish an npm package, a docker image, or both — adding a TypeScript client publish or a Java HTTP image is a property of the module's gradle/zbb config, not a separate workflow.

#### Caller workflow

```yaml
jobs:
  publish:
    uses: zerobias-org/devops/.github/workflows/zbb-publish-reusable.yml@main
    with:
      package-depth: 2          # package/<vendor>/<product>/build.gradle.kts
      ghcr-namespace: zerobias-org
      dispatch-input-name: module
      dispatch-input-value: ${{ inputs.module }}
    secrets: inherit
```

#### Commit message linting

Husky + commitlint runs on `pre-commit` and rejects commits that don't follow Conventional Commits. The affected module(s) are also built via `npm run build` and unit-tested before the commit is allowed.

*Example: good commit*
```
touch badcommit.txt
git add badcommit.txt
git commit -m 'this is a bad commit'
⧗   input: this is a bad commit
✖   subject may not be empty [subject-empty]
✖   type may not be empty [type-empty]

✖   found 2 problems, 0 warnings
ⓘ   Get help: https://github.com/conventional-changelog/commitlint/#what-is-commitlint
```

*Example: Good commit*
```
touch goodcommit.txt
git add goodcommit.txt 
git commit -m 'feat: some cool new feature'
[MOD-599-readme 4e70f32b] feat: some cool new feature
 1 file changed, 0 insertions(+), 0 deletions(-)
 create mode 100644 goodcommit.txt
```

### Commit Message Format
Every commit should follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0-beta.4/).
This section summarizes some of its guidelines.

Each commit message consists of a **header**, a **body** and a **footer**.  The header has a special
format that includes a **type**, a **scope** and a **subject**:

```
<type>(<scope>): <subject>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>
```

The **header** is mandatory and the **scope** of the header is optional.
Either the **body** or **footer** must begin with `BREAKING CHANGE` if the commit is a breaking change.

Example — `feat(lang): add polish language`

#### Type
Must be one of the following:

* **feat**: A new feature.
* **fix**: A bug fix.
* **docs**: Documentation only changes.
* **style**: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc).
* **refactor**: A code change that neither fixes a bug nor adds a feature.
* **perf**: A code change that improves performance.
* **test**: Adding missing tests.
* **chore**: Changes to the build process or auxiliary tools and libraries such as documentation generation.

#### Scope
The scope is optional and could be anything specifying place of the commit change. For example `release`, `api`, `mappers`, etc...

#### Body
The body may include the motivation for the change and contrast this with previous behavior.
It should contain any information about **Breaking Changes** if not provided in the **footer**.

#### Footer
The footer should contain any information about **Breaking Changes** if not provided in the **body**.

**Breaking Changes** should start with the word `BREAKING CHANGE:` with a space or two newlines.
A description of the change should always follow.
