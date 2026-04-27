# IPMI Mapping

## Overview

How an IPMI baseboard management controller (BMC) surfaces through the
DataProducer interface. The BMC is the root; FRU records and chassis state
become Documents; the Sensor Data Repository becomes a Collection of sensor
readings; the System Event Log (SEL) becomes a Collection of event records;
chassis control, boot configuration, BMC reset, and Serial-over-LAN session
control become Functions. IPMI NetFn / Command bytes are an implementation
concern of the producer and never appear at the interface.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| IPMI concept                        | DataProducer concept                  | Notes                                                           |
|-------------------------------------|---------------------------------------|-----------------------------------------------------------------|
| BMC device                          | Root container                        | `id == name == "/"`                                             |
| FRU repository                      | Container                             | Children are individual FRU areas (chassis, board, product).    |
| FRU area                            | Document                              | Inventory record.                                                |
| Sensor Data Repository (SDR)        | Container with one Collection child   | The Collection lists all sensor readings.                        |
| Sensor                              | Collection element                    | Indexed by `sensorNumber`.                                       |
| System Event Log (SEL)              | Collection                            | Each entry is an element keyed by `recordId`.                    |
| Chassis status                      | Document                              | Read-only snapshot of power/intrusion/fault state.               |
| Chassis control (power, identify)   | Function                               | Power on/off/cycle/reset, identify LED.                          |
| Boot options                        | Function                               | Set boot device for next or persistent boot.                     |
| BMC user account                    | Collection element                     | Indexed by `userId`.                                             |
| Serial over LAN (SOL)                | Function (activation) + Binary (stream) | `activate` returns a session handle; the stream is a Binary download. |

IPMI is request/response; out-of-band PEF traps and SEL deassertions delivered
to a separate trap receiver are out of scope for this mapping. The SEL itself
is exposed as a Collection.

## Object Mappings

### Sensor Repository (Collection)

```yaml
Object:
  id: "/sdr/sensors"
  name: "sensors"
  objectClass: ["collection"]
  description: "All BMC sensor readings"
  path: ["sdr", "sensors"]
  collectionSchema: "schema:type:bmc.sdr.sensor"
  collectionSize: 42
  tags: ["sdr", "monitoring"]
```

```yaml
Schema:
  id: "schema:type:bmc.sdr.sensor"
  dataTypes:
    - name: "integer"
    - name: "string"
    - name: "decimal"
    - name: "date-time"
  properties:
    - name: "sensorNumber"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "Unique sensor number within the SDR"
    - name: "name"
      dataType: "string"
      required: true
    - name: "type"
      dataType: "string"
      required: true
      description: "temperature | voltage | current | fan | powerSupply | discrete"
    - name: "reading"
      dataType: "decimal"
      description: "Numeric reading; absent for discrete sensors"
    - name: "units"
      dataType: "string"
      description: "degC | V | A | RPM | W"
    - name: "state"
      dataType: "string"
      description: "ok | warning | critical | nonRecoverable | unknown"
    - name: "thresholds"
      dataType: "string"
      description: "Threshold record"
      references:
        schemaId: "schema:shared:ipmi_sensor_thresholds"
    - name: "lastSampled"
      dataType: "date-time"
```

The shared thresholds schema declares `lowerNonCritical`, `lowerCritical`,
`lowerNonRecoverable`, `upperNonCritical`, `upperCritical`,
`upperNonRecoverable`, all `dataType: decimal`.

### System Event Log (Collection)

```yaml
Object:
  id: "/sel"
  name: "sel"
  objectClass: ["collection"]
  description: "System Event Log"
  collectionSchema: "schema:type:bmc.sel.entry"
  collectionSize: 156
  tags: ["sel", "events", "log"]
```

```yaml
Schema:
  id: "schema:type:bmc.sel.entry"
  dataTypes:
    - name: "integer"
    - name: "date-time"
    - name: "string"
  properties:
    - name: "recordId"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "SEL record id (1..0xFFFE)"
    - name: "timestamp"
      dataType: "date-time"
      required: true
    - name: "sensorType"
      dataType: "string"
      description: "Sensor type that generated the event"
    - name: "sensorNumber"
      dataType: "integer"
    - name: "direction"
      dataType: "string"
      description: "assertion | deassertion"
    - name: "severity"
      dataType: "string"
      description: "informational | warning | critical | nonRecoverable"
    - name: "description"
      dataType: "string"
      description: "Decoded human-readable event text"
```

