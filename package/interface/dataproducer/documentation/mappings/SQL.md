# SQL Mapping

## Overview

How a relational database (PostgreSQL is the canonical example; this generalizes
to MySQL, SQL Server, Oracle, etc.) surfaces through the DataProducer
interface. A producer translates between RFC4515 filters / object operations
and the database's native query language; the interface contract is the same
regardless of dialect.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| Native concept       | DataProducer concept                       | Notes                                    |
|----------------------|--------------------------------------------|------------------------------------------|
| Server               | Root container                              | `id == name == "/"`                      |
| Database / catalog   | Container                                    |                                          |
| Schema               | Container                                    | Direct child of database                  |
| Table                | Collection (read+write if PK declared)        | `collectionSchema` is `schema:table:...` |
| View                 | Collection (read-only)                       | `collectionSchema` is `schema:view:...`  |
| Function / proc      | Function                                     | `inputSchema` / `outputSchema` carry direction |
| Custom type          | (referenced by `schema:type:...`)            | Returned via `getSchema`, not a top-level Object |
| Enum type            | (referenced by `schema:enum:...`)            | Same — surfaces inside schemas, not as a browseable Object |
| Index, constraint    | Not exposed                                  | Implementation detail; doesn't shape the public model |

## Object Mappings

### Database (Container)

```yaml
Object:
  id: "/db:ecommerce"
  name: "ecommerce"
  objectClass: ["container"]
  description: "E-commerce application database"
  tags: ["database"]
```

### Schema (Container)

```yaml
Object:
  id: "/db:ecommerce/schema:public"
  name: "public"
  objectClass: ["container"]
  path: ["ecommerce", "public"]
  tags: ["schema"]
```

### Table (Collection)

```yaml
Object:
  id: "/db:ecommerce/schema:public/table:customers"
  name: "customers"
  objectClass: ["collection"]
  description: "Customer master records"
  path: ["ecommerce", "public", "customers"]
  collectionSchema: "schema:table:ecommerce.public.customers"
  collectionSize: 15000
  tags: ["table", "master-data"]
```

The `collectionSchema` is fetched via `getSchema`:

```yaml
Schema:
  id: "schema:table:ecommerce.public.customers"
  dataTypes:
    - name: "integer"
    - name: "string"
    - name: "email"
    - name: "date-time"
  properties:
    - name: "customer_id"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "Customer ID"
    - name: "email"
      dataType: "email"
      required: true
      description: "Primary contact email"
    - name: "created_at"
      dataType: "date-time"
      required: true
      description: "Row creation timestamp"
    - name: "status"
      dataType: "string"
      description: "Lifecycle status"
      references:
        schemaId: "schema:enum:ecommerce.public.customer_status"
```

### View (Read-only Collection)

```yaml
Object:
  id: "/db:ecommerce/schema:public/view:active_customers"
  name: "active_customers"
  objectClass: ["collection"]
  description: "Customers with status = 'active'"
  collectionSchema: "schema:view:ecommerce.public.active_customers"
  tags: ["view", "derived"]
```

A view's collection schema declares no `primaryKey: true` properties, which
implicitly disables element-level CRUD.

### Function (Function)

```yaml
Object:
  id: "/db:ecommerce/schema:public/function:calculate_tax"
  name: "calculate_tax"
  objectClass: ["function"]
  description: "Compute tax for an amount in a region"
  inputSchema:  "schema:function:ecommerce.public.calculate_tax:input"
  outputSchema: "schema:function:ecommerce.public.calculate_tax:output"
  throws:
    invalid_region:    "schema:shared:invalid_input_error"
    rate_unavailable:  "schema:shared:upstream_error"
  tags: ["function", "business-logic"]
```

```yaml
Schema:
  id: "schema:function:ecommerce.public.calculate_tax:input"
  dataTypes:
    - name: "decimal"
    - name: "geoSubdivision"
  properties:
    - name: "amount"
      dataType: "decimal"
      required: true
      description: "Pre-tax amount"
    - name: "region"
      dataType: "geoSubdivision"
      required: true
      description: "ISO 3166-2 subdivision code"

Schema:
  id: "schema:function:ecommerce.public.calculate_tax:output"
  dataTypes:
    - name: "decimal"
  properties:
    - name: "tax_amount"
      dataType: "decimal"
      required: true
    - name: "total_amount"
      dataType: "decimal"
      required: true
```

## Operation Mappings

### Browsing

| Operation                          | SQL                                                                |
|------------------------------------|--------------------------------------------------------------------|
| `getRootObject`                    | (synthetic — server identity)                                      |
| `getChildren` on root              | `SELECT datname FROM pg_database WHERE datistemplate = false`      |
| `getChildren` on database          | `SELECT schema_name FROM information_schema.schemata`              |
| `getChildren` on schema            | `SELECT table_name, table_type FROM information_schema.tables …`   |
| `getObject`                        | `information_schema` lookup by name                                |
| `searchChildObjects`               | `information_schema` lookup with RFC4515 filter applied to metadata |

