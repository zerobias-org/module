# DynamoDB Mapping

## Overview

How AWS DynamoDB and protocol-compatible stores (ScyllaDB Alternator,
DynamoDB Local) surface through the DataProducer interface. Tables are
Collections; items are collection elements; the partition key (and optional
sort key) drives `primaryKey: true`. The producer translates RFC4515
filters into DynamoDB key-condition and filter expressions, and chooses
`Query` vs `Scan` based on whether the filter touches a key. Streams and TTL
are exposed via descriptive metadata only — the interface contract for
reads and writes is `Query`/`Scan`/`GetItem`/`PutItem`/`UpdateItem`/`DeleteItem`.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| DynamoDB concept              | DataProducer concept                | Notes                                                             |
|-------------------------------|-------------------------------------|-------------------------------------------------------------------|
| Service endpoint              | Root container (`id == "/"`)         | One per producer connection (region-scoped).                      |
| Table                         | Collection                           | Read+write if the key schema is fully addressable; otherwise list/search only. |
| Item                          | Collection element                   | Primary key = partition key (and sort key if present).            |
| Global / Local Secondary Index | (see Edge Cases)                    | Modeled as a separate Collection alongside the base table.        |
| Custom item shape (Map)       | Referenced via `schema:type:…`       | Resolved through `getSchema`.                                     |
| Stream                        | (metadata only)                      | The interface does not surface streams as live Functions.          |
| TTL                           | (metadata only)                      | Exposed as schema property description.                            |
| `BatchGetItem` / `BatchWriteItem` | `executeBulkOperations`          | The standard collection bulk op.                                   |

## Object Mappings

For an account in `us-east-1`, the producer scopes IDs under a logical
catalog (`aws_us_east_1`) so schema IDs have a stable three-part form.

### Service Root (Container)

```yaml
Object:
  id: "/"
  name: "/"
  objectClass: ["container"]
  description: "DynamoDB service (us-east-1)"
  tags: ["dynamodb", "nosql"]
```

### Table with Simple Key (Collection)

```yaml
Object:
  id: "/table:Users"
  name: "Users"
  objectClass: ["collection"]
  description: "User accounts"
  path: ["Users"]
  collectionSchema: "schema:table:aws_us_east_1.default.Users"
  collectionSize: 15847
  tags: ["table"]
```

```yaml
Schema:
  id: "schema:table:aws_us_east_1.default.Users"
  dataTypes:
    - name: "string"
    - name: "email"
    - name: "date-time"
  properties:
    - name: "userId"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Partition key (HASH)"
    - name: "email"
      dataType: "email"
      required: true
    - name: "username"
      dataType: "string"
      required: true
    - name: "createdAt"
      dataType: "date-time"
      required: true
    - name: "status"
      dataType: "string"
      description: "Lifecycle status"
      references:
        schemaId: "schema:enum:aws_us_east_1.default.UserStatus"
    - name: "profile"
      dataType: "string"
      description: "Nested profile"
      references:
        schemaId: "schema:type:aws_us_east_1.default.UserProfile"
    - name: "tags"
      dataType: "string"
      multi: true
```

### Table with Composite Key (Collection)

```yaml
Object:
  id: "/table:Orders"
  name: "Orders"
  objectClass: ["collection"]
  description: "Customer orders"
  collectionSchema: "schema:table:aws_us_east_1.default.Orders"
  tags: ["table", "transactional"]
```

```yaml
Schema:
  id: "schema:table:aws_us_east_1.default.Orders"
  dataTypes:
    - name: "string"
    - name: "date-time"
    - name: "decimal"
  properties:
    - name: "customerId"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Partition key (HASH)"
    - name: "orderDate"
      dataType: "date-time"
      primaryKey: true
      required: true
      description: "Sort key (RANGE)"
    - name: "orderId"
      dataType: "string"
      required: true
    - name: "totalAmount"
      dataType: "decimal"
      required: true
    - name: "status"
      dataType: "string"
      references:
        schemaId: "schema:enum:aws_us_east_1.default.OrderStatus"
```

A composite key marks both fields `primaryKey: true`. Element addressing
requires both values; the interface form
`getCollectionElement(["customerId=customer123","orderDate=2025-10-24…"])`
materializes both into a `Key` map for `GetItem`.

