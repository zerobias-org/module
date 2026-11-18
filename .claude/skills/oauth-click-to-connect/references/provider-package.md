# Adding an OAuth provider package

Only needed when `${CLAUDE_SKILL_DIR}/scripts/list-oauth-providers.sh` shows no provider that fits.
This is ordinary contributor content, not the insider task.

## What a provider is

`oauth` is a loadable artifact type, like vendor, product and module. A provider is a package in
the `zerobias-org/oauth` repository named `@zerobias-org/oauth-<vendor>`. Its only content file is
`index.yml`:

```yaml
# zerobias-org/oauth · package/microsoft/index.yml
id: 9d975c18-45d4-4974-abcb-90aea1795c12
url: https://login.microsoftonline.com/organizations/oauth2/v2.0/authorize
```

No client id and no secret: only an id and the public authorize URL. The package's `package.json`
depends on the vendor (or suite, or product) package, and that dependency is what ties the provider
to a vendor / suite / product in the catalog. A module names the provider code in
`x-oauth-providers`; the two meet when both are loaded.

Because it is content, the provider and the module's link to it are written and loaded like any
other content, and can be loaded into your own organization before going to the shared catalog.

## Prerequisite

The package this provider depends on, `@zerobias-org/vendor-<vendor>` or the suite / product
package, has to be published in the catalog already. A provider whose dependency does not resolve
does not load. If the vendor or product is missing, adding it is catalog content work in that
artifact's own repository and comes first; it is outside this repository and outside this skill.

## Recipe

There is no scaffold script and no gradle gate in the oauth repository. Copy an existing provider
directory (`package/<vendor>/`, including its `.npmrc`) and change four things:

```yaml
# package/<vendor>/index.yml
id: <new v4 UUID>                          # uuidgen; unique in the repository, never changed after publishing
url: https://<provider>/oauth2/authorize   # the authorize endpoint, not the token endpoint
```

```jsonc
// package/<vendor>/package.json — the keys that matter
{
  "name": "@zerobias-org/oauth-<vendor>",
  "files": ["index.yml"],
  "scripts": { "nx:publish": "../../scripts/publish.sh" },
  "zerobias": {
    "package": "<vendor>.oauth",        // the code a module puts in x-oauth-providers
    "import-artifact": "oauth",         // tells the dataloader the artifact type
    "dataloader-version": "<copy from a sibling package>"
  },
  "dependencies": { "@zerobias-org/vendor-<vendor>": "latest" }
}
```

Take every other key (`version`, `publishConfig`, `repository`, `license`) from the sibling you
copied, and start without the sibling's `CHANGELOG.md`.

Scope of the provider follows the dependency:

| Scope | Directory | Depends on | Code |
|---|---|---|---|
| Vendor | `package/<vendor>/` | `@zerobias-org/vendor-<vendor>` | `<vendor>.oauth` |
| Suite | `package/<vendor>/<suite>/` | `@zerobias-org/suite-<vendor>-<suite>` | `<vendor>.<suite>.oauth` |

`package/microsoft/azure` in the oauth repository is a suite-scoped example. A nested package's
`nx:publish` path has one more `../`. For a product-scoped provider, nest one level deeper and
depend on the product package in the same way.

## Pull request

- Base branch `main`; the oauth repository has no `dev` branch.
- One provider per pull request, separate from the module pull request in this repository. Link
  the two.
- After merge the package is published and then loaded by the dataloader as an `oauth` artifact.
  Until that has happened the module's `x-oauth-providers` entry points at nothing.
