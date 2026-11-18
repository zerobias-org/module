---
name: dataproducer-implementation
description: Implements the DataProducer interface (the generic objects / collections / documents / functions / binary / schemas surface) on a Hub module. Use when adding DataProducer support to a new or existing module, or when reviewing one - api.yml $ref wiring, generated producer interfaces, the DataProducerImpl / Mappers / Schemas files, object-ID grammar, schema IDs, error policy and test coverage. It is a required phase for every new module before publish.
---

# DataProducer implementation

Every Hub module exposes the DataProducer interface: a unified, hierarchical
browse/query surface that generic consumers (the dynamic collectorbot, agents,
UIs) walk without knowing the vendor's API. A module that implements it gets
platform-side collection with no per-module ETL.

The rules that matter most, in the order they bite:

1. DataProducer is a layer on top of the module's own tag producers, not a
   replacement. Its mappers call `this.<module>.get<Tag>Api()`, the already
   wrapped producers, so validation, error mapping and pagination are reused.
   If an entity has no tag operation yet, add the operation first.
2. Design the object-ID grammar before writing code (Step 5). Consumers persist
   IDs and send them back, so every emitted ID has to parse back.
3. Nothing is a silent no-op (Step 8). An unsupported operation throws; an
   empty result means "genuinely empty".
4. Objects are typed literals with named fields, not positional constructor
   calls (Step 7).
5. Interface semantics are owned by the interface package's docs in
   `package/interface/dataproducer/documentation/` (`Concepts.md`,
   `SchemaIds.md`, `CoreDataTypes.md`, `FilterSyntax.md`, `Errors.md`). If this
   skill and those docs disagree on interface semantics, the docs win.

This skill plus those docs is everything needed; follow the templates in
`assets/` rather than copying another module. One module in this repo,
`package/microsoft/azure/azureresourcegraph`, uses the same file layout and is
useful for its tests, but it builds objects with positional constructors and
passes the raw client to `DataProducerImpl`, which this skill rules out.

## Two different "producers"

| Term | What it is | Who writes it |
|------|-----------|---------------|
| `<Tag>ProducerApi` | Generated per-tag interface for the module's own operations (for example `UserProducerApi`). A paged list operation takes `results: PagedResults<T>` as its first parameter, returns `Promise<void>` and fills `results` in place | The module author implements one `<Tag>ProducerApiImpl` class per tag (see the `implementation` skill) |
| DataProducer interface | A published interface module, `@zerobias-org/module-interface-dataproducer`, with 6 generic tags (`objects`, `collections`, `documents`, `functions`, `binary`, `schemas`), 15 paths and 22 operations, `$ref`'d into the module's `api.yml` | The module author implements one `DataProducerImpl` class that satisfies all six generated `*ProducerApi` interfaces and delegates to the module's own tag producers |

## Before starting

- The module's gate is green through implementation: `zbb gate`, or at least
  `zbb generate && zbb compile`. DataProducer work on a module that does not
  compile hides which change broke it.
- The environment is set up as the `prerequisites` skill describes (tool
  versions live there, not here).

## Step 1 - dependencies

Add to the module's `package.json` `dependencies`:

- `@zerobias-org/module-interface-dataproducer`, pinned to an exact version
  with no caret. The version of record is the one `bundle/package.json` in this
  repo pins. Do not take `npm view ... latest` instead: the bundle pin is the
  repo-wide contract, and a module on a different version generates different
  types than the modules it ships with.
- `@zerobias-org/data-utils` (caret range), which provides `buildSchema` for
  Step 6.

## Step 2 - spec wiring in api.yml

Copy both blocks from `assets/api-dataproducer.yml`:

- The 15 DataProducer paths go at the end of `paths:`. Each is a `$ref` into
  the bundled spec in the interface package's `dist/` folder
  (`dist/module-interface-dataproducer.yml`), not into its raw `api.yml`,
  because only the bundled file has every nested reference resolved. Path keys
  inside a `$ref` use JSON-pointer escaping: `~1` stands for `/`.
- The interface's shared components are re-exported under `components:` with
  the same local names. The paged parameters (`pageNumberParam`,
  `pageSizeParam`, `sortByParam`, `sortDirParam`) are then also available to
  the module's own list operations.

No `tags:` entry is needed for the six DataProducer tags; they come along
inside the `$ref`'d path objects. No lint configuration changes either: the
`validate` step uses the ruleset the build generates (see the
`build-lifecycle` skill).

## Step 3 - regenerate

```bash
npm install        # so the $refs resolve into node_modules
zbb generate
```

