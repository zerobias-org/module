# IPMI Mapping to Dynamic Data Producer Interface

## Overview

This document describes how IPMI (Intelligent Platform Management Interface) baseboard management controllers (BMCs) map to the Dynamic Data Producer Interface, enabling unified access to server hardware monitoring and management through the generic object model. The interface is implemented via a translation layer that converts generic operations into IPMI commands over LAN (RMCP/RMCP+) or local interfaces.

**Protocol Standards**: IPMI v1.5, IPMI v2.0 (DCMI extensions)
**Transport**: RMCP (UDP port 623), RMCP+ (authenticated), KCS/SMIC/BT (local)

## Conceptual Mapping

### IPMI BMC → Object Model

```
IPMI BMC Device
├── /                                  → Root Container (BMC)
│   ├── fru/                          → Container Object (FRU Information)
│   │   ├── chassis                   → Document Object (Chassis FRU)
│   │   ├── board                     → Document Object (Board FRU)
│   │   └── product                   → Document Object (Product FRU)
│   ├── sdr/                          → Container Object (Sensor Data Repository)
│   │   ├── sensors                   → Collection Object (All Sensors)
│   │   │   ├── sensor_cpu1_temp      → Collection Element (Temperature sensor)
│   │   │   ├── sensor_fan1_speed     → Collection Element (Fan sensor)
│   │   │   └── sensor_psu1_voltage   → Collection Element (Voltage sensor)
│   │   └── events                    → Collection Object (Event Log)
│   ├── sel/                          → Container Object (System Event Log)
│   │   └── entries                   → Collection Object (Log Entries)
│   ├── chassis/                      → Container Object (Chassis Control)
│   │   ├── power_state               → Document Object (Power status)
│   │   ├── boot_options              → Document Object (Boot config)
│   │   └── identify                  → Document Object (Chassis ID LED)
│   ├── lan/                          → Container Object (LAN Configuration)
│   │   ├── ip_address                → Document Object (BMC IP config)
│   │   ├── mac_address               → Document Object (BMC MAC)
│   │   └── channels                  → Collection Object (LAN channels)
│   ├── users/                        → Container Object (User Management)
│   │   └── user_list                 → Collection Object (BMC Users)
│   └── commands/                     → Container Object (IPMI Commands)
│       ├── chassis_power_cycle       → Function Object (Power cycle)
│       ├── chassis_reset             → Function Object (Reset)
│       ├── set_boot_device           → Function Object (Boot override)
│       └── get_device_id             → Function Object (BMC info)
```

## Detailed Object Mappings

### 1. BMC Device → Root Container
```yaml
Object:
  id: "/"
  name: "BMC"
  objectClass: ["container"]
  description: "Baseboard Management Controller"
  tags: ["ipmi", "bmc", "hardware"]
  metadata:
    bmcAddress: "192.168.1.100"
    port: 623
    ipmiVersion: "2.0"
    authentication: "RMCP+"
    privilegeLevel: "ADMINISTRATOR"
    deviceId: "0x20"
    manufacturerId: "0x00000B"
    firmwareVersion: "2.55"
```

### 2. Sensor → Document Object (SDR)
```yaml
Object:
  id: "sensor_cpu1_temp"
  name: "CPU1 Temp"
  objectClass: ["document"]
  description: "CPU 1 temperature sensor"
  path: ["/", "sdr", "sensors", "CPU1 Temp"]
  documentSchema: "ipmi_sensor_schema"
  tags: ["sensor", "temperature", "cpu"]
  metadata:
    sensorNumber: "0x01"
    entityId: "0x03"  # Processor
    entityInstance: "0x01"
    sensorType: "0x01"  # Temperature
    sensorReading: "Threshold"
    units: "degrees C"

Schema:
  id: "ipmi_sensor_schema"
  properties:
    - name: "sensorNumber"
      dataType: "integer"
      required: true
      description: "Unique sensor identifier"
    - name: "name"
      dataType: "string"
      required: true
    - name: "type"
      dataType: "string"
      required: true
      description: "Sensor type (temperature, voltage, fan, etc.)"
    - name: "reading"
      dataType: "decimal"
      description: "Current sensor reading"
    - name: "units"
      dataType: "string"
      description: "Measurement units"
    - name: "status"
      dataType: "string"
      description: "Sensor status (ok, warning, critical)"
    - name: "thresholds"
      dataType: "object"
      description: "Threshold values"

# Document Data (current reading):
Document Data:
{
  "sensorNumber": 1,
  "name": "CPU1 Temp",
  "type": "Temperature",
  "reading": 45.0,
  "units": "degrees C",
  "status": "ok",
  "thresholds": {
    "lowerNonCritical": 10.0,
    "lowerCritical": 5.0,
    "upperNonCritical": 85.0,
    "upperCritical": 95.0,
    "upperNonRecoverable": 100.0
  },
  "timestamp": "2025-10-24T10:30:00.000Z"
}
```

