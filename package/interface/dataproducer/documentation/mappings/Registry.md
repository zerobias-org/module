# Windows Registry Mapping to Dynamic Data Producer Interface

## Overview

This document describes how the Windows Registry hierarchy maps to the Dynamic Data Producer Interface, enabling unified access to Windows system configuration through the generic object model. The interface is implemented via a translation layer that converts generic operations into Windows Registry API calls (RegOpenKeyEx, RegQueryValueEx, RegSetValueEx, RegEnumKeyEx, etc.).

**Platform**: Windows NT-based systems (Windows 2000+)
**API**: Windows Registry API (advapi32.dll)

## Conceptual Mapping

### Windows Registry → Object Model

```
Windows Registry
├── HKEY_LOCAL_MACHINE (HKLM)         → Root Container
│   ├── SOFTWARE                      → Container Object (Key)
│   │   ├── Microsoft                 → Container Object (Key)
│   │   │   ├── Windows               → Container Object (Key)
│   │   │   │   ├── CurrentVersion   → Container Object (Key)
│   │   │   │   │   ├── Run          → Container Object (Key)
│   │   │   │   │   │   ├── (Default)           → Document Object (Value)
│   │   │   │   │   │   ├── SecurityHealth      → Document Object (Value)
│   │   │   │   │   │   └── WindowsDefender     → Document Object (Value)
│   │   │   │   │   ├── ProgramFilesDir         → Document Object (Value)
│   │   │   │   │   └── CommonFilesDir          → Document Object (Value)
│   ├── SYSTEM                        → Container Object (Key)
│   │   ├── CurrentControlSet         → Container Object (Key)
│   │   │   ├── Services              → Container Object (Key)
│   │   │   └── Control               → Container Object (Key)
│   └── HARDWARE                      → Container Object (Key)
├── HKEY_CURRENT_USER (HKCU)          → Root Container
│   ├── Software                      → Container Object (Key)
│   ├── Environment                   → Container Object (Key)
│   └── Control Panel                 → Container Object (Key)
├── HKEY_CLASSES_ROOT (HKCR)          → Root Container
├── HKEY_USERS (HKU)                  → Root Container
└── HKEY_CURRENT_CONFIG (HKCC)        → Root Container
```

## Detailed Object Mappings

### 1. Registry Hive → Root Container
```yaml
Object:
  id: "hive_hklm"
  name: "HKEY_LOCAL_MACHINE"
  objectClass: ["container"]
  description: "Local machine configuration and settings"
  tags: ["registry", "hive", "system"]
  metadata:
    hiveHandle: "HKEY_LOCAL_MACHINE"
    hiveAbbreviation: "HKLM"
    accessRights: "KEY_READ | KEY_WRITE"
    registryView: "Registry64"  # or "Registry32" for WOW64
```

### 2. Registry Key → Container Object
```yaml
Object:
  id: "key_currentversion"
  name: "CurrentVersion"
  objectClass: ["container"]
  description: "Windows version information"
  path: ["HKEY_LOCAL_MACHINE", "SOFTWARE", "Microsoft", "Windows", "CurrentVersion"]
  tags: ["key", "system-info"]
  created: "2024-01-15T10:30:00.000Z"  # From key timestamp
  modified: "2025-10-15T14:22:00.000Z"
  metadata:
    fullPath: "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion"
    subKeyCount: 42
    valueCount: 15
    maxSubKeyLength: 64
    maxValueNameLength: 128
    securityDescriptor: "O:BAG:SYD:(A;CI;KA;;;BA)(A;CI;KR;;;BU)"
```

### 3. Registry Value → Document Object
```yaml
Object:
  id: "value_programfilesdir"
  name: "ProgramFilesDir"
  objectClass: ["document"]
  description: "Default program files directory path"
  path: ["HKEY_LOCAL_MACHINE", "SOFTWARE", "Microsoft", "Windows", "CurrentVersion", "ProgramFilesDir"]
  documentSchema: "registry_value_schema"
  tags: ["value", "path", "system-config"]
  modified: "2025-10-15T14:22:00.000Z"
  metadata:
    fullPath: "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\ProgramFilesDir"
    valueType: "REG_SZ"
    dataSize: 36

Schema:
  id: "registry_value_schema"
  properties:
    - name: "name"
      dataType: "string"
      required: true
      description: "Value name"
    - name: "type"
      dataType: "string"
      required: true
      description: "Registry value type"
    - name: "data"
      description: "Value data (type varies)"
    - name: "dataSize"
      dataType: "integer"
      description: "Data size in bytes"

# Document Data:
Document Data:
{
  "name": "ProgramFilesDir",
  "type": "REG_SZ",
  "data": "C:\\Program Files",
  "dataSize": 36
}
```

