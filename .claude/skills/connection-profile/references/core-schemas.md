# Core connection schemas in `@zerobias-org/types-core`

Snapshot of the connection-related files in `node_modules/@zerobias-org/types-core/schema/` of the
2.x line. The installed package wins over this file: run
`${CLAUDE_SKILL_DIR}/scripts/list-core-schemas.sh <module-dir>` or read the `.yml` directly.

Reference a schema from a module file as
`$ref: './node_modules/@zerobias-org/types-core/schema/<file>'`.

## Profiles for module authors

| File | Required | Optional | Use for |
|---|---|---|---|
| `tokenProfile.yml` | `apiToken` | `url` | API key, bearer token, personal access token |
| `oauthClientProfile.yml` | `client_id`, `client_secret` | `url` | OAuth client credentials grant |
| `basicConnection.yml` | `uri`, `username`, `password` | | Username and password |
| `oauthTokenProfile.yml` | | `tokenType` (default `bearer`), `accessToken`, `url` | A pre-obtained OAuth token; combined with `oauthTokenState.yml` for the authorization-code flow |

`apiToken`, `client_secret`, `password` and `accessToken` are declared `format: password`.

A custom plain profile with `apiToken` (required) and `url` is equivalent to `tokenProfile.yml`;
`package/readyplayerme/readyplayerme/connectionProfile.yml` is written that way.

## States

| File | Fields | Use for |
|---|---|---|
| `baseConnectionState.yml` | `expiresIn` (seconds until the access token becomes invalid) | Base of every state; extend it for a custom state |
| `tokenConnectionState.yml` | `accessToken` + base | A token minted by `connect()` with a lifetime |
| `connectionState.yml` | `accessToken` + base | Same fields as `tokenConnectionState.yml`; `package/avigilon/alta/access/connectionState.yml` references this one |
| `oauthTokenState.yml` | `tokenType`, `accessToken`, `refreshToken`, `scope`, `url` + base | OAuth authorization code with refresh |

## Not for a product module's own profile

| File | What it is |
|---|---|
| `oauthConnectionDetails.yml` | `oauthClientProfile.yml` plus `name`, `authorizationEndpoint`, `tokenEndpoint`. The shape of the OAuth app details the platform passes to `connect()` / `refresh()` at runtime; see the `oauth-click-to-connect` skill |
| `hubConnectionProfile.yml` | `server`, `targetId` (required), `apiKey`, `session`, `orgId`. Profile for talking to a Hub server |
| `sdkConnectionProfile.yml` | `url` (required), `apiKey`, `jwt`, `session`, `orgId`. Profile for SDK API clients |
| `connectionMetadata.yml`, `connectionStatus.yml` | Return type of the impl's `metadata()` and its status enum, not profile schemas |

## Extending a core schema

Compose with `allOf`; the second entry adds the module's own fields:

```yaml
type: object
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/tokenConnectionState.yml'
  - type: object
    properties: {}
```

This is `package/microsoft/azure/azureresourcegraph/connectionState.yml`. Its profile uses the same
pattern with a required `directoryId` on top of `oauthClientProfile.yml`.

Field-name traps when extending:

- `oauthClientProfile.yml` uses snake_case `client_id` / `client_secret`. The value handed to
  `connect()` has been seen with either casing, so `src/AzureResourceGraphClient.ts` reads
  `client_id ?? clientId`.
- `basicConnection.yml` names the address `uri`; the token and OAuth profiles name it `url`.
- `basicConnection.yml` requires all three of its fields. If the vendor authenticates with an email
  and has a fixed base URL, a plain custom profile is clearer than extending it
  (`package/avigilon/alta/access/connectionProfile.yml`).
