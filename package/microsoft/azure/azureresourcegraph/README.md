# microsoft-azure-azureresourcegraph Hub Module

Azure Resource Graph connector — inventories Azure resources and resource
containers (subscriptions, resource groups, management groups) through the
Resource Graph query API (api-version `2024-04-01`). One module serving two
products: `microsoft.azure.azureresourcegraph` and
`microsoft.azure.subscription`.

## Authentication and authorization

Azure AD (Entra ID) client credentials: `directoryId` + `clientId` /
`clientSecret`, exchanged for a token with the Azure Resource Manager audience
(scope `https://management.azure.com/.default`).

The service principal needs the **Azure RBAC Reader** role at subscription or
management-group scope. Resource Graph enforces read access per object: rows
the credential cannot read are omitted from results, and the API returns 403
only when nothing is readable at all. Throttling is reported via the
`x-ms-user-quota-remaining` and `x-ms-user-quota-resets-after` response
headers.

## Usage

```typescript
import { newAzureResourceGraph } from '@zerobias-org/module-microsoft-azure-azureresourcegraph';

const arg = newAzureResourceGraph();
await arg.connect({ directoryId, clientId, clientSecret });

const results = new PagedResults<Resource>();
await arg.getResourceApi().list(results);            // tenant scope
await arg.getResourceApi().list(results, [subId]);   // subscription scope
await arg.getResourceContainerApi().list(results);   // subscriptions/RGs/MGs
```

Paging follows the Resource Graph contract: `options.$top` (max 1000) and
`options.$skip` on the first page, then the response `$skipToken` is carried in
`PagedResults.pageToken` for subsequent pages until absent.

# Test

E2E tests live in `test/e2e/` and skip unless `AZURE_DIRECTORY_ID`,
`AZURE_CLIENT_ID` and `AZURE_CLIENT_SECRET` are set (optionally
`AZURE_SUBSCRIPTION_ID` for the scoped-list test).
