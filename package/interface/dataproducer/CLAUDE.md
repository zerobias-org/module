# Dynamic Data Producer Interface - Claude Instructions

## Module Overview

This module defines the **Dynamic Data Producer Interface** for the Zerobias platform, providing a generic data and browsing interface that supports:

- File and Folder Browsing
- File Content parsing  
- Database Access
- Function Calling (REST/Lambda/OpenAPI)
- Low-Code/No-Code apps (Spreadsheets, Zoho/Salesforce, GRC, etc)

**Supersedes**: `../fileproducer`, `../spreadsheet`, and `../rest` modules by providing a unified abstraction with enhanced functionality.

Built upon `@zerobias-org/types-core` data structures and definitions.

## Key Architecture Components

### Core Entities

1. **Object Interface** - Base unit forming tree-like browsing structure
   - Properties: `id`, `name`, `description`, `path`, `thumbnail`, `tags`, `created`, `modified`, `etag`, `versionId`, `objectClass`
   - Object Classes: `container`, `collection`, `function`, `document`, `binary`
   - Root object: `id` and `name` of '/' (mandatory)
   - Supports full CRUD operations with optimistic concurrency control
   - Enhanced function objects include HTTP properties: `httpMethod`, `httpPath`, `httpHeaders`, `timeout`, `retryPolicy`

2. **Schema Resource** - Metadata describing data relationships
   - Properties: `id`, `dataTypes`, `properties`
   - Used by Collection, Function, and Document objects

3. **Property Entity** - Schema property definitions
   - Properties: `name`, `description`, `required`, `multi`, `primaryKey`, `dataType`, `format`, `references`
   - Primary key properties enable CRUD operations on collection elements

### Object Class Operations

**READ Operations:**
- **Container**: `children()`, `objectSearch()`
- **Collection**: `collectionElements()`, `collectionSearch()`, `getCollectionElement()`
- **Function**: `invoke()`
- **Document**: `documentData()`
- **Binary**: `download()`

**WRITE Operations:**
- **Container**: `createChild()`, `updateObject()`, `deleteObject()`
- **Collection**: `addElement()`, `updateElement()`, `deleteElement()`, `bulkOperations()`
- **Document**: `updateDocument()`
- **Binary**: `uploadContent()`
- **Function**: `validate()`, `executeREST()` (for HTTP-enabled functions)

## Development Guidelines

### Working with Object Classes
- Each object can have zero or more types
- Operations only available for matching object classes
- Unsupported operations throw `UnsupportedOperationException`
- Always check `objectClass` array before invoking operations

### Schema Management
- Schemas define structure for Collections, Functions, and Documents
- Use `inputSchema`/`outputSchema` for Function objects
- Reference schemas by `id` in object properties
- Property `dataType` must reference existing DataType entities from `@zerobias-org/types-core`
- Primary key properties enable CRUD operations on collection elements
- Collections without primary key properties exclude mutable operations

### Using Core DataTypes from @zerobias-org/types-core

**IMPORTANT**: Always use defined DataType names from `@zerobias-org/types-core` instead of custom format strings.

