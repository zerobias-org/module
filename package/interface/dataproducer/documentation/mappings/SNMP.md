# SNMP Mapping

## Overview

How an SNMP agent's MIB tree surfaces through the DataProducer interface. The
OID hierarchy becomes a Container hierarchy; scalar OIDs become Documents;
conceptual tables become Collections whose elements are indexed by the table's
INDEX columns; SNMP walk and bulk-get are exposed as Functions that operate
across the tree. SNMPv1/v2c/v3, GET/GETNEXT/GETBULK/SET, and authentication
are connection-level concerns of the producer; OID bytes never appear at the
interface as transport metadata.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| SNMP concept                         | DataProducer concept              | Notes                                                              |
|--------------------------------------|-----------------------------------|--------------------------------------------------------------------|
| Agent                                | Root container                     | `id == name == "/"`                                                |
| MIB module                           | Container                          | One Container per imported MIB (`SNMPv2-MIB`, `IF-MIB`, etc.).     |
| Object group                         | Container                          | A subtree such as `system`, `interfaces`, `ip`.                    |
| Scalar OID (`name.0`)                | Document                           | Document body is the current value.                                |
| Conceptual table                     | Collection                         | INDEX columns become primary key properties.                        |
| Table row                            | Collection element                 | Keyed by the INDEX column tuple.                                   |
| Column object                        | Schema property                    | Column OIDs aren't browsable by themselves.                        |
| TEXTUAL-CONVENTION                    | Schema property `dataType`        | Resolved to a core type via the MIB.                               |
| GET / SET                            | `getDocumentData` / `updateDocumentData` |                                                            |
| GETNEXT / GETBULK / WALK              | `snmpWalk` Function               | Output is a list of varbinds.                                      |
| Notification (TRAP, INFORM)          | Out of scope                       | Trap reception is asynchronous; this interface is request/response. |

## Object Mappings

### MIB Group (Container)

```yaml
Object:
  id: "/SNMPv2-MIB/system"
  name: "system"
  objectClass: ["container"]
  description: "System group (RFC 3418)"
  path: ["SNMPv2-MIB", "system"]
  tags: ["mib-group"]
```

### Scalar OID (Document)

```yaml
Object:
  id: "/SNMPv2-MIB/system/sysName"
  name: "sysName"
  objectClass: ["document"]
  description: "Administratively assigned name for the managed node"
  path: ["SNMPv2-MIB", "system", "sysName"]
  documentSchema: "schema:type:snmpv2_mib.scalars.sysName"
  tags: ["scalar", "writable"]
```

```yaml
Schema:
  id: "schema:type:snmpv2_mib.scalars.sysName"
  dataTypes:
    - name: "string"
  properties:
    - name: "value"
      dataType: "string"
      required: true
      description: "DisplayString, max 255 chars"
```

`getDocumentData` returns `{ "value": "router-core-01" }`. `updateDocumentData`
accepts the same shape and issues SNMP SET; `MAX-ACCESS` of `read-only` makes
the operation respond with `UnsupportedOperationError`.

### Conceptual Table (Collection)

```yaml
Object:
  id: "/IF-MIB/interfaces/ifTable"
  name: "ifTable"
  objectClass: ["collection"]
  description: "List of interface entries"
  path: ["IF-MIB", "interfaces", "ifTable"]
  collectionSchema: "schema:type:if_mib.tables.ifTable"
  collectionSize: 24
  tags: ["table"]
```

```yaml
Schema:
  id: "schema:type:if_mib.tables.ifTable"
  dataTypes:
    - name: "integer"
    - name: "string"
  properties:
    - name: "ifIndex"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "Unique value greater than zero for each interface"
    - name: "ifDescr"
      dataType: "string"
      description: "Textual description"
    - name: "ifType"
      dataType: "integer"
      description: "IANAifType enumeration"
    - name: "ifMtu"
      dataType: "integer"
    - name: "ifSpeed"
      dataType: "integer"
      description: "Estimated bandwidth in bits per second"
    - name: "ifPhysAddress"
      dataType: "string"
      description: "Hardware address; colon-hex form"
    - name: "ifAdminStatus"
      dataType: "integer"
      description: "1=up, 2=down, 3=testing"
    - name: "ifOperStatus"
      dataType: "integer"
      description: "1=up, 2=down, 3=testing, 4=unknown, 5=dormant, 6=notPresent, 7=lowerLayerDown"
    - name: "ifInOctets"
      dataType: "integer"
      description: "Counter32"
    - name: "ifOutOctets"
      dataType: "integer"
      description: "Counter32"
```

