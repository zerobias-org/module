# DNS Mapping

## Overview

How DNS zones, records, and queries surface through the DataProducer interface.
A producer wraps an authoritative server (BIND, PowerDNS, Windows DNS), a cloud
DNS API (Route 53, Cloud DNS, Cloudflare), or zone-file storage; the
interface contract is the same regardless of backend. Records are organized
under their zone's record-type collections; the namespace itself is the
hierarchy.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| DNS concept                       | DataProducer concept              | Notes                                                  |
|-----------------------------------|-----------------------------------|--------------------------------------------------------|
| Service root                      | Root container (`id == "/"`)       |                                                        |
| Zone                              | Container                          | One per managed zone (`example.com.`, `10.in-addr.arpa.`).|
| Record type within a zone (A, MX, …) | Collection                       | One per (zone, record type) pair.                      |
| Individual record (RR)            | Collection element                 | Primary key is the owner name (plus discriminator for MX/SRV). |
| SOA record                        | Document                           | One per zone, exposed under the zone container.         |
| Forward / reverse / AXFR query    | Function                           | Lookups that aren't tied to a specific zone.            |
| DNSSEC keys, NS server config     | Document or Collection             | Backend-specific; expose if the producer manages them.  |

## Object Mappings

### Zone (Container)

```yaml
Object:
  id: "/zone:example.com"
  name: "example.com."
  objectClass: ["container"]
  description: "DNS zone for example.com"
  path: ["example.com."]
  tags: ["zone", "authoritative"]
```

### Record-Type Collection

```yaml
Object:
  id: "/zone:example.com/rrtype:A"
  name: "A"
  objectClass: ["collection"]
  description: "A records in example.com"
  path: ["example.com.", "A"]
  collectionSchema: "schema:type:example_com.records.a"
  collectionSize: 12
  tags: ["records", "ipv4"]
```

```yaml
Schema:
  id: "schema:type:example_com.records.a"
  dataTypes:
    - name: "string"
    - name: "ipAddress"
    - name: "integer"
  properties:
    - name: "name"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Owner name (FQDN with trailing dot, or relative to zone)"
    - name: "value"
      dataType: "ipAddress"
      required: true
      description: "IPv4 address (the producer rejects non-v4 values for type A)"
    - name: "ttl"
      dataType: "integer"
      description: "Time to live, in seconds"
    - name: "class"
      dataType: "string"
      description: "DNS class (typically IN)"
```

AAAA records use the same shape with the same `ipAddress` core type — no
`ipv4` / `ipv6` format string is needed; `ipAddress` accepts both, and the
collection's record type is what constrains the family.

### MX Record Collection

```yaml
Schema:
  id: "schema:type:example_com.records.mx"
  dataTypes:
    - name: "string"
    - name: "hostname"
    - name: "integer"
  properties:
    - name: "name"
      dataType: "string"
      primaryKey: true
      required: true
    - name: "preference"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "Lower values have higher priority"
    - name: "exchange"
      dataType: "hostname"
      required: true
      description: "Mail exchanger hostname"
    - name: "ttl"
      dataType: "integer"
```

`name` + `preference` form a composite primary key — multiple MX records can
share an owner name.

### SOA (Document)

```yaml
Object:
  id: "/zone:example.com/soa"
  name: "SOA"
  objectClass: ["document"]
  description: "Start of authority for example.com"
  documentSchema: "schema:type:example_com.records.soa"
  tags: ["soa"]
```

```yaml
Schema:
  id: "schema:type:example_com.records.soa"
  dataTypes:
    - name: "hostname"
    - name: "email"
    - name: "integer"
  properties:
    - name: "primaryNs"
      dataType: "hostname"
      required: true
    - name: "adminEmail"
      dataType: "email"
      required: true
      description: "RNAME translated from DNS-form (admin.example.com.) to RFC822"
    - name: "serial"
      dataType: "integer"
      required: true
    - name: "refresh"
      dataType: "integer"
      description: "Seconds"
    - name: "retry"
      dataType: "integer"
      description: "Seconds"
    - name: "expire"
      dataType: "integer"
      description: "Seconds"
    - name: "minimum"
      dataType: "integer"
      description: "Negative-cache TTL, seconds"
```

### Forward Lookup (Function)

```yaml
Object:
  id: "/queries/forward_lookup"
  name: "forward_lookup"
  objectClass: ["function"]
  description: "Resolve a name to one or more records of the requested type"
  inputSchema:  "schema:shared:dns_forward_lookup_input"
  outputSchema: "schema:shared:dns_lookup_result"
  tags: ["query", "forward"]
```

```yaml
Schema:
  id: "schema:shared:dns_forward_lookup_input"
  dataTypes:
    - name: "hostname"
    - name: "string"
    - name: "boolean"
  properties:
    - name: "name"
      dataType: "hostname"
      required: true
    - name: "recordType"
      dataType: "string"
      description: "A, AAAA, MX, TXT, …; defaults to A"
    - name: "recursive"
      dataType: "boolean"

Schema:
  id: "schema:shared:dns_lookup_result"
  dataTypes:
    - name: "string"
    - name: "integer"
    - name: "boolean"
  properties:
    - name: "name"
      dataType: "string"
      required: true
    - name: "type"
      dataType: "string"
      required: true
    - name: "value"
      dataType: "string"
      required: true
      multi: true
    - name: "ttl"
      dataType: "integer"
    - name: "authoritative"
      dataType: "boolean"
```

