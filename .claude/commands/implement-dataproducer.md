---
description: Implement the DataProducer interface for an existing module (3–6 hours, 5 gates)
argument-hint: <module-identifier>
---

Implement DataProducer for module: $1

`$1` is `<vendor>-[<suite>-]<service>`. The module must already exist with working `<Tag>Api` producers and a clean `zbb gate` baseline.

## What this adds

The DataProducer interface is the unified hierarchical-access surface (Objects / Collections / Documents / Functions / Binary / Schemas). Implementing it lets the module participate in object navigation, schema discovery, and downstream query paths.

Reference template: `package/github/github/src/DataProducerImpl.ts` (and `DataProducerMappers.ts`, `DataProducerSchemas.ts`).

## Prerequisites

- Module is on gradle + ESM (post-migration). If not, `/create-module` it.
- `zbb gate --slot local` on the current module is green.
- The module's existing `<Tag>Api` producers cover the operations DataProducer will wrap (list/get/etc.). Add what's missing via `/add-operation` first.

## Phase 1 — Domain analysis (30–60 min)

- @product-specialist: enumerate entity types, parent-child relationships, and identify entities that should be **Objects** (nodes), **Collections** (lists), **Documents** (payloads), **Functions** (search etc.), or **Binary** (file downloads)
- @api-researcher: confirm the existing producer methods needed; identify any list/get gap that must be filled before continuing
- Define object-ID prefixes per entity type (e.g., `org_`, `repo_`, `pr_`)
- Output: `.claude/.localmemory/dataproducer-{module-id}/analysis.md`

## Phase 2 — API spec wiring (45–60 min)

- @api-architect: add the DataProducer interface paths to `api.yml`. Reference the **bundled** spec from the published interface, not the raw `api.yml`:

  ```yaml
  /objects:
    $ref: './node_modules/@zerobias-org/module-interface-dataproducer/dist/module-interface-dataproducer.yml#/paths/~1objects'
  /objects/{objectId}:
    $ref: './node_modules/@zerobias-org/module-interface-dataproducer/dist/module-interface-dataproducer.yml#/paths/~1objects~1%7BobjectId%7D'
  # … (all 13 DataProducer paths)
  ```

  Same pattern for `components.schemas` ($ref into `dist/module-interface-dataproducer.yml#/components/schemas/...`).

- Add `@zerobias-org/module-interface-dataproducer` to `package.json` `dependencies` (pinned to the current latest — `npm view @zerobias-org/module-interface-dataproducer version` to check)
- @api-reviewer: validate Gate 1 against the augmented spec
- Run `zbb bundleSpec` then `zbb generate`
- **Gate 1 + 2** — DataProducer interfaces now in `generated/api/` and `hub-sdk/generated/api/`

## Phase 3 — Schema definitions (60–90 min)

Create `src/DataProducerSchemas.ts`:

- `SCHEMA_IDS` constant with one ID per entity: `{vendor}_{entity}_schema`
- For each entity: a `Schema` built with `buildSchemaFromClass(EntityType, { schemaId, primaryKeys, requiredFields, descriptions, references })`
- A `getSchemaById(schemaId: string): Schema | undefined` lookup
- @schema-specialist: review descriptions for accuracy

## Phase 4 — Mappers + routing impl (2–3 hours)

`src/DataProducerMappers.ts` (the big one):

- @mapping-engineer:
  - **Container builders**: `buildRootObject`, `buildContainerObject`, `buildCollectionObject`, `buildFunctionObject`
  - **Children mappers**: `getRootChildren`, one `get<Entity>Children` per container
  - **Object getters**: one `get<Entity>Object` per entity type — parse ID, call the existing producer, wrap in `ModelObject`
  - **Collection getters**: one `get<Collection>Collection` per collection — call the existing list method, map items to `{ field: { value: x } }` per element
  - **Document getters**: one `get<Entity>DocumentData` per document type
  - **Function mappers**: `objectSearch` (if supported), one `invoke<Operation>Function` per function
  - **Binary downloaders**: `downloadFileContent` etc.

`src/DataProducerImpl.ts` (router):

- @operation-engineer:
  - `ObjectsProducerApi`: `getRootObject` → root mapper; `getObject(id)` → route by ID prefix; `getChildren(id)` → route to children mapper; `objectSearch` → search mapper; **write methods throw `UnexpectedError` ("not implemented")**
  - `CollectionsProducerApi`: `getCollectionElements` routes; write/search methods throw
  - `DocumentsProducerApi`: `getDocumentData` routes; update throws
  - `FunctionsProducerApi`: `invokeFunction` routes; rest throw
  - `BinaryProducerApi`: `downloadBinary` routes; upload throws
  - `SchemasProducerApi`: `getSchema` → `getSchemaById`

Wire the producers into `src/<Class>Impl.ts`:

```typescript
import { wrapObjectsProducer, wrapCollectionsProducer, /* … */ }
  from '../generated/api/index.js';
import { DataProducerImpl } from './DataProducerImpl.js';

// inside the Impl:
getObjectsApi(): ObjectsApi {
  return wrapObjectsProducer(new DataProducerImpl(this.client, this));
}
// ... 5 more API getters
```

- @style-reviewer: `zbb lint`
- Run `zbb compile`
- **Gate 3** — code compiles, lints, no `any`, no env access in `src/`

## Phase 5 — Tests (1–2 hours)

Extend `test/e2e/<name>.test.ts` (the existing `describeModule<T>` file) with a `describe('DataProducer', ...)` block:

- `client.getObjectsApi().getRootObject()` — non-null shape
- `client.getObjectsApi().getObject(<known id>)` — one per main entity type
- `client.getObjectsApi().getChildren(<root or container id>)` — per container
- `client.getCollectionsApi().getCollectionElements(<known collection id>)` — per collection
- `client.getDocumentsApi().getDocumentData(<known document id>)` — per document
- `client.getSchemasApi().getSchema(<schemaId>)` — per schema
- Each write method throws `UnexpectedError` (or `NotFoundError` for missing collection elements)

If the existing module has a unit test file structure, extend it with a `test/unit/DataProducerTest.test.ts` covering the routing logic (nock-mocked).

- @producer-it-engineer + @ut-reviewer + @it-reviewer
- Run `zbb test --slot local`, `zbb testDirect --slot local`, and (connectors) `zbb testDocker --slot local`
- **Gate 4 + 5** — tests created and passing

## Phase 6 — Gate + commit

- @build-reviewer: `zbb gate --slot local`
- Stamp updated; commit
- **Gate 6** — `gate-stamp.json` reflects the DataProducer additions

## Success criteria

- All six DataProducer producer APIs return functional clients
- Read operations work end-to-end against the real API
- Write operations throw `UnexpectedError` consistently (no silent no-ops)
- All gates green; `gate-stamp.json` committed
- No `Promise<any>`, no `process.env` in any new `src/` file

## Time estimate

| Phase                  | Estimate     |
|------------------------|--------------|
| 1 — Domain analysis    | 30–60 min    |
| 2 — API spec wiring    | 45–60 min    |
| 3 — Schemas            | 60–90 min    |
| 4 — Mappers + impl     | 2–3 hours    |
| 5 — Tests              | 1–2 hours    |
| 6 — Gate + commit      | 15–30 min    |
| **Total**              | **3–6 hours**|

Reference implementation: `package/github/github/src/DataProducer{Impl,Mappers,Schemas}.ts`.
