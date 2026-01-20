# Dynamic Data Producer Interface

This interface defines a generic data and browsing system for the Auditmation platform, enabling a wide variety of use cases through a unified API.

## 🎯 Supported Use Cases

- **File and Folder Browsing** - Navigate filesystem-like structures
- **File Content Parsing** - Extract and process document contents  
- **Database Access** - Query and browse database schemas and records
- **Function Calling** - Invoke REST/Lambda/OpenAPI endpoints
- **Low-Code/No-Code Integration** - Connect to Spreadsheets, Zoho, Salesforce, GRC platforms

## 🏗️ Architecture Overview

This module builds upon the data structures and definitions provided by the Core Types model: `@zerobias-org/types-core`.

### Core Components

The DynamicData module uses three primary interfaces:

#### 1. Object Interface
The foundational unit that forms a tree-like browsing structure. Objects can represent files, folders, database tables, API endpoints, or any hierarchical data.

#### 2. Schema Resource  
Provides metadata and describes data relationships. Used by Collection, Function, and Document objects to define their structure.

#### 3. Property Entity
Defines individual properties within schemas, including data types, validation rules, and relationships.

---

## 📋 Object Interface Specification

### Core Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | string | ✅ | Immutable primary key (unique identifier) |
| `name` | string | ✅ | Human-readable name (relatively unique under parent) |
| `description` | string | ❌ | Optional description |
| `path` | string[] | ❌ | Documentary path array from root to object |
| `thumbnail` | string | ❌ | Public URL or data URI for visual representation |
| `tags` | string[] | ❌ | Optional organizational tags |
| `created` | timestamp | ❌ | Creation timestamp |
| `modified` | timestamp | ❌ | Last modification timestamp |
| `objectClass` | string[] | ❌ | Capability indicators (see Object Classes below) |

### ⚠️ Important Implementation Notes

- **Root Object**: All implementations **MUST** provide a root object with `id` and `name` of `'/'` that includes the `container` objectClass
- **Identity**: Object `id` is the only reliable identifier; `path` is documentary only
- **Hierarchy**: Objects can have multiple parents (flexible hierarchy support)
- **Naming**: Name collisions are allowed but discouraged (names are informational)

---

## 🔧 Object Classes (Subtypes)

Objects can implement zero or more classes, each providing specific capabilities. Attempting unsupported operations throws `UnsupportedOperationException`.

### 📁 Container Objects
*Objects that can contain other objects*

**Capabilities**: `["container"]`

**Required Operations**:
- **`children()`** - Returns paged results of direct child objects
  - Supports optional `type` parameter to filter by object class
  - Only returns immediate children (not grandchildren)

**Optional Operations**:
- **`objectSearch()`** - Keyword search within object hierarchy
  - `scope`: `"one_level"` | `"subtree"` (default: `"one_level"`)
  - `keywords`: string[] - Optional search terms
  - `filter`: RFC4515 structured filter expression
  - `sortBy`: Object property for sorting
  - `sortDirection`: `"asc"` | `"desc"` (default: `"asc"`)

### 📊 Collection Objects
*Objects representing collections of records*

**Capabilities**: `["collection"]`

**Properties**:
- `collectionSchema`: Schema ID describing collection elements (optional)
- `collectionSize`: Number of elements in collection (optional)

**Required Operations**:
- **`collectionElements()`** - Returns paged collection data
  - `sortBy`: Property name for sorting
  - `sortDirection`: `"ascending"` | `"descending"` (default: `"ascending"`)

**Optional Operations**:
- **`collectionSearch()`** - Filtered collection search
  - `filter`: RFC4515 structured filter expression
  - `sortBy` / `sortDirection`: Sorting parameters

### ⚡ Function Objects
*Objects that can be invoked with parameters*

**Capabilities**: `["function"]`

**Properties**:
- `inputSchema`: Schema ID for function inputs (optional)
- `outputSchema`: Schema ID for function outputs (optional)  
- `throws`: Map of HTTP error codes to error Schema IDs (optional)

**Required Operations**:
- **`invoke()`** - Execute function with input parameters
  - Input must conform to `inputSchema` (if specified)
  - Output conforms to `outputSchema` (if specified)
  - May raise errors as defined in `throws` or standard errors

### 📄 Document Objects
*Objects representing structured documents*

**Capabilities**: `["document"]`

**Properties**:
- `documentSchema`: Schema ID describing document structure

**Required Operations**:
- **`documentData()`** - Returns JSON document conforming to `documentSchema`

### 📎 Binary Objects
*Objects containing binary data (files, etc.)*