### 3. Sensor List → Collection Object
```yaml
Object:
  id: "collection_sensors"
  name: "sensors"
  objectClass: ["collection"]
  description: "All BMC sensors"
  path: ["/", "sdr", "sensors"]
  collectionSchema: "ipmi_sensor_collection_schema"
  collectionSize: 42
  tags: ["sensors", "monitoring"]
  metadata:
    sdrVersion: "0x51"
    recordCount: 42

Schema:
  id: "ipmi_sensor_collection_schema"
  properties:
    - name: "sensorNumber"
      dataType: "integer"
      primaryKey: true
      required: true
    - name: "name"
      dataType: "string"
      required: true
    - name: "type"
      dataType: "string"
      required: true
    - name: "reading"
      dataType: "decimal"
    - name: "units"
      dataType: "string"
    - name: "status"
      dataType: "string"
```

### 4. System Event Log → Collection Object
```yaml
Object:
  id: "collection_sel"
  name: "entries"
  objectClass: ["collection"]
  description: "System Event Log entries"
  path: ["/", "sel", "entries"]
  collectionSchema: "ipmi_sel_schema"
  collectionSize: 156
  tags: ["sel", "events", "logs"]
  metadata:
    selVersion: "0x51"
    entries: 156
    freeSpace: 8192
    addTimestamp: "2025-10-20T08:45:00.000Z"

Schema:
  id: "ipmi_sel_schema"
  properties:
    - name: "recordId"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "SEL record ID (0x0001-0xFFFE)"
    - name: "timestamp"
      dataType: "date-time"
      required: true
    - name: "sensorType"
      dataType: "string"
      description: "Sensor type that generated event"
    - name: "sensorNumber"
      dataType: "integer"
      description: "Sensor number"
    - name: "eventType"
      dataType: "string"
      description: "Event direction (assertion, deassertion)"
    - name: "description"
      dataType: "string"
      description: "Human-readable event description"
    - name: "severity"
      dataType: "string"
      description: "Event severity (informational, warning, critical)"
```

### 5. FRU Information → Document Object
```yaml
Object:
  id: "fru_chassis"
  name: "chassis"
  objectClass: ["document"]
  description: "Chassis Field Replaceable Unit information"
  path: ["/", "fru", "chassis"]
  documentSchema: "ipmi_fru_schema"
  tags: ["fru", "chassis", "hardware-info"]

Schema:
  id: "ipmi_fru_schema"
  properties:
    - name: "partNumber"
      dataType: "string"
    - name: "serialNumber"
      dataType: "string"
    - name: "manufacturer"
      dataType: "string"
    - name: "productName"
      dataType: "string"
    - name: "assetTag"
      dataType: "string"

# Document Data:
Document Data:
{
  "partNumber": "CSE-829U",
  "serialNumber": "C9290UX12345678",
  "manufacturer": "Supermicro",
  "productName": "X11DPH-T",
  "assetTag": "SRV-DC1-R42-U10"
}
```

### 6. Chassis Power Control → Function Object
```yaml
Object:
  id: "func_chassis_power"
  name: "chassis_power_control"
  objectClass: ["function"]
  description: "Control chassis power state"
  inputSchema: "ipmi_power_control_input"
  outputSchema: "ipmi_power_control_output"
  tags: ["chassis", "power", "control"]

Schema (Input):
  id: "ipmi_power_control_input"
  properties:
    - name: "action"
      dataType: "string"
      required: true
      description: "Power action: on, off, cycle, reset, soft"

Schema (Output):
  id: "ipmi_power_control_output"
  properties:
    - name: "success"
      dataType: "boolean"
      required: true
    - name: "powerState"
      dataType: "string"
      description: "New power state"
    - name: "message"
      dataType: "string"
```

## API Usage Examples with IPMI Translation

