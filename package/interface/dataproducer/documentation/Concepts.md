# DataProducer Concepts

The canonical specification of the DataProducer interface: object model, classes,
operations, and lifecycle. This document is the source of truth for what an
implementation must provide. The OpenAPI spec in `api.yml` reflects this model;
deviations between the two should be treated as bugs in `api.yml`.

> **Audience:** anyone implementing or consuming the DataProducer interface, in
> either direction. For the workflow rules that govern editing this codebase,
> see `../CLAUDE.md`.

## Three Core Entities

| Entity   | Purpose                                                            |
|----------|--------------------------------------------------------------------|
| Object   | A node in the browseable tree. Carries identity, metadata, and one or more capability indicators (`objectClass`). |
| Schema   | The shape of the data inside an Object. Always referenced by `id`, never embedded inline. |
| Property | A single field within a Schema. Names a `dataType` from `@zerobias-org/types-core`. |

## Object

### Fields

| Field         | Type        | Required | Notes |
|---------------|-------------|----------|-------|
| `id`          | string      | yes      | Immutable primary key. Identifier for the object's lifetime. |
| `name`        | string      | yes      | Human-readable name. Names are informational; collisions are allowed but discouraged. |
| `description` | string      | no       | Free-form description. |
| `path`        | string[]    | no       | Documentary path from root. **Not** an identifier — `id` is the identifier. |
| `thumbnail`   | URI string  | no       | Visual preview. May be a `data:` URI or public URL. |
| `tags`        | string[]    | no       | Organizational tags. |
| `created`     | date-time   | no       | Creation timestamp. |
| `modified`    | date-time   | no       | Last modification timestamp. |
| `etag`        | string      | no       | Used with `If-Match` for optimistic concurrency on writes. |
| `versionId`   | string      | no       | Version pointer when the underlying source supports versioning. |
| `objectClass` | ObjectClass[] | no     | Capability indicators (see next section). |

Class-specific fields (`collectionSchema`, `inputSchema`, `outputSchema`,
`documentSchema`, `mimeType`, `fileName`, `size`, `throws`) appear only on
objects whose `objectClass` array includes the relevant class.

### Root Object

Every implementation **MUST** expose a root object with both `id` and `name`
equal to `"/"` and `objectClass` containing `container`. The root is reached
via `GET /objects` (operation `getRootObject`).

### Hierarchy

Objects can have multiple parents — the structure is a DAG, not a strict tree.
The path from root to an object is documentary; navigate via `id` and the
parent/child operations.

### Optimistic Concurrency

Writes that modify an existing object (`updateObject`, `updateCollectionElement`,
`updateDocumentData`) accept an `If-Match` header carrying the `etag` last
observed by the caller. Implementations that support concurrency control must
reject mismatched ETags with `412 Precondition Failed`.

## ObjectClass

A fixed enum. Each class adds a set of operations to the object.

```
container | collection | function | document | binary
```

An object may have any combination of classes (e.g. a Container that is also a
Collection). Operations only valid for classes the object does not declare must
fail with `UnsupportedOperationError`.

## Operations Matrix

| Class      | Read operations                                                                                 | Write operations                                                                |
|------------|-------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| container  | `getChildren`, `objectSearch`, `searchChildObjects`                                             | `createChildObject`, `updateObject`, `deleteObject`                              |
| collection | `getCollectionElements`, `searchCollectionElements`, `getCollectionElement`                     | `addCollectionElement`, `updateCollectionElement`, `deleteCollectionElement`, `executeBulkOperations` |
| function   | `invokeFunction`, `validateFunctionInput`                                                       | (invocation-only — no separate write ops)                                        |
| document   | `getDocumentData`                                                                                | `updateDocumentData`                                                             |
| binary     | `downloadBinary` (supports streaming + Range)                                                    | `uploadBinaryContent`                                                            |

The schema for an object's content (collection elements, function inputs/outputs,
document body) is referenced via a Schema ID — fetch via `getSchema`.

### Operation Notes

- **`searchChildObjects`** (`/objects/{objectId}/searchChildren`) is the
  RFC4515-aware counterpart to `getChildren`. It supports filtering on child
  metadata, property projection (`properties` query param), and both pagination
  modes.
