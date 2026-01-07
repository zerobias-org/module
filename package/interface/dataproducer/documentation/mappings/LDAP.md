# LDAP Directory Services Mapping to Dynamic Data Producer Interface

## Overview

This document describes how LDAP (Lightweight Directory Access Protocol) concepts map to the Dynamic Data Producer Interface, enabling unified access to directory services through the generic object model. The interface is implemented via a translation layer that converts generic operations (RFC4515 filters, object hierarchy navigation) into appropriate LDAP operations (LDAP search filters, DN traversal, etc.).

## Conceptual Mapping

### LDAP Directory → Object Model

```
LDAP Root DSE
├── dc=example,dc=com                    → Container Object (Naming Context)
│   ├── ou=people                       → Container Object (Organizational Unit)
│   │   ├── cn=John Doe                 → Document Object (Person Entry)
│   │   ├── cn=Jane Smith               → Document Object (Person Entry)
│   │   └── uid=jdoe                    → Document Object (Person Entry)
│   ├── ou=groups                       → Container Object (Organizational Unit)
│   │   ├── cn=administrators           → Collection Object (Group)
│   │   └── cn=developers               → Collection Object (Group)
│   ├── ou=roles                        → Container Object (Organizational Unit)
│   │   └── cn=manager                  → Document Object (Role)
│   └── cn=schema                       → Container Object (Schema Definition)
│       ├── cn=attributeTypes           → Container Object
│       └── cn=objectClasses           → Container Object
```

## Detailed Object Mappings

### 1. LDAP Root DSE → Root Container
```yaml
Object:
  id: "/"
  name: "LDAP Directory"
  objectClass: ["container"]
  description: "LDAP Root Directory Service Entry"
  tags: ["ldap", "directory"]
```

### 2. Naming Context (Domain Component) → Container Object
```yaml
Object:
  id: "dc_example_com"
  name: "example.com"
  objectClass: ["container"]
  description: "Domain naming context for example.com"
  path: ["example.com"]
  tags: ["domain", "naming-context"]
```

### 3. Organizational Unit → Container Object
```yaml
Object:
  id: "ou_people"
  name: "people"
  objectClass: ["container"]
  description: "Organizational unit containing user accounts"
  path: ["example.com", "people"]
  tags: ["organizational-unit", "users"]
```

### 4. Person Entry → Document Object
```yaml
Object:
  id: "cn_john_doe"
  name: "John Doe"
  objectClass: ["document"]
  description: "User account for John Doe"
  path: ["example.com", "people", "John Doe"]
  documentSchema: "person_schema"
  tags: ["person", "user", "employee"]

# LDAP Entry as Document Data:
Document Data:
{
  "cn": "John Doe",
  "uid": "jdoe",
  "mail": "john.doe@example.com",
  "telephoneNumber": "+1-555-123-4567",
  "employeeNumber": "E12345",
  "department": "Engineering",
  "objectClass": ["inetOrgPerson", "organizationalPerson", "person"]
}
```

### 5. Group → Collection Object
```yaml
Object:
  id: "cn_administrators"
  name: "administrators"
  objectClass: ["collection"]
  description: "Administrative users group"
  path: ["example.com", "groups", "administrators"]
  collectionSchema: "group_member_schema"
  collectionSize: 12
  tags: ["group", "security", "admin"]

# Group Members as Collection Elements
Schema:
  id: "group_member_schema"
  properties:
    - name: "member"
      dataType: "dn"
      primaryKey: true
      description: "Distinguished name of group member"
    - name: "memberType"
      dataType: "string"
      description: "Type of membership (direct, nested)"
```

### 6. Schema Definitions → Document Objects
```yaml
Object:
  id: "schema_person"
  name: "person"
  objectClass: ["document"]
  description: "LDAP objectClass definition for person"
  documentSchema: "ldap_objectclass_schema"
  tags: ["schema", "objectclass"]

Document Data:
{
  "objectClass": "person",
  "type": "structural",
  "sup": ["top"],
  "must": ["sn", "cn"],
  "may": ["userPassword", "telephoneNumber", "seeAlso", "description"]
}
```

