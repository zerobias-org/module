# The module half of click-to-connect

Contents

- [Where to look for working code](#where-to-look-for-working-code)
- [connectionProfile.yml](#connectionprofileyml)
- [Choosing x-ui-required and x-ui-hidden](#choosing-x-ui-required-and-x-ui-hidden)
- [connectionState.yml](#connectionstateyml)
- [connect() and refresh()](#connect-and-refresh)
- [What the module does at runtime](#what-the-module-does-at-runtime)
- [Where each URL and scope lives](#where-each-url-and-scope-lives)
- [Recorded pitfalls](#recorded-pitfalls)
- [Local testing](#local-testing)

## Where to look for working code

No module in this repository uses the authorization-code flow. The shipping examples live in the
sibling repository `auditlogic/module` (packages `@auditlogic/module-*`). Read them there when
access is available; do not expect the paths below to exist here.

| Module (path in `auditlogic/module`) | Provider code | Shows |
|---|---|---|
| `package/microsoft/azure/msgraph` | `microsoft.oauth` | Entra ID. Two modes: OAuth, or manual `clientId` / `clientSecret` / `directoryId` |
| `package/atlassian/cloud/jira` | `atlassian.oauth` | OAuth next to email + API token; a `connectionMode` field in its state and a different base path for OAuth connections |
| `package/slack/slack` | `slack.oauth` | Smallest OAuth profile; the form asks for `accessToken`, so a pasted bot token also works |
| `package/github/github` | `github.oauth` | OAuth with a personal access token as the manual method |

Older modules there reference `@auditmation/types-core`. The schemas are the same; new work uses
`@zerobias-org/types-core`.

In this repository, `package/microsoft/azure/azureresourcegraph` is the closest relative: it mints
and re-mints a token against the Microsoft token endpoint with client credentials and returns
`expiresIn`. It has no provider link and no refresh token.

## connectionProfile.yml

Combine the OAuth token profile with the OAuth token state, name the provider, and steer the
connect form with `x-ui-*`:

```yaml
type: object
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenProfile.yml'
  - $ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenState.yml'
  - type: object
    x-oauth-providers:
      - <vendor>.oauth        # the provider code found in step 1
    x-ui-required:            # fields of the manual connect method
      - clientId
      - clientSecret
    x-ui-hidden:              # fields the OAuth flow fills in
      - tokenType
      - accessToken
      - refreshToken
      - expiresIn
      - scope
      - url
    properties:
      clientId:
        type: string
      clientSecret:
        type: string
        format: password
```

The state is included in the profile on purpose: the tokens the platform obtained are delivered to
the module as profile fields (see below).

## Choosing x-ui-required and x-ui-hidden

These two lists decide what the connect form shows.

- `x-ui-hidden`: fields a user never types. Hide `tokenType`, `refreshToken`, `expiresIn`, `scope`
  and, unless the module needs it, `url`.
- `x-ui-required`: the fields that make up the manual way to connect. This is a design choice per
  module:
  - msgraph requires the app credentials (`directoryId`, `clientId`, `clientSecret`) and hides
    `accessToken`.
  - slack requires `accessToken` and hides the rest, which allows pasting a token by hand.
  - jira and github require their API token fields and hide `accessToken`.

Hide `accessToken` when the only ways in are the button or app credentials. Require it only when
pasting a token by hand is a supported way to connect.

## connectionState.yml

Reference `oauthTokenState.yml` (`accessToken`, `refreshToken`, `scope`, plus `expiresIn` in
seconds from `baseConnectionState.yml`). The state has to carry `expiresIn` because the platform
uses it to schedule the refresh.

```yaml
type: object
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenState.yml'
  - type: object
    properties: {}
```

## connect() and refresh()

The OAuth app's credentials reach the module at runtime as an extra argument typed
`OauthConnectionDetails` from `@zerobias-org/types-core-js` (schema `oauthConnectionDetails.yml`:
client id and secret plus `name`, `authorizationEndpoint`, `tokenEndpoint`).

```ts
import { OauthConnectionDetails } from '@zerobias-org/types-core-js';

async connect(
  connectionProfile: ConnectionProfile,
  oauthConnectionDetails?: OauthConnectionDetails,
): Promise<ConnectionState>

async refresh(
  connectionProfile: ConnectionProfile,
  connectionState: ConnectionState,
  oauthConnectionDetails?: OauthConnectionDetails,
): Promise<ConnectionState>
```

The usual implementation of `refresh()` delegates to the client's connect path with a "refresh"
flag, so both share validation and state building. General client and impl structure is in the
`implementation` skill.

## What the module does at runtime

The platform's shared OAuth app performs the authorization-code exchange: the popup, the `code`,
the `redirect_uri`. The module receives an already issued `accessToken` and `refreshToken`.

- `connect()` on an OAuth connection: use the `accessToken` it was handed, and check that one is
  present. No token request.
- `refresh()`: request a new token from the vendor's token endpoint with
  `grant_type=refresh_token`, the `refresh_token` from the profile, and `client_id` /
  `client_secret` from `OauthConnectionDetails`. Store the new access token, and the new refresh
  token when the vendor rotates it.
- Manual mode, if the module offers one: `grant_type=client_credentials` with the credentials from
  the profile. This is the only path where the module itself obtains the first token.

## Where each URL and scope lives

| Thing | Lives in | Example (msgraph) |
|---|---|---|
| Authorize URL | the provider package's `index.yml` (`url`) | `https://login.microsoftonline.com/organizations/oauth2/v2.0/authorize` |
| Token URL | a constant in the module's client | base URI + tenant + `/oauth2/v2.0/token` |
| Scopes | a constant in the module's client; the `securitySchemes` entry in `api.yml` only documents them | `https://graph.microsoft.com/.default` |
| `accessToken`, `refreshToken` | fields on the connection profile at runtime | `this.connectionProfile.refreshToken` |

`connect()` has no state parameter. Because the profile includes `oauthTokenState.yml`, the tokens
are fields on the profile object the platform passes in. `refresh()` does receive the state, but
the refresh token is still read from the profile.

The provider package carries nothing but the authorize URL and an id. Token endpoint and scopes are
the same for every customer of a vendor, so they are constants in code, not profile fields.

## Recorded pitfalls

- **Both casings.** The OAuth details may arrive as `clientId` or `client_id` (and `clientSecret`
  or `client_secret`). msgraph lost its credentials on a forced refresh until it read both:
  `oauth?.clientId ?? oauth?.client_id`. The same defensive read is in this repository in
  `package/microsoft/azure/azureresourcegraph/src/AzureResourceGraphClient.ts`.
- **Two modes are common.** OAuth and a manual method usually coexist. Use the OAuth details when
  present, otherwise the profile's own credentials, and throw `InvalidCredentialsError` only when
  neither path has what it needs.
- **Do not assume the token service from the product's owner.** Some products issue tokens from a
  separate identity service than the vendor's user sign-in. Verify the real token endpoint in the
  vendor's documentation.
- **Do not persist the OAuth app's secret** in state or profile. It is supplied on each call.

## Local testing

The e2e framework reads one zbb secret and passes it to `connect()` as the connection profile; its
keys are the profile's field names (see the `connection-profile` skill for the commands). In direct
mode it calls `connect()` with that profile and no `OauthConnectionDetails`.

- With a manual mode: put the manual fields in the secret, for example

  ```bash
  # from the module directory, so the secret is tagged with this module (see the `testing` skill)
  zbb --slot <slot> secret create <name> clientId=<value> clientSecret=<value>
  ```

  `connect()` then obtains its own token and no popup is needed.
- Click-to-connect only: get an `accessToken` and `refreshToken` once, outside the test run, with
  the vendor's own tooling, and store them as keys of the secret.
- With no secret for the module the suite records a skip, so missing OAuth credentials never fail
  the gate. Keep a `hasCredentials()` guard in `test/e2e/constants.ts` for values the tests need
  beyond the profile, as the modules in this repository do.