- **Collection element CRUD** requires the collection's schema to declare at
  least one `primaryKey: true` property. Collections without a primary key are
  read-only at the element level.
- **`executeBulkOperations`** runs a batch of `create`/`update`/`delete`
  collection-element operations with optional atomicity and continue-on-error.
- **`invokeFunction`** is intentionally transport-agnostic. The interface
  exposes only `inputSchema` / `outputSchema` / `throws` — no transport
  metadata. If a particular implementation needs HTTP-specific routing
  (`httpMethod`, `httpPath`, etc.) it lives on the implementation's own
  sub-interface (e.g. an `HttpModule`), not on the base DataProducer Object.
- **`downloadBinary`** must support HTTP Range requests for resumable downloads.

## Schema Resource

A Schema describes the shape of data inside an Object.

| Field        | Type        | Required | Notes |
|--------------|-------------|----------|-------|
| `id`         | SchemaId    | yes      | Canonical form `schema:{type}:{catalog}.{schema}.{name}[:{direction}]`. See `SchemaIds.md`. |
| `dataTypes`  | DataType[]  | yes      | The DataType entities the schema's properties refer to, by name. |
| `properties` | Property[]  | yes      | The fields. |

Schemas are **never inlined** in object metadata. Object fields like
`collectionSchema`, `inputSchema`, `outputSchema`, `documentSchema`, and the
values of `throws` are all Schema IDs (strings). Callers fetch the full schema
via `GET /schemas/{schemaId}` and are expected to cache it.

## Property

| Field         | Type      | Default | Notes |
|---------------|-----------|---------|-------|
| `name`        | string    | —       | JavaScript-compatible identifier. |
| `description` | string    | —       | Free-form. |
| `required`    | boolean   | `false` | Whether the property must be present. |
| `multi`       | boolean   | `false` | Whether multiple values are allowed (array semantics). |
| `primaryKey`  | boolean   | `false` | Marks the property as part of the collection's primary key. |
| `dataType`    | string    | —       | Name of a DataType from `@zerobias-org/types-core`. See `CoreDataTypes.md`. |
| `format`      | string    | —       | Display/input formatting hint **only**. Never use this as a substitute for picking the correct `dataType`. |
| `references`  | object    | —       | `{ schemaId, propertyName? }`. Presence of `propertyName` indicates a foreign-key relationship; absence indicates schema composition. See `SchemaIds.md`. |

## Pagination

All list/search operations support both modes:

- **Offset-based:** `pageNumber` (1-indexed), `pageSize` (default 100, max 1000).
- **Cursor-based:** `pageToken`. When provided, `pageNumber` is ignored. The
  next-page token is returned in the `pageToken` response header. Absence of
  the header indicates the last page.

Implementations should return whichever mode their underlying source supports
naturally. Clients must not assume both modes are functional; they detect the
mode from response headers.

## Filtering

Structured filters use **RFC4515** syntax (LDAP filter grammar). LDAP backends
pass filters through unchanged; other backends translate via
`org/util/packages/litefilter`. See `FilterSyntax.md`.

## Property Selection

List/search operations accept a `properties` query parameter — a dot-notation
array selecting which fields to return. Examples:

```
properties=email                        # only the email field (fuzzy-matched against e-mail, email_address, …)
properties=contact.phone,contact.email  # include only these nested fields
properties=                              # (omitted) — return all fields
```

Fuzzy matching normalizes case and word separators (`-`, `_`, camelCase).

## Errors

The platform error model lives in `@zerobias-org/types-core`. The two relevant
to almost every operation:

- `noSuchObjectError` (HTTP 404) — object or schema ID not found.
- `UnsupportedOperationError` (HTTP 400) — the object does not declare the
  class required for this operation.

Function objects may declare additional errors via the `throws` map (error code
→ Schema ID). See `Errors.md`.

## Lifecycle Constraints

- **Root**: required, immutable, `id == name == "/"`, includes `container`.
- **Identifiers**: Object `id` and Schema `id` are immutable for the lifetime
  of the entity.
- **Names**: informational; uniqueness under a parent is encouraged, not enforced.
- **Multiple parents**: allowed; circular reference detection is the caller's
  responsibility.
- **Authorization**: inherited from the underlying connection's principal —
  the interface does not define its own auth model.
