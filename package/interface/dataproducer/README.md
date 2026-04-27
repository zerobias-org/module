# Dynamic Data Producer Interface

A unified interface for browsing and acting on heterogeneous data sources —
filesystems, databases, REST APIs, directories, low-code platforms — through a
single object/operation model. The full specification lives in
[`documentation/`](documentation/); this README is the consumer entry point.

## What It Does

DataProducer abstracts five capability classes — **container**, **collection**,
**function**, **document**, **binary** — into one interface. An implementation
declares which classes its objects support, and the interface provides the
matching operations: tree browsing, filtered/paged queries, function
invocation, document/binary I/O, and schema introspection.

## When to Use It

- You need to surface a non-API data source (database, directory, filesystem,
  proprietary store) through a uniform interface.
- You're building a UI or pipeline that should work the same way over many
  data sources.
- You need composable schemas — referenced by ID, fetched on demand, cached
  for the session.

## Installation

```bash
npm install @zerobias-org/module-interface-dataproducer
```

Peer dependencies (provided by your platform):

- `@zerobias-org/types-core` — core data type catalog and error model
- `@zerobias-org/types-core-js` — runtime helpers (`PagedResults`,
  `PropertySelector`, `CoreType`)

## Quick Start

A minimal flow: get the root, browse children, query a collection.

```typescript
import { ObjectsApi, CollectionsApi, SchemasApi } from '@zerobias-org/module-interface-dataproducer/api';

// 1. Start at the root
const root = await new ObjectsApi(config).getRootObject();
//    → { id: '/', name: '/', objectClass: ['container'], ... }

// 2. Browse the tree
const children = await new ObjectsApi(config).getChildren(root.id, { pageSize: 50 });

// 3. Pick a collection and fetch its schema
const customers = children.find(c => c.objectClass.includes('collection'));
const schema    = await new SchemasApi(config).getSchema(customers.collectionSchema);
//    schema.id === customers.collectionSchema, e.g. 'schema:table:mydb.public.customers'

// 4. Query elements with an RFC4515 filter
const rows = await new CollectionsApi(config).searchCollectionElements(
  customers.id,
  { filter: '(&(active=true)(country=US))', pageSize: 100 }
);
```

## Core Concepts (one-liners)

| Concept                                                                | One-liner                                                                                  |
|------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Object** ([Concepts](documentation/Concepts.md))                     | A node in the browseable tree, identified by `id`, capable of one or more `objectClass` operations. |
| **ObjectClass** ([Concepts](documentation/Concepts.md))                | One of `container`, `collection`, `function`, `document`, `binary` — gates which operations are valid. |
| **Schema** ([SchemaIds](documentation/SchemaIds.md))                   | Structure for an object's content. Always referenced by an opaque ID, fetched via `getSchema`. |
| **Schema ID**  ([SchemaIds](documentation/SchemaIds.md))               | Canonical form `schema:{type}:{catalog}.{schema}.{name}[:{direction}]`. Discriminator-prefixed to avoid ambiguity. |
| **Property / DataType** ([CoreDataTypes](documentation/CoreDataTypes.md)) | Each schema property names a `dataType` from `@zerobias-org/types-core`.                  |
| **Filter** ([FilterSyntax](documentation/FilterSyntax.md))             | RFC4515 filter expressions for structured queries; LDAP-native, translated for other backends via `lite-filter`. |
| **Errors** ([Errors](documentation/Errors.md))                         | Standard platform errors plus per-function `throws` schemas.                                |

## Common Use Cases

- **Browse a database** — connect a SQL implementation; databases become
  containers, schemas containers, tables collections, functions Function
  objects with declared input/output schemas. See
  [SQL mapping](documentation/mappings/SQL.md).
- **Treat a directory as a tree** — LDAP/AD entries map directly to the object
  model with native RFC4515 filtering. See
  [LDAP mapping](documentation/mappings/LDAP.md).
- **Surface a REST API** — an implementation that pairs DataProducer with
  HTTP-specific routing on a sub-interface (`HttpModule`). See
  [OpenAPI mapping](documentation/mappings/OpenAPI.md).
- **Build a generic UI** — a single component over `getRootObject` /
  `getChildren` / `getCollectionElements` works for every implementation.

## Reference

| Topic                                                                 | Document                                                |
|-----------------------------------------------------------------------|---------------------------------------------------------|
| Object model, classes, operations matrix, lifecycle                   | [`documentation/Concepts.md`](documentation/Concepts.md) |
| Schema ID format and `getSchema` contract                             | [`documentation/SchemaIds.md`](documentation/SchemaIds.md) |
| Choosing the right `dataType`                                          | [`documentation/CoreDataTypes.md`](documentation/CoreDataTypes.md) |
| RFC4515 filters and per-backend translation                            | [`documentation/FilterSyntax.md`](documentation/FilterSyntax.md) |
| Error taxonomy and the `throws` mechanism                              | [`documentation/Errors.md`](documentation/Errors.md) |
| Per-source mapping guides (SQL, LDAP, GraphQL, …)                      | [`documentation/mappings/`](documentation/mappings/) |
| Design rationale and per-source assessment                             | [`documentation/Analysis.md`](documentation/Analysis.md) |

The `api.yml` OpenAPI spec is the on-the-wire contract. The documents above
are normative for what an implementation must provide; `api.yml` reflects them.

## Pagination and Property Selection

Every list/search operation supports both pagination modes and dot-notation
property projection:

```typescript
// Cursor-based — pageToken comes from the previous response header
collectionsApi.getCollectionElements(id, { pageToken, pageSize: 100 });

// Offset-based — pageNumber, 1-indexed
collectionsApi.getCollectionElements(id, { pageNumber: 2, pageSize: 100 });

// Project only certain fields (fuzzy-matched: 'email' matches 'e_mail', 'emailAddress', …)
collectionsApi.searchCollectionElements(id, {
  properties: ['id', 'name', 'contact.email'],
});
```

See [`PagedResults`](https://github.com/zerobias/types-core-js) and
`PropertySelector` in `@zerobias-org/types-core-js`.

## FAQ

**Why are schemas referenced by ID instead of inlined?** Browsing a database
with 100 tables would otherwise return ~500KB of duplicate schema bodies.
Schema IDs are 60 bytes each; the full body is fetched on demand and cached.
See [SchemaIds.md](documentation/SchemaIds.md).

**Why RFC4515 instead of, say, JSON-based filter trees?** It's compact, has a
spec, and a single shared parser (`@zerobias-org/util-lite-filter`) means
every implementation interprets it the same way. See
[FilterSyntax.md](documentation/FilterSyntax.md).

**Why can't I find HTTP method/path on a Function object?** The base
DataProducer is transport-agnostic. HTTP-specific properties belong on a
sub-interface (e.g. `HttpModule`) that an implementation can adopt alongside
DataProducer. See [OpenAPI.md](documentation/mappings/OpenAPI.md).

**How do I add a new data source?** Implement the operations relevant to your
source's classes and start with the matching mapping document in
`documentation/mappings/`. If your source category isn't covered, follow the
template in [`documentation/mappings/README.md`](documentation/mappings/README.md).

---

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
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