### 4. Registry Multi-Value Key → Collection Object
```yaml
Object:
  id: "key_run"
  name: "Run"
  objectClass: ["container", "collection"]
  description: "Programs that run at startup"
  path: ["HKEY_LOCAL_MACHINE", "SOFTWARE", "Microsoft", "Windows", "CurrentVersion", "Run"]
  collectionSchema: "registry_value_collection_schema"
  collectionSize: 8
  tags: ["key", "startup", "autorun"]
  metadata:
    fullPath: "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
    treatAsCollection: true

Schema:
  id: "registry_value_collection_schema"
  properties:
    - name: "name"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Value name (unique within key)"
    - name: "type"
      dataType: "string"
      required: true
    - name: "data"
      dataType: "string"
      description: "Command line or path"
```

### 5. Registry Search → Function Object
```yaml
Object:
  id: "func_registry_search"
  name: "registrySearch"
  objectClass: ["function"]
  description: "Search registry for keys or values by name or data"
  inputSchema: "registry_search_input_schema"
  outputSchema: "registry_search_output_schema"
  tags: ["registry", "search"]

Schema (Input):
  id: "registry_search_input_schema"
  properties:
    - name: "rootKey"
      dataType: "string"
      required: true
      description: "Starting key path"
    - name: "searchType"
      dataType: "string"
      required: true
      description: "Search type: key, value, data"
    - name: "pattern"
      dataType: "string"
      required: true
      description: "Search pattern (supports wildcards)"
    - name: "recursive"
      dataType: "boolean"
      default: true
      description: "Search recursively"
    - name: "maxDepth"
      dataType: "integer"
      default: 10
      description: "Maximum recursion depth"

Schema (Output):
  id: "registry_search_output_schema"
  properties:
    - name: "results"
      dataType: "array"
      multi: true
      references:
        schemaId: "registry_search_result_schema"

Schema:
  id: "registry_search_result_schema"
  properties:
    - name: "path"
      dataType: "string"
      required: true
    - name: "matchType"
      dataType: "string"
      description: "Type: key, valueName, valueData"
    - name: "valueType"
      dataType: "string"
      description: "Registry value type (if applicable)"
    - name: "data"
      description: "Value data (if applicable)"
```

## API Usage Examples with Registry Translation

### Registry Navigation

**List Registry Hives:**
```http
GET /objects/
# Returns: HKLM, HKCU, HKCR, HKU, HKCC containers
```

**List Keys in Hive:**
```http
GET /objects/hive_hklm/children
# Translates to: RegEnumKeyEx(HKEY_LOCAL_MACHINE, ...)
# Returns: SOFTWARE, SYSTEM, HARDWARE, SAM, SECURITY keys
```

**List Subkeys:**
```http
GET /objects/key_software/children
# Translates to: RegEnumKeyEx(HKLM\SOFTWARE, ...)
# Returns: Microsoft, Classes, Policies, etc.
```

**List Values in Key:**
```http
GET /objects/key_currentversion/children?type=document
# Translates to: RegEnumValue(HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion, ...)
# Returns: Document objects for each registry value
```

### Value Operations

**Get Registry Value:**
```http
GET /objects/value_programfilesdir/document
# Translates to: RegQueryValueEx(HKLM\SOFTWARE\...\CurrentVersion, "ProgramFilesDir", ...)

# Response:
{
  "name": "ProgramFilesDir",
  "type": "REG_SZ",
  "data": "C:\\Program Files",
  "dataSize": 36
}
```

**Set Registry Value:**
```http
PUT /objects/value_customsetting/document
Content-Type: application/json

{
  "type": "REG_SZ",
  "data": "CustomValue"
}

# Translates to: RegSetValueEx(..., "CustomSetting", REG_SZ, "CustomValue", ...)
```

**Create New Value:**
```http
POST /objects/key_myapp/children
Content-Type: application/json

{
  "name": "Version",
  "objectClass": ["document"],
  "documentData": {
    "type": "REG_SZ",
    "data": "1.0.0"
  }
}

# Translates to: RegSetValueEx(..., "Version", REG_SZ, "1.0.0", ...)
```

**Delete Value:**
```http
DELETE /objects/value_oldvalue
# Translates to: RegDeleteValue(..., "OldValue")
```

### Key Operations

**Create Registry Key:**
```http
POST /objects/key_software/children
Content-Type: application/json

{
  "name": "MyApplication",
  "objectClass": ["container"]
}

# Translates to: RegCreateKeyEx(HKLM\SOFTWARE, "MyApplication", ...)
```

