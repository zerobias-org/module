# GraphQL Mapping

## Overview

How a GraphQL endpoint surfaces through the DataProducer interface. A
producer wraps a GraphQL client and uses the introspection schema to derive
the object hierarchy: list-returning Query fields become Collections,
parameterized fields become Functions, types defined in the schema surface
as referenceable Schema IDs. Mutations are Functions; the interface does
not distinguish them from queries — both invocations go through
`invokeFunction`. Subscriptions are out of scope for this version of the
DataProducer contract.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| GraphQL concept                          | DataProducer concept                | Notes                                                            |
|------------------------------------------|-------------------------------------|------------------------------------------------------------------|
| Endpoint                                 | Root container (`id == "/"`)         |                                                                  |
| `Query` root, `Mutation` root            | Containers                           | Named children of the root.                                      |
| List-returning field on `Query`          | Collection                           | `[T]` or Relay-connection `…Connection`. Element schema is `T`.   |
| Single-object field with arguments       | Function                             | `inputSchema` carries arguments; `outputSchema` is the field's return type. |
| Mutation field                           | Function                             | Same shape as a query Function.                                   |
| Object type / interface / union          | Referenced via `schema:type:…`       | Resolved through `getSchema`, not browseable as an Object.        |
| Enum                                     | Referenced via `schema:enum:…`       | Same.                                                             |
| Introspection (`__schema`, `__type`)     | Document                             | Read-only; surfaces the raw introspection payload.                |
| Subscription                             | (out of scope)                       | Streaming is not part of the DataProducer interface.              |

## Object Mappings

For a hypothetical endpoint at `https://api.shop.example/graphql`, the
producer scopes IDs under a logical name (`shop_example`) so that schema IDs
have a stable three-part form.

### Query Root (Container)

```yaml
Object:
  id: "/query"
  name: "Query"
  objectClass: ["container"]
  description: "GraphQL Query root — read operations"
  path: ["Query"]
  tags: ["query"]
```

### List Field (Collection)

```yaml
Object:
  id: "/query/users"
  name: "users"
  objectClass: ["collection"]
  description: "users field returning [User!]!"
  path: ["Query", "users"]
  collectionSchema: "schema:type:shop_example.types.User"
  tags: ["users"]
```

```yaml
Schema:
  id: "schema:type:shop_example.types.User"
  dataTypes:
    - name: "string"
    - name: "email"
    - name: "date-time"
  properties:
    - name: "id"
      dataType: "string"
      primaryKey: true
      required: true
      description: "GraphQL ID scalar"
    - name: "email"
      dataType: "email"
      required: true
    - name: "name"
      dataType: "string"
    - name: "createdAt"
      dataType: "date-time"
      required: true
    - name: "posts"
      dataType: "string"
      multi: true
      description: "GraphQL [Post!]! — element references the Post schema"
      references:
        schemaId: "schema:type:shop_example.types.Post"
        propertyName: "id"
```

The producer renders `[Post!]!` as a multi-valued reference. A foreign-key
form (with `propertyName`) is appropriate when the field is a list of related
objects identifiable by ID; embed-by-composition (no `propertyName`) is
appropriate when the field is a list of value objects with no separate
identity.

### Parameterized Field (Function)

```yaml
Object:
  id: "/query/user"
  name: "user"
  objectClass: ["function"]
  description: "user(id: ID!): User"
  path: ["Query", "user"]
  inputSchema:  "schema:function:shop_example.query.user:input"
  outputSchema: "schema:function:shop_example.query.user:output"
  tags: ["query", "lookup"]
```

```yaml
Schema:
  id: "schema:function:shop_example.query.user:input"
  dataTypes:
    - name: "string"
  properties:
    - name: "id"
      dataType: "string"
      required: true
      description: "GraphQL ID scalar"

Schema:
  id: "schema:function:shop_example.query.user:output"
  dataTypes:
    - name: "string"
  properties:
    - name: "user"
      dataType: "string"
      description: "User reference; resolved against the User type schema"
      references:
        schemaId: "schema:type:shop_example.types.User"
        propertyName: "id"
```

### Mutation (Function)

```yaml
Object:
  id: "/mutation/createUser"
  name: "createUser"
  objectClass: ["function"]
  description: "createUser(input: CreateUserInput!): User!"
  path: ["Mutation", "createUser"]
  inputSchema:  "schema:function:shop_example.mutation.createUser:input"
  outputSchema: "schema:function:shop_example.mutation.createUser:output"
  throws:
    validation_error: "schema:shared:validation_error"
  tags: ["mutation"]
```

The base interface has no notion of "mutation" distinct from "function" —
GraphQL semantics (single execution per request, side-effecting) live inside
the producer.

### Introspection (Document)

```yaml
Object:
  id: "/__schema"
  name: "__schema"
  objectClass: ["document"]
  description: "Raw GraphQL introspection payload for this endpoint"
  documentSchema: "schema:shared:graphql_introspection"
  tags: ["introspection", "metadata"]
```

The schema body for `schema:shared:graphql_introspection` is the
GraphQL-spec-defined shape (`__Schema`, `__Type`, etc.) — describe it by
composition rather than redefining it inline.

## Operation Mappings

