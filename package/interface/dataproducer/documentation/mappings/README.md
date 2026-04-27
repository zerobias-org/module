# Data Source Mappings

Each mapping document describes how a particular data source surfaces through
the DataProducer interface — which native concepts become Containers, which
become Collections, how operations translate, and which Core DataTypes apply.

These documents are **illustrative**, not authoritative. The authoritative
spec lives one level up:

- [`../Concepts.md`](../Concepts.md) — object model, classes, operations
- [`../SchemaIds.md`](../SchemaIds.md) — canonical schema ID format
- [`../CoreDataTypes.md`](../CoreDataTypes.md) — which `dataType` to use
- [`../FilterSyntax.md`](../FilterSyntax.md) — RFC4515 + translation strategy
- [`../Errors.md`](../Errors.md) — error model

Mapping examples must conform to those rules. Bad examples here propagate to
real implementations.

## Index

| Mapping                       | Native concept anchor                                  |
|-------------------------------|--------------------------------------------------------|
| [SQL.md](SQL.md)              | Relational databases (tables, views, functions)        |
| [LDAP.md](LDAP.md)            | LDAP / Active Directory directory entries              |
| [DynamoDB.md](DynamoDB.md)    | DynamoDB tables and items                              |
| [GraphQL.md](GraphQL.md)      | GraphQL schemas, queries, mutations                    |
| [OpenAPI.md](OpenAPI.md)      | OpenAPI / REST resources (via `HttpModule` sub-interface) |
| [SOAP.md](SOAP.md)            | SOAP services (WSDL operations)                        |
| [Confluence.md](Confluence.md)| Confluence spaces, pages, attachments                  |
| [DNS.md](DNS.md)              | DNS zones, records, queries                            |
| [DynamoDB.md](DynamoDB.md)    | DynamoDB tables                                        |
| [FTP.md](FTP.md)              | FTP/SFTP filesystems                                   |
| [IPMI.md](IPMI.md)            | IPMI baseband management                               |
| [Registry.md](Registry.md)    | Windows Registry / config registries                   |
| [SNMP.md](SNMP.md)            | SNMP MIBs and OID trees                                |

## Mapping Document Template

New mappings must follow this section structure. Existing mappings should
trend toward it as they are revised.

```markdown
# {Source} Mapping

## Overview
{One paragraph: what kind of data source, when to use this mapping, what
sub-interface (if any) it implements alongside DataProducer.}

## Conceptual Mapping
{Native concept → DataProducer concept table. Three columns: native term,
DataProducer term, notes.}

## Object Mappings
{For each native entity, show the resulting Object — id pattern, name,
objectClass, class-specific properties (collectionSchema, etc.). Use the
canonical schema ID format from ../SchemaIds.md.}

## Operation Mappings
{For each DataProducer operation that applies, show how it executes against
the source. Include the read/write split.}

## Schema Examples
{One or two end-to-end Schema objects in JSON, using core dataTypes from
../CoreDataTypes.md.}

## Filter Translation Example
{A worked example showing one RFC4515 filter translating to the native
query language. Reference org/util/packages/lite-filter.}

## Core Types Reference
{Only types relevant to this source — short table, with a pointer to
../CoreDataTypes.md for the full catalog. Omit if nothing notable.}

## Edge Cases
{Anything the implementer needs to know that isn't covered by the
canonical docs: native quirks, naming collisions, paging limits, etc.}
```

## Hard Rules for Mapping Examples

These match the canonical docs and exist to prevent bad examples from
spreading into real modules:

1. **Schema IDs** — always the full canonical form. No shortcuts like
   `customers_schema` or `calc_tax_input`. See [`../SchemaIds.md`](../SchemaIds.md).
2. **`dataType`** — always a name from `@zerobias-org/types-core`. No
   `dataType: string, format: email`. See [`../CoreDataTypes.md`](../CoreDataTypes.md).
3. **No transport metadata on Function objects** — the base interface has no
   `httpMethod` / `httpPath` / `timeout` / `retryPolicy`. Transport-specific
   routing belongs on a sub-interface (e.g. `HttpModule`).
4. **Filters** — wire format is RFC4515. Show the native translation in the
   "Filter Translation Example" section.

## When to Add a New Mapping

- A new data-source category that doesn't fit any existing mapping.
- A platform-specific mapping that overrides the generic one (e.g. a vendor's
  SQL dialect that warrants its own document).

Don't add a mapping for a single specific product if an existing category
covers it well — extend the existing document with an "Edge Cases" entry
instead.