### Device Discovery and Information

**Get BMC Device Info:**
```http
GET /objects/
# Translates to: IPMI Get Device ID command (NetFn: App, Cmd: 0x01)

# Response includes BMC metadata
```

**List FRU Information:**
```http
GET /objects/fru_chassis/document
# Translates to: IPMI Read FRU Data (NetFn: Storage, Cmd: 0x11)

# Response:
{
  "partNumber": "CSE-829U",
  "serialNumber": "C9290UX12345678",
  "manufacturer": "Supermicro",
  "productName": "X11DPH-T",
  "assetTag": "SRV-DC1-R42-U10"
}
```

### Sensor Monitoring

**List All Sensors:**
```http
GET /objects/collection_sensors/collection/elements
# Translates to:
# 1. IPMI Get SDR Repository Info (NetFn: Storage, Cmd: 0x20)
# 2. IPMI Get SDR (NetFn: Storage, Cmd: 0x23) for each sensor
# 3. IPMI Get Sensor Reading (NetFn: Sensor/Event, Cmd: 0x2D) for current values

# Response:
[
  {
    "sensorNumber": 1,
    "name": "CPU1 Temp",
    "type": "Temperature",
    "reading": 45.0,
    "units": "degrees C",
    "status": "ok"
  },
  {
    "sensorNumber": 2,
    "name": "CPU2 Temp",
    "type": "Temperature",
    "reading": 47.0,
    "units": "degrees C",
    "status": "ok"
  },
  {
    "sensorNumber": 10,
    "name": "FAN1",
    "type": "Fan",
    "reading": 3200.0,
    "units": "RPM",
    "status": "ok"
  }
]
```

**Get Specific Sensor Reading:**
```http
GET /objects/sensor_cpu1_temp/document
# Translates to: IPMI Get Sensor Reading (NetFn: Sensor/Event, Cmd: 0x2D, Sensor: 0x01)

# Response includes current reading, thresholds, status
```

**Search Sensors by Type:**
```http
GET /objects/collection_sensors/collection/search?filter=(type=Temperature)
# Translates to: SDR enumeration with client-side filtering by sensor type

# Returns only temperature sensors
```

**Monitor Critical Sensors:**
```http
GET /objects/collection_sensors/collection/search?filter=(status=critical)
# Translates to: Get all sensor readings, filter by critical status

# Returns sensors in critical state
```

### System Event Log (SEL)

**List Recent Events:**
```http
GET /objects/collection_sel/collection/elements?pageSize=20&sortBy=timestamp&sortDirection=descending
# Translates to:
# 1. IPMI Get SEL Info (NetFn: Storage, Cmd: 0x40)
# 2. IPMI Get SEL Entry (NetFn: Storage, Cmd: 0x43) for most recent entries

# Response:
[
  {
    "recordId": 156,
    "timestamp": "2025-10-24T10:15:00.000Z",
    "sensorType": "Power Supply",
    "sensorNumber": 42,
    "eventType": "assertion",
    "description": "Power Supply AC lost",
    "severity": "critical"
  },
  {
    "recordId": 155,
    "timestamp": "2025-10-24T09:30:00.000Z",
    "sensorType": "Temperature",
    "sensorNumber": 1,
    "eventType": "deassertion",
    "description": "CPU1 Temp - Upper Critical threshold",
    "severity": "warning"
  }
]
```

**Clear Event Log:**
```http
DELETE /objects/collection_sel
# Translates to: IPMI Clear SEL (NetFn: Storage, Cmd: 0x47)
```

**Add SEL Entry (OEM):**
```http
POST /objects/collection_sel/collection/elements
{
  "sensorType": "System Event",
  "description": "Maintenance performed",
  "severity": "informational"
}

# Translates to: IPMI Add SEL Entry (NetFn: Storage, Cmd: 0x44)
```

### Chassis Control

**Get Power Status:**
```http
GET /objects/chassis_power_state/document
# Translates to: IPMI Get Chassis Status (NetFn: Chassis, Cmd: 0x01)

# Response:
{
  "powerState": "on",
  "powerRestorePolicy": "always-on",
  "lastPowerEvent": "power-button",
  "chassisIntrusion": "inactive",
  "frontPanelLockout": "inactive",
  "driveFault": false,
  "coolingFault": false,
  "powerFault": false
}
```