**Capabilities**: `["binary"]`

**Properties**:
- `mimeType`: MIME type of the binary content
- `fileName`: Original filename
- `size`: Content size in bytes

**Required Operations**:
- **`download()`** - Download binary content
  - Supports HTTP Range headers for resumable downloads
  - Uses streaming interface to prevent memory allocation for large files
  - See `../fileproducer` for OpenAPI implementation pattern

---

## 📐 Schema Resource Specification

### Schema Properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | string | Unique, immutable schema identifier |
| `dataTypes` | DataType[] | Array of all DataType entities used by this schema |
| `properties` | Property[] | Array of property definitions |

### Property Definition

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | string | - | JavaScript-compatible property name |
| `description` | string | - | Optional property description |
| `required` | boolean | `false` | Whether property is required |
| `multi` | boolean | `false` | Whether property accepts multiple values |
| `dataType` | string | - | Name of DataType entity |
| `references` | object | - | Schema ID and property name for relationships |

### DataType Entities

DataType entities are defined in `@zerobias-org/types-core` and provide validated, standardized type definitions across the Auditmation platform.

#### Finding Available DataTypes

**Complete Type List**: Located at `node_modules/@zerobias-org/types-core/data/types/types.json`

**Programmatic Access**:
```typescript
import { CoreType } from '@zerobias-org/types-core-js';

// List all available type names
const allTypes = CoreType.listTypes();

// Get type metadata by name
const emailType = new CoreType('email');
console.log(emailType.description); // "A valid email"
console.log(emailType.examples);    // ["john.doe01@gmail.com", ...]

// Get type metadata by format alias
const urlType = new CoreType(undefined, 'uri');
console.log(urlType.name); // "url"
```

#### Common DataTypes for Schema Properties

| DataType | JSON Type | Description | Example Values |
|----------|-----------|-------------|----------------|
| `string` | string | General text values | "any text" |
| `integer` | number | Integer numbers | 42, -100, 999 |
| `decimal` | number | Decimal numbers (for currency, precise math) | 19.99, 100.50 |
| `boolean` | boolean | True/false values | true, false |
| `date` | string | ISO 8601 dates (YYYY-MM-DD) | "2025-10-24" |
| `date-time` | string | ISO 8601 timestamps (RFC3339) | "2025-10-24T10:30:00.000Z" |
| `email` | string | Validated email addresses | "user@example.com" |
| `phoneNumber` | string | International phone numbers | "+12673103464" |
| `url` | string | URLs and URIs | "https://example.com" |
| `geoCountry` | string | ISO 3166-1 alpha-2 country codes | "US", "DE", "FR" |
| `geoSubdivision` | string | ISO 3166-2 subdivision codes | "US-CA", "DE-BE" |
| `byte` | string | Base64-encoded binary data | "SGVsbG8sIHdvcmxkCg==" |
| `mimeType` | string | MIME type identifiers | "application/json", "text/xml" |
| `uuid` | string | UUID identifiers | "b7168568-af49-11ea-8b0b-47ecc4197a7f" |
| `ipAddress` | string | IPv4 or IPv6 addresses | "192.168.1.1", "::1" |
| `hostname` | string | DNS hostnames | "github.com" |
| `duration` | string | ISO 8601 durations | "P3Y6M4DT12H30M5S", "12h" |

#### Choosing the Right DataType

**Use specific types instead of generic ones:**

❌ **Avoid**:
```yaml
properties:
  - name: "country"
    dataType: "string"
    format: "iso3166-alpha2"  # DON'T use custom formats
```

✅ **Prefer**:
```yaml
properties:
  - name: "country"
    dataType: "geoCountry"  # Use defined core type
    description: "Country code (ISO 3166-1 alpha-2)"
```

**Benefits of using core types:**
- ✅ Automatic validation against type rules
- ✅ Consistent behavior across all modules
- ✅ Built-in examples and documentation
- ✅ Type-specific parsing and formatting
- ✅ IDE autocomplete and type checking

#### DataType Properties

Each DataType entity includes:
- `name` - Type identifier (lowerCamel convention)
- `jsonType` - JSON primitive type (`string`, `number`, `boolean`, `object`)
- `isEnum` - Whether type has enumerated values
- `formats` - Alternative format names (e.g., `url` has format alias `uri`)
- `description` - Human-readable description
- `examples` - Array of valid example values
- `pattern` - Regex pattern for validation (if applicable)
- `htmlInput` - HTML input type hint (e.g., `email`, `tel`, `url`)

---

## 🔄 Schema Composition and Reuse

