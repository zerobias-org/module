# Registry Mapping

## Overview

How the Windows Registry (and equivalent hierarchical configuration registries)
surfaces through the DataProducer interface. Registry hives become top-level
Containers; keys become Containers (and may additionally declare `collection`
when the caller wants to enumerate the key's values as elements); each key's
values are exposed as a Collection rather than as individual child Documents.
This keeps a key's children purely structural (subkeys) while still giving
direct access to value-level CRUD.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| Registry concept             | DataProducer concept                  | Notes                                                              |
|------------------------------|---------------------------------------|--------------------------------------------------------------------|
| Registry root                 | Root container                       | `id == name == "/"`                                                |
| Hive (HKLM, HKCU, …)          | Container                            | One per predefined root key.                                       |
| Subkey                        | Container + Collection               | Container exposes child subkeys; Collection exposes the key's values. |
| Value                         | Collection element                   | Element id is the value name (or `(default)` for the unnamed value). |
| Value type                    | DataType + optional `references`     | REG_SZ→string, REG_DWORD→integer, REG_BINARY→byte, REG_MULTI_SZ→string + multi, etc. |
| Symlink (REG_LINK)            | Property with `references.schemaId`  | Target is a key path; modeled as a string with reference semantics. |
| Security descriptor (SDDL)    | Tag / metadata, not a child object   | Surfaced via `tags` if the producer chooses to expose it.          |
| Watch / change notification    | Out of scope                         | Long-lived notifications are not part of the request/response interface. |

The "key is both Container and Collection" model is the canonical one. Use
Container ops (`getChildren`, `createChildObject`, `deleteObject`) to navigate
subkeys; use Collection ops (`getCollectionElements`, `addCollectionElement`,
`updateCollectionElement`, `deleteCollectionElement`) to manipulate the values
directly under the key.

## Object Mappings

### Hive (Container)

```yaml
Object:
  id: "/HKLM"
  name: "HKEY_LOCAL_MACHINE"
  objectClass: ["container"]
  description: "Local machine configuration"
  path: ["HKLM"]
  tags: ["hive"]
```

### Key (Container + Collection)

```yaml
Object:
  id: "/HKLM/SOFTWARE/Microsoft/Windows/CurrentVersion/Run"
  name: "Run"
  objectClass: ["container", "collection"]
  description: "Programs that launch at user logon"
  path: ["HKLM", "SOFTWARE", "Microsoft", "Windows", "CurrentVersion", "Run"]
  collectionSchema: "schema:shared:windows_registry_value"
  collectionSize: 8
  modified: "2025-10-15T14:22:00.000Z"
  tags: ["key"]
```

The single shared schema covers all values regardless of REG_* type — the
`type` discriminator and union-shaped `data` field carry the typing
information at the row level.

```yaml
Schema:
  id: "schema:shared:windows_registry_value"
  dataTypes:
    - name: "string"
    - name: "integer"
    - name: "byte"
  properties:
    - name: "name"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Value name. Empty string represents (Default)."
    - name: "type"
      dataType: "string"
      required: true
      description: "REG_SZ | REG_EXPAND_SZ | REG_MULTI_SZ | REG_DWORD | REG_QWORD | REG_BINARY | REG_NONE | REG_LINK"
    - name: "stringData"
      dataType: "string"
      description: "Set for REG_SZ, REG_EXPAND_SZ, REG_LINK"
    - name: "stringList"
      dataType: "string"
      multi: true
      description: "Set for REG_MULTI_SZ"
    - name: "intData"
      dataType: "integer"
      description: "Set for REG_DWORD, REG_QWORD"
    - name: "bytes"
      dataType: "byte"
      description: "Set for REG_BINARY and REG_NONE; base64-encoded"
    - name: "byteSize"
      dataType: "integer"
      description: "Decoded size in bytes (informational)"
```

When a REG_BINARY value carries a known structure (e.g. a serialized device
descriptor), a derived per-value schema can be exposed and referenced via
`references.schemaId` on `bytes`:

```yaml
- name: "bytes"
  dataType: "byte"
  references:
    schemaId: "schema:shared:windows_devnode_descriptor"
```

### Hierarchical Browsing

`getChildren` on the Run key returns subkey Containers (none in this example).
`getCollectionElements` on the same key returns the eight value rows:

```json
[
  {
    "name": "SecurityHealth",
    "type": "REG_EXPAND_SZ",
    "stringData": "%ProgramFiles%\\Windows Defender\\MSASCuiL.exe"
  },
  {
    "name": "OneDrive",
    "type": "REG_SZ",
    "stringData": "C:\\Program Files\\Microsoft\\OneDrive\\OneDrive.exe /background"
  }
]
```

### Registry Search (Function)

```yaml
Object:
  id: "/functions/registrySearch"
  name: "registrySearch"
  objectClass: ["function"]
  description: "Recursive search for keys, value names, or value data"
  inputSchema:  "schema:shared:windows_registry_search_input"
  outputSchema: "schema:shared:windows_registry_search_output"
  tags: ["search"]
```

```yaml
Schema:
  id: "schema:shared:windows_registry_search_input"
  dataTypes:
    - name: "string"
    - name: "boolean"
    - name: "integer"
  properties:
    - name: "rootPath"
      dataType: "string"
      required: true
      description: "Starting key path (e.g. HKLM\\SOFTWARE)"
    - name: "match"
      dataType: "string"
      required: true
      description: "key | valueName | valueData"
    - name: "pattern"
      dataType: "string"
      required: true
      description: "Glob pattern — *foo*, foo*, etc."
    - name: "recursive"
      dataType: "boolean"
    - name: "maxDepth"
      dataType: "integer"
```

```yaml
Schema:
  id: "schema:shared:windows_registry_search_output"
  dataTypes:
    - name: "string"
  properties:
    - name: "matches"
      dataType: "string"
      multi: true
      description: "Matching key/value paths"
      references:
        schemaId: "schema:shared:windows_registry_search_match"
```

## Operation Mappings

| DataProducer operation                          | Win32 Registry API                                         |
|-------------------------------------------------|------------------------------------------------------------|
| `getRootObject`                                 | (synthetic — registry instance)                            |
| `getChildren` on hive / key                     | `RegEnumKeyExW`                                            |
| `getObject` on key                              | `RegOpenKeyExW` + `RegQueryInfoKeyW`                       |
| `createChildObject` (subkey)                    | `RegCreateKeyExW`                                          |
| `deleteObject` (subkey, non-recursive)          | `RegDeleteKeyExW`                                          |
| `deleteObject` (subkey, recursive)              | `RegDeleteTreeW`                                           |
| `getCollectionElements`                          | `RegEnumValueW` loop                                      |
| `getCollectionElement(name)`                    | `RegQueryValueExW`                                         |
| `addCollectionElement` / `updateCollectionElement` | `RegSetValueExW`                                       |
| `deleteCollectionElement`                       | `RegDeleteValueW`                                          |
| `objectSearch` / `searchChildObjects`           | Recursive `RegEnumKeyExW` walk + RFC4515 filter post-fetch |
| `invokeFunction` (registrySearch)               | Recursive walk with glob match                             |

## Filter Translation Example

The Registry has no query language. The producer recursively enumerates the
target subtree and applies the parsed RFC4515 filter via
`expression.matches(item)` from `@zerobias-org/util-lite-filter`.

```
RFC4515 (against a key's value Collection):
  (&(type=REG_SZ)(name=*Path*)(stringData=*Java*))

AST (lite-filter):
  AND
    EQ type "REG_SZ"
    SUBSTR name "Path"
    SUBSTR stringData "Java"

Execution:
  1. RegEnumValueW over the target key (or recursively, for subtree search)
  2. For each value row, expression.matches(row)
  3. Yield matching rows; honor pagination
```

For Container-level searches (key names rather than values), the producer
walks subkeys and matches against `name`, `path`, and `tags`. Push-down of
prefix matches (`(name=Microsoft*)`) into the enumeration cursor is an
implementation optimization — RegEnumKeyExW does not natively support
filtering, so push-down means early-exit when the substring is known to
appear at the start.

## Core Types Reference

| Registry value type          | `dataType`         | Notes                                                       |
|------------------------------|--------------------|-------------------------------------------------------------|
| REG_SZ                       | `string`           |                                                             |
| REG_EXPAND_SZ                | `string`           | Producer chooses whether to expand `%env%` before returning. |
| REG_MULTI_SZ                 | `string` + `multi: true` | Multi-valued strings.                                  |
| REG_DWORD                    | `integer`          | 32-bit unsigned. JSON `number` representation is fine.       |
| REG_QWORD                    | `integer`          | 64-bit; serialize as JSON string if precision matters.       |
| REG_BINARY                   | `byte`             | Base64 in JSON. Use `references.schemaId` if structured.     |
| REG_NONE                     | `byte`             | Untyped binary.                                             |
| REG_LINK                     | `string` + `references` | Symbolic link to another key path.                       |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **Case insensitivity.** Registry names are case-insensitive but case-preserving.
  Object IDs use the original case from the source; comparisons are
  case-insensitive within the producer.
- **Backslash in IDs.** Object IDs use `/` as the path separator at the
  interface level. The producer maps `/HKLM/SOFTWARE/Microsoft` to the native
  `HKLM\SOFTWARE\Microsoft` when calling Win32 APIs.
- **(Default) value.** The unnamed default value is exposed as an element with
  `name: ""`. UIs may display it as `(Default)`.
- **WOW64 redirection.** 32-bit-on-64-bit redirection is a connection-level
  setting (`registryView: native | 32 | 64`); not exposed per operation.
- **Permission denied.** `ERROR_ACCESS_DENIED` from Win32 surfaces as
  `403`-equivalent. Existence-leaking keys (where ACL forbids reads on a
  known-present key) should return `noSuchObjectError` instead, matching the
  Confluence pattern.
- **Transactional updates.** Win32 `RegCreateKeyTransacted` is optional. If
  the connection negotiates transactions, multiple writes within a single
  `executeBulkOperations` call run inside one transaction.
- **Symlinks.** REG_LINK values are not auto-followed. Callers see a string
  pointing at another key path and traverse explicitly.
- **Large binary values.** Values over a few MB should be exposed as Binary
  Objects under the key rather than embedded in a Collection element. Use
  the producer's discretion based on a configurable size threshold.
- **Hive types beyond Windows.** This mapping generalizes to any hierarchical
  string-keyed configuration registry (macOS preferences, systemd unit
  configuration trees). Only the value-type vocabulary changes.
