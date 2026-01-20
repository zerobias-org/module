# SNMP Mapping to Dynamic Data Producer Interface

## Overview

This document describes how SNMP (Simple Network Management Protocol) agents and their MIB (Management Information Base) structures map to the Dynamic Data Producer Interface, enabling unified access to network device monitoring and management through the generic object model. The interface is implemented via a translation layer that converts generic operations into SNMP GET, SET, GETNEXT, GETBULK, and TRAP operations.

**Protocol Standards**: RFC 1157 (SNMPv1), RFC 3416 (SNMPv2c), RFC 3414 (SNMPv3)
**Assumption**: Complete MIB database available for OID-to-name resolution and metadata

## Conceptual Mapping

### SNMP Agent → Object Model

```
SNMP Agent (Device)
├── /                                  → Root Container (Agent Root)
│   ├── system/                       → Container Object (system.1.3.6.1.2.1.1)
│   │   ├── sysDescr                  → Document Object (scalar OID)
│   │   ├── sysUpTime                 → Document Object (scalar OID)
│   │   ├── sysContact                → Document Object (scalar OID)
│   │   └── sysName                   → Document Object (scalar OID)
│   ├── interfaces/                   → Container Object (interfaces.1.3.6.1.2.1.2)
│   │   ├── ifNumber                  → Document Object (scalar OID)
│   │   └── ifTable                   → Collection Object (table OID)
│   │       ├── ifEntry.1             → Collection element (table row)
│   │       ├── ifEntry.2             → Collection element (table row)
│   │       └── ifEntry.3             → Collection element (table row)
│   ├── ip/                           → Container Object (ip.1.3.6.1.2.1.4)
│   │   ├── ipForwarding              → Document Object (scalar OID)
│   │   ├── ipAddrTable               → Collection Object (table OID)
│   │   └── ipRouteTable              → Collection Object (table OID)
│   ├── tcp/                          → Container Object (tcp.1.3.6.1.2.1.6)
│   │   └── tcpConnTable              → Collection Object (table OID)
│   ├── udp/                          → Container Object (udp.1.3.6.1.2.1.7)
│   │   └── udpTable                  → Collection Object (table OID)
│   └── snmp/                         → Container Object (snmp.1.3.6.1.2.1.11)
│       ├── snmpInPkts                → Document Object (scalar OID)
│       └── snmpOutPkts               → Document Object (scalar OID)
```

## Detailed Object Mappings

### 1. SNMP Agent Root → Root Container
```yaml
Object:
  id: "/"
  name: "SNMP Agent"
  objectClass: ["container"]
  description: "SNMP-enabled network device"
  tags: ["snmp", "network-device"]
  metadata:
    agentAddress: "192.168.1.1"
    port: 161
    snmpVersion: "2c"  # or "1", "3"
    community: "public"  # SNMPv1/v2c only
    engineId: null  # SNMPv3 only
    contextName: ""
    sysObjectID: "1.3.6.1.4.1.9.1.516"  # Device type identifier
```

### 2. MIB Group → Container Object
```yaml
Object:
  id: "mib_system"
  name: "system"
  objectClass: ["container"]
  description: "System MIB group (RFC 1213)"
  path: ["/", "system"]
  tags: ["mib-group", "system"]
  metadata:
    oid: "1.3.6.1.2.1.1"
    mibName: "SNMPv2-MIB"
    mibModule: "system"
```

### 3. Scalar OID → Document Object
```yaml
Object:
  id: "scalar_sysname"
  name: "sysName"
  objectClass: ["document"]
  description: "An administratively-assigned name for this managed node"
  path: ["/", "system", "sysName"]
  documentSchema: "snmp_scalar_schema"
  tags: ["scalar", "system-info", "writable"]
  metadata:
    oid: "1.3.6.1.2.1.1.5.0"  # Scalar OIDs end in .0
    syntax: "DisplayString"
    maxAccess: "read-write"
    status: "current"

# Document Data (current value):
Document Data:
{
  "oid": "1.3.6.1.2.1.1.5.0",
  "name": "sysName",
  "syntax": "DisplayString",
  "value": "router-core-01",
  "timestamp": "2025-10-24T10:30:00.000Z"
}
```