**Delete Registry Key:**
```http
DELETE /objects/key_myapp?recursive=true
# Translates to: RegDeleteTree(..., "MyApplication")
# Or: RegDeleteKeyEx(..., "MyApplication", ...) if recursive=false
```

**Export Registry Key:**
```http
GET /objects/key_myapp?export=true
# Translates to:
# 1. Enumerate all subkeys and values recursively
# 2. Format as .reg file content

# Response: .reg file format
Windows Registry Editor Version 5.00

[HKEY_LOCAL_MACHINE\SOFTWARE\MyApplication]
"Version"="1.0.0"
"InstallDate"=dword:00000001
```

### Collection Operations (Multi-Value Keys)

**List Startup Programs:**
```http
GET /objects/key_run/collection/elements
# Translates to: RegEnumValue(HKLM\...\Run, ...) for all values

# Response:
[
  {
    "name": "SecurityHealth",
    "type": "REG_EXPAND_SZ",
    "data": "%ProgramFiles%\\Windows Defender\\MSASCuiL.exe"
  },
  {
    "name": "WindowsDefender",
    "type": "REG_EXPAND_SZ",
    "data": "%ProgramFiles%\\Windows Defender\\MSASCui.exe"
  }
]
```

**Get Specific Startup Entry:**
```http
GET /objects/key_run/collection/elements/SecurityHealth
# Translates to: RegQueryValueEx(...\Run, "SecurityHealth", ...)
```

**Add Startup Entry:**
```http
POST /objects/key_run/collection/elements
Content-Type: application/json

{
  "name": "MyApp",
  "type": "REG_SZ",
  "data": "C:\\Program Files\\MyApp\\myapp.exe"
}

# Translates to: RegSetValueEx(...\Run, "MyApp", REG_SZ, ...)
```

**Remove Startup Entry:**
```http
DELETE /objects/key_run/collection/elements/MyApp
# Translates to: RegDeleteValue(...\Run, "MyApp")
```

### Search Operations

**Search for Keys:**
```http
POST /objects/func_registry_search/invoke
{
  "rootKey": "HKEY_LOCAL_MACHINE\\SOFTWARE",
  "searchType": "key",
  "pattern": "*Java*",
  "recursive": true,
  "maxDepth": 5
}

# Translates to: Recursive RegEnumKeyEx with pattern matching

# Response:
{
  "results": [
    {
      "path": "HKLM\\SOFTWARE\\JavaSoft",
      "matchType": "key"
    },
    {
      "path": "HKLM\\SOFTWARE\\Microsoft\\Java",
      "matchType": "key"
    }
  ]
}
```

**Search for Values:**
```http
GET /objects/key_software/search?keywords=Version&scope=subtree
# Translates to: Recursive enumeration with value name filtering

# Response: Document objects for all values named "Version"
```

## Translation Layer Capabilities

### 1. **Registry API Mapping**

| Generic Operation | Registry API | Notes |
|-------------------|--------------|-------|
| `children()` (keys) | `RegEnumKeyEx` | Enumerate subkeys |
| `children()` (values) | `RegEnumValue` | Enumerate values |
| `documentData()` (get) | `RegQueryValueEx` | Read value data |
| `documentData()` (set) | `RegSetValueEx` | Write value data |
| `createChild()` (key) | `RegCreateKeyEx` | Create new key |
| `createChild()` (value) | `RegSetValueEx` | Create new value |
| `deleteObject()` (key) | `RegDeleteTree` / `RegDeleteKeyEx` | Delete key |
| `deleteObject()` (value) | `RegDeleteValue` | Delete value |
| `collectionElements()` | `RegEnumValue` | List all values as collection |
| Export | `RegSaveKey` / custom | Export to .reg format |

### 2. **Path Resolution**
- **Capability**: Convert object paths to registry paths
- **Implementation**: ["HKEY_LOCAL_MACHINE", "SOFTWARE", "Microsoft"] → "HKLM\\SOFTWARE\\Microsoft"
- **Normalization**: Handle abbreviated hive names (HKLM, HKCU, etc.)

### 3. **Registry Value Type Mapping**

| Registry Type | DataType | Description |
|---------------|----------|-------------|
| REG_SZ | `string` | Null-terminated string |
| REG_EXPAND_SZ | `string` | Expandable string (with %env%) |
| REG_MULTI_SZ | `array` (string[]) | Multiple strings |
| REG_DWORD | `integer` | 32-bit number |
| REG_QWORD | `integer` | 64-bit number |
| REG_BINARY | `byte` | Binary data (base64) |
| REG_NONE | `string` | No defined type |
| REG_LINK | `string` | Symbolic link |

### 4. **Access Control**
- **Capability**: Respect Windows ACLs and permissions
- **Implementation**: Use appropriate access rights (KEY_READ, KEY_WRITE, KEY_ALL_ACCESS)
- **User Context**: Operations run under connecting user's credentials