Reverse lookup is the same shape with input keyed on `address` (`dataType:
ipAddress`); zone transfer is a Function whose output is a stream of records.

## Operation Mappings

| DataProducer operation              | DNS backend operation                                                       |
|-------------------------------------|-----------------------------------------------------------------------------|
| `getRootObject`                     | (synthetic — server identity)                                                |
| `getChildren` on root               | List of managed zones                                                        |
| `getChildren` on zone               | One child per record type present in the zone                                |
| `getCollectionElements`             | Filter records of one type within the zone (zone transfer or backend API)    |
| `searchCollectionElements`          | Same, with the RFC4515 filter applied                                        |
| `getCollectionElement(key)`         | DNS query with `name` (and discriminator if needed)                          |
| `addCollectionElement`              | RFC 2136 dynamic update or backend API insert                                |
| `updateCollectionElement(key)`      | Dynamic update (delete + add) or backend API update                          |
| `deleteCollectionElement(key)`      | Dynamic update delete or backend API delete                                  |
| `getDocumentData` on SOA            | SOA query                                                                     |
| `updateDocumentData` on SOA         | Dynamic update / zone-file edit + reload                                     |
| `invokeFunction` (lookup)           | Direct DNS query via the producer's resolver                                 |
| `invokeFunction` (zone transfer)    | AXFR / IXFR                                                                  |

DDL-equivalent operations (creating a zone, deleting a zone) are not part of
this mapping; treat them as administrative and out of scope.

## Filter Translation Example

DNS backends do not generally accept structured filters — the producer fetches
the candidate record set (a zone transfer or a paginated backend query) and
filters in process using the lite-filter `matches` evaluator.

```
RFC4515:
  (&(name=*.api.example.com)(ttl>=300))

AST (lite-filter):
  AND
    EQ name "*.api.example.com"   (suffix wildcard)
    GTE ttl 300

In-process evaluation:
  records = backend.transfer("example.com", "A")
  filtered = records.filter(r => expression.matches(r))
```

When the backend is a cloud DNS API with a record-listing endpoint that
supports prefix queries, the producer may push the prefix predicate down and
post-filter the rest. In every case the wire-level contract is RFC4515.

## Core Types Reference

| DNS field            | `dataType`         | Notes                                                                  |
|----------------------|--------------------|------------------------------------------------------------------------|
| Owner name / RDATA hostname | `hostname` | Use for fields that hold a DNS name (CNAME target, MX exchange, NS).   |
| A / AAAA value        | `ipAddress`        | One core type covers v4 and v6; the record type implies the family.    |
| Email (SOA RNAME)    | `email`            | Translate `admin.example.com.` ↔ `admin@example.com` at the boundary.  |
| TTL / refresh / retry / expire / minimum | `integer` | Seconds. Modeling as `duration` is also acceptable; choose one. |
| Class (IN, CH, HS)    | `string`           |                                                                        |
| Owner-name field that may be a wildcard | `string` | When the value is the literal pattern `*.foo.example.com.`, not a hostname. |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **Trailing dot.** Owner names in records are FQDNs and must include the
  trailing dot in canonical form. Some backends accept relative names that the
  server expands using `$ORIGIN`. The producer normalizes to FQDN before
  emitting an `id`-comparable key.
- **Composite primary keys.** MX, SRV, TXT, and similar types can have
  multiple records with the same owner name. Mark all distinguishing fields
  `primaryKey: true` (e.g. `name`+`preference` for MX, `name`+`port`+`target`
  for SRV).
- **Wildcards.** A wildcard record (`*.example.com.`) is a literal owner name;
  treat it as a string value, not as a filter.
- **Zone transfers.** AXFR is the canonical full-zone read. IXFR is an
  optimization. The producer should prefer IXFR when the backend supports it
  but must fall back to AXFR.
- **TTL semantics on writes.** Some backends apply zone-default TTL when a
  caller omits `ttl`. The producer should pass the absent value through, not
  invent a default.
- **DNSSEC.** Signed records carry RRSIG, DNSKEY, DS, NSEC/NSEC3 alongside the
  payload. Expose these as additional record-type collections under the zone
  rather than embedding into the data type they sign.
- **Reverse zones.** `*.in-addr.arpa.` and `*.ip6.arpa.` are zones like any
  other; PTR records are a record-type collection. Reverse lookup is then just
  a Function that constructs the reverse name and queries.
- **Case sensitivity.** DNS labels are case-insensitive on the wire; the
  producer normalizes to lower case for IDs and equality comparisons.
- **Notify / NOTIFY (RFC 1996).** Out of scope at the interface level —
  push-style change notification is implementation-specific.
- **Authentication.** TSIG keys and backend API credentials are part of the
  underlying connection; the interface adds no separate auth model.