### 4. SNMP Table → Collection Object
```yaml
Object:
  id: "table_interfaces"
  name: "ifTable"
  objectClass: ["collection"]
  description: "List of interface entries"
  path: ["/", "interfaces", "ifTable"]
  collectionSchema: "if_table_schema"
  collectionSize: 24  # Number of interfaces
  tags: ["table", "interfaces"]
  metadata:
    oid: "1.3.6.1.2.1.2.2"
    mibName: "IF-MIB"
    indexColumns: ["ifIndex"]

Schema:
  id: "if_table_schema"
  properties:
    - name: "ifIndex"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "Interface index (1.3.6.1.2.1.2.2.1.1)"
    - name: "ifDescr"
      dataType: "string"
      description: "Interface description (1.3.6.1.2.1.2.2.1.2)"
    - name: "ifType"
      dataType: "integer"
      description: "Interface type (1.3.6.1.2.1.2.2.1.3)"
    - name: "ifMtu"
      dataType: "integer"
      description: "Maximum transmission unit (1.3.6.1.2.1.2.2.1.4)"
    - name: "ifSpeed"
      dataType: "integer"
      description: "Interface speed in bits/sec (1.3.6.1.2.1.2.2.1.5)"
    - name: "ifPhysAddress"
      dataType: "string"
      description: "Physical address (MAC) (1.3.6.1.2.1.2.2.1.6)"
    - name: "ifAdminStatus"
      dataType: "integer"
      description: "Admin status: up(1), down(2), testing(3) (1.3.6.1.2.1.2.2.1.7)"
    - name: "ifOperStatus"
      dataType: "integer"
      description: "Operational status: up(1), down(2), testing(3) (1.3.6.1.2.1.2.2.1.8)"
    - name: "ifInOctets"
      dataType: "integer"
      description: "Octets received (1.3.6.1.2.1.2.2.1.10)"
    - name: "ifOutOctets"
      dataType: "integer"
      description: "Octets transmitted (1.3.6.1.2.1.2.2.1.16)"
```

### 5. SNMP Walk → Function Object
```yaml
Object:
  id: "func_snmp_walk"
  name: "snmpWalk"
  objectClass: ["function"]
  description: "Walk SNMP tree from starting OID"
  inputSchema: "snmp_walk_input_schema"
  outputSchema: "snmp_walk_output_schema"
  tags: ["snmp", "walk", "discovery"]

Schema (Input):
  id: "snmp_walk_input_schema"
  properties:
    - name: "startOid"
      dataType: "string"
      required: true
      description: "Starting OID for walk (e.g., 1.3.6.1.2.1.2)"
    - name: "maxRepetitions"
      dataType: "integer"
      default: 10
      description: "Max repetitions for GETBULK (SNMPv2c/v3)"

Schema (Output):
  id: "snmp_walk_output_schema"
  properties:
    - name: "results"
      dataType: "array"
      multi: true
      references:
        schemaId: "snmp_varbind_schema"

Schema:
  id: "snmp_varbind_schema"
  properties:
    - name: "oid"
      dataType: "string"
      required: true
    - name: "name"
      dataType: "string"
      description: "Resolved MIB name"
    - name: "syntax"
      dataType: "string"
      description: "SNMP data type"
    - name: "value"
      dataType: "string"
      description: "String representation of value"
```

## API Usage Examples with SNMP Translation

### Device Discovery

**List MIB Groups:**
```http
GET /objects/{root}/children
# Translates to: Parse loaded MIB database for top-level groups
# Returns: system, interfaces, ip, tcp, udp, snmp, etc.
```

**List System Group Objects:**
```http
GET /objects/mib_system/children
# Translates to: Parse MIB for objects under system (1.3.6.1.2.1.1)
# Returns: sysDescr, sysUpTime, sysContact, sysName, etc.
```

### Scalar Operations

**Get System Name:**
```http
GET /objects/scalar_sysname/document
# Translates to: SNMP GET 1.3.6.1.2.1.1.5.0

# Response:
{
  "oid": "1.3.6.1.2.1.1.5.0",
  "name": "sysName",
  "syntax": "DisplayString",
  "value": "router-core-01",
  "timestamp": "2025-10-24T10:30:00.000Z"
}
```

**Set System Name:**
```http
PUT /objects/scalar_sysname/document
Content-Type: application/json

{
  "value": "router-core-02"
}

# Translates to: SNMP SET 1.3.6.1.2.1.1.5.0 = "router-core-02"
```

**Get Multiple Scalars:**
```http
POST /objects/{root}/batch-get
{
  "objects": [
    "scalar_sysname",
    "scalar_sysdescr",
    "scalar_sysuptime"
  ]
}

# Translates to: SNMP GET with multiple OIDs in single PDU
```

### Table Operations

**List All Table Rows:**
```http
GET /objects/table_interfaces/collection/elements?pageSize=10&pageNumber=1
# Translates to:
# 1. SNMP GETBULK starting at 1.3.6.1.2.1.2.2.1
# 2. Parse table rows from response
# 3. Return paginated results
```

