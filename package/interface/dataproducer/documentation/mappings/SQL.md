# PostgreSQL RDBMS Mapping to Dynamic Data Producer Interface

## Overview

This document describes how PostgreSQL database concepts map to the Dynamic Data Producer Interface, enabling unified access to relational database structures through the generic object model. The interface is implemented via a translation layer that converts generic operations (RFC4515 filters, object hierarchy navigation) into appropriate SQL operations (WHERE clauses, JOINs, etc.).

## Conceptual Mapping

### PostgreSQL Hierarchy → Object Model

```
PostgreSQL Server (Root)
├── Database 1                    → Container Object
│   ├── Schema "public"          → Container Object  
│   │   ├── Table "customers"    → Collection Object
│   │   ├── View "active_users"  → Collection Object (read-only)
│   │   ├── Function "calc_tax"  → Function Object
│   │   └── Enum "status_type"   → Document Object
│   └── Schema "analytics"       → Container Object
│       └── Table "reports"      → Collection Object
└── Database 2                   → Container Object
    └── ...
```

## Detailed Object Mappings

### 1. PostgreSQL Server (Root Container)
```yaml
Object:
  id: "/"
  name: "PostgreSQL Server"
  objectClass: ["container"]
  description: "PostgreSQL database server instance"
  # Server metadata
  tags: ["postgresql", "rdbms"]
```

### 2. Database → Container Object
```yaml
Object:
  id: "db_ecommerce"
  name: "ecommerce"
  objectClass: ["container"]
  description: "E-commerce application database"
  # Database-specific metadata
  tags: ["database"]
```

### 3. Schema → Container Object
```yaml
Object:
  id: "schema_public"
  name: "public"
  objectClass: ["container"]
  description: "Default PostgreSQL schema"
  path: ["ecommerce", "public"]
  tags: ["schema"]
```

### 4. Table → Collection Object
```yaml
Object:
  id: "table_customers"
  name: "customers"
  objectClass: ["collection"]
  description: "Customer information table"
  path: ["ecommerce", "public", "customers"]
  collectionSchema: "customers_schema"
  collectionSize: 15000
  tags: ["table", "master-data"]

Schema:
  id: "customers_schema"
  properties:
    - name: "customer_id"
      dataType: "integer"
      primaryKey: true
      required: true
      description: "Primary key"
    - name: "email"
      dataType: "string"
      format: "email"
      required: true
    - name: "created_at"
      dataType: "timestamp"
      format: "ISO8601"
    - name: "status"
      dataType: "enum"
      references:
        schemaId: "status_enum_schema"
```

### 5. View → Collection Object (Read-only)
```yaml
Object:
  id: "view_active_customers"
  name: "active_customers"
  objectClass: ["collection"]
  description: "View of currently active customers"
  collectionSchema: "active_customers_schema"
  # Views are read-only - no primary key properties
  tags: ["view", "derived"]
```

### 6. Function → Function Object
```yaml
Object:
  id: "func_calculate_tax"
  name: "calculate_tax"
  objectClass: ["function"]
  description: "Calculate tax for given amount and region"
  inputSchema: "calc_tax_input_schema"
  outputSchema: "calc_tax_output_schema"
  throws:
    "400": "invalid_input_schema"
  tags: ["function", "business-logic"]

# Input Schema
Schema:
  id: "calc_tax_input_schema"
  properties:
    - name: "amount"
      dataType: "decimal"
      required: true
    - name: "region_code"
      dataType: "string"
      required: true

# Output Schema  
Schema:
  id: "calc_tax_output_schema"
  properties:
    - name: "tax_amount"
      dataType: "decimal"
    - name: "total_amount"
      dataType: "decimal"
```

### 7. Enum Type → Document Object
```yaml
Object:
  id: "enum_status_type"
  name: "status_type"
  objectClass: ["document"]
  description: "Customer status enumeration"
  documentSchema: "enum_schema"
  tags: ["enum", "type-definition"]

# Enum values stored as document
Document Data:
{
  "type": "enum",
  "values": ["active", "inactive", "pending", "suspended"],
  "default": "pending"
}
```


## API Usage Examples

### Database Operations

**List Databases:**
```http
GET /objects/children
```

**Create Database:**
```http
POST /objects/children
{
  "name": "analytics_db",
  "objectClass": ["container"],
  "description": "Analytics database"
}
```

### Table Operations

**List Tables in Schema:**
```http
GET /objects/schema_public/children?type=collection
```

**List Only Tables (not views):**
```http
GET /objects/schema_public/children?tags=table
```

**List Only Views:**
```http
GET /objects/schema_public/children?tags=view
```

**Get Table Rows (with pagination):**
```http
GET /objects/table_customers/collection/elements?pageSize=50&pageNumber=1
```

**Query Table with Filter (RFC4515 → SQL WHERE):**
```http
GET /objects/table_customers/collection/search?filter=(status=active)
# Translates to: SELECT * FROM customers WHERE status = 'active'
```