The DataProducer interface supports schema composition through **schema ID references**, enabling three key benefits:

### 1. Composition and Sharing

Reuse common schema definitions across multiple properties, avoiding duplication and ensuring consistency.

**Example: Shared Address Schema**

```json
{
  "id": "schema:shared:address",
  "dataTypes": [
    { "name": "string", "jsonType": "string" },
    { "name": "geoCountry", "jsonType": "string" },
    { "name": "geoSubdivision", "jsonType": "string" }
  ],
  "properties": [
    {
      "name": "street",
      "dataType": "string",
      "required": true,
      "description": "Street address"
    },
    {
      "name": "city",
      "dataType": "string",
      "required": true,
      "description": "City name"
    },
    {
      "name": "state",
      "dataType": "geoSubdivision",
      "required": true,
      "description": "State or province code (ISO 3166-2)"
    },
    {
      "name": "country",
      "dataType": "geoCountry",
      "required": true,
      "description": "Country code (ISO 3166-1 alpha-2)"
    },
    {
      "name": "postalCode",
      "dataType": "string",
      "required": true,
      "description": "Postal/ZIP code"
    }
  ]
}
```

**Usage: Customer Schema Referencing Address**

```json
{
  "id": "schema:table:mydb.public.customers",
  "dataTypes": [
    { "name": "integer", "jsonType": "number" },
    { "name": "string", "jsonType": "string" },
    { "name": "email", "jsonType": "string" }
  ],
  "properties": [
    {
      "name": "id",
      "dataType": "integer",
      "primaryKey": true,
      "required": true,
      "description": "Customer ID"
    },
    {
      "name": "name",
      "dataType": "string",
      "required": true,
      "description": "Customer name"
    },
    {
      "name": "email",
      "dataType": "email",
      "required": true,
      "description": "Contact email"
    },
    {
      "name": "billingAddress",
      "dataType": "string",
      "required": true,
      "description": "Billing address",
      "references": {
        "schemaId": "schema:shared:address"
      }
    },
    {
      "name": "shippingAddress",
      "dataType": "string",
      "required": false,
      "description": "Shipping address (if different)",
      "references": {
        "schemaId": "schema:shared:address"
      }
    }
  ]
}
```

**Benefits:**
- ✅ Single source of truth for address structure
- ✅ Both `billingAddress` and `shippingAddress` reference same schema
- ✅ Changes to address schema automatically apply to all uses
- ✅ Client can fetch `schema:shared:address` once and cache it

### 2. Simplifying Nested Objects

Each layer is a flat map of key/value pairs with schema ID references, avoiding deeply nested JSON structures.

**❌ Without Schema Composition (Deeply Nested):**

```json
{
  "id": "schema:table:mydb.public.orders",
  "properties": [
    {
      "name": "customer",
      "dataType": "object",
      "properties": [
        {
          "name": "billingAddress",
          "dataType": "object",
          "properties": [
            { "name": "street", "dataType": "string" },
            { "name": "city", "dataType": "string" },
            { "name": "state", "dataType": "string" }
          ]
        }
      ]
    }
  ]
}
```

**✅ With Schema Composition (Flat References):**

```json
{
  "id": "schema:table:mydb.public.orders",
  "properties": [
    {
      "name": "customerId",
      "dataType": "integer",
      "references": {
        "schemaId": "schema:table:mydb.public.customers",
        "propertyName": "id"
      }
    }
  ]
}
```

**Benefits:**
- ✅ Flat, simple structure at each level
- ✅ References instead of deep nesting
- ✅ Client loads schemas on-demand as needed
- ✅ Easier to parse and validate

### 3. Cheap Calls (Avoid Materializing Full Schemas)

Object metadata returns schema **IDs** instead of full schema objects, minimizing payload size and allowing clients to fetch schemas only when needed.

**Collection Object with Schema ID:**

```json
{
  "id": "/db:mydb/schema:public/table:customers",
  "name": "customers",
  "description": "Customer records",
  "objectClass": ["collection"],
  "collectionSchema": "schema:table:mydb.public.customers",
  "collectionSize": 1543
}
```

**Client Workflow:**

1. **Browse objects** - Gets lightweight metadata with schema IDs
2. **Display list** - Shows object names, counts, descriptions
3. **User selects object** - Only then fetch full schema via `getSchema()`
4. **Cache schema** - Reuse for multiple data queries

**Performance Benefits:**

| Operation | Without Schema IDs | With Schema IDs |
|-----------|-------------------|----------------|
| List 100 tables | 500KB+ (full schemas) | 15KB (IDs only) |
| Browse hierarchy | All schemas loaded | Zero schemas loaded |
| View table data | Schema already loaded | Fetch schema once |
| View related table | Duplicate schema | Reuse cached schema |

