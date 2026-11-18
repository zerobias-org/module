---
name: connection-profile
description: Decides whether a module needs credentials and designs its connectionProfile.yml and connectionState.yml. Use when creating a module, choosing between a connector and a plain (profile-less) module, picking a core profile schema from types-core (token, OAuth client credentials, basic auth), adding profile fields, deciding whether a connection state is needed, or creating the zbb secret that feeds the profile in e2e tests.
---

# Connection profile

A connection profile is the schema of what a user types in to connect a module to a remote system.
Decide first whether the module needs one at all; everything else follows from that answer.

## 1. Decide: does the source need credentials?

| Source | Module kind | What exists in the package |
|---|---|---|
| Needs a secret or an account (API token, client id + secret, user + password) | Connector | `connectionProfile.yml`, `zb.typescript-connector` in `build.gradle.kts`, generated `ConnectionProfile` model, `connect()` / `isConnected()` / `disconnect()` on the impl |
| Public, no authentication (a public website or open API) | Plain | No `connectionProfile.yml`, `zb.typescript` in `build.gradle.kts`, `"interface": true` in `package.json`, no connect lifecycle |

Worked examples in this repository:

- Connector with a custom profile: `package/readyplayerme/readyplayerme`
- Connector that extends a core profile and returns a state: `package/microsoft/azure/azureresourcegraph`
- Plain module: `package/nist/cmvp`

Do not add an empty or placeholder profile to a public source "because modules have one". A profile
turns the module into a connector: the build generates a server and a Docker image for it, and the
platform asks every user for credentials that are never used.

Java receiver modules (`zb.java-module`, for example `package/x12/x12`) are a separate case: they keep a
documentation-only profile, and its `description` says why. Leave those as they are; this decision is
for TypeScript modules.

### What a plain module does instead (no profile)

Compare `package/nist/cmvp` with a connector when in doubt:

- `build.gradle.kts` applies `zb.typescript`, not `zb.typescript-connector`.
- `package.json` sets `"interface": true`. The gate schedules the Docker image tasks for every
  module, and this is the switch that makes the build skip server generation and the image for a
  module that has no profile. Without it the gate fails while compiling the generated server.
- `package.json` `files` lists `dist`, `generated/api/manifest.json` and `api.yml` only.
- The impl implements the generated plain interface (`CmvpImpl implements Cmvp`) and the client is
  built eagerly in its constructor (`src/CmvpClient.ts`); there is no `connect()`.
- There is no `test/e2e` directory and no zbb secret. Unit tests mock the real host. See the
  `testing` skill for why e2e does not run for a plain module.

Everything below this point applies to connectors only.

## 2. Pick the profile shape (connector)

Two shapes are valid and both pass the gate.

**Reference a core schema** when one matches the authentication method. The core schemas ship in
`@zerobias-org/types-core` and are referenced by relative path into `node_modules`:

```yaml
# connectionProfile.yml — core profile plus one extra field
type: object
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/oauthClientProfile.yml'
  - type: object
    required:
      - directoryId
    properties:
      directoryId:
        type: string
        description: Directory (tenant) the service principal lives in
```

The full file is `package/microsoft/azure/azureresourcegraph/connectionProfile.yml`.

**Write a plain `type: object` profile** when no core schema fits or the field names would mislead
the user. `package/readyplayerme/readyplayerme/connectionProfile.yml` and
`package/avigilon/alta/access/connectionProfile.yml` (email, password, optional TOTP code) do this,
and the module generator scaffolds this shape.

| Authentication | Core schema | Fields it brings |
|---|---|---|
| API key, bearer token, personal access token | `tokenProfile.yml` | `apiToken` (required), `url` |
| OAuth client credentials (machine to machine) | `oauthClientProfile.yml` | `client_id`, `client_secret` (required, snake_case), `url` |
| Username and password | `basicConnection.yml` | `uri`, `username`, `password` (all required) |
| OAuth authorization code ("Connect" button) | `oauthTokenProfile.yml` + `oauthTokenState.yml` | see the `oauth-click-to-connect` skill |

Field names differ between schemas (`url` in the token and OAuth profiles, `uri` in
`basicConnection.yml`; `client_id`, not `clientId`). Read the schema before adding a property so a
field is not declared twice under two names. To list what the installed version ships, run:

```bash
${CLAUDE_SKILL_DIR}/scripts/list-core-schemas.sh package/<vendor>/[<suite>/]<product>
```

The catalog with every field and the state schemas is in `references/core-schemas.md`. The installed
package is the source of truth; the reference is a snapshot.

## 3. Choose the fields