`addCollectionElement` maps to OEM event injection; many BMCs reject this from
non-OEM principals. `deleteObject` on `/sel` clears the log; element-level
delete is unsupported.

### Chassis Status (Document)

```yaml
Object:
  id: "/chassis/status"
  name: "status"
  objectClass: ["document"]
  description: "Current chassis power and fault state"
  documentSchema: "schema:type:bmc.chassis.status"
  tags: ["chassis", "read-only"]
```

```yaml
Schema:
  id: "schema:type:bmc.chassis.status"
  dataTypes:
    - name: "string"
    - name: "boolean"
  properties:
    - name: "powerState"
      dataType: "string"
      required: true
      description: "on | off"
    - name: "powerRestorePolicy"
      dataType: "string"
      description: "always-off | restore-previous | always-on"
    - name: "lastPowerEvent"
      dataType: "string"
    - name: "chassisIntrusion"
      dataType: "boolean"
    - name: "driveFault"
      dataType: "boolean"
    - name: "coolingFault"
      dataType: "boolean"
    - name: "powerFault"
      dataType: "boolean"
```

### FRU (Document)

```yaml
Object:
  id: "/fru/chassis"
  name: "chassis"
  objectClass: ["document"]
  description: "Chassis FRU inventory"
  documentSchema: "schema:shared:ipmi_fru_record"
  tags: ["fru", "inventory"]
```

```yaml
Schema:
  id: "schema:shared:ipmi_fru_record"
  dataTypes:
    - name: "string"
    - name: "date-time"
  properties:
    - name: "manufacturer"
      dataType: "string"
    - name: "productName"
      dataType: "string"
    - name: "partNumber"
      dataType: "string"
    - name: "serialNumber"
      dataType: "string"
    - name: "assetTag"
      dataType: "string"
    - name: "manufactureDate"
      dataType: "date-time"
```

### Chassis Power Control (Function)

```yaml
Object:
  id: "/commands/chassisPower"
  name: "chassisPower"
  objectClass: ["function"]
  description: "Control chassis power"
  inputSchema:  "schema:shared:ipmi_chassis_power_input"
  outputSchema: "schema:shared:ipmi_chassis_power_output"
  throws:
    privilege_denied:    "schema:shared:ipmi_completion_error"
    chassis_unreachable: "schema:shared:ipmi_completion_error"
  tags: ["chassis", "control"]
```

```yaml
Schema:
  id: "schema:shared:ipmi_chassis_power_input"
  dataTypes:
    - name: "string"
  properties:
    - name: "action"
      dataType: "string"
      required: true
      description: "on | off | cycle | reset | softShutdown | diagInterrupt"

Schema:
  id: "schema:shared:ipmi_chassis_power_output"
  dataTypes:
    - name: "string"
    - name: "boolean"
  properties:
    - name: "accepted"
      dataType: "boolean"
      required: true
    - name: "powerState"
      dataType: "string"
      description: "Power state observed after the command settled"
```

### Set Boot Device (Function)

```yaml
Object:
  id: "/commands/setBootDevice"
  name: "setBootDevice"
  objectClass: ["function"]
  inputSchema:  "schema:shared:ipmi_set_boot_input"
  outputSchema: "schema:shared:ipmi_set_boot_output"
  tags: ["chassis", "boot"]
```

The input schema declares `device: string` (`pxe | hdd | cdrom | bios | usb`),
`persistent: boolean`, and `uefi: boolean`.

### Activate SOL (Function)

```yaml
Object:
  id: "/commands/activateSol"
  name: "activateSol"
  objectClass: ["function"]
  description: "Activate a Serial-over-LAN payload session"
  inputSchema:  "schema:shared:ipmi_sol_activate_input"
  outputSchema: "schema:shared:ipmi_sol_activate_output"
  tags: ["sol"]
```

The output schema returns a session handle and the `id` of a transient
Binary object whose `downloadBinary` streams the SOL data. The Binary's
lifetime ends with the session.

## Operation Mappings