This emits into `generated/api/` one pair per DataProducer tag
(`ObjectsApi`/`ObjectsProducerApi`, `CollectionsApi`/`CollectionsProducerApi`,
`DocumentsApi`/`DocumentsProducerApi`, `FunctionsApi`/`FunctionsProducerApi`,
`BinaryApi`/`BinaryProducerApi`, `SchemasApi`/`SchemasProducerApi`) plus the
`wrap<Tag>Producer(...)` factories, and into `generated/model/` the
`DataProducerObject`, `ObjectClass`, `Schema` and related model types.

The implementation imports the `*ProducerApi` interfaces from the module's own
`generated/` folder; their signatures are the contract, so read them there
instead of guessing parameter order. `generated/` is git-ignored, so a fresh
checkout does not typecheck until `zbb generate` has run.

## Step 4 - file layout

All hand-written DataProducer code lives in `src/impl/dataproducer/`:

```
src/impl/dataproducer/
  DataProducerImpl.ts      # one class implementing all six *ProducerApi
  DataProducerMappers.ts   # free functions: entity -> object / element / document
  DataProducerSchemas.ts   # SCHEMA_IDS registry + buildSchema calls + getSchemaById
  SchemaBuilder.ts         # thin adapter over data-utils buildSchema
  findSpec.ts              # locates the bundled OpenAPI spec at runtime
```

Each file has a template of the same name in `assets/`.

## Step 5 - design the object-ID grammar first

Object IDs are the API of the hierarchy. Write the full grammar down before
any code, and keep it in the `DataProducerImpl` class docblock.

- The root is `'/'`.
- Every other ID is `<typePrefix>_<coordinates>`, with coordinates joined by
  `/` for nesting: `org_{login}`, `repo_{owner}/{name}`, `team_{org}/{slug}`,
  `collection_{type}_{scope}`, `func_{operation}`.
- Prefixes are unambiguous: no prefix is a prefix of another, otherwise
  `startsWith` routing picks the wrong branch.
- Coordinates round-trip: every ID that `getChildren` or `getObject` emits
  parses back in `getObject`. Parse with `slice(prefix.length)` and
  `split('/')`. If a coordinate can itself contain `/` or `_`, encode it (for
  example with `encodeURIComponent`) instead of shipping an ambiguous grammar.
- Collection IDs embed a type token taken from the single `COLLECTIONS`
  registry (Step 7), not from a second hand-maintained list.

## Step 6 - DataProducerSchemas.ts

Start from `assets/DataProducerSchemas.ts`; copy `assets/SchemaBuilder.ts` and
`assets/findSpec.ts` unchanged.

Schema IDs follow the canonical form defined in the interface package's
`documentation/SchemaIds.md`:

```
schema:{type}:{catalog}.{schema}.{name}[:{direction}]
```

- `{type}` is one of the discriminators that document lists; the interface
  spec validates IDs against that list. For an API entity that is not a
  database table the fitting type is `shared`
  (`schema:shared:acme.acme.organization`); function input and output schemas
  use `schema:function:...:input` / `:output`.
- The coordinates mirror the module's package coordinates (for example
  `github.github`), and `{name}` is the entity name in lowerCamelCase,
  singular.
- Do not invent ad-hoc formats such as `{vendor}_{entity}_schema`: `getSchema`
  round-trips these IDs and consumers store them.

`buildSchema` combines the generated model class (property names and types)
with the module's bundled OpenAPI spec (descriptions, formats, enums). The
class is used for reflection only; nothing is constructed.

`findSpec.ts` derives the bundled spec's file name from the `package.json`
`name` without its scope (`@zerobias-org/module-acme-widget` becomes
`module-acme-widget.yml`) and probes the locations used in development, in the
published `dist/` and in containers.

Schemas are built at import time, as module-scope constants, so a missing spec
fails when the module loads rather than inside `getSchema`. That is intended
(fail fast); remember it when debugging a module that will not load.

## Step 7 - DataProducerMappers.ts

Start from `assets/DataProducerMappers.ts`. Mappers are free exported
functions, not class methods, in three signature families: an async object
getter, an async children/collection filler that mutates `results` and returns
`void`, and a synchronous pure builder for static structure.

### Typed literals, not positional constructors

The generated model may offer multi-argument positional constructors. Do not
use them: a call with fifteen arguments padded with `undefined` cannot be
reviewed, breaks silently when the model gains a field, and swaps adjacent
same-typed arguments without a compiler error. Declare a typed object literal
with named fields, as the `mapping` skill requires for every mapper.