**Power On Server:**
```http
POST /objects/func_chassis_power/invoke
{
  "action": "on"
}

# Translates to: IPMI Chassis Control (NetFn: Chassis, Cmd: 0x02, Data: 0x01)

# Response:
{
  "success": true,
  "powerState": "on",
  "message": "Chassis power on command completed"
}
```

**Power Cycle Server:**
```http
POST /objects/func_chassis_power/invoke
{
  "action": "cycle"
}

# Translates to: IPMI Chassis Control (NetFn: Chassis, Cmd: 0x02, Data: 0x02)
```

**Set Boot Device:**
```http
POST /objects/func_set_boot_device/invoke
{
  "device": "pxe",
  "persistent": false,
  "uefi": true
}

# Translates to: IPMI Set System Boot Options (NetFn: Chassis, Cmd: 0x08)
# Sets boot device to PXE for next boot only (UEFI mode)
```

**Chassis Identify (Blink LED):**
```http
PUT /objects/chassis_identify/document
{
  "state": "on",
  "duration": 15
}

# Translates to: IPMI Chassis Identify (NetFn: Chassis, Cmd: 0x04, Interval: 15)
```

### User Management

**List BMC Users:**
```http
GET /objects/user_list/collection/elements
# Translates to: IPMI Get User Name (NetFn: App, Cmd: 0x46) for all user IDs

# Response:
[
  {
    "userId": 2,
    "username": "admin",
    "privilegeLevel": "ADMINISTRATOR",
    "enabled": true
  },
  {
    "userId": 3,
    "username": "monitoring",
    "privilegeLevel": "USER",
    "enabled": true
  }
]
```

**Update User Password:**
```http
PUT /objects/user_list/collection/elements/2
{
  "password": "newSecurePassword123"
}

# Translates to: IPMI Set User Password (NetFn: App, Cmd: 0x47)
```

## Translation Layer Capabilities

### 1. **IPMI Command Mapping**

| Generic Operation | IPMI Command | NetFn | Cmd | Notes |
|-------------------|--------------|-------|-----|-------|
| `children()` (sensors) | Get SDR | Storage | 0x23 | Enumerate SDR repository |
| `documentData()` (sensor) | Get Sensor Reading | Sensor/Event | 0x2D | Read sensor value |
| `collectionElements()` (SEL) | Get SEL Entry | Storage | 0x43 | Read event log |
| `deleteObject()` (SEL) | Clear SEL | Storage | 0x47 | Clear event log |
| `invoke()` (power) | Chassis Control | Chassis | 0x02 | Power on/off/cycle/reset |
| `invoke()` (boot) | Set Boot Options | Chassis | 0x08 | Configure boot device |
| `documentData()` (FRU) | Read FRU Data | Storage | 0x11 | Read FRU information |
| Get BMC info | Get Device ID | App | 0x01 | BMC identification |

### 2. **Sensor Data Repository (SDR) Integration**
- **Capability**: Parse SDR records for sensor metadata
- **Implementation**: Cache SDR for fast sensor name/type lookups
- **Types**: Full Sensor, Compact Sensor, Event-Only, Entity Association, FRU, Management Controller

### 3. **Sensor Reading Translation**

| Sensor Type | DataType | Units | Example |
|-------------|----------|-------|---------|
| Temperature | `decimal` | °C, °F | 45.0 |
| Voltage | `decimal` | V | 12.05 |
| Current | `decimal` | A | 3.2 |
| Fan | `decimal` | RPM | 3200 |
| Power Supply | `integer` | W | 450 |
| Discrete | `string` | state | "Present", "Absent" |

### 4. **IPMI Session Management**
- **Capability**: Maintain authenticated IPMI sessions
- **RMCP+ (v2.0)**: RAKP authentication with integrity/confidentiality
- **Session Privilege**: USER, OPERATOR, ADMINISTRATOR levels
- **Timeout**: Configurable session timeout with keepalive

## Implementation Considerations

### 1. **IPMI Version Support**

**IPMI v1.5:**
```yaml
metadata:
  ipmiVersion: "1.5"
  authentication: "MD5"  # None, MD5, Password, OEM
  encryptionSupport: false
```

**IPMI v2.0 (RMCP+):**
```yaml
metadata:
  ipmiVersion: "2.0"
  authentication: "RAKP-HMAC-SHA1"
  encryption: "AES-CBC-128"
  integrityAlgorithm: "HMAC-SHA1-96"
  encryptionSupport: true
```