**Complex Filter (RFC4515 → SQL WHERE):**
```http
GET /objects/table_customers/collection/search?filter=(&(status=active)(created_at>=2024-01-01))
# Translates to: SELECT * FROM customers WHERE status = 'active' AND created_at >= '2024-01-01'
```

**Get Specific Row:**
```http
GET /objects/table_customers/collection/elements/12345
```

**Insert Row:**
```http
POST /objects/table_customers/collection/elements
{
  "email": "john@example.com",
  "status": "active",
  "created_at": "2024-01-15T10:30:00Z"
}
```

**Update Row:**
```http
PUT /objects/table_customers/collection/elements/12345
{
  "status": "inactive"
}
```

### Function Operations

**Execute Function:**
```http
POST /objects/func_calculate_tax/invoke
{
  "amount": 100.00,
  "region_code": "CA"
}
```

## Translation Layer Capabilities

### 1. **RFC4515 Filter → SQL WHERE Translation**
- **Capability**: Standard RFC4515 predicates map directly to SQL WHERE clauses
- **Examples**:
  - `(name=John)` → `WHERE name = 'John'`
  - `(&(age>=18)(status=active))` → `WHERE age >= 18 AND status = 'active'`
  - `(|(dept=sales)(dept=marketing))` → `WHERE dept IN ('sales', 'marketing')`

### 2. **Object Hierarchy → SQL Schema Navigation**
- **Capability**: Container/Collection hierarchy maps to database/schema/table structure
- **Implementation**: Translation layer maintains metadata about database structure

### 3. **Tag-based Filtering**
- **Capability**: Filter children by tags enables semantic grouping
- **Examples**:
  - `?tags=table` returns only tables, not views or functions
  - `?tags=master-data` returns core business entities
  - `?tags=temporary` returns temporary or staging tables

## Implementation Considerations

### 1. **Complex Query Patterns**
- **Challenge**: Multi-table JOINs, subqueries, window functions
- **Solution**: Use Function objects to encapsulate complex SQL patterns
- **Example**: Create a function object for "customer_orders_with_totals" that performs the necessary JOINs

### 2. **Transaction Management**
- **Approach**: Implementation layer handles transaction boundaries
- **Strategy**: Single API calls are atomic; multi-call operations rely on application-level coordination

### 3. **Schema Evolution**
- **Approach**: DDL operations (CREATE, ALTER, DROP) handled outside the data access interface
- **Rationale**: Schema changes are administrative operations, not data access operations

### 4. **Performance Optimization**
- **Indexes**: Not exposed through the interface since they're implementation details
- **Query Plans**: Translation layer can optimize based on usage patterns
- **Caching**: Implementation can cache frequently accessed metadata and data

### 5. **Data Type Handling**
- **Strategy**: Use `format` field to capture PostgreSQL-specific type information
- **Examples**:
  - `dataType: "array", format: "integer[]"`
  - `dataType: "json", format: "jsonb"`
  - `dataType: "geometry", format: "point"`

## Recommended Extensions

### 1. **Query Function Objects**
Create standardized function objects for common SQL patterns:
```yaml
Object:
  id: "query_customer_orders"
  name: "customer_orders_join"
  objectClass: ["function"]
  description: "JOIN customers with orders"
  inputSchema: "customer_filter_schema"
  outputSchema: "customer_orders_schema"
```

### 2. **Relationship Metadata**
Extend Schema properties to include relationship information:
```yaml
Property:
  name: "customer_id"
  dataType: "integer"
  references:
    schemaId: "customers_schema"
    propertyName: "customer_id"
    relationshipType: "foreign_key"
```

### 3. **Constraint Objects**
Model constraints as Document objects:
```yaml
Object:
  id: "constraint_customers_email_unique"
  name: "customers_email_unique"
  objectClass: ["document"]
  documentSchema: "constraint_schema"
```

## Conclusion

The Dynamic Data Producer Interface provides an effective abstraction for PostgreSQL databases through a translation layer that converts generic operations into appropriate SQL. The interface successfully abstracts core database operations into a consistent, RESTful API that integrates seamlessly with other data sources in the Auditmation platform.

**Key Benefits:**
- **Unified Interface**: Same API patterns work across databases, spreadsheets, and files
- **Translation Layer**: RFC4515 filters translate naturally to SQL WHERE clauses
- **Tag-based Organization**: Semantic grouping enables filtering tables vs views vs functions
- **Schema Flexibility**: `format` field captures database-specific type information
- **Function Encapsulation**: Complex SQL patterns wrapped in reusable function objects

**Implementation Strategy:**
The translation layer handles the complexity of converting generic object operations into efficient SQL, while maintaining the abstraction that allows the same client code to work across multiple data source types. Performance optimizations, indexing, and query planning remain implementation details hidden from the interface consumers.