# Azure Resource Graph Module

Hub connector for [Azure Resource Graph](https://learn.microsoft.com/en-us/azure/governance/resource-graph/) —
Microsoft's tenant-wide resource query service. The module owns Azure **asset
inventory**: it lists resources (the ARG `Resources` table) and resource
containers — subscriptions, resource groups and management groups (the ARG
`ResourceContainers` table).

Everything goes through one vendor endpoint, pinned to the current stable
release:

```
POST https://management.azure.com/providers/Microsoft.ResourceGraph/resources?api-version=2024-04-01
```

## Authentication and authorization

Azure AD (Microsoft Entra ID) **OAuth2 client credentials**. The connection
profile carries:

| Field | Meaning |
|-------|---------|
| `directoryId` | Entra ID directory (tenant) ID |
| `client_id` | App registration (service principal) client ID |
| `client_secret` | Client secret |
| `url` (optional) | Alternate login authority base (defaults to `https://login.microsoftonline.com`) |

`connect()` exchanges these for a bearer token at
`https://login.microsoftonline.com/{directoryId}/oauth2/v2.0/token` with scope
`https://management.azure.com/.default`, and returns a connection state carrying
`accessToken` + `expiresIn` (seconds). There is no refresh token —
`refresh()` re-mints via the same grant.

**Authorization:** grant the service principal the Azure RBAC **Reader** role at
subscription or management-group scope. ARG silently returns no rows for objects
the principal cannot read; a 403 means *nothing* in scope is readable.

### Connect using credentials:

```typescript
import { newAzureresourcegraph, ConnectionProfile } from '@zerobias-org/module-microsoft-azure-azureresourcegraph';

const api = newAzureresourcegraph();
const profile: ConnectionProfile = {
  directoryId: '72f988bf-86f1-41af-91ab-2d7cd011db47',
  client_id: '<app-client-id>',
  client_secret: '<app-client-secret>',
};
await api.connect(profile);
```

## Operations

| Operation | ARG query | Returns |
|-----------|-----------|---------|
| `getResourceApi().list()` | `Resources` | Azure resources (id, name, type, location, tags, sku, identity, properties, …) |
| `getResourceContainerApi().list()` | `ResourceContainers` | Subscriptions, resource groups and management groups |

Both operations:

- run at **tenant scope by default**; pass `subscriptionIds` and/or
  `managementGroupIds` to narrow the scope,
- use **cursor pagination**: `pageSize` maps to ARG `options.$top` (capped at
  1000); a truncated response carries a `$skipToken` surfaced as
  `results.pageToken`, which the next call sends back as `options.$skipToken`;
  iteration stops when no token is returned,
- return `objectArray`-shaped rows whose column names map 1:1 onto the module
  schemas (the `type` column doubles as the downstream `assetType`
  discriminator on the base `Asset` interface).

```typescript
// Page through every resource in the tenant
const resourceApi = api.getResourceApi();
let pageToken: string | undefined;
do {
  const page = await resourceApi.list({ pageSize: 1000, pageToken });
  for (const resource of page.items) {
    console.log(resource.type, resource.id);
  }
  pageToken = page.pageToken;
} while (pageToken);
```

## Throttling

Azure Resource Graph throttles per user. The module maps HTTP 429 to
`RateLimitExceededError`; the vendor response carries
`x-ms-user-quota-remaining` / `x-ms-user-quota-resets-after` headers for
backoff decisions.

## Test

This module is built and tested via Gradle + zbb. From the repo root:

```bash
./gradlew :microsoft:azure:azureresourcegraph:build   # validate → generate → compile → test → buildImage

# Local test modes (run from this module dir)
zbb test --slot local         # unit tests
zbb testDirect --slot local   # e2e direct (in-process)
zbb testDocker --slot local   # e2e docker (container)
zbb testHub --slot local      # e2e hub (full stack)
zbb gate --slot local         # full gate (writes gate-stamp.json — commit it)
```

E2E tests need a live tenant: create zbb secrets named after the
connection-profile fields (`client_id`, `client_secret`, `directoryId`), and
optionally set `AZURE_SUBSCRIPTION_ID` for the scoped-query test. The suite
skips gracefully when no tenant is wired up.