**Get Specific Interface:**
```http
GET /objects/table_interfaces/collection/elements/3
# Translates to: SNMP GET for ifTable row with ifIndex=3
# Fetches: ifIndex.3, ifDescr.3, ifType.3, ifMtu.3, etc.

# Response:
{
  "ifIndex": 3,
  "ifDescr": "GigabitEthernet0/0",
  "ifType": 6,
  "ifMtu": 1500,
  "ifSpeed": 1000000000,
  "ifPhysAddress": "00:1A:2B:3C:4D:5E",
  "ifAdminStatus": 1,
  "ifOperStatus": 1,
  "ifInOctets": 458392847,
  "ifOutOctets": 892473829
}
```

**Update Interface Admin Status:**
```http
PUT /objects/table_interfaces/collection/elements/3
Content-Type: application/json

{
  "ifAdminStatus": 2
}

# Translates to: SNMP SET 1.3.6.1.2.1.2.2.1.7.3 = 2 (down)
```

**Search Interfaces by Status:**
```http
GET /objects/table_interfaces/collection/search?filter=(ifOperStatus=1)
# Translates to:
# 1. SNMP GETBULK entire ifTable
# 2. Filter rows where ifOperStatus = 1 (up)
# 3. Return matching interfaces
```

### SNMP Walk Operations

**Walk Interface Table:**
```http
POST /objects/func_snmp_walk/invoke
{
  "startOid": "1.3.6.1.2.1.2.2",
  "maxRepetitions": 10
}

# Translates to:
# SNMPv2c/v3: GETBULK with maxRepetitions=10
# SNMPv1: Multiple GETNEXT operations

# Response:
{
  "results": [
    {
      "oid": "1.3.6.1.2.1.2.2.1.1.1",
      "name": "ifIndex.1",
      "syntax": "INTEGER",
      "value": "1"
    },
    {
      "oid": "1.3.6.1.2.1.2.2.1.2.1",
      "name": "ifDescr.1",
      "syntax": "DisplayString",
      "value": "lo0"
    },
    // ... more results
  ]
}
```

## Translation Layer Capabilities

### 1. **SNMP Operation Mapping**

| Generic Operation | SNMP Commands | Notes |
|-------------------|--------------|-------|
| `children()` | MIB parse | List objects in MIB group |
| `documentData()` (scalar) | `GET` | Get single OID value |
| `documentData()` (update) | `SET` | Set single OID value |
| `collectionElements()` | `GETBULK`/`GETNEXT` | Walk table, paginate rows |
| `getCollectionElement()` | `GET` (multiple) | Get specific table row |
| `updateElement()` | `SET` (multiple) | Update table row columns |
| `objectSearch()` | `GETBULK` + filter | Walk + client-side filtering |
| Batch operations | `GET` (multiple OIDs) | Single PDU with multiple OIDs |

### 2. **OID Resolution with MIB Database**
- **Capability**: Bidirectional translation between numeric OIDs and symbolic names
- **Examples**:
  - `1.3.6.1.2.1.1.5.0` ↔ `sysName.0`
  - `1.3.6.1.2.1.2.2.1.2.3` ↔ `ifDescr.3`
- **Metadata**: MIB provides syntax, max-access, status, description

### 3. **Table Index Handling**
- **Capability**: Parse table indices from OIDs
- **Implementation**: Extract index values from OID suffix
- **Examples**:
  - `ifTable` indexed by `ifIndex`: OID `1.3.6.1.2.1.2.2.1.2.3` → row index `3`
  - `ipAddrTable` indexed by `ipAdEntAddr`: OID ending in `192.168.1.1` → IP address index

### 4. **Data Type Translation**

| SNMP Syntax | DataType | Notes |
|-------------|----------|-------|
| INTEGER | `integer` | 32-bit signed |
| Counter32 | `integer` | 32-bit unsigned counter |
| Counter64 | `integer` | 64-bit unsigned counter |
| Gauge32 | `integer` | 32-bit unsigned gauge |
| TimeTicks | `integer` | Time in hundredths of seconds |
| DisplayString | `string` | ASCII string |
| OctetString | `byte` | Binary data (base64) |
| IpAddress | `ipAddress` | IPv4 address |
| ObjectIdentifier | `string` | OID string |

## Implementation Considerations

### 1. **SNMPv1/v2c/v3 Support**

**SNMPv1:**
```yaml
metadata:
  snmpVersion: "1"
  community: "public"
  operations: ["GET", "GETNEXT", "SET"]
```

