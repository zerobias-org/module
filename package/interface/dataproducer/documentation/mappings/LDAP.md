# LDAP Mapping

## Overview

How LDAP / Active Directory directories surface through the DataProducer
interface. LDAP is unique among the mapped sources because it natively uses
**RFC4515** for filtering and a hierarchical DN model for navigation — the
mapping is essentially pass-through.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| LDAP concept                          | DataProducer concept                | Notes                                              |
|---------------------------------------|-------------------------------------|----------------------------------------------------|
| Root DSE                              | Root container (`id == "/"`)         |                                                    |
| Naming context (`dc=...,dc=...`)      | Container                             |                                                    |
| Organizational unit (`ou=...`)        | Container                             |                                                    |
| Person / inetOrgPerson entry           | Document                              | Document body = entry attributes.                   |
| Group entry (`groupOfNames`, etc.)     | Collection                            | Members are collection elements.                    |
| LDAP schema (objectClass definition)   | Document                              | Read-only metadata.                                 |
| Saved/dynamic search                  | Function                              | Input describes query; output is matching entries.  |
| DN                                    | Object `id`                           | DN is opaque to callers; treat as the object's identifier. |

## Object Mappings

### Naming Context (Container)

```yaml
Object:
  id: "dc=example,dc=com"
  name: "example.com"
  objectClass: ["container"]
  description: "Domain naming context"
  path: ["example.com"]
  tags: ["naming-context"]
```

### Organizational Unit (Container)

```yaml
Object:
  id: "ou=people,dc=example,dc=com"
  name: "people"
  objectClass: ["container"]
  path: ["example.com", "people"]
  tags: ["organizational-unit"]
```

### Person Entry (Document)

```yaml
Object:
  id: "cn=John Doe,ou=people,dc=example,dc=com"
  name: "John Doe"
  objectClass: ["document"]
  path: ["example.com", "people", "John Doe"]
  documentSchema: "schema:type:example_com.entries.inetOrgPerson"
  tags: ["person"]
```

`getDocumentData` returns the entry's attributes:

```json
{
  "cn": "John Doe",
  "uid": "jdoe",
  "mail": "john.doe@example.com",
  "telephoneNumber": "+15551234567",
  "department": "Engineering",
  "objectClass": ["inetOrgPerson", "organizationalPerson", "person"]
}
```

The corresponding Schema (fetched via `getSchema`):

```yaml
Schema:
  id: "schema:type:example_com.entries.inetOrgPerson"
  dataTypes:
    - name: "string"
    - name: "email"
    - name: "phoneNumber"
  properties:
    - name: "cn"
      dataType: "string"
      required: true
      multi: true
      description: "Common name"
    - name: "uid"
      dataType: "string"
      description: "User ID"
    - name: "mail"
      dataType: "email"
      multi: true
      description: "Email address(es)"
    - name: "telephoneNumber"
      dataType: "phoneNumber"
      multi: true
    - name: "department"
      dataType: "string"
    - name: "objectClass"
      dataType: "string"
      multi: true
      required: true
      description: "LDAP object classes this entry implements"
```

> Note: LDAP DNs are modeled as plain strings (`dataType: string`). There is no
> `dn` core type — DNs are entry-specific identifiers, not a domain-validated
> data type like `email` or `ipAddress`. If a property's value is a DN
> reference, use `string` plus a `references.schemaId` to indicate what kind
> of entry it points at.

### Group (Collection)

```yaml
Object:
  id: "cn=administrators,ou=groups,dc=example,dc=com"
  name: "administrators"
  objectClass: ["collection"]
  path: ["example.com", "groups", "administrators"]
  collectionSchema: "schema:shared:ldap_group_member"
  collectionSize: 12
  tags: ["group"]
```

```yaml
Schema:
  id: "schema:shared:ldap_group_member"
  dataTypes:
    - name: "string"
  properties:
    - name: "member"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Distinguished name of the member entry"
      references:
        schemaId: "schema:type:example_com.entries.inetOrgPerson"
    - name: "memberType"
      dataType: "string"
      description: "direct | nested"
```

The `references.schemaId` (without `propertyName`) tells the client that
`member` is a DN pointing at an entry whose schema is the linked person type.

### LDAP Search (Function)

```yaml
Object:
  id: "/searches/active_users"
  name: "active_users_search"
  objectClass: ["function"]
  description: "Search for active user accounts"
  inputSchema:  "schema:shared:ldap_search_input"
  outputSchema: "schema:shared:ldap_search_output"
  tags: ["search"]
```