The field names are those of `DataProducerObject`; the table in the interface
package's `documentation/Concepts.md` is the list. `id` and `name` are
required. The root object has `id` and `name` both equal to `'/'`.

### objectClass decides which operations are legal

`objectClass` is an array; its values gate which operations a node accepts.

| Node kind | objectClass | Also set |
|---|---|---|
| Pure container (root, grouping nodes) | `[Container]` | - |
| Entity with document data and children | `[Document, Container]` | `documentSchema` |
| Collection node | `[Collection]` | `collectionSchema`, `collectionSize` (derived, not hardcoded) |
| Function | `[Function]` | `inputSchema`, `outputSchema` |
| File / blob | `[Document, Binary]` | `documentSchema`, `mimeType`, `fileName`, `size` |

### Collections: one registry

Define every collection a parent exposes once, in a `COLLECTIONS` array, and
derive the rest from it: child lists, ID parsing, element dispatch, schema
lookup and sizes. Duplicated lists drift apart.

Collection elements are plain literals shaped by the element schema, with the
parent coordinates denormalized onto every element. That is what lets a flat
element trace back to its parent.

### Pagination

`PagedResults` is both input and output: read `results.pageNumber` and
`results.pageSize`, pass them straight to the tag producer, and assign
`results.items`. Leave `pageNumber`, `pageSize` and `pageCount` alone.

Do not paginate and then filter on the client: filtering after the fetch
returns short pages without any signal. If the vendor API cannot scope a query
server-side, expose the broader collection instead.

### Document data

`getDocumentData` returns the entity's fields with each leaf wrapped as
`{ value: <x> }`, and nested objects spread conditionally
(`...(r.owner && { owner: mapOwnerDoc(r.owner) })`).

## Step 8 - DataProducerImpl.ts

Start from `assets/DataProducerImpl.ts`: one class implementing all six
generated producer interfaces, routing on the ID prefix and delegating to one
mapper per branch. It is constructed with the module Impl only and does not
hold the raw client, so that all data access goes through the module's own
wrapped producers.

### Error policy

| Situation | Throw | Message shape |
|---|---|---|
| Unknown objectId / schemaId / element | `NoSuchObjectError` | `('object', objectId)` / `('schema', schemaId)` |
| Write operation not implemented | `UnexpectedError` | `` `<Verb> not implemented for ${objectId}` `` |
| Read operation the module does not support (for example search with no backing API) | `UnexpectedError` | `` `<Op> not supported` `` |
| Vendor API returns 404 on a listing because the entity legitimately has none | nothing | `results.items = []` |

Returning `results.items = []` for an unsupported operation is not allowed: a
consumer cannot tell "no matches" from "not supported". Empty results are for
genuinely empty answers only. The same holds for parameters that are accepted
but ignored: if `filter`, `properties` or `recursive` is not honored, throw
`UnexpectedError` when a caller passes it.

## Step 9 - wire the six getters on the module Impl

Add the fragment in `assets/module-impl-getters.ts` to `src/<Class>Impl.ts`.

- The `Api` types and `wrap*Producer` factories are imported from the published
  interface package; the producer interfaces `DataProducerImpl` implements come
  from the module's own `generated/`. The split is deliberate.
- All six getters share one memoized `DataProducerImpl`. The class is
  stateless, so memoization is an optimization rather than a correctness
  requirement, but six instances per connector are noise.

## Step 10 - tests

Connector modules: DataProducer coverage lives in the module's e2e suite
(`test/e2e/*.test.ts`, the `describeModule<T>` harness; see the `testing`
skill), and the same suite runs under `TEST_MODE=direct|docker|hub`. The
minimum blocks are in `assets/dataproducer.e2e.test.ts`: root object, unknown
ID, child-ID round-trip, collection listing, empty listing on a vendor 404,
schema-ID round-trip, unknown schema ID, and one assertion for every write the
implementation declines.

The child-ID round-trip test is required. It is the only test that catches an
ambiguous ID grammar before a consumer does.

Plain (profile-less) modules have no e2e suite (see the `testing` skill), so
the same assertions go in `test/unit/` against `DataProducerImpl` directly.
`package/microsoft/azure/azureresourcegraph/test/unit/DataProducerTest.ts`
shows that form.

## Step 11 - gate

Stage the new files, then run the gate; the stamp only covers tracked files
(see the `gates` skill).

```bash
zbb gate
```

The work is done when the full lifecycle is green and the regenerated
`gate-stamp.json` is committed. DataProducer is part of a module's definition
of done: a new module is not finished until its six DataProducer getters work
end to end.

Before the final run, go through `references/review.md` (anti-patterns and the
completion checklist).