Tables with composite indices (e.g. `ipAddrTable` indexed by `ipAdEntAddr`,
`tcpConnTable` indexed by the four-tuple) declare each INDEX column with
`primaryKey: true`. Element ids are formed by joining primary-key values with
`/`, percent-encoding any reserved characters.

### IP Address Table (Composite-Index Collection)

```yaml
Object:
  id: "/IP-MIB/ip/ipAddrTable"
  name: "ipAddrTable"
  objectClass: ["collection"]
  collectionSchema: "schema:type:ip_mib.tables.ipAddrTable"
  tags: ["table"]
```

The schema declares `ipAdEntAddr: ipAddress, primaryKey: true` plus
`ipAdEntIfIndex`, `ipAdEntNetMask: ipAddress`, `ipAdEntBcastAddr`,
`ipAdEntReasmMaxSize: integer`.

### SNMP Walk (Function)

```yaml
Object:
  id: "/functions/snmpWalk"
  name: "snmpWalk"
  objectClass: ["function"]
  description: "Walk the MIB tree from a starting OID and return varbinds"
  inputSchema:  "schema:shared:snmp_walk_input"
  outputSchema: "schema:shared:snmp_walk_output"
  throws:
    timeout:           "schema:shared:snmp_error"
    auth_failure:      "schema:shared:snmp_error"
    no_such_object:    "schema:shared:snmp_error"
  tags: ["walk"]
```

```yaml
Schema:
  id: "schema:shared:snmp_walk_input"
  dataTypes:
    - name: "string"
    - name: "integer"
  properties:
    - name: "startOid"
      dataType: "string"
      required: true
      description: "Numeric or symbolic OID (e.g. 1.3.6.1.2.1.2 or IF-MIB::ifTable)"
    - name: "maxRepetitions"
      dataType: "integer"
      description: "GETBULK max-repetitions; ignored for SNMPv1"

Schema:
  id: "schema:shared:snmp_walk_output"
  dataTypes:
    - name: "string"
  properties:
    - name: "varbinds"
      dataType: "string"
      multi: true
      references:
        schemaId: "schema:shared:snmp_varbind"

Schema:
  id: "schema:shared:snmp_varbind"
  dataTypes:
    - name: "string"
  properties:
    - name: "oid"
      dataType: "string"
      required: true
      description: "Numeric OID"
    - name: "name"
      dataType: "string"
      description: "Resolved symbolic name (e.g. ifDescr.3)"
    - name: "syntax"
      dataType: "string"
      description: "INTEGER | OCTET STRING | OBJECT IDENTIFIER | Counter32 | Gauge32 | TimeTicks | IpAddress | Counter64"
    - name: "value"
      dataType: "string"
      description: "String representation of the value; for OctetString, base64 if non-printable"
```

## Operation Mappings

| DataProducer operation                       | SNMP execution                                                                     |
|----------------------------------------------|------------------------------------------------------------------------------------|
| `getRootObject`                              | (synthetic — agent identity from `sysObjectID.0`)                                  |
| `getChildren` on agent root                  | Enumerate loaded MIB modules                                                       |
| `getChildren` on MIB group                   | Enumerate child objects in MIB metadata (no SNMP traffic)                          |
| `getDocumentData` (scalar)                   | `GET <name>.0`                                                                     |
| `updateDocumentData` (scalar)                | `SET <name>.0 = value` (subject to `MAX-ACCESS`)                                   |
| `getCollectionElements` (table)              | Walk the table OID with GETBULK (v2c/v3) or repeated GETNEXT (v1)                  |
| `getCollectionElement(key)` (table row)      | One `GET` PDU containing every column for the indexed row                          |
| `updateCollectionElement(key)`               | One `SET` PDU containing the columns being updated                                  |
| `addCollectionElement` (RowStatus tables)    | `SET` with `RowStatus` (createAndGo / createAndWait); rejected if row already exists |
| `deleteCollectionElement(key)` (RowStatus)   | `SET RowStatus = destroy(6)`                                                        |
| `searchCollectionElements`                   | GETBULK walk + RFC4515 filter; push-down where possible                             |
| `invokeFunction` (snmpWalk)                  | GETBULK / GETNEXT loop                                                              |