### Secondary Index (Collection)

A GSI surfaces as its own Collection sibling to the base table:

```yaml
Object:
  id: "/table:Users/index:EmailIndex"
  name: "EmailIndex"
  objectClass: ["collection"]
  description: "GSI: Users by email"
  collectionSchema: "schema:table:aws_us_east_1.default.Users.EmailIndex"
  tags: ["index", "gsi"]
```

The schema body declares the GSI's key fields as `primaryKey: true` and
notes that mutating writes are not supported (an index is read-only). When
the projection is `KEYS_ONLY` or `INCLUDE`, the schema lists only the
projected attributes.

### Custom Map Type (Referenced Schema)

```yaml
Schema:
  id: "schema:type:aws_us_east_1.default.UserProfile"
  dataTypes:
    - name: "string"
    - name: "phoneNumber"
  properties:
    - name: "firstName"
      dataType: "string"
    - name: "lastName"
      dataType: "string"
    - name: "phone"
      dataType: "phoneNumber"
```

## Operation Mappings

### Browsing

| Operation                      | DynamoDB API                                                          |
|--------------------------------|-----------------------------------------------------------------------|
| `getRootObject`                | (synthetic — endpoint identity)                                       |
| `getChildren` on root          | `ListTables`                                                          |
| `getChildren` on table         | `DescribeTable` to list secondary indexes                             |
| `getObject` on table           | `DescribeTable`                                                       |

### Items

| Operation                      | DynamoDB API                                                          |
|--------------------------------|-----------------------------------------------------------------------|
| `getCollectionElements`        | `Scan` (no key constraint) — paginated via `ExclusiveStartKey`         |
| `searchCollectionElements`     | `Query` if filter touches the partition key; otherwise `Scan` with `FilterExpression` |
| `getCollectionElement(key)`    | `GetItem` with `Key`                                                  |
| `addCollectionElement`         | `PutItem` (with `attribute_not_exists` condition for create-only)     |
| `updateCollectionElement(key)` | `UpdateItem` with `UpdateExpression` (`SET`, `REMOVE`, `ADD`, `DELETE`) |
| `deleteCollectionElement(key)` | `DeleteItem`                                                          |
| `executeBulkOperations`        | `BatchWriteItem` (≤25 ops) or `TransactWriteItems` when `atomic: true` (≤100 ops) |

### Query vs Scan Selection

The producer inspects the parsed filter AST:

- If the AST contains an equality on the partition key (and optionally a
  comparison on the sort key), emit a `Query` with that pair as the
  `KeyConditionExpression` and the remainder as `FilterExpression`.
- Otherwise emit a `Scan` with the entire filter as `FilterExpression`. Scan
  is correct, but expensive on large tables — the producer should warn
  through telemetry, not refuse.

## Filter Translation Example

The producer walks the lite-filter AST and emits parameterized DynamoDB
expressions, then routes the request to `Query` or `Scan` based on which
attributes are constrained.

```
RFC4515:
  (&(customerId=cust-123)(orderDate>=2025-10-01)(status=SHIPPED))

AST (lite-filter):
  AND
    EQ customerId "cust-123"
    GTE orderDate "2025-10-01"
    EQ status "SHIPPED"

Routing decision:
  customerId is the table's partition key → Query
  orderDate is the sort key             → KeyConditionExpression
  status is non-key                     → FilterExpression

Emitted Query:
  TableName: "Orders"
  KeyConditionExpression: "#cid = :cid AND #od >= :od"
  FilterExpression:       "#st = :st"
  ExpressionAttributeNames:  { "#cid":"customerId", "#od":"orderDate", "#st":"status" }
  ExpressionAttributeValues: { ":cid":{"S":"cust-123"}, ":od":{"S":"2025-10-01"}, ":st":{"S":"SHIPPED"} }
```

| RFC4515            | DynamoDB FilterExpression       |
|--------------------|---------------------------------|
| `(name=Alice)`     | `name = :v`                      |
| `(age>=18)`        | `age >= :v`                      |
| `(name=A*)`        | `begins_with(name, :v)`          |
| `(name=*ice*)`     | `contains(name, :v)`             |
| `(email=*)`        | `attribute_exists(email)`        |
| `(&(a=1)(b=2))`    | `a = :v1 AND b = :v2`            |
| `(\|(a=1)(b=2))`   | `a = :v1 OR b = :v2`             |
| `(!(active=true))` | `NOT (active = :v)`              |

