---
description: Create API entity diagram and operation catalog for a vendor/product (30–60 min)
argument-hint: <vendor> <product>
---

Produce an entity model + prioritized operation catalog for: $1 $2

## Goal

A read-only artifact (no module code written). The output drives subsequent `/create-module` or `/add-operations` runs.

## Phase 1 — Documentation research

@api-researcher (complete-scope):

- Inventory **every** endpoint exposed by `$1 $2`'s public API
- Authentication methods, scopes, rate limits, pagination conventions
- API versioning + breaking-change policy
- Test 3–5 representative endpoints against the live API with the slot's credentials
- Save sanitized example responses for each tested endpoint

## Phase 2 — Entity discovery

@product-specialist:

- Identify core entities (e.g. for github: Organization, Repository, User, Webhook, …)
- Map relationships: which entities own / reference which
- Classify each entity: primary resource vs. secondary / configuration / metadata
- Note which entities are CRUD vs. read-only

## Phase 3 — Entity diagram

Produce a Mermaid (or plain ASCII) diagram showing:

- Entities with key properties (id, name, type, owner-ref)
- Relationships with cardinality (`1:1`, `1:N`, `N:M`)
- Containment (which entities scope under which — e.g. Webhook under Repository under Organization)
- Reference vs. embedded relationships

## Phase 4 — Operation catalog

For each entity, list every available operation grouped by:

- **CRUD**: `list`, `get`, `create`, `update`, `delete`
- **Search / query**: `search<Entity>`, filter endpoints
- **Specialized actions**: anything that doesn't map to CRUD (e.g. `getRepositoryBranchProtection`, `triggerWorkflowDispatch`)

Per operation:

- HTTP method + path
- Required parameters
- Pagination style (`pageNumber`/`pageToken`/cursor)
- Notes on quirks (e.g. "returns 422 instead of 404", "doesn't honor `per_page`")

## Phase 5 — Priority analysis

@operation-engineer + @product-specialist:

- Rank operations: **core** (must-have for the first module release), **secondary** (high value, defer), **nice-to-have** (edge cases)
- Identify operations that pair (e.g. `getRepository` is useless without `listRepositories`)
- Flag complex operations that warrant their own ticket (e.g. webhooks with secret rotation)
- Suggest a phased rollout: which `/add-operations` batches map to which release

## Outputs

Saved under `.claude/.localmemory/analyze-{vendor}-{product}/`:

- `entity-diagram.md` — Mermaid diagram + key properties per entity
- `operation-catalog.md` — full operation list with per-operation notes
- `implementation-plan.md` — prioritized rollout: core, secondary, nice-to-have, with suggested `/add-operations` batches
- `special-notes.md` — edge cases, vendor docs URLs, auth quirks, rate-limit observations

## What this does NOT do

- Doesn't scaffold a module (use `/create-module`)
- Doesn't write `api.yml` (that's @api-architect during Phase 3 of `/create-module`)
- Doesn't decide the connector vs plain choice (the auth research informs it, but the call is made when scaffolding)

## Example

```
/analyze-api github webhooks
/analyze-api jira jira
```

Each run is read-only against the vendor API and writes only to `.claude/.localmemory/`.