### 7. LDAP Search → Function Object
```yaml
Object:
  id: "search_active_users"
  name: "active_users_search"
  objectClass: ["function"]
  description: "Search for active user accounts"
  inputSchema: "ldap_search_input_schema"
  outputSchema: "ldap_search_output_schema"
  tags: ["search", "query", "users"]

# Search Input Schema
Schema:
  id: "ldap_search_input_schema"
  properties:
    - name: "baseDN"
      dataType: "dn"
      description: "Search base distinguished name"
    - name: "scope"
      dataType: "string"
      description: "Search scope (base, one, sub)"
    - name: "filter"
      dataType: "string"
      description: "LDAP search filter"
    - name: "attributes"
      dataType: "string"
      multi: true
      description: "Attributes to return"
```

## API Usage Examples with LDAP Translation

### Directory Navigation

**List Naming Contexts:**
```http
GET /objects/children
# Translates to: LDAP RootDSE query for namingContexts
```

**List Organizational Units:**
```http
GET /objects/dc_example_com/children?tags=organizational-unit
# Translates to: LDAP search (objectClass=organizationalUnit) scope=one
```

**List Users in OU:**
```http
GET /objects/ou_people/children?tags=person
# Translates to: LDAP search base="ou=people,dc=example,dc=com" (objectClass=person) scope=one
```

### User Account Operations

**Search Users with Filter (RFC4515 → LDAP Filter):**
```http
GET /objects/ou_people/search?filter=(&(objectClass=person)(department=Engineering))
# Translates to: LDAP search base="ou=people,dc=example,dc=com" filter="(&(objectClass=person)(department=Engineering))" scope=sub
```

**Get Specific User:**
```http
GET /objects/cn_john_doe/document
# Translates to: LDAP search base="cn=John Doe,ou=people,dc=example,dc=com" scope=base
```

**Update User Attributes:**
```http
PUT /objects/cn_john_doe/document
{
  "telephoneNumber": "+1-555-999-8888",
  "title": "Senior Engineer"
}
# Translates to: LDAP modify operation with replace operations
```

**Create New User:**
```http
POST /objects/ou_people/children
{
  "name": "Alice Johnson",
  "objectClass": ["document"],
  "documentSchema": "person_schema",
  "tags": ["person", "user", "employee"]
}
# Then populate with:
PUT /objects/cn_alice_johnson/document
{
  "cn": "Alice Johnson",
  "uid": "ajohnson",
  "mail": "alice.johnson@example.com",
  "objectClass": ["inetOrgPerson", "organizationalPerson", "person"]
}
# Translates to: LDAP add operation
```

### Group Operations

**List Group Members:**
```http
GET /objects/cn_administrators/collection/elements
# Translates to: LDAP search for group entry, extract member attributes
```

**Add User to Group:**
```http
POST /objects/cn_administrators/collection/elements
{
  "member": "cn=Alice Johnson,ou=people,dc=example,dc=com",
  "memberType": "direct"
}
# Translates to: LDAP modify operation adding member attribute value
```

**Remove User from Group:**
```http
DELETE /objects/cn_administrators/collection/elements/cn=Alice%20Johnson,ou=people,dc=example,dc=com
# Translates to: LDAP modify operation removing specific member attribute value
```

### Search Operations

**Execute Saved Search:**
```http
POST /objects/search_active_users/invoke
{
  "baseDN": "ou=people,dc=example,dc=com",
  "scope": "sub",
  "filter": "(&(objectClass=person)(!(userAccountControl:1.2.840.113556.1.4.803:=2)))",
  "attributes": ["cn", "mail", "department"]
}
# Translates to: LDAP search operation with specified parameters
```

## Translation Layer Capabilities

### 1. **RFC4515 Filter Compatibility**
- **Perfect Match**: LDAP natively uses RFC4515 filter syntax
- **Direct Translation**: API filters pass through unchanged to LDAP
- **Examples**:
  - `(cn=John*)` → LDAP filter `(cn=John*)`
  - `(&(objectClass=person)(department=Engineering))` → Direct LDAP usage
  - `(|(mail=*@example.com)(mail=*@subsidiary.com))` → Direct LDAP usage

### 2. **DN to Object ID Translation**
- **Capability**: Distinguished Names map to hierarchical object IDs
- **Implementation**: Bidirectional translation between DN and object paths
- **Examples**:
  - `cn=John Doe,ou=people,dc=example,dc=com` ↔ `/dc_example_com/ou_people/cn_john_doe`