DynamoDB has no native suffix-only `LIKE '%foo'`. A producer that receives
`(name=*foo)` falls back to a Scan with in-process post-filtering for that
predicate, or rejects the request with a clear error — the choice belongs
to the producer's documented capability set.

## Core Types Reference

| DynamoDB attribute type | `dataType`         | Notes                                                                 |
|-------------------------|--------------------|-----------------------------------------------------------------------|
| `S` (String)            | `string`           | Use a more specific core type if the value is email / URL / etc.      |
| `N` (Number, integer)   | `integer`          |                                                                       |
| `N` (Number, fractional)| `decimal`          | Money must be `decimal` — never a JS float.                          |
| `BOOL`                  | `boolean`          |                                                                       |
| `B` (Binary)            | `byte`             | Base64-encoded over the wire.                                         |
| `SS` / `NS` / `BS` (Sets)| parent `dataType` + `multi: true` | Sets are unordered; the interface treats them as arrays. |
| `L` (List)              | parent `dataType` + `multi: true` | Lists are ordered; preserve insertion order.            |
| `M` (Map)               | `string` + `references.schemaId` to a `schema:type:…` |                                          |
| `NULL`                  | (any property with `required: false`) |                                                            |

DynamoDB has no native `date-time` type — timestamps are stored as `S`
(ISO 8601) or `N` (epoch). Either is acceptable; the schema's
`dataType: date-time` validates the value regardless of which is on the
wire. A producer that stores them as numbers is responsible for
converting to/from ISO 8601 at the boundary.

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **Item size limit.** DynamoDB rejects items > 400KB. For large opaque
  payloads, store in S3 and keep a `url` reference in the item.
- **Eventual consistency.** Reads default to eventually consistent. The
  producer may expose `ConsistentRead` as a per-call option; without it,
  callers must accept stale reads.
- **Pagination.** `Query` and `Scan` paginate via `LastEvaluatedKey`. The
  producer wraps that key in the DataProducer `pageToken` (opaque to the
  caller; serialize as base64 JSON so it survives the response header).
- **Parallel scan.** `Segment` / `TotalSegments` are a `Scan` performance
  knob and not surfaced through the interface; the producer may use them
  internally when a single scan exceeds a configured row count.
- **Transactions.** `executeBulkOperations` with `atomic: true` translates
  to `TransactWriteItems` (≤100 items, includes optional condition checks).
  Without `atomic`, it uses `BatchWriteItem` (≤25 items, no isolation).
- **Conditional writes.** Optimistic concurrency goes through the
  DataProducer `If-Match` header on `updateCollectionElement` /
  `deleteCollectionElement`. The producer translates the ETag into a
  `ConditionExpression` against an `etag` or `version` attribute.
- **TTL.** When the table has TTL enabled, expose the TTL attribute as an
  ordinary property with a description noting "TTL — items are removed
  after this epoch". Use `integer` (epoch seconds) per the DynamoDB
  contract.
- **Streams.** Stream ARNs and view types are descriptive — record them
  in producer metadata (or expose a `Document` object listing stream
  configuration) but do not present streams as Functions.
- **Reserved words.** DynamoDB reserves a long list of attribute names
  (`Status`, `Name`, `Size`, …). Always use `ExpressionAttributeNames`
  placeholders (`#name`) when emitting expressions; never substitute the
  raw attribute name.
- **Nested attribute updates.** `UpdateItem` paths support dot/index
  notation (`profile.phone`, `tags[2]`). The producer walks the
  `updateCollectionElement` payload and emits one `SET`/`REMOVE` per leaf.
- **Index queries.** A request that targets `/table:Foo/index:BarIndex`
  routes to `Query` with `IndexName: "BarIndex"`. Filter translation rules
  apply to the index's key schema, not the base table's.
- **ScyllaDB Alternator / DynamoDB Local.** Same protocol, same mapping;
  feature differences (no streams, no transactions in older versions, no
  PITR) are documented per-deployment in the connection profile rather than
  in this mapping.
- **Authentication.** AWS SigV4 / IAM / role assumption all live on the
  connection. The interface adds no auth concepts.
