# Dynamic Data Producer Interface

This interface defines a generic data and browsing system for the Zerobias platform, enabling a wide variety of use cases through a unified API.

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

DataType entities are defined in `@zerobias-org/types-core` and provide validated, standardized type definitions across the zerobias platform.

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

All error responses conform to the error model definitions in `@zerobias-org/types-core`, providing consistent error handling across the platform.<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Apis](#apis)
  - [BinaryApi](#binaryapi)
  - [**downloadBinary**](#downloadbinary)
  - [**uploadBinaryContent**](#uploadbinarycontent)
  - [CollectionsApi](#collectionsapi)
  - [**addCollectionElement**](#addcollectionelement)
  - [**deleteCollectionElement**](#deletecollectionelement)
  - [**executeBulkOperations**](#executebulkoperations)
  - [**getCollectionElement**](#getcollectionelement)
  - [**getCollectionElements**](#getcollectionelements)
  - [**searchCollectionElements**](#searchcollectionelements)
  - [**updateCollectionElement**](#updatecollectionelement)
  - [DocumentsApi](#documentsapi)
  - [**getDocumentData**](#getdocumentdata)
  - [**updateDocumentData**](#updatedocumentdata)
  - [FunctionsApi](#functionsapi)
  - [**executeRestRequest**](#executerestrequest)
  - [**invokeFunction**](#invokefunction)
  - [**validateFunctionInput**](#validatefunctioninput)
  - [ObjectsApi](#objectsapi)
  - [**createChildObject**](#createchildobject)
  - [**deleteObject**](#deleteobject)
  - [**getChildren**](#getchildren)
  - [**getObject**](#getobject)
  - [**getRootObject**](#getrootobject)
  - [**objectSearch**](#objectsearch)
  - [**searchChildObjects**](#searchchildobjects)
  - [**updateObject**](#updateobject)
  - [SchemasApi](#schemasapi)
  - [**getSchema**](#getschema)
- [Models](#models)
  - [BulkOperation](#bulkoperation)
    - [Properties](#properties)
  - [BulkOperationRequest](#bulkoperationrequest)
    - [Properties](#properties-1)
  - [BulkOperationResponse](#bulkoperationresponse)
    - [Properties](#properties-2)
  - [BulkOperationResponse_results](#bulkoperationresponse_results)
    - [Properties](#properties-3)
  - [BulkOperationResponse_summary](#bulkoperationresponse_summary)
    - [Properties](#properties-4)
  - [CreateObjectRequest](#createobjectrequest)
    - [Properties](#properties-5)
  - [EnhancedFunctionResponse](#enhancedfunctionresponse)
    - [Properties](#properties-6)
  - [EnhancedFunctionResponse_context](#enhancedfunctionresponse_context)
    - [Properties](#properties-7)
  - [EnhancedFunctionResponse_links](#enhancedfunctionresponse_links)
    - [Properties](#properties-8)
  - [EnhancedFunctionResponse_links_related](#enhancedfunctionresponse_links_related)
    - [Properties](#properties-9)
  - [Object](#object)
    - [Properties](#properties-10)
  - [ObjectClass](#objectclass)
    - [Properties](#properties-11)
  - [Object_retryPolicy](#object_retrypolicy)
    - [Properties](#properties-12)
  - [Property](#property)
    - [Properties](#properties-13)
  - [Property_references](#property_references)
    - [Properties](#properties-14)
  - [RestRequest](#restrequest)
    - [Properties](#properties-15)
  - [RestResponse](#restresponse)
    - [Properties](#properties-16)
  - [RestResponse_timing](#restresponse_timing)
    - [Properties](#properties-17)
  - [Schema](#schema)
    - [Properties](#properties-18)
  - [SearchScope](#searchscope)
    - [Properties](#properties-19)
  - [UpdateObjectRequest](#updateobjectrequest)
    - [Properties](#properties-20)
  - [ValidateFunctionInputRequest](#validatefunctioninputrequest)
    - [Properties](#properties-21)
  - [ValidationResult](#validationresult)
    - [Properties](#properties-22)
  - [ValidationResult_errors](#validationresult_errors)
    - [Properties](#properties-23)
  - [ValidationResult_warnings](#validationresult_warnings)
    - [Properties](#properties-24)
  - [connectionProfile](#connectionprofile)
    - [Properties](#properties-25)
  - [errorModelBase](#errormodelbase)
    - [Properties](#properties-26)
  - [illegalArgumentError](#illegalargumenterror)
    - [Properties](#properties-27)
  - [illegalArgumentError_allOf](#illegalargumenterror_allof)
    - [Properties](#properties-28)
  - [noSuchObjectError](#nosuchobjecterror)
    - [Properties](#properties-29)
  - [noSuchObjectError_allOf](#nosuchobjecterror_allof)
    - [Properties](#properties-30)
  - [sortDirection](#sortdirection)
    - [Properties](#properties-31)
  - [type](#type)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-interface-dataproducer](#documentation-for-zerobias-orgmodule-interface-dataproducer)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisbinaryapimd"></a>

## BinaryApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**downloadBinary**](#downloadBinary) | **GET** /objects/{objectId}/download | Download binary content
[**uploadBinaryContent**](#uploadBinaryContent) | **POST** /objects/{objectId}/upload | Upload binary content


<a name="downloadBinary"></a>
## **downloadBinary**
> File downloadBinary(objectId)

Download binary content

    Download binary content with streaming and resumable support

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*

<a name="uploadBinaryContent"></a>
## **uploadBinaryContent**
> Object uploadBinaryContent(objectId, body)

Upload binary content

    Uploads or replaces binary content for binary objects

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **body** | **File**|  |

#### Return type

[**Object**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apiscollectionsapimd"></a>

## CollectionsApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addCollectionElement**](#addCollectionElement) | **POST** /objects/{objectId}/collection/elements | Add collection element
[**deleteCollectionElement**](#deleteCollectionElement) | **DELETE** /objects/{objectId}/collection/elements/{elementKey} | Delete collection element
[**executeBulkOperations**](#executeBulkOperations) | **POST** /objects/{objectId}/collection/bulk | Execute bulk operations on collection
[**getCollectionElement**](#getCollectionElement) | **GET** /objects/{objectId}/collection/elements/{elementKey} | Get collection element by key
[**getCollectionElements**](#getCollectionElements) | **GET** /objects/{objectId}/collection/elements | Get collection elements
[**searchCollectionElements**](#searchCollectionElements) | **GET** /objects/{objectId}/collection/search | Search collection elements
[**updateCollectionElement**](#updateCollectionElement) | **PUT** /objects/{objectId}/collection/elements/{elementKey} | Update collection element


<a name="addCollectionElement"></a>
## **addCollectionElement**
> Map addCollectionElement(objectId, request\_body)

Add collection element

    Adds a new element to the collection (requires collection schema with primary key properties)

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **request\_body** | [**Map**](../Models/object.md)|  |

#### Return type

**Map**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="deleteCollectionElement"></a>
## **deleteCollectionElement**
> deleteCollectionElement(objectId, elementKey)

Delete collection element

    Deletes a specific collection element by its primary key

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **elementKey** | **String**| Primary key value(s) separated by commas for composite keys | [default to null]

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="executeBulkOperations"></a>
## **executeBulkOperations**
> BulkOperationResponse executeBulkOperations(objectId, BulkOperationRequest)

Execute bulk operations on collection

    Perform multiple create/update/delete operations in a single request

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **BulkOperationRequest** | [**BulkOperationRequest**](#modelsbulkoperationrequestmd)|  |

#### Return type

[**BulkOperationResponse**](#modelsbulkoperationresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getCollectionElement"></a>
## **getCollectionElement**
> Map getCollectionElement(objectId, elementKey)

Get collection element by key

    Retrieves a specific collection element by its primary key

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **elementKey** | **String**| Primary key value(s) separated by commas for composite keys | [default to null]

#### Return type

**Map**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getCollectionElements"></a>
## **getCollectionElements**
> List getCollectionElements(objectId, pageToken, pageNumber, pageSize, sortBy, sortDir, properties)

Get collection elements

    Returns paged collection data for collection objects

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **pageToken** | **String**| Opaque cursor for cursor-based pagination. When provided, pageNumber is ignored. Use the token from the previous response&#39;s pageToken header to get the next page.  | [optional] [default to null]
 **pageNumber** | **Integer**| The requested page (1-indexed). Ignored when pageToken is provided. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page | [optional] [default to 100]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](#modelssortdirectionmd)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null]
 **properties** | [**List**](../Models/String.md)| Dot notation property selection array. Controls which properties are returned in results.  Examples: - [\&quot;email\&quot;] - Return only email field (fuzzy matches e-mail, email_address, etc.) - [\&quot;contact.phone\&quot;, \&quot;contact.email\&quot;] - Return contact object with only phone and email - [\&quot;contact\&quot;] - Return entire contact object - [] or omitted - Return all properties (default)  Fuzzy matching applies to field names (email matches e-mail, email_address, emailAddress). For nested objects, specify full dot path to include only specific sub-properties.  | [optional] [default to null]

#### Return type

[**List**](../Models/map.md)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="searchCollectionElements"></a>
## **searchCollectionElements**
> List searchCollectionElements(objectId, pageToken, pageNumber, pageSize, filter, sortBy, sortDir, properties)

Search collection elements

    Filtered search within collection data

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **pageToken** | **String**| Opaque cursor for cursor-based pagination. When provided, pageNumber is ignored. Use the token from the previous response&#39;s pageToken header to get the next page.  | [optional] [default to null]
 **pageNumber** | **Integer**| The requested page (1-indexed). Ignored when pageToken is provided. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page | [optional] [default to 100]
 **filter** | **String**| RFC4515 structured filter expression | [optional] [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](#modelssortdirectionmd)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null]
 **properties** | [**List**](../Models/String.md)| Dot notation property selection array. Controls which properties are returned in results.  Examples: - [\&quot;email\&quot;] - Return only email field (fuzzy matches e-mail, email_address, etc.) - [\&quot;contact.phone\&quot;, \&quot;contact.email\&quot;] - Return contact object with only phone and email - [\&quot;contact\&quot;] - Return entire contact object - [] or omitted - Return all properties (default)  Fuzzy matching applies to field names (email matches e-mail, email_address, emailAddress). For nested objects, specify full dot path to include only specific sub-properties.  | [optional] [default to null]

#### Return type

[**List**](../Models/map.md)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateCollectionElement"></a>
## **updateCollectionElement**
> Map updateCollectionElement(objectId, elementKey, request\_body, If-Match)

Update collection element

    Updates a specific collection element by its primary key

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **elementKey** | **String**| Primary key value(s) separated by commas for composite keys | [default to null]
 **request\_body** | [**Map**](../Models/object.md)|  |
 **If-Match** | **String**| ETag for optimistic concurrency control | [optional] [default to null]

#### Return type

**Map**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisdocumentsapimd"></a>

## DocumentsApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getDocumentData**](#getDocumentData) | **GET** /objects/{objectId}/document | Get document data
[**updateDocumentData**](#updateDocumentData) | **PUT** /objects/{objectId}/document | Update document data


<a name="getDocumentData"></a>
## **getDocumentData**
> Map getDocumentData(objectId)

Get document data

    Returns JSON document for document objects

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]

#### Return type

**Map**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateDocumentData"></a>
## **updateDocumentData**
> Map updateDocumentData(objectId, request\_body, If-Match)

Update document data

    Updates JSON document content for document objects

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **request\_body** | [**Map**](../Models/object.md)|  |
 **If-Match** | **String**| ETag for optimistic concurrency control | [optional] [default to null]

#### Return type

**Map**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisfunctionsapimd"></a>

## FunctionsApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**executeRestRequest**](#executeRestRequest) | **POST** /objects/{objectId}/rest | Execute raw REST request
[**invokeFunction**](#invokeFunction) | **POST** /objects/{objectId}/invoke | Invoke function
[**validateFunctionInput**](#validateFunctionInput) | **POST** /objects/{objectId}/validate | Validate function input


<a name="executeRestRequest"></a>
## **executeRestRequest**
> RestResponse executeRestRequest(objectId, RestRequest)

Execute raw REST request

    Execute low-level REST request (function objects with httpMethod only)

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **RestRequest** | [**RestRequest**](#modelsrestrequestmd)|  |

#### Return type

[**RestResponse**](#modelsrestresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="invokeFunction"></a>
## **invokeFunction**
> Map invokeFunction(objectId, request\_body)

Invoke function

    Execute function with input parameters

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **request\_body** | [**Map**](../Models/object.md)| Function input parameters (must conform to inputSchema if specified) | [optional]

#### Return type

**Map**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="validateFunctionInput"></a>
## **validateFunctionInput**
> ValidationResult validateFunctionInput(objectId, ValidateFunctionInputRequest)

Validate function input

    Validate input parameters against function schema without execution

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **ValidateFunctionInputRequest** | [**ValidateFunctionInputRequest**](#modelsvalidatefunctioninputrequestmd)|  |

#### Return type

[**ValidationResult**](#modelsvalidationresultmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisobjectsapimd"></a>

## ObjectsApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createChildObject**](#createChildObject) | **POST** /objects/{objectId}/children | Create child object
[**deleteObject**](#deleteObject) | **DELETE** /objects/{objectId}/children | Delete object
[**getChildren**](#getChildren) | **GET** /objects/{objectId}/children | Get child objects
[**getObject**](#getObject) | **GET** /objects/{objectId} | Get object by ID
[**getRootObject**](#getRootObject) | **GET** /objects | Get root object
[**objectSearch**](#objectSearch) | **GET** /objects/{objectId}/search | Search objects
[**searchChildObjects**](#searchChildObjects) | **GET** /objects/{objectId}/searchChildren | Search child objects with filtering and property selection
[**updateObject**](#updateObject) | **PUT** /objects/{objectId}/children | Update object


<a name="createChildObject"></a>
## **createChildObject**
> Object createChildObject(objectId, CreateObjectRequest)

Create child object

    Creates a new object as a child of the specified parent container

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **CreateObjectRequest** | [**CreateObjectRequest**](#modelscreateobjectrequestmd)| Child object properties (id is optional and will be generated if not provided) |

#### Return type

[**Object**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="deleteObject"></a>
## **deleteObject**
> deleteObject(objectId, recursive)

Delete object

    Deletes an object and optionally its children

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **recursive** | **Boolean**| Delete children recursively (containers only) | [optional] [default to false]

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="getChildren"></a>
## **getChildren**
> List getChildren(objectId, pageToken, pageNumber, pageSize, sortBy, sortDir, type, tags)

Get child objects

    Returns paged results of objects that are children of the specified object

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **pageToken** | **String**| Opaque cursor for cursor-based pagination. When provided, pageNumber is ignored. Use the token from the previous response&#39;s pageToken header to get the next page.  | [optional] [default to null]
 **pageNumber** | **Integer**| The requested page (1-indexed). Ignored when pageToken is provided. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page | [optional] [default to 100]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](#modelssortdirectionmd)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null]
 **type** | [**List**](#modelsobjectclassmd)| Optional object class filter | [optional] [default to null]
 **tags** | [**List**](../Models/String.md)| Optional tags filter - returns objects containing any of the specified tags | [optional] [default to null]

#### Return type

[**List**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getObject"></a>
## **getObject**
> Object getObject(objectId)

Get object by ID

    Retrieve a specific object by its unique identifier

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]

#### Return type

[**Object**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getRootObject"></a>
## **getRootObject**
> Object getRootObject()

Get root object

    Returns the root object with id and name of &#39;/&#39;

#### Parameters
This endpoint does not need any parameter.

#### Return type

[**Object**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="objectSearch"></a>
## **objectSearch**
> List objectSearch(objectId, pageToken, pageNumber, pageSize, sortBy, sortDir, properties, scope, keywords, filter)

Search objects

    Performs keyword search or filtered search within object hierarchy. Either keywords or filter must be provided, but not both.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **pageToken** | **String**| Opaque cursor for cursor-based pagination. When provided, pageNumber is ignored. Use the token from the previous response&#39;s pageToken header to get the next page.  | [optional] [default to null]
 **pageNumber** | **Integer**| The requested page (1-indexed). Ignored when pageToken is provided. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page | [optional] [default to 100]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](#modelssortdirectionmd)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null]
 **properties** | [**List**](../Models/String.md)| Dot notation property selection array. Controls which properties are returned in results.  Examples: - [\&quot;email\&quot;] - Return only email field (fuzzy matches e-mail, email_address, etc.) - [\&quot;contact.phone\&quot;, \&quot;contact.email\&quot;] - Return contact object with only phone and email - [\&quot;contact\&quot;] - Return entire contact object - [] or omitted - Return all properties (default)  Fuzzy matching applies to field names (email matches e-mail, email_address, emailAddress). For nested objects, specify full dot path to include only specific sub-properties.  | [optional] [default to null]
 **scope** | [**SearchScope**](../Models/.md)| Search scope depth | [optional] [default to null] [enum: one_level, subtree]
 **keywords** | [**List**](../Models/String.md)| Search keywords (mutually exclusive with filter) | [optional] [default to null]
 **filter** | **String**| RFC4515 structured filter expression (mutually exclusive with keywords) | [optional] [default to null]

#### Return type

[**List**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="searchChildObjects"></a>
## **searchChildObjects**
> List searchChildObjects(objectId, pageToken, pageNumber, pageSize, sortBy, sortDir, properties, filter, scope, includeCount)

Search child objects with filtering and property selection

    Advanced search and filtering of child objects within a container. Supports RFC4515 filters for child object metadata and dot notation property selection.  Differences from /children: - /children: Simple listing with basic type/tag filters - /searchChildren: Advanced RFC4515 filtering + property projection 

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **pageToken** | **String**| Opaque cursor for cursor-based pagination. When provided, pageNumber is ignored. Use the token from the previous response&#39;s pageToken header to get the next page.  | [optional] [default to null]
 **pageNumber** | **Integer**| The requested page (1-indexed). Ignored when pageToken is provided. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page | [optional] [default to 100]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](#modelssortdirectionmd)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null]
 **properties** | [**List**](../Models/String.md)| Dot notation property selection array. Controls which properties are returned in results.  Examples: - [\&quot;email\&quot;] - Return only email field (fuzzy matches e-mail, email_address, etc.) - [\&quot;contact.phone\&quot;, \&quot;contact.email\&quot;] - Return contact object with only phone and email - [\&quot;contact\&quot;] - Return entire contact object - [] or omitted - Return all properties (default)  Fuzzy matching applies to field names (email matches e-mail, email_address, emailAddress). For nested objects, specify full dot path to include only specific sub-properties.  | [optional] [default to null]
 **filter** | **String**| RFC4515 filter expression for child object properties | [optional] [default to null]
 **scope** | [**SearchScope**](../Models/.md)| Search scope depth | [optional] [default to null] [enum: one_level, subtree]
 **includeCount** | **Boolean**| Include total count in response headers (may be expensive) | [optional] [default to false]

#### Return type

[**List**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="updateObject"></a>
## **updateObject**
> Object updateObject(objectId, UpdateObjectRequest, If-Match)

Update object

    Updates object metadata and properties

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **objectId** | **String**| Unique object identifier | [default to null]
 **UpdateObjectRequest** | [**UpdateObjectRequest**](#modelsupdateobjectrequestmd)|  |
 **If-Match** | **String**| ETag for optimistic concurrency control | [optional] [default to null]

#### Return type

[**Object**](#modelsobjectmd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisschemasapimd"></a>

## SchemasApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getSchema**](#getSchema) | **GET** /schemas/{schemaId} | Get schema by ID


<a name="getSchema"></a>
## **getSchema**
> Schema getSchema(schemaId)

Get schema by ID

    Retrieve schema metadata and structure

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **schemaId** | **String**| Unique schema identifier | [default to null]

#### Return type

[**Schema**](#modelsschemamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsbulkoperationmd"></a>

## BulkOperation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**action** | **String** | Operation type | [default to null]
**key** | **String** | Element key (for update/delete) | [optional] [default to null]
**data** | **Map** | Data for operation | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsbulkoperationrequestmd"></a>

## BulkOperationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**operations** | [**List**](#modelsbulkoperationmd) | List of operations to execute | [default to null]
**atomic** | **Boolean** | All or nothing execution | [optional] [default to false]
**continueOnError** | **Boolean** | Continue processing on errors | [optional] [default to true]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsbulkoperationresponsemd"></a>

## BulkOperationResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**results** | [**List**](#modelsbulkoperationresponse_resultsmd) | Individual operation results | [optional] [default to null]
**summary** | [**BulkOperationResponse_summary**](#modelsbulkoperationresponse_summarymd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsbulkoperationresponse_resultsmd"></a>

## BulkOperationResponse_results
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**success** | **Boolean** | Whether the operation succeeded | [optional] [default to null]
**data** | **Map** | Result data (if successful) | [optional] [default to null]
**error** | **Map** | Error information (if failed) | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsbulkoperationresponse_summarymd"></a>

## BulkOperationResponse_summary
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**total** | **Integer** | Total number of operations | [optional] [default to null]
**succeeded** | **Integer** | Number of successful operations | [optional] [default to null]
**failed** | **Integer** | Number of failed operations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateobjectrequestmd"></a>

## CreateObjectRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Optional object ID (will be generated if not provided) | [optional] [default to null]
**name** | **String** | Human-readable name | [default to null]
**description** | **String** | Optional description | [optional] [default to null]
**thumbnail** | **URI** | Public URL or data URI for visual representation | [optional] [default to null]
**tags** | **List** | Optional organizational tags | [optional] [default to null]
**objectClass** | [**List**](#modelsobjectclassmd) | Capability indicators | [default to null]
**collectionSchema** | **String** | Schema ID for collection elements (collection objects only) | [optional] [default to null]
**inputSchema** | **String** | Schema ID for function inputs (function objects only) | [optional] [default to null]
**outputSchema** | **String** | Schema ID for function outputs (function objects only) | [optional] [default to null]
**throws** | **Map** | Map of HTTP error codes to error Schema IDs (function objects only) | [optional] [default to null]
**documentSchema** | **String** | Schema ID for document structure (document objects only) | [optional] [default to null]
**mimeType** | **String** | MIME type of binary content (binary objects only) | [optional] [default to null]
**fileName** | **String** | Original filename (binary objects only) | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsenhancedfunctionresponsemd"></a>

## EnhancedFunctionResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | **Map** | Response data | [optional] [default to null]
**schema** | [**Schema**](#modelsschemamd) |  | [optional] [default to null]
**context** | [**EnhancedFunctionResponse_context**](#modelsenhancedfunctionresponse_contextmd) |  | [optional] [default to null]
**links** | [**EnhancedFunctionResponse_links**](#modelsenhancedfunctionresponse_linksmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsenhancedfunctionresponse_contextmd"></a>

## EnhancedFunctionResponse_context
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**requestId** | **String** | Unique request identifier | [optional] [default to null]
**timestamp** | **Date** | Execution timestamp | [optional] [default to null]
**duration** | **Integer** | Execution time in milliseconds | [optional] [default to null]
**retryCount** | **Integer** | Number of retries attempted | [optional] [default to null]
**cached** | **Boolean** | Whether response was cached | [optional] [default to null]
**warnings** | **List** | Non-fatal warnings | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsenhancedfunctionresponse_linksmd"></a>

## EnhancedFunctionResponse_links
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**self** | **URI** | Link to this operation | [optional] [default to null]
**related** | [**List**](#modelsenhancedfunctionresponse_links_relatedmd) | Links to related operations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsenhancedfunctionresponse_links_relatedmd"></a>

## EnhancedFunctionResponse_links_related
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**rel** | **String** | Relationship type | [optional] [default to null]
**href** | **URI** | Related operation link | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsobjectmd"></a>

## Object
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Immutable primary key (unique identifier) | [default to null]
**name** | **String** | Human-readable name | [default to null]
**description** | **String** | Optional description | [optional] [default to null]
**path** | **List** | Documentary path array from root to object | [optional] [default to null]
**thumbnail** | **URI** | Public URL or data URI for visual representation | [optional] [default to null]
**tags** | **List** | Optional organizational tags | [optional] [default to null]
**created** | **Date** | Creation timestamp | [optional] [default to null]
**modified** | **Date** | Last modification timestamp | [optional] [default to null]
**etag** | **String** | Entity tag for optimistic concurrency control | [optional] [default to null]
**versionId** | **String** | Version identifier for object versioning | [optional] [default to null]
**objectClass** | [**List**](#modelsobjectclassmd) | Capability indicators | [optional] [default to null]
**collectionSchema** | **String** | Schema ID for collection elements (collection objects only) | [optional] [default to null]
**collectionSize** | **Long** | Number of elements in collection (collection objects only) | [optional] [default to null]
**inputSchema** | **String** | Schema ID for function inputs (function objects only) | [optional] [default to null]
**outputSchema** | **String** | Schema ID for function outputs (function objects only) | [optional] [default to null]
**throws** | **Map** | Map of HTTP error codes to error Schema IDs (function objects only) | [optional] [default to null]
**documentSchema** | **String** | Schema ID for document structure (document objects only) | [optional] [default to null]
**mimeType** | **String** | MIME type of binary content (binary objects only) | [optional] [default to null]
**fileName** | **String** | Original filename (binary objects only) | [optional] [default to null]
**size** | **Long** | Content size in bytes (binary objects only) | [optional] [default to null]
**httpMethod** | **String** | HTTP method for REST operations (function objects only) | [optional] [default to null]
**httpPath** | **String** | HTTP path template for REST operations (function objects only) | [optional] [default to null]
**httpHeaders** | **Map** | Default HTTP headers for REST operations (function objects only) | [optional] [default to null]
**timeout** | **Integer** | Default timeout in milliseconds (function objects only) | [optional] [default to null]
**retryPolicy** | [**Object_retryPolicy**](#modelsobject_retrypolicymd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsobjectclassmd"></a>

## ObjectClass
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsobject_retrypolicymd"></a>

## Object_retryPolicy
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**maxRetries** | **Integer** | Maximum number of retry attempts | [optional] [default to 3]
**backoffStrategy** | **String** | Strategy for calculating delay between retries | [optional] [default to exponential]
**retryableErrors** | **List** | List of error codes that trigger a retry | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelspropertymd"></a>

## Property
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | JavaScript-compatible property name | [default to null]
**description** | **String** | Optional property description | [optional] [default to null]
**required** | **Boolean** | Whether property is required | [optional] [default to false]
**multi** | **Boolean** | Whether property accepts multiple values | [optional] [default to false]
**primaryKey** | **Boolean** | Whether property is part of the primary key | [optional] [default to false]
**dataType** | **String** | Name of DataType entity | [default to null]
**format** | **String** | Format specification for data display/input (e.g., date format, number format) | [optional] [default to null]
**references** | [**Property_references**](#modelsproperty_referencesmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsproperty_referencesmd"></a>

## Property_references
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**schemaId** | **String** | Referenced schema ID | [optional] [default to null]
**propertyName** | **String** | Referenced property name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrestrequestmd"></a>

## RestRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**method** | **String** | HTTP method | [default to null]
**path** | **String** | The relative path to call | [default to null]
**headers** | **Map** | The headers to send with the request | [optional] [default to null]
**body** | **Map** | The body to send with the request | [optional] [default to null]
**params** | **Map** | The query parameters to send with the request | [optional] [default to null]
**timeout** | **Integer** | Request timeout in milliseconds | [optional] [default to null]
**retries** | **Integer** | Number of retries | [optional] [default to null]
**followRedirects** | **Boolean** | Follow HTTP redirects | [optional] [default to true]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrestresponsemd"></a>

## RestResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**status** | **Integer** | The HTTP status code | [default to null]
**headers** | **Map** | The headers returned by the server | [optional] [default to null]
**body** | **Map** | The body returned by the server | [optional] [default to null]
**timing** | [**RestResponse_timing**](#modelsrestresponse_timingmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrestresponse_timingmd"></a>

## RestResponse_timing
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**total** | **Integer** | Total request time in milliseconds | [optional] [default to null]
**dns** | **Integer** | DNS resolution time | [optional] [default to null]
**connect** | **Integer** | Connection establishment time | [optional] [default to null]
**ttfb** | **Integer** | Time to first byte | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsschemamd"></a>

## Schema
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique, immutable schema identifier | [default to null]
**dataTypes** | [**List**](#modelstypemd) | Array of DataType entities used by this schema | [default to null]
**properties** | [**List**](#modelspropertymd) | Array of property definitions | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssearchscopemd"></a>

## SearchScope
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateobjectrequestmd"></a>

## UpdateObjectRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Human-readable name | [optional] [default to null]
**description** | **String** | Optional description | [optional] [default to null]
**thumbnail** | **URI** | Public URL or data URI for visual representation | [optional] [default to null]
**tags** | **List** | Optional organizational tags | [optional] [default to null]
**collectionSchema** | **String** | Schema ID for collection elements (collection objects only) | [optional] [default to null]
**inputSchema** | **String** | Schema ID for function inputs (function objects only) | [optional] [default to null]
**outputSchema** | **String** | Schema ID for function outputs (function objects only) | [optional] [default to null]
**throws** | **Map** | Map of HTTP error codes to error Schema IDs (function objects only) | [optional] [default to null]
**documentSchema** | **String** | Schema ID for document structure (document objects only) | [optional] [default to null]
**mimeType** | **String** | MIME type of binary content (binary objects only) | [optional] [default to null]
**fileName** | **String** | Original filename (binary objects only) | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsvalidatefunctioninputrequestmd"></a>

## ValidateFunctionInputRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**input** | **Map** | Input to validate | [optional] [default to null]
**strict** | **Boolean** | Strict validation mode | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsvalidationresultmd"></a>

## ValidationResult
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**valid** | **Boolean** | Whether input is valid | [optional] [default to null]
**errors** | [**List**](#modelsvalidationresult_errorsmd) | List of validation errors | [optional] [default to null]
**warnings** | [**List**](#modelsvalidationresult_warningsmd) | List of validation warnings | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsvalidationresult_errorsmd"></a>

## ValidationResult_errors
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**path** | **String** | Path to invalid field | [optional] [default to null]
**message** | **String** | Error message | [optional] [default to null]
**code** | **String** | Error code | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsvalidationresult_warningsmd"></a>

## ValidationResult_warnings
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**path** | **String** | Path to field with warning | [optional] [default to null]
**message** | **String** | Warning message | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## connectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**server** | **String** | The URL for the Hub Server instance | [default to null]
**apiKey** | **String** | The API key to use to execute operations against the Hub Server | [optional] [default to null]
**targetId** | **UUID** | The target against which to execute | [default to null]
**orgId** | **UUID** | ID for the Org. Optional. If provided, it must match the Org of the Target. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelserrormodelbasemd"></a>

## errorModelBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**key** | **String** | A unique message key for this error. This is used both for discrimination and i18n | [default to null]
**template** | **String** | The error message. This may contain substitutions of the form &#x60;{key}&#x60; where &#x60;key&#x60; maps to a key in the &#x60;args&#x60; list. | [default to null]
**timestamp** | **Date** | Timestamp when this error was generated | [default to null]
**statusCode** | **Integer** | An HTTP status code to use for this error when transmitting it over HTTP | [default to 500]
**stack** | **String** | The stack trace, if available, for this error. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsillegalargumenterrormd"></a>

## illegalArgumentError
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**key** | **String** | A unique message key for this error. This is used both for discrimination and i18n | [default to null]
**template** | **String** | The error message. This may contain substitutions of the form &#x60;{key}&#x60; where &#x60;key&#x60; maps to a key in the &#x60;args&#x60; list. | [default to null]
**timestamp** | **Date** | Timestamp when this error was generated | [default to null]
**statusCode** | **Integer** | An HTTP status code to use for this error when transmitting it over HTTP | [default to 500]
**stack** | **String** | The stack trace, if available, for this error. | [optional] [default to null]
**msg** | **String** | The error message | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsillegalargumenterror_allofmd"></a>

## illegalArgumentError_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**msg** | **String** | The error message | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsnosuchobjecterrormd"></a>

## noSuchObjectError
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**key** | **String** | A unique message key for this error. This is used both for discrimination and i18n | [default to null]
**template** | **String** | The error message. This may contain substitutions of the form &#x60;{key}&#x60; where &#x60;key&#x60; maps to a key in the &#x60;args&#x60; list. | [default to null]
**timestamp** | **Date** | Timestamp when this error was generated | [default to null]
**statusCode** | **Integer** | An HTTP status code to use for this error when transmitting it over HTTP | [default to 500]
**stack** | **String** | The stack trace, if available, for this error. | [optional] [default to null]
**type** | **String** | The type of object which cannot be found | [default to null]
**id** | **String** | The ID of the object | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsnosuchobjecterror_allofmd"></a>

## noSuchObjectError_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**type** | **String** | The type of object which cannot be found | [default to null]
**id** | **String** | The ID of the object | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssortdirectionmd"></a>

## sortDirection
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstypemd"></a>

## type
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the type. This should be &#x60;lowerCamel&#x60; by convention. This value, combined with &#x60;formats&#x60;, will be valid OpenAPI &#x60;format&#x60; values to match this type. | [default to null]
**formats** | **List** | Additional values for the OpenAPI &#x60;format&#x60; field. | [optional] [default to null]
**jsonType** | **String** | The JSON type for this type. | [default to null]
**isEnum** | **Boolean** | Flag indicating whether or not this type is restricted to | [default to false]
**extendedInfoType** | **String** | The name of the type which represents extended info for this type.  This is only valid for &#x60;enum&#x60; types. This should be the name of the  type which provide extended info which can be keyed by the enum type&#39;s  value | [optional] [default to null]
**extendedInfoValues** | **String** | Optional path to the JSON file containing extended info values. This is only required if the convention of a pluralized version of the enum name will not locate the values (i.e. geoCountries.json for geoCountry) | [optional] [default to null]
**description** | **String** | A long description of the type. | [default to null]
**examples** | **List** | An array of valid example values for this type. | [default to null]
**pattern** | **String** | A regular expression used to validate the core type. | [optional] [default to null]
**path** | **String** | The relative path to the yml type, if it exists. | [optional] [default to null]
**htmlInput** | **String** | The HTML input type to use for the type | [default to text]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-interface-dataproducer

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*BinaryApi* | [**downloadBinary**](#downloadbinary) | **GET** /objects/{objectId}/download | Download binary content
*BinaryApi* | [**uploadBinaryContent**](#uploadbinarycontent) | **POST** /objects/{objectId}/upload | Upload binary content
*CollectionsApi* | [**addCollectionElement**](#addcollectionelement) | **POST** /objects/{objectId}/collection/elements | Add collection element
*CollectionsApi* | [**deleteCollectionElement**](#deletecollectionelement) | **DELETE** /objects/{objectId}/collection/elements/{elementKey} | Delete collection element
*CollectionsApi* | [**executeBulkOperations**](#executebulkoperations) | **POST** /objects/{objectId}/collection/bulk | Execute bulk operations on collection
*CollectionsApi* | [**getCollectionElement**](#getcollectionelement) | **GET** /objects/{objectId}/collection/elements/{elementKey} | Get collection element by key
*CollectionsApi* | [**getCollectionElements**](#getcollectionelements) | **GET** /objects/{objectId}/collection/elements | Get collection elements
*CollectionsApi* | [**searchCollectionElements**](#searchcollectionelements) | **GET** /objects/{objectId}/collection/search | Search collection elements
*CollectionsApi* | [**updateCollectionElement**](#updatecollectionelement) | **PUT** /objects/{objectId}/collection/elements/{elementKey} | Update collection element
*DocumentsApi* | [**getDocumentData**](#getdocumentdata) | **GET** /objects/{objectId}/document | Get document data
*DocumentsApi* | [**updateDocumentData**](#updatedocumentdata) | **PUT** /objects/{objectId}/document | Update document data
*FunctionsApi* | [**executeRestRequest**](#executerestrequest) | **POST** /objects/{objectId}/rest | Execute raw REST request
*FunctionsApi* | [**invokeFunction**](#invokefunction) | **POST** /objects/{objectId}/invoke | Invoke function
*FunctionsApi* | [**validateFunctionInput**](#validatefunctioninput) | **POST** /objects/{objectId}/validate | Validate function input
*ObjectsApi* | [**createChildObject**](#createchildobject) | **POST** /objects/{objectId}/children | Create child object
*ObjectsApi* | [**deleteObject**](#deleteobject) | **DELETE** /objects/{objectId}/children | Delete object
*ObjectsApi* | [**getChildren**](#getchildren) | **GET** /objects/{objectId}/children | Get child objects
*ObjectsApi* | [**getObject**](#getobject) | **GET** /objects/{objectId} | Get object by ID
*ObjectsApi* | [**getRootObject**](#getrootobject) | **GET** /objects | Get root object
*ObjectsApi* | [**objectSearch**](#objectsearch) | **GET** /objects/{objectId}/search | Search objects
*ObjectsApi* | [**searchChildObjects**](#searchchildobjects) | **GET** /objects/{objectId}/searchChildren | Search child objects with filtering and property selection
*ObjectsApi* | [**updateObject**](#updateobject) | **PUT** /objects/{objectId}/children | Update object
*SchemasApi* | [**getSchema**](#getschema) | **GET** /schemas/{schemaId} | Get schema by ID


<a name="documentation-for-models"></a>
## Documentation for Models

 - [BulkOperation](#modelsbulkoperationmd)
 - [BulkOperationRequest](#modelsbulkoperationrequestmd)
 - [BulkOperationResponse](#modelsbulkoperationresponsemd)
 - [BulkOperationResponse_results](#modelsbulkoperationresponse_resultsmd)
 - [BulkOperationResponse_summary](#modelsbulkoperationresponse_summarymd)
 - [CreateObjectRequest](#modelscreateobjectrequestmd)
 - [EnhancedFunctionResponse](#modelsenhancedfunctionresponsemd)
 - [EnhancedFunctionResponse_context](#modelsenhancedfunctionresponse_contextmd)
 - [EnhancedFunctionResponse_links](#modelsenhancedfunctionresponse_linksmd)
 - [EnhancedFunctionResponse_links_related](#modelsenhancedfunctionresponse_links_relatedmd)
 - [Object](#modelsobjectmd)
 - [ObjectClass](#modelsobjectclassmd)
 - [Object_retryPolicy](#modelsobject_retrypolicymd)
 - [Property](#modelspropertymd)
 - [Property_references](#modelsproperty_referencesmd)
 - [RestRequest](#modelsrestrequestmd)
 - [RestResponse](#modelsrestresponsemd)
 - [RestResponse_timing](#modelsrestresponse_timingmd)
 - [Schema](#modelsschemamd)
 - [SearchScope](#modelssearchscopemd)
 - [UpdateObjectRequest](#modelsupdateobjectrequestmd)
 - [ValidateFunctionInputRequest](#modelsvalidatefunctioninputrequestmd)
 - [ValidationResult](#modelsvalidationresultmd)
 - [ValidationResult_errors](#modelsvalidationresult_errorsmd)
 - [ValidationResult_warnings](#modelsvalidationresult_warningsmd)
 - [connectionProfile](#modelsconnectionprofilemd)
 - [errorModelBase](#modelserrormodelbasemd)
 - [illegalArgumentError](#modelsillegalargumenterrormd)
 - [illegalArgumentError_allOf](#modelsillegalargumenterror_allofmd)
 - [noSuchObjectError](#modelsnosuchobjecterrormd)
 - [noSuchObjectError_allOf](#modelsnosuchobjecterror_allofmd)
 - [sortDirection](#modelssortdirectionmd)
 - [type](#modelstypemd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
