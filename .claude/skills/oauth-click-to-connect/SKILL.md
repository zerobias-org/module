---
name: oauth-click-to-connect
description: Wires a connector module for the OAuth authorization-code flow, the "Connect" button in Hub. Use when a module should support click-to-connect, OAuth login, x-oauth-providers, an OAuth provider package in zerobias-org/oauth, refresh tokens, or when the Connect button does not appear for an OAuth-capable module. Covers the three layers (module, provider package, external OAuth app and secret) and who owns each.
---

# OAuth click-to-connect

Applies to connector modules that let a user connect by clicking "Connect" and signing in at the
vendor (OAuth `authorization_code`). It does not apply to:

- **OAuth client credentials** (the user pastes a client id and secret, no popup): that is an
  ordinary profile on `oauthClientProfile.yml` with no provider link and nothing in this skill. See
  the `connection-profile` skill; `package/microsoft/azure/azureresourcegraph` is the example.
- **Plain modules** (public source, no `connectionProfile.yml`, like `package/nist/cmvp`): there is
  nothing to connect.

Much of this skill describes platform behaviour that cannot be checked from this repository. The
statements were recorded from shipping OAuth modules; where the code disagrees, the code wins.

## The three layers

Click-to-connect works only when all three exist. Two are content a contributor writes; one is not.

| # | Layer | What it is | Who can do it | Where |
|---|---|---|---|---|
| 1 | Module | `connectionProfile.yml` with `x-oauth-providers`, `connectionState.yml`, `connect()` / `refresh()` | Contributor | this repository |
| 2 | Provider | An `oauth` content package holding a UUID and the public authorize URL | Contributor | `zerobias-org/oauth` repository |
| 3 | OAuth app + secret | The app registered at the vendor and its client id / secret in the platform's secret store | Platform insider only | outside any repository |

If layers 1 and 2 ship and nobody does layer 3, the button never appears, and nothing fails to tell
you why. So when the authorization-code flow is chosen, open the layer 3 task in the same piece of
work (step 4).

## Steps

### 1. Check whether the provider already exists

```bash
${CLAUDE_SKILL_DIR}/scripts/list-oauth-providers.sh            # all providers
${CLAUDE_SKILL_DIR}/scripts/list-oauth-providers.sh microsoft  # filter by vendor
```

The script reads `zerobias-org/oauth` live and prints each provider code with its authorize URL.
Check live every time; a list copied into a document goes stale as providers are added.

- **A provider fits** (same vendor identity service, same authorize URL): use its code in
  `x-oauth-providers` and do not touch the oauth repository. One provider serves every product
  behind the same identity service, for example `microsoft.oauth` for any Entra ID-backed product.
- **None fits**: adding it is a separate content pull request (step 3). Report it as its own
  deliverable, because the module cannot offer OAuth until the provider is loaded.

### 2. Write the module half

Full detail, the profile YAML and the method signatures are in `references/module-side.md`. The
points that are easy to get wrong:

- The profile combines `oauthTokenProfile.yml` and `oauthTokenState.yml` and adds
  `x-oauth-providers: [<code>]`. `x-ui-hidden` hides the token fields the flow fills in;
  `x-ui-required` names the fields of the manual connect method.
- The module does not exchange the authorization `code` for a token. The platform does the popup
  and the exchange and hands the module an already issued `accessToken` and `refreshToken`. Writing
  a code exchange in the module is wasted work.
- `connect()` has no state argument: the tokens arrive as fields on the connection profile.
- `refresh()` is the module's job: post `grant_type=refresh_token` to the vendor's token endpoint
  with the client id and secret from the `OauthConnectionDetails` argument.
- The token URL and the scopes are constants in the module's client. The provider package carries
  only the authorize URL.
- Read the OAuth app credentials under both `clientId` and `client_id` (same for the secret); both
  casings have been delivered at runtime.
- Do not store or hardcode the OAuth app's client id or secret in the module. They arrive per call.

### 3. Add the provider package, only if step 1 found none

A small content package in `zerobias-org/oauth`: `index.yml` with a new UUID and the authorize URL,
and a `package.json` whose `zerobias.package` is the provider code. The recipe and its
prerequisites (the vendor must already exist in the catalog) are in
`references/provider-package.md`. That pull request targets `main`; the oauth repository has no
`dev` branch.

### 4. Open the task for the external app and the secret

Registering the OAuth app with the vendor and storing its client id and secret cannot be done as
content, because a secret cannot live in a loadable package. Ask the user to open a task for
exactly these two steps, using the text in `references/insider-task.md`, and link it from the
module's pull request. Say plainly in the pull request body that the button stays hidden until that
task is done.

### 5. Test without a popup

`testDirect` and `testDocker` have no browser, so something has to supply a token. The e2e
framework passes one zbb secret to `connect()` as the whole connection profile (keys are the
profile's field names; see the `connection-profile` skill), and in direct mode it calls `connect()`
with the profile only, without OAuth app details.

- **Module also supports a manual method** (client credentials, API token): put those fields in
  the secret; `connect()` obtains or uses the token itself. This is the practical way to keep an
  OAuth module covered by e2e tests.
- **Click-to-connect only**: obtain an `accessToken` and `refreshToken` once outside the test run
  and put them in the secret. Without a secret the suite is skipped, so the gate stays green
  without them.

The full gate and the test modes are covered by the `gates` and `testing` skills.

## Before finishing

- Provider checked live; its code is in `x-oauth-providers`, or a provider pull request is open.
- No code exchange in the module; `refresh()` uses the refresh-token grant.
- Token fields are in `x-ui-hidden`; client id and secret are read in both casings.
- The layer 3 task exists and is linked from the pull request.
- The report to the user lists what is still outside this repository: provider pull request (if
  any) and the layer 3 task.

## References

| File | Read it when |
|---|---|
| `references/module-side.md` | Writing the profile, the state, `connect()` / `refresh()`, or choosing `x-ui-*` fields |
| `references/provider-package.md` | Step 1 found no provider and one has to be added |
| `references/insider-task.md` | Writing the layer 3 task |