```yaml
Schema:
  id: "schema:shared:ldap_search_input"
  dataTypes:
    - name: "string"
  properties:
    - name: "baseDN"
      dataType: "string"
      required: true
      description: "Search base distinguished name"
    - name: "scope"
      dataType: "string"
      required: true
      description: "base | one | sub"
    - name: "filter"
      dataType: "string"
      required: true
      description: "RFC4515 filter expression"
    - name: "attributes"
      dataType: "string"
      multi: true
      description: "Attributes to return; empty = all"
```

## Operation Mappings

| DataProducer operation        | LDAP operation                                                               |
|-------------------------------|------------------------------------------------------------------------------|
| `getRootObject`               | RootDSE query                                                                 |
| `getChildren`                 | Search, scope=one, base = parent DN                                           |
| `objectSearch` / `searchChildObjects` | Search, scope=sub, base = parent DN, with the filter passed through  |
| `getObject`                   | Search, scope=base, base = object DN                                          |
| `createChildObject`           | Add operation                                                                  |
| `updateObject`                | Modify operation                                                               |
| `deleteObject`                | Delete operation (with `recursive` → recursive subtree delete)                |
| `getDocumentData`             | Search scope=base; serialize attributes as JSON                                |
| `updateDocumentData`          | Modify operation with replace semantics                                        |
| `getCollectionElements` (group) | Search scope=base on group entry; return `member` attribute values           |
| `addCollectionElement`        | Modify with `add: member`                                                      |
| `deleteCollectionElement`     | Modify with `delete: member`                                                   |
| `invokeFunction` (search fn)   | Search with the function's encoded parameters                                 |

## Filter Translation Example

LDAP is the **only** mapped backend that requires no translation. RFC4515
filters travel through `@zerobias-org/util-lite-filter`'s parser only for
validation; the original filter string is forwarded to the LDAP client
verbatim.

```
RFC4515 (caller):
  (&(objectClass=person)(department=Engineering)(!(userAccountControl:1.2.840.113556.1.4.803:=2)))

LDAP search filter (wire):
  (&(objectClass=person)(department=Engineering)(!(userAccountControl:1.2.840.113556.1.4.803:=2)))
```

The Active Directory bitwise-match extension (`:1.2.840.113556.1.4.803:=`) is
an example of an LDAP-specific extended match. The lite-filter parser tolerates
extended matches via its `:function:` form, but the producer does not
*translate* them — it just forwards.

## Core Types Reference

| LDAP attribute syntax            | `dataType`         | Notes                                            |
|----------------------------------|--------------------|--------------------------------------------------|
| Directory String                 | `string`           |                                                  |
| IA5 String (RFC822 Mailbox)      | `email`            | When the attribute holds an email address.       |
| Telephone Number                  | `phoneNumber`     |                                                  |
| Distinguished Name (DN)          | `string` + `references` | DN is a reference; the schema referenced gives the target type. |
| Generalized Time                 | `date-time`        |                                                  |
| Boolean                          | `boolean`          |                                                  |
| Integer                          | `integer`          |                                                  |
| Octet String                     | `byte`             | Base64-encode binary attributes.                  |
| URI                              | `url`              |                                                  |
| `objectClass` (multi-valued)     | `string` + `multi: true` |                                                |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **DN as object id.** Object IDs are DNs verbatim. Encode reserved URL
  characters (`,`, `=`, spaces) when embedding in path parameters.
- **Multi-valued attributes.** Use `multi: true` on the property. Wire format
  is a JSON array.
- **Binary attributes** (`userCertificate`, `jpegPhoto`, `thumbnailPhoto`).
  Either model as `byte` (base64 in JSON) or expose the entry as a Binary
  object with class `binary` for streaming download.
- **Referrals.** When a search receives a referral, follow it transparently or
  surface it as `noSuchObjectError` if the referred-to server is not reachable
  by the producer's connection.
- **Paged results.** Use LDAP's simple paged result control. The `pageToken`
  the producer returns wraps the LDAP cookie.
- **VLV (Virtual List View).** Optional. When the underlying server supports
  it, use VLV for sorted offset-based pagination instead of fetching pages
  cumulatively.
- **Operational attributes.** `createTimestamp`, `modifyTimestamp`,
  `creatorsName`, `modifiersName` populate `Object.created`, `Object.modified`,
  and may be exposed under `tags` if useful — they are *not* part of the
  document body unless explicitly requested.
- **Bind / authentication.** Inherited from the connection. The interface adds
  no separate bind operation; the connection layer handles it.