| DataProducer operation             | GraphQL execution                                                                                          |
|------------------------------------|------------------------------------------------------------------------------------------------------------|
| `getRootObject`                    | (synthetic — endpoint identity)                                                                            |
| `getChildren` on root              | Static — `Query`, `Mutation`, `__schema`, plus producer-defined groupings.                                  |
| `getChildren` on `/query`          | Driven by the introspection of the `Query` type.                                                            |
| `getCollectionElements`            | `query { <field>(first: $pageSize, after: $cursor) { …selected fields } }` (Relay connection)              |
| `searchCollectionElements`         | Same, with translated `where:` / `filter:` argument from the RFC4515 expression.                           |
| `getCollectionElement(key)`        | `query { <field>(id: $key) { …selected fields } }` if a singular by-id field exists; otherwise filter +1. |
| `addCollectionElement`             | Corresponding `create…` mutation (if exposed).                                                              |
| `updateCollectionElement(key)`     | Corresponding `update…` mutation.                                                                           |
| `deleteCollectionElement(key)`     | Corresponding `delete…` mutation.                                                                           |
| `invokeFunction`                   | Single GraphQL operation — query for query Functions, mutation for mutation Functions.                     |
| `getDocumentData` on `/__schema`   | `query { __schema { … } }` introspection.                                                                  |

The producer is responsible for selecting fields. Selection is driven by the
DataProducer `properties` query parameter (see `Concepts.md`); the producer
walks the schema referenced by `collectionSchema` / `outputSchema` and emits
the matching GraphQL selection set.

## Filter Translation Example

GraphQL has no standard filter argument convention. The producer translates
the RFC4515 AST into whichever convention the target endpoint uses (Relay
`where:`, Hasura-style `where: { field: { _eq: ... } }`, custom argument
sets). The lite-filter parser provides the AST; the emit step is endpoint-
specific.

```
RFC4515:
  (&(active=true)(|(country=US)(country=CA))(createdAt>=2025-01-01))

AST (lite-filter):
  AND
    EQ active true
    OR
      EQ country "US"
      EQ country "CA"
    GTE createdAt "2025-01-01"

Emitted (Hasura-style example):
  where: {
    _and: [
      { active: { _eq: true } },
      { _or: [
        { country: { _eq: "US" } },
        { country: { _eq: "CA" } }
      ]},
      { createdAt: { _gte: "2025-01-01" } }
    ]
  }
```

For endpoints that do not expose a filter argument at all, the producer
falls back to `expression.matches(item)` post-fetch. Producers that translate
must document the supported predicate set in their connector documentation.

## Core Types Reference

| GraphQL type                | `dataType`         | Notes                                                              |
|-----------------------------|--------------------|--------------------------------------------------------------------|
| `ID`                        | `string`           | Treated as opaque; mark `primaryKey` on the canonical id property. |
| `String`                    | `string`           | If the value is an email/URL/etc., use the matching core type.     |
| `Int`                       | `integer`          |                                                                    |
| `Float`                     | `decimal` (currency) or generic number | Currency-bearing fields must be `decimal`.            |
| `Boolean`                   | `boolean`          |                                                                    |
| `DateTime` (custom scalar)  | `date-time`        | Matches ISO 8601 — the core type's pattern validates the value.    |
| `Date` (custom scalar)      | `date`             |                                                                    |
| `URL`/`URI` (custom scalar) | `url`              |                                                                    |
| `Email` (custom scalar)     | `email`            |                                                                    |
| `BigDecimal` / `Money`      | `decimal`          |                                                                    |
| Enum types                  | `string` + `references.schemaId` to a `schema:enum:…` |                                          |
| Object types                | property whose `references.schemaId` points at the type's `schema:type:…` schema. |               |
| List wrapper (`[T]`)        | parent `dataType` + `multi: true` |                                                              |
| Non-null wrapper (`T!`)     | maps to `required: true` on the property |                                                          |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **Pagination.** Relay connections use `pageInfo { endCursor hasNextPage }`
  + `edges { node }`. Wrap the `endCursor` in the DataProducer `pageToken`
  response header. Offset-style pagination (`first` / `offset`) is also
  acceptable; the producer picks based on what the field exposes.
- **Connection-style collections.** When a field is `UserConnection` rather
  than `[User!]!`, the collection's element schema is the connection's `node`
  type, not the connection itself. The producer flattens edges/cursors into
  pagination metadata.
- **Aliasing.** GraphQL fields may have non-JS-identifier names if quoted in
  the schema. The producer should expose them under their schema name, not
  rewrite them.
- **Interfaces and unions.** Either expose them as `schema:type:…` whose
  schema body documents the `__typename` discriminator and the union arms, or
  flatten to the most common subtype. The first option preserves the type
  information; the second loses it.
- **Custom scalars.** Map to the closest core type by inspecting the scalar's
  format. If unknown, fall back to `string` + `description` — never invent a
  custom `format:` string.
- **Schema evolution.** GraphQL has no schema versioning; introspect on
  connection establishment and re-introspect on a TTL or on cache miss. Cache
  the resulting schema bodies under their `schema:type:…` IDs.
- **Errors.** GraphQL `errors[]` accompany partial data. A producer maps
  fatal errors (no `data`) to the appropriate DataProducer error and surfaces
  field-level errors in `throws[errorCode]`-shaped form when the operation's
  schema declares them.
- **Authorization.** Bearer tokens, signed requests, etc. live on the
  underlying connection. The interface adds nothing.
- **Subscriptions.** Not surfaced. If a use case demands streaming, model it
  via repeated invocation of a Function or surface the subscription
  separately on a streaming sub-interface — not on the base DataProducer.