**SNMPv2c:**
```yaml
metadata:
  snmpVersion: "2c"
  community: "public"
  operations: ["GET", "GETNEXT", "GETBULK", "SET"]
```

**SNMPv3:**
```yaml
metadata:
  snmpVersion: "3"
  securityLevel: "authPriv"  # noAuthNoPriv, authNoPriv, authPriv
  authProtocol: "SHA"  # MD5, SHA
  authPassword: "authpass"
  privProtocol: "AES"  # DES, AES
  privPassword: "privpass"
  engineId: "0x80001f8880"
  contextName: ""
```

### 2. **MIB Database Integration**
- **Loading**: Parse MIB files (SMI format) at startup
- **Resolution**: Fast OID ↔ name lookups via indexed data structure
- **Metadata**: Store syntax, max-access, status, description for each OID
- **Dependencies**: Handle MIB imports and dependencies (e.g., SNMPv2-TC, SNMPv2-SMI)

### 3. **Performance Optimization**
- **GETBULK**: Use for table walks (SNMPv2c/v3) instead of multiple GETNEXT
- **Batching**: Combine multiple scalar GETs into single PDU
- **Caching**: Cache scalar values with TTL to reduce SNMP traffic
- **Paging**: Implement efficient table paging with GETBULK max-repetitions
- **Timeouts**: Configurable per-request timeouts (default: 5 seconds)

### 4. **Error Handling**

| SNMP Error Status | HTTP Status | Error Type |
|-------------------|-------------|------------|
| `noError` (0) | 200 | Success |
| `tooBig` (1) | 413 | `payload_too_large` |
| `noSuchName` (2) | 404 | `noSuchObjectError` |
| `badValue` (3) | 400 | `InvalidInputError` |
| `readOnly` (4) | 403 | `ForbiddenError` |
| `genErr` (5) | 500 | `UnexpectedError` |
| Timeout | 408 | `TimeoutError` |
| `authorizationError` (16) | 401 | `UnauthenticatedError` |

### 5. **Trap and Notification Handling**
- **Traps**: SNMP traps (v1/v2c) and notifications (v3) received by trap receiver
- **Event Objects**: Model traps as Event documents with trap metadata
- **Subscription**: Function objects for trap subscription/filtering
- **Correlation**: Link traps to source objects via agent address and OIDs

## Limitations and Workarounds

### 1. **Read-Only vs Read-Write Access**
- **Issue**: Many SNMP objects are read-only
- **Workaround**: Use MIB metadata `max-access` field to determine write capability
- **Indication**: Tag objects with "writable" or "read-only"

### 2. **Performance with Large Tables**
- **Issue**: Large tables (10K+ rows) slow to retrieve
- **Workaround**: Implement smart paging with GETBULK, cache frequently accessed data

### 3. **Missing MIB Definitions**
- **Issue**: Custom/proprietary OIDs without MIB files
- **Workaround**: Support numeric OID access, partial MIB information

### 4. **SNMP Timeout and Retry**
- **Issue**: Devices may be slow or unreachable
- **Workaround**: Configurable timeout/retry with exponential backoff

## Security Best Practices

1. **Use SNMPv3**: Always prefer SNMPv3 with authPriv over v1/v2c
2. **Strong Credentials**: Use strong authentication and privacy passphrases
3. **Access Control**: Limit SNMP access by IP address and community/user
4. **Encryption**: Enable AES encryption for SNMPv3 privacy
5. **Read-Only Community**: Use read-only community strings when possible
6. **Credential Storage**: Securely store SNMP credentials (encrypted at rest)

## Conclusion

The Dynamic Data Producer Interface provides effective abstraction for SNMP-enabled network devices through a translation layer that converts generic operations into SNMP protocol operations, leveraging MIB databases for metadata and naming.

**Key Benefits:**
- **Unified Interface**: Same API patterns work across SNMP devices, databases, and other sources
- **MIB Integration**: Complete OID resolution and metadata from MIB database
- **Table Operations**: SNMP tables map naturally to collections with CRUD operations
- **Batch Efficiency**: Multiple OID retrieval in single SNMP PDU
- **Version Support**: SNMPv1, SNMPv2c, and SNMPv3 with full security features

**Implementation Strategy:**
The translation layer handles SNMP protocol complexity, OID resolution via MIB database, and efficient table walking while providing a consistent REST-like interface. Performance optimizations include GETBULK usage, batch operations, and intelligent caching.

**Perfect Use Cases:**
- Network device monitoring and management
- Multi-vendor device configuration
- Network performance metrics collection
- SNMP table data analysis and reporting
- Unified monitoring across heterogeneous network infrastructure