### Collection (table) operations

| Operation                          | SQL                                                                |
|------------------------------------|--------------------------------------------------------------------|
| `getCollectionElements`            | `SELECT * FROM "schema"."table" ORDER BY pk LIMIT pageSize OFFSET …`|
| `searchCollectionElements`         | `SELECT * FROM "schema"."table" WHERE <translated filter> …`        |
| `getCollectionElement(key)`        | `SELECT * FROM "schema"."table" WHERE pk = $1`                      |
| `addCollectionElement`             | `INSERT INTO "schema"."table" (...) VALUES (...) RETURNING *`       |
| `updateCollectionElement(key)`     | `UPDATE "schema"."table" SET ... WHERE pk = $1 RETURNING *`         |
| `deleteCollectionElement(key)`     | `DELETE FROM "schema"."table" WHERE pk = $1`                        |
| `executeBulkOperations`            | Wrapped in a single transaction; rolls back on first error if `atomic:true`. |

### Function operations

| Operation                          | SQL                                                                |
|------------------------------------|--------------------------------------------------------------------|
| `invokeFunction`                   | `SELECT "schema"."function"($1, $2, …)`                             |
| `validateFunctionInput`            | Type-check input against `inputSchema` without executing            |

## Filter Translation Example

Translation uses the parsed RFC4515 AST from `@zerobias-org/util-lite-filter`.
The producer walks the AST and emits parameterized SQL.

```
Filter:    (&(active=true)(|(country=US)(country=CA))(created_at>=2025-01-01))

AST (lite-filter):
  AND
    EQ active true
    OR
      EQ country "US"
      EQ country "CA"
    GTE created_at "2025-01-01"

Emitted SQL:
  WHERE active = $1
    AND (country = $2 OR country = $3)
    AND created_at >= $4
  -- params: [true, 'US', 'CA', '2025-01-01']
```

Substring/prefix patterns map to `LIKE`:

| RFC4515            | SQL                              |
|--------------------|----------------------------------|
| `(name=A*)`        | `name LIKE 'A%'`                  |
| `(name=*ice)`      | `name LIKE '%ice'`                |
| `(name=*ice*)`     | `name LIKE '%ice%'`               |
| `(email=*)`        | `email IS NOT NULL`               |
| `(!(active=true))` | `NOT (active = true)`             |

Producers must always parameterize values to avoid SQL injection — the lite-filter
AST never returns raw text, so emitting `$1`-style placeholders is straightforward.

## Core Types Reference

Common mappings from PostgreSQL types to DataProducer core dataTypes. See
[`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

| PostgreSQL type            | `dataType`         | Notes                                          |
|----------------------------|--------------------|------------------------------------------------|
| `int2`, `int4`, `int8`     | `integer`          |                                                |
| `numeric`, `decimal`       | `decimal`          | Use for money — never `float`/`double`.        |
| `float4`, `float8`         | `decimal` or generic number, depending on need  | Currency must be `decimal`.                    |
| `text`, `varchar`, `char`  | `string`           | If the text is an email/URL/etc., use that core type instead. |
| `boolean`                  | `boolean`          |                                                |
| `date`                     | `date`             |                                                |
| `timestamp`, `timestamptz` | `date-time`        |                                                |
| `interval`                 | `duration`         |                                                |
| `uuid`                     | `uuid`             |                                                |
| `bytea`                    | `byte`             | Base64-encoded over the wire.                  |
| `inet`, `cidr`             | `ipAddress`        |                                                |
| `json`, `jsonb`            | `string` (with composition reference) | Prefer modeling structure via a referenced shared schema. |
| `array` (e.g. `int[]`)     | parent `dataType` + `multi: true` | E.g. `dataType: integer, multi: true`. |
| user-defined enum          | `string` + `references.schemaId` to a `schema:enum:…`   | The schema body lists the allowed values. |
| user-defined composite     | `string` + `references.schemaId` to a `schema:type:…`   | Schema body lists the fields.            |

For PostGIS or other extension types, model via composition: define a
`schema:shared:point` (or similar), and have properties reference it.

## Edge Cases

- **Quoted identifiers.** PostgreSQL identifiers can contain any character if
  quoted (`"My Table"`). Schema IDs do not encode quotes — the producer is
  responsible for round-tripping the original casing/quoting when emitting SQL.
- **Schema search path.** `getChildren` on a schema reflects only that schema;
  it does not honor the session's `search_path`.
- **Materialized views.** Treated like views for the purposes of this mapping
  (read-only Collection); refresh is administrative and not exposed.
- **Triggers and rules.** Not exposed. They run server-side as a side effect
  of `INSERT`/`UPDATE`/`DELETE` operations.
- **DDL.** Out of scope. Creating/altering/dropping tables is administrative,
  not a DataProducer operation.
- **Row-Level Security.** Inherited from the connection's principal; the
  interface adds no separate authorization layer.
