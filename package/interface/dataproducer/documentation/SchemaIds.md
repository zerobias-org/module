# Schema IDs

Schemas are referenced by **opaque, canonical IDs** rather than embedded inline.
This document defines the ID format and the contract for the `getSchema`
operation that resolves IDs to schema bodies.

## Format

```
schema:{type}:{catalog}.{schema}.{name}[:{direction}]
```

The leading `schema:` discriminator makes IDs self-describing. The `{type}`
token disambiguates the kind of resource — necessary because databases like
PostgreSQL allow tables, views, types, and domains to share the same name
within one schema.

### Type Discriminators

| Type       | Form                                                    | Used for                                            |
|------------|---------------------------------------------------------|-----------------------------------------------------|
| `table`    | `schema:table:{catalog}.{schema}.{name}`                 | Database tables                                     |
| `view`     | `schema:view:{catalog}.{schema}.{name}`                  | Database views                                      |
| `function` | `schema:function:{catalog}.{schema}.{name}:{direction}`  | Function input or output (direction: `input`/`output`) |
| `type`     | `schema:type:{catalog}.{schema}.{name}`                  | Custom or composite types                            |
| `enum`     | `schema:enum:{catalog}.{schema}.{name}`                  | Enumerated types                                     |
| `shared`   | `schema:shared:{name}`                                   | Reusable, non-database-scoped schemas               |

Only `function` IDs include the trailing `:input` / `:output` direction. Only
`shared` omits catalog/schema (it is intentionally not bound to any data
source).

### Examples

```
schema:table:mydb.public.customers
schema:view:mydb.analytics.monthly_revenue
schema:function:mydb.public.calculate_tax:input
schema:function:mydb.public.calculate_tax:output
schema:type:mydb.public.address
schema:enum:mydb.public.order_status
schema:shared:address
schema:shared:pagination_params
```

### Why Discriminators Are Required

```sql
-- All four can legally coexist in the same PostgreSQL schema:
CREATE TABLE  public.address (...);
CREATE VIEW   public.address AS SELECT ...;
CREATE TYPE   public.address AS (...);
CREATE DOMAIN public.address AS VARCHAR(100);
```

Without a discriminator, `mydb.public.address` is ambiguous. With:

```
schema:table:mydb.public.address   ← unambiguous table
schema:type:mydb.public.address    ← unambiguous type
schema:view:mydb.public.address    ← unambiguous view
```

### Validation Pattern

The OpenAPI spec validates schema IDs with the following regex:

```
^(schema:(table|view|type|enum):[^:.]+\.[^:.]+\.[^:.]+|schema:function:[^:.]+\.[^:.]+\.[^:.]+:(input|output)|schema:shared:[^:]+)$
```

Names containing `:` or `.` must be encoded by the implementation before being
embedded in an ID. The exact escape strategy is implementation-defined (only
the resulting ID round-trips through `getSchema`).

## Where Schema IDs Appear

A schema ID is the value held by these fields, in order of frequency:

| Field                            | On                                                      |
|----------------------------------|---------------------------------------------------------|
| `collectionSchema`               | Collection objects                                      |
| `inputSchema` / `outputSchema`   | Function objects                                        |
| `documentSchema`                 | Document objects                                        |
| `throws[errorCode]`              | Function objects (per-error schemas)                    |
| `references.schemaId`            | Properties — foreign keys and composition               |
| `Schema.id`                      | The schema's own self-identifier                        |
| `/schemas/{schemaId}` path param | Lookup endpoint                                         |

The path-parameter form **must be URL-encoded** because canonical IDs contain
`:` and `.`.

## getSchema Contract

`GET /schemas/{schemaId}` returns the full Schema body for an ID. The
operation contract:

1. **URL-decode** the path parameter — schema IDs always arrive percent-encoded.
2. **Validate** against the canonical pattern; reject malformed IDs with a
   `400` (`UnsupportedOperationError` is the closest fit, but a domain-specific
   validation error is acceptable).
3. **Parse** the discriminator and path components.
4. **Resolve** the schema from the underlying source (database introspection,
   static catalog, etc.).
5. **Cache** the result. Recommended TTL is 5 minutes — long enough to amortize
   introspection cost across a typical browse session, short enough that schema
   evolution is picked up promptly.
6. **404** with `noSuchObjectError` if the resource resolves but no schema can
   be produced (e.g. table dropped between browse and lookup).

### Reference Implementation Sketch

```typescript
const SCHEMA_ID = /^(schema:(table|view|type|enum):[^:.]+\.[^:.]+\.[^:.]+|schema:function:[^:.]+\.[^:.]+\.[^:.]+:(input|output)|schema:shared:[^:]+)$/;

async getSchema(rawId: string): Promise<Schema> {
  const id = decodeURIComponent(rawId);
  if (!SCHEMA_ID.test(id)) throw new UnsupportedOperationError(`invalid schema id: ${id}`);

  const cached = this.cache.get(id);
  if (cached && !cached.expired) return cached.value;

  const [, type, ...rest] = id.split(':');
  const schema = await this.resolve(type, rest);
  if (!schema) throw new NoSuchObjectError(id);

  this.cache.set(id, { value: schema, expiresAt: Date.now() + 5 * 60 * 1000 });
  return schema;
}
```

## References vs Composition

The `Property.references` object is interpreted by the **presence or absence**
of `propertyName`:

```jsonc
// Foreign key — points to a row in another collection
{
  "name": "customerId",
  "dataType": "integer",
  "references": {
    "schemaId": "schema:table:mydb.public.customers",
    "propertyName": "id"
  }
}

// Composition — embeds a sub-structure defined elsewhere
{
  "name": "billingAddress",
  "dataType": "string",
  "references": {
    "schemaId": "schema:shared:address"
  }
}
```

The composition form lets shared structures (addresses, contact blocks, money
amounts) live in `schema:shared:*` and be reused across many tables without
duplication. Clients fetch the referenced schema once and cache it.

## What Schema IDs Are Not

- **Not URLs.** A schema ID is opaque to the caller and not directly fetchable
  via the path it implies. Always go through `GET /schemas/{schemaId}`.
- **Not addresses.** Two schemas with the same ID are the same schema. Two
  callers asking the same `getSchema` should get equivalent results (modulo
  cache TTL).
- **Not human-readable display text.** Use the schema's properties /
  description for UI labels.