Tables that don't declare RowStatus are read-only at the row level — `addCollectionElement`
and `deleteCollectionElement` return `UnsupportedOperationError`.

## Filter Translation Example

SNMP has no native filter language, so the producer fetches and filters
in-process via `@zerobias-org/util-lite-filter`. Index-based predicates are
the one push-down opportunity: when the filter pins a primary-key column to
a single value, the producer can issue a row GET instead of a full walk.

```
RFC4515:
  (&(ifType=6)(ifOperStatus=1)(ifSpeed>=1000000000))

AST (lite-filter):
  AND
    EQ ifType 6
    EQ ifOperStatus 1
    GTE ifSpeed 1000000000

Execution:
  1. GETBULK walk over ifTable (1.3.6.1.2.1.2.2)
  2. Decode each row from varbinds
  3. expression.matches(row) → keep rows where ifType=6 AND ifOperStatus=1 AND ifSpeed>=1Gbps
  4. Apply pagination after filtering
```

Push-down example (key pin):

```
RFC4515:    (ifIndex=3)
Execution:  GET ifTable.row(3) — one round trip, no walk
```

## Core Types Reference

| SNMP syntax                            | `dataType`         | Notes                                                  |
|----------------------------------------|--------------------|--------------------------------------------------------|
| INTEGER / Integer32                    | `integer`          |                                                        |
| Counter32 / Counter64 / Gauge32         | `integer`         | Document semantics in property description.            |
| TimeTicks                              | `duration` or `integer` | Hundredths of a second since some epoch; convert when meaningful. |
| DisplayString (TEXTUAL-CONVENTION)      | `string`          |                                                        |
| OCTET STRING (printable)                | `string`          |                                                        |
| OCTET STRING (binary)                   | `byte`            | Base64 in JSON.                                        |
| OBJECT IDENTIFIER                      | `string`           | Numeric dotted form.                                    |
| IpAddress                              | `ipAddress`        |                                                        |
| Network Address                         | `ipAddress`       |                                                        |
| MAC address (PhysAddress)               | `string`          | Colon-hex; no `mac` core type.                          |
| TruthValue (1=true, 2=false)           | `boolean`          | Producer normalizes the encoding.                       |
| DateAndTime (RFC 2579)                 | `date-time`        | Decode the SMIv2 8/11-octet form.                       |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **MIB resolution.** OID-to-name resolution depends on a loaded MIB database.
  Agents under unresolved subtrees expose numeric OIDs; resolution is best-effort.
- **MAX-ACCESS.** `read-only`, `read-write`, `read-create`, `not-accessible`.
  The interface enforces these — `updateDocumentData` on a `read-only` scalar
  returns `UnsupportedOperationError` without issuing a SET.
- **RowStatus tables.** Only tables whose schema declares a `RowStatus` column
  support row-level CRUD. Others are read-only Collections.
- **Counter wrapping.** Counter32 wraps at 2^32. Producers expose the raw value;
  rate computation is the caller's responsibility.
- **Composite indices.** When a table is indexed by multiple columns (e.g.
  `tcpConnTable`), the element id concatenates them with `/`, in MIB-defined
  index order. The producer encodes IpAddress and OctetString indices per
  RFC 2578 §7.7.
- **GETBULK errors.** Some agents miscompute `max-repetitions` and return
  partial walks. Treat a varbind whose OID is outside the requested subtree
  as end-of-walk.
- **`endOfMibView` and `noSuchInstance`.** Both terminate a walk; only the
  former indicates "no more data overall."
- **SNMPv3 contexts.** Non-empty `contextName` belongs on the connection,
  not on per-object metadata. The same agent across two contexts is two
  separate DataProducer connections.
- **Traps and INFORMs.** Out of scope. A separate trap-receiver service may
  publish events to the platform's event bus; this interface only services
  request/response operations.
- **Vendor enterprise OIDs.** Custom MIBs (`1.3.6.1.4.1.<enterprise>`) work
  the same way — load the MIB, browse the tree, expose tables and scalars.