### Schema ID Naming Convention

Schema IDs follow a structured format with **type discriminators** to handle naming conflicts in databases where tables, views, types, and enums can share the same name.

**Format:** `schema:{type}:{catalog}.{schema}.{name}[:{direction}]`

**Examples:**

```
# Tables
schema:table:mydb.public.customers
schema:table:mydb.sales.orders

# Views
schema:view:mydb.public.active_customers
schema:view:mydb.analytics.monthly_revenue

# Functions (with direction)
schema:function:mydb.public.calculate_tax:input
schema:function:mydb.public.calculate_tax:output

# Custom Types
schema:type:mydb.public.address
schema:type:mydb.public.contact_info

# Enums
schema:enum:mydb.public.order_status
schema:enum:mydb.public.payment_method

# Shared Types (not database-specific)
schema:shared:address
schema:shared:pagination_params
```

**Why Discriminators Are Required:**

PostgreSQL allows naming conflicts:
```sql
-- All of these can coexist in the same schema:
CREATE TABLE public.address (...);
CREATE TYPE public.address AS (...);
CREATE VIEW public.address AS SELECT ...;
```

Without discriminators, `schema:mydb.public.address` would be ambiguous. With discriminators:
- `schema:table:mydb.public.address` - Unambiguous table reference
- `schema:type:mydb.public.address` - Unambiguous type reference
- `schema:view:mydb.public.address` - Unambiguous view reference

### Foreign Key vs Composition Patterns

**Foreign Key Pattern** - Property references another collection:

```json
{
  "name": "customerId",
  "dataType": "integer",
  "references": {
    "schemaId": "schema:table:mydb.public.customers",
    "propertyName": "id"
  }
}
```

Client knows:
- ✅ `customerId` is a foreign key to `customers` table
- ✅ Can fetch `customers` collection schema to understand the referenced entity
- ✅ Can navigate: order → customer → customer details

**Composition Pattern** - Property is a structured sub-object:

```json
{
  "name": "billingAddress",
  "dataType": "string",
  "references": {
    "schemaId": "schema:shared:address"
  }
}
```

Client knows:
- ✅ `billingAddress` is an embedded object (no separate collection)
- ✅ Schema defines structure of the nested object
- ✅ No navigation to separate entity (it's contained data)

**Key Difference:** `propertyName` indicates foreign key relationship; absence indicates composition.

---

## ⚙️ Implementation Guidelines

### Error Handling

| Error Type | When | Response |
|------------|------|----------|
| `noSuchObjectError` | Object ID not found | 404 with object type and ID |
| `noSuchObjectError` | Invalid schema reference | 404 with schema details |
| `UnsupportedOperationException` | Operation not supported by object class | 400 with operation details |
| Custom Function Errors | Function execution fails | As defined in function's `throws` property |

**Note**: Error definitions come from `@zerobias-org/types-core`. Circular reference detection is the caller's responsibility.

### Paging and Filtering

- **Default Page Size**: 100 items
- **Paging Structure**: Uses `@zerobias-org/types-core` paging model
  - `count`: Total items available
  - `pageCount`: Total pages
  - `pageNumber`: Current page (1-indexed)
  - `pageSize`: Items per page
- **Filters**: Use RFC4515 syntax for structured expressions
- **NPM Library**: Available for filter creation and parsing with sanity checking

### Security and Access Control

- **Authentication**: Inherited from underlying product connections
- **Authorization**: Uses permissions of the principal that established the connection
- **Function Limits**: Applied by underlying runtime components
- **Binary Downloads**: No size limits, but must support streaming/chunking

### Binary Download Implementation

For binary objects, implement downloads using the pattern from `../fileproducer`:

```yaml
responses:
  '200':
    description: Binary file download
    content:
      '*/*':
        schema:
          type: string
          format: binary
```

This enables:
- ✅ Streaming downloads (standard HTTP)
- ✅ Resumable downloads (HTTP Range headers)  
- ✅ Any MIME type support
- ✅ Memory efficient processing

---

## 🔗 Dependencies

- **`@zerobias-org/types-core`** - Core data types and error models
- **RFC4515 Filter Library** - For structured filter expressions (NPM package available)

---

## 📝 Exception Handling

When objects are no longer present, operations return 404 status with `noSuchObjectError` containing the same structure that would be returned by browsing the object's parent with matching filters.

All error responses conform to the error model definitions in `@zerobias-org/types-core`, providing consistent error handling across the platform.