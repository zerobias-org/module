# Connecting to Azure Resource Graph

Azure Resource Graph (ARG) lets ZeroBias inventory the resources in your Azure subscriptions — virtual machines, storage accounts, virtual networks, databases and the subscriptions and resource groups that contain them.

This connector reads **Azure resources**, not your directory. That distinction determines how you authorise it, and it is the step most people get wrong.

## What you need

| | |
|---|---|
| **Directory (tenant) ID** | your Microsoft Entra ID tenant |
| **Application (client) ID** | the registered application ZeroBias authenticates as |
| **Client secret** | a secret on that application |
| **An Azure RBAC role assignment** | `Reader`, on the application's service principal |

The first three are the same values used by any ZeroBias Azure connection. **The fourth is specific to resource connectors and has no equivalent in the Microsoft Graph setup.**

## Step 1 — Register an application

If you have already connected another ZeroBias Azure connector, you have this and can skip to Step 2.

Otherwise follow [Registering an Azure AD App](https://cdn.zerobias.com/kb/kb10/index.html), which walks through registration and creating a client secret with screenshots.

You do **not** need to add any Microsoft Graph API permissions for this connector — it never calls `graph.microsoft.com`.

## Step 2 — Grant Reader access to your Azure resources

**This is the step that is easy to miss.** Registering an application and creating a secret gives ZeroBias an identity with **no access to any Azure resource**. Azure Resource Manager authorises separately, through a role assignment.

Full instructions: [Granting Azure Resource Manager (ARM) Access](https://cdn.zerobias.com/kb/kb266/index.html)

In short:

1. Open the **management group** (recommended) or **subscription** you want ZeroBias to inventory.
2. **Access control (IAM) → Add → Add role assignment**.
3. Role: **Reader**. Member: the application from Step 1.
4. **Review + assign**.

Requires **Owner** or **User Access Administrator** on that scope — which is usually a different person from whoever administers your directory.

**Prefer management-group scope.** A subscription-scoped assignment does not cover subscriptions created later, so your inventory quietly goes stale as the estate grows.

## Step 3 — Create the connection

Enter the Directory (tenant) ID, Application (client) ID and client secret.

You do **not** enter a subscription ID. The connector discovers every subscription the application is permitted to read, and collects across all of them.

## What gets collected

| Operation | Returns |
|---|---|
| `listResources` | all Azure resources visible to the application — VMs, storage accounts, networks, databases and the rest |
| `listResourceContainers` | subscriptions, resource groups and management groups |

Both query Azure Resource Graph directly, so results reflect what ARG itself returns. Collection runs at tenant scope by default, across everything the application can reach.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Connection succeeds but **nothing is collected** | No `Reader` role assignment, or it is scoped somewhere empty. This is the most common problem, and the credentials are fine — recheck Step 2. |
| **Some** resources appear, others are missing | The assignment is too narrow. A subscription outside the assigned scope is invisible. Prefer management-group scope. |
| Authentication failure | Wrong client secret, or it expired. Client secrets have an expiry date — check **Certificates & secrets**. |
| Unknown tenant | Wrong Directory (tenant) ID. |
| Collection slow or intermittently incomplete on a large estate | Azure Resource Graph throttles per principal. Contact ZeroBias support if it persists. |

The first row matters most: **a permissions problem here looks like an empty result, not an error.** Azure Resource Graph omits resources the application cannot read rather than failing the request, so there is nothing in the logs to find. If a collection returns nothing, check the role assignment before anything else.

## Reference

- [Registering an Azure AD App](https://cdn.zerobias.com/kb/kb10/index.html)
- [Granting Azure Resource Manager (ARM) Access](https://cdn.zerobias.com/kb/kb266/index.html)
