# stellarcyber-stellarcyber Hub Module

ZeroBias Hub module (connector) for **Stellar Cyber** Open XDR. Reads correlated
threat findings (cases) and their alerts from the Stellar Cyber REST API.

## Authentication and authorization

A scoped **API key** is exchanged for a short-lived (~10 min) JWT bearer at connect
time (`POST /connect/api/v1/access_token`). Connection profile:

| Field | Description |
|---|---|
| `host` | Stellar Cyber console host (e.g. `example.stellarcyber.cloud`), no scheme |
| `apiToken` | Stellar Cyber scoped API key |

## Operations

| Api | Method | Description |
|---|---|---|
| `CaseApi` | `list` | List cases (correlated threat findings) |
| `CaseApi` | `listAlerts` | List the alerts within a case (resource / threat detail) |

## Build / test

Built via gradle (`zb.typescript-connector`) / `zbb`. Integration tests under
`test/e2e` skip unless `STELLARCYBER_HOST` + `STELLARCYBER_API_TOKEN` are set.

```bash
zbb build
LOG_LEVEL=debug npm run test   # e2e, requires live SC credentials
```