- **Only what is needed to connect.** The test: if the module can authenticate without the value,
  it is an operation parameter, not a profile field. A tenant id that is part of the token URL
  belongs in the profile (`directoryId` in azureresourcegraph). A project or workspace id that only
  narrows a list call does not, because it would tie one connection to one project.
- **Include optional connection parameters the vendor documents**, such as a region, an environment
  base URL or an MFA code (`totpCode` in avigilon). Leave them out of `required`. Some tenants
  cannot connect without them, and adding a field later changes the form for every user.
- **Mark secrets with `format: password`**, as the core schemas do for every token, secret and
  password, so the value is treated as a secret rather than as plain text.
- **Give every field a `description` and an `example`**, as the core schemas do; the profile is
  the only documentation a user sees when connecting.
- **Keep `$ref`s in the committed file.** The build resolves them from `node_modules` when it
  bundles the spec; a hand-flattened copy stops following the core schema.

After changing a profile, regenerate so `generated/model/ConnectionProfile` matches (see the
`build-lifecycle` skill). The client reads the fields from that generated type, as
`src/ReadyPlayerMeClient.ts` does with `connectionProfile.apiToken`.

## 4. Add `connectionState.yml` only when a token expires

The state is what `connect()` returns for the platform to keep: a short-lived token and its
lifetime. Create the file only when the module mints a token that expires. The build checks
whether `connectionState.yml` exists and generates the `ConnectionState` model and the
state-returning `connect()` signature only then.

| Situation | State file | `connect()` returns | Example |
|---|---|---|---|
| Static API key or basic auth sent on every request | none | `Promise<void>` | `package/readyplayerme/readyplayerme` |
| `connect()` exchanges credentials for an expiring token | `tokenConnectionState.yml` (`accessToken` + `expiresIn`) | `Promise<ConnectionState>` | `package/microsoft/azure/azureresourcegraph`, `package/avigilon/alta/access` |
| OAuth authorization code with a refresh token | `oauthTokenState.yml` | see the `oauth-click-to-connect` skill | |

When a state exists:

- Build it on a core state schema so it carries `expiresIn` from `baseConnectionState.yml`. The
  platform reads `expiresIn` to schedule the reconnect before the token lapses, so a state
  without it gives the platform nothing to schedule on.
- `expiresIn` is a number of seconds from now. Vendors that return an absolute expiry need a
  conversion; `src/AzureResourceGraphClient.ts` shows it (`Math.floor((expiresAt - now) / 1000)`,
  floored at zero).
- Add a state field only if `connect()` returns it and something reads it later. Identifiers that
  scope operations stay out for the same reason they stay out of the profile.
- List `connectionState.yml` next to `connectionProfile.yml` in `package.json` `files`, as the two
  example modules do.
- Implement `refresh()` on the impl. With no refresh token, re-run `connect()`
  (`src/AzureresourcegraphImpl.ts`).

The `connect()` / `refresh()` code itself belongs to the `implementation` skill.

## 5. Feed the profile in e2e tests with a zbb secret

There is no credentials file in a module. For e2e tests the profile comes from a zbb secret stored
in the slot, and the test framework (`describeModule` from `@zerobias-org/module-test-client`)
passes that secret to `connect()` as the connection profile.

The link between the two is by name: **one secret is one whole profile, and its keys are the
profile's field names**, spelled exactly as in `connectionProfile.yml`.

```bash
# from the module directory
zbb --slot <slot> secret create <name> client_id=<value> client_secret=<value> directoryId=<value>

zbb --slot <slot> secret list --module @zerobias-org/module-<vendor>-<product>
```

- Create the secret from the module directory: zbb tags it with the `name` from that directory's
  `package.json`, and `--module` on `secret list` filters by the same name. The framework lists the
  secrets recorded for the module and runs the suite once per secret, so two secrets test two accounts.
  The `testing` skill owns the full secret command reference.
- `--type @connectionProfile.yml` validates the values against the profile when creating.
- Values can also come from a YAML file (`@file.yml`). Keep such a file outside the repository.
- With no secret for the module the suite records one skipped test, so the gate still passes
  without credentials.
- A renamed or added profile field means the secret needs the same change (`zbb secret update`);
  otherwise `connect()` receives a profile without that field.
- Values that are not part of the profile (a test user id, a subscription id) are plain environment
  variables read in `test/e2e/constants.ts`, not secret keys.

Test structure, the three test modes and skip rules are in the `testing` skill.

## Before finishing

- Public source: no `connectionProfile.yml`, `zb.typescript`, `"interface": true`.
- Connector: profile holds only what is needed to connect; secrets use `format: password`; no field
  duplicates one that a referenced core schema already provides.
- State file exists only if a token expires, and it resolves to `baseConnectionState.yml`.
- `package.json` `files` lists exactly the profile and state files that exist.
- The zbb secret's keys match the profile's field names.