### 2. **Performance Optimization**
- **SDR Caching**: Cache sensor data repository to avoid repeated reads
- **Batch Sensor Reads**: Use DCMI extensions for efficient multi-sensor reads
- **Connection Pooling**: Reuse IPMI sessions for multiple operations
- **Async Operations**: Non-blocking sensor reads for bulk monitoring

### 3. **Error Handling**

| IPMI Completion Code | HTTP Status | Error Type |
|----------------------|-------------|------------|
| `0x00` (Success) | 200 | Success |
| `0xC0` (Node Busy) | 503 | `service_unavailable` |
| `0xC1` (Invalid Command) | 400 | `InvalidInputError` |
| `0xC4` (Invalid Field) | 400 | `InvalidInputError` |
| `0xCB` (Cannot Execute) | 403 | `ForbiddenError` |
| `0xCC` (Command Not Supported) | 501 | `not_implemented` |
| `0xD4` (Insufficient Privilege) | 403 | `ForbiddenError` |
| Timeout | 408 | `TimeoutError` |

### 4. **Special IPMI Features**

**SOL (Serial Over LAN):**
```yaml
Object:
  id: "func_sol_activate"
  name: "activateSOL"
  objectClass: ["function"]
  description: "Activate Serial Over LAN console"
  metadata:
    payloadType: "SOL"
```

**DCMI (Data Center Management Interface):**
```yaml
# Enhanced power monitoring via DCMI extensions
Object:
  id: "func_dcmi_power"
  name: "getDCMIPower"
  objectClass: ["function"]
  description: "Get enhanced power statistics via DCMI"
```

**Firmware Update:**
```yaml
Object:
  id: "func_firmware_update"
  name: "updateFirmware"
  objectClass: ["function"]
  description: "Upload and activate BMC firmware"
```

### 5. **Security Considerations**
- **Privilege Levels**: Enforce minimum required privilege for operations
- **Cipher Suites**: Support IPMI v2.0 cipher suites (0, 1, 2, 3, 6, 7, 8, 11, 12, 15, 17)
- **Password Policies**: BMC password complexity requirements
- **Session Limits**: Prevent session exhaustion attacks

## Limitations and Workarounds

### 1. **Sensor Polling Overhead**
- **Issue**: Reading all sensors individually is slow
- **Workaround**: Use SDR caching, batch reads, DCMI extensions

### 2. **Limited SEL Storage**
- **Issue**: SEL has finite capacity (typically 512-4096 entries)
- **Workaround**: Implement automated SEL archiving and clearing

### 3. **BMC Performance**
- **Issue**: BMCs have limited processing power
- **Workaround**: Rate limit requests, use appropriate timeouts

### 4. **Vendor-Specific Features**
- **Issue**: OEM-specific sensors and commands
- **Workaround**: Support OEM-specific SDR parsing and command extensions

## Security Best Practices

1. **Use IPMI v2.0**: Always prefer RMCP+ with encryption over v1.5
2. **Strong Passwords**: Enforce strong BMC passwords (16+ characters)
3. **Restrict Access**: Limit IPMI access to management network only
4. **Minimal Privilege**: Use lowest required privilege level for operations
5. **Audit Logging**: Enable BMC audit logging and monitor SEL
6. **Firmware Updates**: Keep BMC firmware up to date for security patches

## Conclusion

The Dynamic Data Producer Interface provides effective abstraction for IPMI-based hardware management through a translation layer that converts generic operations into IPMI commands over RMCP/RMCP+.

**Key Benefits:**
- **Unified Interface**: Same API patterns work across IPMI BMCs, SNMP devices, and other sources
- **Sensor Monitoring**: Real-time hardware sensor access via collection objects
- **Event Management**: System Event Log as searchable collection with CRUD operations
- **Remote Control**: Chassis power management and boot configuration via function objects
- **Hardware Inventory**: FRU information as structured document objects

**Implementation Strategy:**
The translation layer handles IPMI protocol complexity, session management, and SDR parsing while providing a consistent REST-like interface. Performance optimizations include SDR caching, batch operations, and connection pooling.

**Perfect Use Cases:**
- Data center hardware monitoring and alerting
- Remote server power management
- Hardware inventory and asset tracking
- Predictive maintenance via sensor trending
- Unified monitoring across heterogeneous server infrastructure