| DataProducer operation                    | IPMI execution                                                      |
|-------------------------------------------|---------------------------------------------------------------------|
| `getRootObject`                           | (synthetic — BMC identity from Get Device ID)                        |
| `getDocumentData` (FRU)                   | Read FRU Data; parse standard FRU layout                              |
| `getCollectionElements` (sensors)         | Enumerate SDR; per-record Get Sensor Reading                          |
| `searchCollectionElements` (sensors)      | Enumerate SDR; apply RFC4515 filter post-fetch                        |
| `getCollectionElement` (sensor)           | Get SDR record + Get Sensor Reading for that `sensorNumber`           |
| `getCollectionElements` (SEL)             | Get SEL Info + iterative Get SEL Entry                                |
| `getCollectionElement` (SEL entry)         | Get SEL Entry by `recordId`                                          |
| `addCollectionElement` (SEL)              | Add SEL Entry (OEM-restricted on most BMCs)                          |
| `deleteObject` (SEL)                      | Clear SEL                                                            |
| `getDocumentData` (chassis status)        | Get Chassis Status                                                    |
| `invokeFunction` (chassisPower)           | Chassis Control with action byte                                     |
| `invokeFunction` (setBootDevice)          | Set System Boot Options                                              |
| `invokeFunction` (activateSol)            | Activate Payload (SOL)                                               |
| `getCollectionElements` (users)           | Enumerate user slots via Get User Name / Get User Access             |
| `updateCollectionElement` (user)          | Set User Name / Set User Password / Set User Access                  |

## Filter Translation Example

IPMI has no native filter language. The producer fetches the candidate set
(SDR records, SEL entries) and applies the parsed filter in-process using
`expression.matches(item)` from `@zerobias-org/util-lite-filter`.

```
RFC4515:
  (&(type=Temperature)(|(state=warning)(state=critical))(reading>=80))

AST (lite-filter):
  AND
    EQ type "Temperature"
    OR
      EQ state "warning"
      EQ state "critical"
    GTE reading 80

Execution:
  1. Enumerate full SDR via Get SDR loop
  2. For each sensor record, call Get Sensor Reading
  3. expression.matches(record) → keep matching rows
  4. Apply pagination after filtering
```

For SEL searches, the producer can short-circuit by walking newest-first and
stopping when a `timestamp <` lower bound in the AST is reached.

## Core Types Reference

| IPMI value                              | `dataType`     | Notes                                                  |
|-----------------------------------------|----------------|--------------------------------------------------------|
| Temperature / voltage / fan reading      | `decimal`     | Apply SDR linearization before returning.              |
| Sensor counts / record IDs               | `integer`     |                                                        |
| Sensor / event timestamps                | `date-time`   | Convert from IPMI BCD timestamp + epoch offset.        |
| BMC IP address                            | `ipAddress`   |                                                        |
| BMC MAC address                           | `string`      | Colon-separated form; no `mac` core type.              |
| FRU manufacture date                      | `date-time`   |                                                        |
| FRU asset tag / serial / part number      | `string`      |                                                        |
| Sensor / event severity                   | `string`      | Enumerated; document allowed values in description.    |
| Boolean fault flags                       | `boolean`     |                                                        |
| Raw FRU multi-record (binary)             | `byte`        | Base64 in JSON; expose as Binary if large.             |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **Privilege levels.** IPMI has USER / OPERATOR / ADMINISTRATOR. The producer
  surfaces the connection's effective privilege; commands above that privilege
  return `UnsupportedOperationError` rather than a transport-level error.
- **Cipher suite mismatches.** RMCP+ negotiation failures surface at connection
  time, not per operation.
- **SDR caching.** SDR is large and rarely changes between BMC restarts. The
  producer should cache the parsed SDR with a long TTL and invalidate on the
  Get Device ID's `device_revision` change.
- **Sensor linearization.** Raw sensor reading bytes go through the SDR's
  m, b, b-exp, r-exp formula. The producer applies this; callers see decimals.
- **Discrete sensors.** Have no numeric reading — `reading` is null and `state`
  carries the bitfield decode.
- **SEL wraparound.** SEL has finite capacity; oldest records are overwritten
  when full. The Collection's `collectionSize` reflects the current count, not
  a monotonic total.
- **Concurrent sessions.** BMCs limit concurrent IPMI sessions (typically 4).
  The producer pools sessions and queues additional callers.
- **DCMI extensions.** Optional. When the BMC supports DCMI, expose enhanced
  power statistics as additional Document objects under `/dcmi/`. Absence is
  not an error.
- **Vendor OEM commands.** Hide behind vendor-specific Function objects under
  `/oem/{vendor}/...` so generic browsing isn't polluted.