**Finding Available DataTypes**:
- Complete list: `node_modules/@zerobias-org/types-core/data/types/types.json`
- Programmatic access: `CoreType.listTypes()` from `@zerobias-org/types-core-js`
- Common types documented in [README.md](./README.md#datatype-entities)

**Core Type Usage Rules**:

❌ **INCORRECT** - Don't use custom formats:
```yaml
properties:
  - name: "country"
    dataType: "string"
    format: "iso3166-alpha2"  # Custom format - DON'T DO THIS
  - name: "email"
    dataType: "string"
    format: "email"  # Should use email dataType directly
```

✅ **CORRECT** - Use defined core types:
```yaml
properties:
  - name: "country"
    dataType: "geoCountry"  # ISO 3166-1 alpha-2 country codes
    description: "Country code"
  - name: "email"
    dataType: "email"  # Validated email addresses
    description: "User email address"
  - name: "state"
    dataType: "geoSubdivision"  # ISO 3166-2 subdivision codes
    description: "State or province code"
  - name: "phone"
    dataType: "phoneNumber"  # International phone numbers
    description: "Contact phone number"
  - name: "website"
    dataType: "url"  # URLs and URIs
    description: "Website URL"
  - name: "amount"
    dataType: "decimal"  # For currency/precise calculations
    description: "Transaction amount"
  - name: "attachment"
    dataType: "byte"  # Base64-encoded binary data
    description: "File content"
  - name: "contentType"
    dataType: "mimeType"  # MIME type strings
    description: "Content MIME type"
```

**Common Core Types**:
- `email` - Email addresses with validation
- `phoneNumber` - International phone numbers
- `url` - URLs/URIs (format alias: `uri`)
- `geoCountry` - ISO 3166-1 alpha-2 country codes (US, DE, FR)
- `geoSubdivision` - ISO 3166-2 subdivisions (US-CA, DE-BE)
- `date` - ISO 8601 dates (YYYY-MM-DD)
- `date-time` - ISO 8601 timestamps (format aliases: `time`, `timestamp`)
- `decimal` - Decimal numbers for currency/precise math
- `byte` - Base64-encoded binary (format aliases: `b64`, `base64`)
- `mimeType` - MIME type identifiers
- `uuid` - UUID identifiers
- `ipAddress` - IPv4/IPv6 addresses
- `hostname` - DNS hostnames
- `duration` - ISO 8601 durations

**When Creating Mapping Documents**:
1. Check `types.json` for existing types before creating custom formats
2. Use specific types (e.g., `geoCountry`) over generic ones (e.g., `string`)
3. Add Core Types Reference section (see [SOAP.md](./documentation/mappings/SOAP.md) for template)
4. Include common types table in mapping introduction
5. Validate all examples use correct core type names

### Error Handling
- Missing objects return 404 (`noSuchObjectError` from @zerobias-org/types-core)
- Invalid schema references trigger `noSuchObjectError` 
- Circular reference detection is caller's responsibility
- Unsupported operations throw UnsupportedOperationException
- Function objects can define custom error schemas via `throws` property
- Function execution limits applied by underlying runtime components

### Implementation Requirements
- Root object ('/') is mandatory with `container` objectClass
- Object `id` is immutable primary key, `path` is documentary only
- Objects can have multiple parents (flexible hierarchy)
- Name collisions allowed but discouraged (names are informational)
- Default page size: 100 items
- Use RFC4515 syntax for structured filter expressions
- Binary downloads must support streaming/chunking for resumability
- Authentication/authorization inherited from underlying product connections

## Superseded Module Mappings

### FileProducer Module Replacement
- **File/Folder** → **Container/Binary Objects** with full CRUD operations
- **File Upload** → `POST /objects/{objectId}/upload`
- **File Download** → `GET /objects/{objectId}/download` 
- **File Browsing** → `GET /objects/{objectId}/children`
- **File Search** → `GET /objects/{objectId}/search`
- **File Metadata** → Object properties with `mimeType`, `fileName`, `size`

### Spreadsheet Module Replacement
- **Spreadsheet** → **Container Object** containing sheet objects
- **Sheet** → **Collection Object** with `collectionSchema` defining columns
- **Column Definitions** → **Schema Properties** with `dataType`, `format`, `primaryKey`
- **Row Access** → Collection element operations using schema-defined primary key
- **Cell Values** → Property values within collection elements
- **Examples**:
  - List rows: `GET /objects/{sheetId}/collection/elements`
  - Get row: `GET /objects/{sheetId}/collection/elements/{primaryKeyValue}`
  - Update row: `PUT /objects/{sheetId}/collection/elements/{primaryKeyValue}`
  - Search rows: `GET /objects/{sheetId}/collection/search?filter=...`

### REST Module Replacement
- **Raw REST Execution** → **Enhanced Function Objects** with HTTP properties and validation
- **Request/Response** → **Typed Operations** with OpenAPI schema integration
- **Error Handling** → **Standardized Errors** with retry logic and HTTP context
- **Examples**:
  - Legacy: `POST /rest` → `POST /objects/func_legacy_rest/invoke`
  - Enhanced: **Function Objects** with `httpMethod`, `httpPath`, input/output schemas
  - Low-level: `POST /objects/{functionId}/rest` for raw HTTP execution
  - Validation: `POST /objects/{functionId}/validate` for pre-execution checks
  - Bulk operations: `POST /objects/{collectionId}/collection/bulk`

## Schema Management Patterns

### Schema ID Generation

All DataProducer implementations must return **schema IDs** (not full schema objects) in object properties like `collectionSchema`, `inputSchema`, `outputSchema`, and `documentSchema`.

**Required Format:** `schema:{type}:{catalog}.{schema}.{name}[:{direction}]`

**Type Discriminators:**
- `table` - Database tables
- `view` - Database views
- `function` - Function input/output (requires `:input` or `:output` suffix)
- `type` - Custom types
- `enum` - Enumerated types
- `shared` - Shared/reusable schemas (not database-specific)

**Why Discriminators Are Critical:**

PostgreSQL and other databases allow naming conflicts:
```sql
-- All valid in the same schema:
CREATE TABLE public.address (...);
CREATE TYPE public.address AS (...);
CREATE VIEW public.address AS SELECT ...;
CREATE DOMAIN public.address AS VARCHAR(100);
```

Without discriminators, `schema:mydb.public.address` is ambiguous. With discriminators:
- `schema:table:mydb.public.address` → Unambiguous table
- `schema:type:mydb.public.address` → Unambiguous type
- `schema:view:mydb.public.address` → Unambiguous view

**Examples:**

```java
// Collections (tables/views)
String schemaId = String.format("schema:%s:%s.%s.%s",
    objectType,      // "table" or "view"
    catalogName,     // "mydb"
    schemaName,      // "public"
    tableName        // "customers"
);
// Result: "schema:table:mydb.public.customers"

// Functions (input and output are separate schemas)
String inputSchemaId = String.format("schema:function:%s.%s.%s:input",
    catalogName, schemaName, functionName);
String outputSchemaId = String.format("schema:function:%s.%s.%s:output",
    catalogName, schemaName, functionName);
// Results: "schema:function:mydb.public.calculate_tax:input"
//          "schema:function:mydb.public.calculate_tax:output"

// Shared types (not database-specific)
String sharedSchemaId = "schema:shared:address";
```

### getSchema Operation Implementation

The `getSchema` operation must parse schema IDs and return full schema definitions on-demand.

**Key Requirements:**

1. **Parse discriminator and path components**
2. **Support all schema ID formats**
3. **Implement caching with TTL** (recommended: 5 minutes)
4. **Handle URL encoding** (schema IDs may be URL-encoded in requests)
5. **Return proper error responses** for invalid/unknown schema IDs

**Java Implementation Example:**

```java
public class SqlModuleFacade {
    // Cache with 5-minute TTL
    private static final long SCHEMA_CACHE_TTL_SECONDS = 300;
    private final Map<String, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    private static class CachedSchema {
        final String schemaJson;
        final Instant expiresAt;

        CachedSchema(String schemaJson) {
            this.schemaJson = schemaJson;
            this.expiresAt = Instant.now().plusSeconds(SCHEMA_CACHE_TTL_SECONDS);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String getSchema(String schemaId) {
        try {
            // URL decode if needed
            String decodedId = URLDecoder.decode(schemaId, "UTF-8");

            // Check cache
            CachedSchema cached = schemaCache.get(decodedId);
            if (cached != null && !cached.isExpired()) {
                return cached.schemaJson;
            }

            String schemaJson;

            // Parse schema ID format: schema:{type}:{catalog}.{schema}.{name}[:{direction}]
            if (decodedId.startsWith("schema:")) {
                String remainder = decodedId.substring(7); // Remove "schema:" prefix
                int firstColon = remainder.indexOf(':');

                String objectType = remainder.substring(0, firstColon);
                String pathAndDirection = remainder.substring(firstColon + 1);

                switch (objectType) {
                    case "table":
                    case "view":
                        String[] tableParts = pathAndDirection.split("\\.");
                        schemaJson = introspector.getTableSchemaDefinition(
                            tableParts[0],  // catalogName
                            tableParts[1],  // schemaName
                            tableParts[2]   // tableName
                        );
                        break;

                    case "function":
                        int lastColon = pathAndDirection.lastIndexOf(':');
                        String functionPath = pathAndDirection.substring(0, lastColon);
                        String direction = pathAndDirection.substring(lastColon + 1);

                        String[] functionParts = functionPath.split("\\.");
                        schemaJson = introspector.getFunctionSchemaDefinition(
                            functionParts[0],  // catalogName
                            functionParts[1],  // schemaName
                            functionParts[2],  // functionName
                            direction          // "input" or "output"
                        );
                        break;

                    default:
                        return errorToJson("invalid_schema_id",
                            "Unknown schema type: " + objectType);
                }
            } else {
                return errorToJson("invalid_schema_id",
                    "Schema ID must start with 'schema:'");
            }

            // Cache result
            schemaCache.put(decodedId, new CachedSchema(schemaJson));

            return schemaJson;
        } catch (Exception e) {
            logger.error("Failed to get schema: {}", schemaId, e);
            return errorToJson("database_error", e.getMessage());
        }
    }
}
```

**TypeScript Client Usage:**

```typescript
// UI components receive schema IDs in object metadata
const object = await dataProducerClient.getObjectsApi()
  .getObject("/db:mydb/schema:public/table:customers");

console.log(object.collectionSchema);
// Output: "schema:table:mydb.public.customers"

// Detect schema ID tokens (strings starting with "schema:")
if (typeof object.collectionSchema === 'string' &&
    object.collectionSchema.startsWith('schema:')) {

  // Fetch full schema on-demand via getSchema
  const schema = await dataProducerClient.getSchemasApi()
    .getSchema(object.collectionSchema);

  console.log(schema);
  // Output: { id: "schema:table:mydb.public.customers",
  //           properties: [...], dataTypes: [...] }
}
```

### Schema Composition Patterns

**Shared Schema Pattern:**

```json
{
  "id": "schema:shared:address",
  "properties": [
    { "name": "street", "dataType": "string" },
    { "name": "city", "dataType": "string" },
    { "name": "state", "dataType": "geoSubdivision" },
    { "name": "postalCode", "dataType": "string" }
  ]
}
```

**Referencing Shared Schema (Composition):**

```json
{
  "name": "billingAddress",
  "dataType": "string",
  "description": "Billing address",
  "references": {
    "schemaId": "schema:shared:address"
  }
}
```

**Referencing Collection (Foreign Key):**

```json
{
  "name": "customerId",
  "dataType": "integer",
  "description": "Customer ID",
  "references": {
    "schemaId": "schema:table:mydb.public.customers",
    "propertyName": "id"
  }
}
```

**Key Difference:** Presence of `propertyName` indicates foreign key relationship; absence indicates embedded composition.

### Performance Considerations

**Why Schema IDs Matter:**

| Scenario | Full Schemas Inline | Schema IDs with getSchema |
|----------|---------------------|--------------------------|
| List 100 tables | 500KB+ payload | 15KB payload |
| Browse hierarchy | All schemas materialized | Zero schemas until needed |
| View table data | Schema already in payload | Fetch schema once, cache |
| Related tables | Duplicate schemas | Shared cached schemas |
| Network calls | 1 (large) | Multiple (small + on-demand) |

**Caching Strategy:**

1. **Server-side cache** (5-minute TTL):
   - Reduces database introspection overhead
   - Handles concurrent requests efficiently
   - Balances freshness vs performance

2. **Client-side cache** (session-based):
   - Store fetched schemas in memory
   - Reuse across multiple data queries
   - Clear on connection/scope change

**Example Client Caching:**

```typescript
class SchemaCache {
  private cache = new Map<string, Schema>();

  async getSchema(schemaId: string, fetcher: (id: string) => Promise<Schema>): Promise<Schema> {
    if (this.cache.has(schemaId)) {
      return this.cache.get(schemaId)!;
    }

    const schema = await fetcher(schemaId);
    this.cache.set(schemaId, schema);
    return schema;
  }

  clear() {
    this.cache.clear();
  }
}

// Usage in React component
const schemaCache = useRef(new SchemaCache());

useEffect(() => {
  // Clear cache when connection/scope changes
  return () => schemaCache.current.clear();
}, [connectionId, scopeId]);

// Fetch with caching
const schema = await schemaCache.current.getSchema(
  object.collectionSchema,
  (id) => dataProducerClient.getSchemasApi().getSchema(id)
);
```

### Implementation Checklist

When implementing DataProducer interface with schema support:

**Schema ID Generation:**
- ✅ Return schema IDs (not objects) in `collectionSchema`, `inputSchema`, `outputSchema`, `documentSchema`
- ✅ Use type discriminators: `table`, `view`, `function`, `type`, `enum`
- ✅ Include all three path components: `catalog.schema.name`
- ✅ Add direction suffix for functions: `:input` or `:output`
- ✅ Handle special characters in names (escaping/encoding)

**getSchema Operation:**
- ✅ Parse schema ID format correctly
- ✅ Handle URL-encoded schema IDs
- ✅ Support all schema types (table, view, function, etc.)
- ✅ Implement caching with TTL (recommended: 5 minutes)
- ✅ Return proper error responses for invalid IDs
- ✅ Generate separate schemas for function input/output

**Schema Content:**
- ✅ Include `id` field matching the schema ID
- ✅ Populate `dataTypes` array with CoreType definitions
- ✅ Define `properties` array with all columns/fields
- ✅ Mark `primaryKey: true` on primary key properties
- ✅ Set `required: true` on non-nullable properties
- ✅ Use `references` for foreign keys and composition
- ✅ Include property descriptions when available

**Testing:**
- ✅ Verify schema IDs are strings, not objects
- ✅ Test schema ID parsing with various formats
- ✅ Validate schema content against Schema component in api.yml
- ✅ Test caching behavior (hits and misses)
- ✅ Verify foreign key vs composition patterns
- ✅ Test with special characters in names

## Data Source Integration Examples

The Dynamic Data Producer Interface provides unified access across diverse data sources. See detailed mapping documentation for implementation guidance:

### Database Integration
- **[SQL/PostgreSQL Mapping](./documentation/mappings/SQL.md)**: Relational databases, tables as collections, SQL query translation
  - Tables → Collection Objects with primary key-based CRUD
  - Views → Read-only Collection Objects  
  - Functions → Function Objects with input/output schemas
  - Schemas/Databases → Container Objects in hierarchy

### Directory Services
- **[LDAP Mapping](./documentation/mappings/LDAP.md)**: Active Directory, LDAP directories, user/group management
  - LDAP Entries → Document Objects (users, computers, etc.)
  - Organizational Units → Container Objects
  - Groups → Collection Objects (members as elements)
  - Perfect RFC4515 filter compatibility (native LDAP)

### API Integration  
- **[GraphQL Mapping](./documentation/mappings/GraphQL.md)**: GraphQL APIs, dynamic schema composition, field selection
  - GraphQL Types → Document Objects (schema definitions)
  - Collection Fields → Collection Objects with dynamic schemas
  - Mutations/Queries → Function Objects with projection support
  - Schema introspection enables dynamic object discovery

- **[OpenAPI/REST Mapping](./documentation/mappings/OpenAPI.md)**: REST APIs, OpenAPI specifications, HTTP operations
  - REST Resources → Collection Objects with CRUD operations
  - HTTP Operations → Function Objects with validation and retry logic
  - OpenAPI Schemas → Automatic object hierarchy generation
  - Supersedes `../rest` module with enhanced functionality

### Infrastructure Services
- **[DNS Mapping](./documentation/mappings/DNS.md)**: DNS zones, records, queries, zone management
  - DNS Zones → Container Objects (domain hierarchy)
  - Record Types → Collection Objects (A, MX, TXT records)
  - DNS Queries → Function Objects (forward/reverse lookup)
  - Zone transfers and dynamic updates supported

### Key Integration Benefits
- **Unified Filtering**: RFC4515 syntax works across all data sources (native in LDAP, translated elsewhere)
- **Consistent CRUD**: Same API patterns for all data manipulation
- **Schema Reuse**: Shared DataType definitions across multiple sources
- **Cross-Source Queries**: Function objects can aggregate data from multiple sources
- **Error Handling**: Standardized error responses with source-specific context

## Build Process and Known Issues

### Redocly CLI $ref Dereferencing Limitation

**Problem**: Redocly CLI's `bundle` command does not dereference `$ref` references when they appear inside arrays, even with the `--dereferenced` flag. This affects the `x-product-infos` array in `api.yml`:

```yaml
info:
  x-product-infos:
    - $ref: './node_modules/@zerobias-org/product-zerobias-generic-dataproducer/catalog.yml#/Product'
```

**Why This Matters**: The bundled output file (`module-interface-dataproducer.yml`) needs fully dereferenced content for downstream tooling and publishing workflows. Tools consuming this file expect inline values, not external references.

**Solution**: Custom post-processing script (`dereference-product-infos.sh`) that:
1. Detects `$ref` in `x-product-infos` array elements
2. Dynamically parses the file path and JSON pointer from the `$ref` value
3. Uses `yq` to load the referenced file and extract the value
4. Replaces the `$ref` with the actual dereferenced content

**Script Implementation**:
```bash
#!/bin/bash
# Script to dynamically dereference $ref in x-product-infos array

INPUT_FILE="${1:-full.yml}"
OUTPUT_FILE="${2:-full.yml}"

# Check if x-product-infos[0] contains a $ref
REF_VALUE=$(yq eval '.info.x-product-infos[0].$ref' "$INPUT_FILE")

if [ "$REF_VALUE" != "null" ] && [ -n "$REF_VALUE" ]; then
    # Parse the $ref value to extract file path and JSON pointer
    FILE_PATH=$(echo "$REF_VALUE" | sed 's/#.*//')
    JSON_POINTER=$(echo "$REF_VALUE" | sed 's/.*#//')

    # Convert JSON pointer to yq path (e.g., /Product -> .Product)
    YQ_PATH=$(echo "$JSON_POINTER" | sed 's/^\//\./')

    # Dereference using yq eval-all
    yq eval-all "select(fileIndex == 0).info.x-product-infos[0] = select(fileIndex == 1)${YQ_PATH} | select(fileIndex == 0)" \
        "$INPUT_FILE" "$FILE_PATH" > "$OUTPUT_FILE.tmp"

    mv "$OUTPUT_FILE.tmp" "$OUTPUT_FILE"
fi
```

**Integration**: The script is called in the `generate:inflate` npm script after Redocly bundling:

```json
"generate:inflate": "npx @redocly/cli@1.34.5 bundle full.yml --output full.yml && ./dereference-product-infos.sh full.yml full.yml && cp full.yml module-interface-dataproducer.yml"
```

**Related Issues**:
- [Redocly CLI #1012](https://github.com/Redocly/redocly-cli/issues/1012) - Array item references removed with `--remove-unused-components`
- [Redocly CLI #1602](https://github.com/Redocly/redocly-cli/issues/1602) - Discriminator mapping values not dereferenced
- Using `--dereferenced` flag alone causes errors in downstream tooling and doesn't solve array dereferencing

**Alternative Approaches Considered**:
- ❌ `--dereferenced` flag: Doesn't work for arrays, causes downstream errors
- ❌ `allOf` workaround: Only addresses `--remove-unused-components`, not general dereferencing
- ❌ `swagger-cli`: Deprecated, maintainer recommends Redocly CLI instead
- ✅ Post-processing with `yq`: Dynamic, reliable, handles any `$ref` format

## Architecture Integration

This interface module follows the **flat, two-level structure** from the root CLAUDE.md:
- Delegate planning to specialized agents
- Use appropriate validation gates for API specs and implementations
- Store temporary work in `.claude/.localmemory/{workflow}-{module}/`

### Key Delegations for this Module
- API specification design → `api-researcher`
- Schema validation → `api-reviewer`
- Type generation → `build-validator`
- Interface implementation → Direct coordination
- Testing → `ut-reviewer` + `it-reviewer`
- OpenAPI integration → Implementation-specific configuration
- REST module migration → Backward compatibility function objects
- Data source mapping → Translation layer implementations per mapping documents