## Implementation Considerations

### 1. **Security and Permissions**

**Read Access:**
```yaml
metadata:
  accessRights: "KEY_READ"
  samDesired: "KEY_QUERY_VALUE | KEY_ENUMERATE_SUB_KEYS"
```

**Write Access:**
```yaml
metadata:
  accessRights: "KEY_WRITE"
  samDesired: "KEY_SET_VALUE | KEY_CREATE_SUB_KEY"
```

**Full Control:**
```yaml
metadata:
  accessRights: "KEY_ALL_ACCESS"
  requiresElevation: true
  requiresAdmin: true
```

### 2. **Registry Redirection (WOW64)**
- **32-bit on 64-bit**: Handle registry redirection for 32-bit applications
- **KEY_WOW64_64KEY**: Access 64-bit registry view
- **KEY_WOW64_32KEY**: Access 32-bit registry view (WOW64 node)
- **Default**: Use native architecture view

### 3. **Performance Optimization**
- **Caching**: Cache frequently accessed keys/values with TTL
- **Batch Operations**: Combine multiple value reads when possible
- **Lazy Enumeration**: Don't enumerate all subkeys unless requested
- **Handle Reuse**: Keep registry key handles open for multiple operations

### 4. **Error Handling**

| Registry Error | HTTP Status | Error Type |
|----------------|-------------|------------|
| `ERROR_SUCCESS` | 200 | Success |
| `ERROR_FILE_NOT_FOUND` | 404 | `noSuchObjectError` |
| `ERROR_ACCESS_DENIED` | 403 | `ForbiddenError` |
| `ERROR_INVALID_PARAMETER` | 400 | `InvalidInputError` |
| `ERROR_MORE_DATA` | 500 | `buffer_overflow` |
| `ERROR_NO_MORE_ITEMS` | 200 | End of enumeration |

### 5. **Special Registry Features**

**Registry Symbolic Links:**
```yaml
Object:
  metadata:
    valueType: "REG_LINK"
    target: "\\Registry\\Machine\\System\\CurrentControlSet"
    isSymbolicLink: true
```

**Registry Transactions (Windows Vista+):**
```yaml
# Support for RegCreateTransaction, RegCommitTransaction
metadata:
  supportsTransactions: true
  transactionId: "HTRX_12345"
```

**Registry Notifications:**
```yaml
# Support for RegNotifyChangeKeyValue
Object:
  id: "func_watch_key"
  name: "watchRegistryKey"
  objectClass: ["function"]
  description: "Monitor registry key for changes"
  inputSchema: "registry_watch_schema"
```

## Limitations and Workarounds

### 1. **Case Sensitivity**
- **Issue**: Registry is case-insensitive but preserves case
- **Workaround**: Normalize paths for comparison, preserve original case for display

### 2. **Special Characters in Names**
- **Issue**: Backslashes in key names can be confusing
- **Workaround**: URL-encode special characters in REST paths

### 3. **Large Binary Values**
- **Issue**: Large REG_BINARY values can be slow to transfer
- **Workaround**: Use streaming or chunking for values > 1MB

### 4. **Permission Requirements**
- **Issue**: Some keys require administrator privileges
- **Workaround**: Clearly indicate required permissions in metadata

## Security Best Practices

1. **Principle of Least Privilege**: Request only required access rights (READ vs WRITE)
2. **Audit Logging**: Log all registry modifications for security auditing
3. **Validation**: Validate value types and data before writing
4. **Protected Keys**: Prevent deletion of critical system keys
5. **Backup Before Write**: Consider backing up keys before modification
6. **User Context**: Operations respect Windows user permissions and ACLs

## Conclusion

The Dynamic Data Producer Interface provides effective abstraction for Windows Registry access through a translation layer that converts generic operations into Windows Registry API calls.

**Key Benefits:**
- **Unified Interface**: Same API patterns work across Registry, databases, and other sources
- **Hierarchical Navigation**: Registry key hierarchy maps naturally to container objects
- **Type Safety**: Registry value types mapped to appropriate DataTypes
- **Collection Model**: Multi-value keys can be treated as collections with CRUD operations
- **Security**: Respects Windows ACLs and user permissions

**Implementation Strategy:**
The translation layer handles Windows Registry API complexity, permission checking, and WOW64 redirection while providing a consistent REST-like interface. Performance optimizations include handle caching, lazy enumeration, and batch operations.

**Perfect Use Cases:**
- Windows system configuration management
- Application settings storage and retrieval
- System inventory and auditing
- Registry backup and restore operations
- Cross-platform configuration management (Registry + Linux config files + databases)