### 3. **LDAP Schema Integration**
- **Capability**: LDAP schema definitions become Document objects
- **Implementation**: objectClass definitions, attribute types, and syntax rules
- **Benefit**: Schema-aware validation and attribute handling

### 4. **Search Scope Translation**
- **Capability**: Object hierarchy operations map to LDAP search scopes
- **Mapping**:
  - `GET /objects/{id}/children` → LDAP scope=one
  - `GET /objects/{id}/search` → LDAP scope=sub
  - `GET /objects/{id}` → LDAP scope=base

## Implementation Considerations

### 1. **Authentication Integration**
- **LDAP Bind**: Translation layer handles LDAP authentication
- **Pass-through**: Use connection credentials for LDAP bind operations
- **Security**: Maintain LDAP security contexts throughout operations

### 2. **Attribute Handling**
- **Multi-valued Attributes**: Use Property `multi: true` for LDAP multi-valued attributes
- **Binary Attributes**: Handle certificates, photos via Binary objects or base64 encoding
- **Operational Attributes**: Include createTimestamp, modifyTimestamp in object metadata

### 3. **Group Membership Models**
- **Static Groups**: Member attributes stored as collection elements
- **Dynamic Groups**: Use Function objects for memberURL-based groups
- **Nested Groups**: Handle via recursive member resolution

### 4. **Schema Discovery**
- **Dynamic Schema**: Query LDAP schema to build Property definitions
- **Caching**: Cache schema information for performance
- **Updates**: Monitor schema changes for dynamic updates

### 5. **Large Directory Handling**
- **Paging**: Use LDAP paged results for large result sets
- **VLV**: Support Virtual List View for sorted large datasets
- **Referrals**: Handle LDAP referrals for distributed directories

## LDAP-Specific Advantages

### 1. **Natural Filter Compatibility**
- LDAP already uses RFC4515 filters - no translation overhead
- Complex directory searches work seamlessly
- Standard LDAP operators (wildcards, presence, etc.) work directly

### 2. **Hierarchical Structure Match**
- LDAP's hierarchical DN structure maps naturally to object paths
- Container/leaf distinction aligns with LDAP entry types
- Organizational structure preservation

### 3. **Schema Integration**
- LDAP schema definitions can be exposed as Document objects
- Dynamic schema discovery enables runtime adaptation
- Attribute validation leverages existing LDAP schema rules

### 4. **Security Model Alignment**
- LDAP access controls map to object-level permissions
- Authentication passthrough maintains security context
- Directory-based authorization integrates naturally

## Recommended Extensions

### 1. **LDAP-Specific Query Functions**
```yaml
Object:
  id: "query_user_groups"
  name: "user_group_membership"
  objectClass: ["function"]
  description: "Get all groups for a user (including nested)"
  inputSchema: "user_dn_schema"
  outputSchema: "group_list_schema"
```

### 2. **Directory Synchronization**
```yaml
Object:
  id: "sync_from_ad"
  name: "active_directory_sync"
  objectClass: ["function"]
  description: "Synchronize users from Active Directory"
  inputSchema: "sync_filter_schema"
  outputSchema: "sync_result_schema"
```

### 3. **Enhanced Group Operations**
```yaml
Property:
  name: "member"
  dataType: "dn"
  format: "distinguished-name"
  references:
    schemaId: "person_schema"
    relationshipType: "group_membership"
```

## Conclusion

The Dynamic Data Producer Interface provides an exceptionally natural mapping to LDAP directory services. The translation layer benefits from LDAP's native use of RFC4515 filters and hierarchical structure, making the implementation straightforward and efficient.

**Key Benefits:**
- **Native Filter Support**: RFC4515 filters pass through directly to LDAP
- **Hierarchical Alignment**: DN structure maps perfectly to object hierarchy
- **Schema Integration**: LDAP schema becomes discoverable through the API
- **Security Preservation**: LDAP authentication and authorization integrate seamlessly
- **Standard Operations**: All CRUD operations map to standard LDAP operations

**Implementation Strategy:**
The translation layer primarily handles DN ↔ Object ID mapping and result formatting, while leveraging LDAP's native capabilities for filtering, searching, and schema management. This results in a highly efficient implementation that preserves LDAP's strengths while providing the unified interface benefits.

**Perfect Use Cases:**
- User account management across multiple directories
- Group membership administration
- Directory schema discovery and documentation
- Cross-directory synchronization and migration
- Unified identity management interfaces