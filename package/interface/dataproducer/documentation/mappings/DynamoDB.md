# DynamoDB API Mapping to Dynamic Data Producer Interface

## Overview

This document describes how AWS DynamoDB and protocol-compatible databases (ScyllaDB, Amazon DynamoDB Local) map to the Dynamic Data Producer Interface, enabling unified access to NoSQL key-value and document databases through the generic object model. The interface is implemented via a translation layer that converts generic operations into DynamoDB API calls (GetItem, PutItem, Query, Scan, etc.).

**Protocol**: DynamoDB HTTP API (JSON over HTTPS)
**Compatible Implementations**: AWS DynamoDB, ScyllaDB Alternator, DynamoDB Local
**API Version**: DynamoDB API 2012-08-10

## Conceptual Mapping

### DynamoDB Database → Object Model

```
DynamoDB Account
├── /                                  → Root Container (DynamoDB Service)
│   ├── tables/                       → Container Object (Table List)
│   │   ├── Users                     → Collection Object (Table)
│   │   │   ├── item_user123          → Collection Element (Item)
│   │   │   ├── item_user456          → Collection Element (Item)
│   │   │   └── item_user789          → Collection Element (Item)
│   │   ├── Orders                    → Collection Object (Table)
│   │   │   ├── item_order001         → Collection Element (Item)
│   │   │   └── item_order002         → Collection Element (Item)
│   │   └── Products                  → Collection Object (Table)
│   ├── indexes/                      → Container Object (GSI/LSI Metadata)
│   │   ├── Users_EmailIndex          → Document Object (GSI definition)
│   │   └── Orders_StatusIndex        → Document Object (GSI definition)
│   └── operations/                   → Container Object (DynamoDB Operations)
│       ├── batchGetItems             → Function Object (BatchGetItem)
│       ├── batchWriteItems           → Function Object (BatchWriteItem)
│       ├── transactWriteItems        → Function Object (TransactWriteItems)
│       ├── transactGetItems          → Function Object (TransactGetItems)
│       └── queryWithIndex            → Function Object (Query with GSI)
```

## Detailed Object Mappings

### 1. DynamoDB Service → Root Container
```yaml
Object:
  id: "/"
  name: "DynamoDB"
  objectClass: ["container"]
  description: "DynamoDB service endpoint"
  tags: ["dynamodb", "nosql", "database"]
  metadata:
    endpoint: "https://dynamodb.us-east-1.amazonaws.com"
    region: "us-east-1"
    apiVersion: "2012-08-10"
    provider: "aws"  # or "scylladb", "local"
    accountId: "123456789012"
```

### 2. DynamoDB Table → Collection Object
```yaml
Object:
  id: "table_users"
  name: "Users"
  objectClass: ["collection"]
  description: "User accounts table"
  path: ["/", "tables", "Users"]
  collectionSchema: "dynamodb_users_schema"
  collectionSize: 15847  # Approximate item count
  tags: ["table", "users", "master-data"]
  created: "2024-01-15T10:30:00.000Z"
  metadata:
    tableName: "Users"
    tableStatus: "ACTIVE"
    tableArn: "arn:aws:dynamodb:us-east-1:123456789012:table/Users"
    # Key schema
    partitionKey: "userId"
    partitionKeyType: "S"  # String
    sortKey: null
    # Capacity
    billingMode: "PAY_PER_REQUEST"  # or "PROVISIONED"
    readCapacityUnits: null
    writeCapacityUnits: null
    # Secondary indexes
    globalSecondaryIndexes: ["EmailIndex", "StatusIndex"]
    localSecondaryIndexes: []
    # Additional metadata
    itemCount: 15847
    tableSizeBytes: 2458624
    streamEnabled: true
    streamViewType: "NEW_AND_OLD_IMAGES"

Schema:
  id: "dynamodb_users_schema"
  properties:
    - name: "userId"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Partition key (HASH)"
    - name: "email"
      dataType: "email"
      required: true
      description: "User email address"
    - name: "username"
      dataType: "string"
      required: true
    - name: "createdAt"
      dataType: "date-time"
      description: "Account creation timestamp"
    - name: "status"
      dataType: "string"
      description: "Account status: active, inactive, suspended"
    - name: "profile"
      dataType: "object"
      description: "Nested profile object"
    - name: "tags"
      dataType: "array"
      multi: true
      description: "User tags"
```

### 3. DynamoDB Table with Composite Key → Collection Object
```yaml
Object:
  id: "table_orders"
  name: "Orders"
  objectClass: ["collection"]
  description: "Customer orders table"
  path: ["/", "tables", "Orders"]
  collectionSchema: "dynamodb_orders_schema"
  tags: ["table", "orders", "transactional"]
  metadata:
    tableName: "Orders"
    partitionKey: "customerId"
    partitionKeyType: "S"
    sortKey: "orderDate"
    sortKeyType: "S"  # ISO 8601 date string

Schema:
  id: "dynamodb_orders_schema"
  properties:
    - name: "customerId"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Partition key (HASH)"
    - name: "orderDate"
      dataType: "date-time"
      primaryKey: true
      required: true
      description: "Sort key (RANGE)"
    - name: "orderId"
      dataType: "string"
      required: true
      description: "Unique order identifier"
    - name: "totalAmount"
      dataType: "decimal"
      description: "Order total"
    - name: "status"
      dataType: "string"
      description: "Order status"
    - name: "items"
      dataType: "array"
      multi: true
      description: "Order line items"
```

### 4. Global Secondary Index → Document Object
```yaml
Object:
  id: "gsi_users_email"
  name: "EmailIndex"
  objectClass: ["document"]
  description: "GSI for querying users by email"
  path: ["/", "indexes", "Users_EmailIndex"]
  documentSchema: "dynamodb_gsi_schema"
  tags: ["index", "gsi"]
  metadata:
    tableName: "Users"
    indexName: "EmailIndex"
    indexStatus: "ACTIVE"
    indexArn: "arn:aws:dynamodb:us-east-1:123456789012:table/Users/index/EmailIndex"
    projection: "ALL"  # or "KEYS_ONLY", "INCLUDE"

Schema:
  id: "dynamodb_gsi_schema"
  properties:
    - name: "indexName"
      dataType: "string"
      required: true
    - name: "partitionKey"
      dataType: "string"
      required: true
    - name: "sortKey"
      dataType: "string"
    - name: "projection"
      dataType: "string"
      description: "Projection type: ALL, KEYS_ONLY, INCLUDE"
    - name: "projectedAttributes"
      dataType: "array"
      multi: true

# Document Data:
Document Data:
{
  "indexName": "EmailIndex",
  "partitionKey": "email",
  "sortKey": null,
  "projection": "ALL",
  "projectedAttributes": null,
  "readCapacityUnits": null,
  "writeCapacityUnits": null,
  "itemCount": 15847,
  "indexSizeBytes": 2458624
}
```

### 5. Batch Write Operation → Function Object
```yaml
Object:
  id: "func_batch_write"
  name: "batchWriteItems"
  objectClass: ["function"]
  description: "Write multiple items across tables in batch"
  inputSchema: "dynamodb_batch_write_input"
  outputSchema: "dynamodb_batch_write_output"
  tags: ["batch", "write", "performance"]

Schema (Input):
  id: "dynamodb_batch_write_input"
  properties:
    - name: "requestItems"
      dataType: "object"
      required: true
      description: "Map of table name to write requests"

Schema (Output):
  id: "dynamodb_batch_write_output"
  properties:
    - name: "unprocessedItems"
      dataType: "object"
      description: "Items that failed to process"
    - name: "itemCollectionMetrics"
      dataType: "object"
      description: "Metrics about the operation"
```

### 6. Query Operation → Function Object
```yaml
Object:
  id: "func_query_orders"
  name: "queryOrdersByCustomer"
  objectClass: ["function"]
  description: "Query orders for a specific customer"
  inputSchema: "dynamodb_query_input"
  outputSchema: "dynamodb_query_output"
  tags: ["query", "orders"]

Schema (Input):
  id: "dynamodb_query_input"
  properties:
    - name: "partitionKeyValue"
      dataType: "string"
      required: true
      description: "Partition key value to query"
    - name: "sortKeyCondition"
      dataType: "string"
      description: "Sort key condition expression"
    - name: "filterExpression"
      dataType: "string"
      description: "Additional filter expression"
    - name: "indexName"
      dataType: "string"
      description: "GSI/LSI name (optional)"
    - name: "limit"
      dataType: "integer"
      description: "Maximum items to return"
    - name: "scanIndexForward"
      dataType: "boolean"
      default: true
      description: "Sort order (true=ascending, false=descending)"

Schema (Output):
  id: "dynamodb_query_output"
  properties:
    - name: "items"
      dataType: "array"
      multi: true
      description: "Matching items"
    - name: "count"
      dataType: "integer"
      description: "Number of items returned"
    - name: "scannedCount"
      dataType: "integer"
      description: "Number of items examined"
    - name: "lastEvaluatedKey"
      dataType: "object"
      description: "Pagination token"
```

## API Usage Examples with DynamoDB Translation

### Table Management

**List All Tables:**
```http
GET /objects/tables/children
# Translates to: DynamoDB ListTables API call

# Response:
[
  {
    "id": "table_users",
    "name": "Users",
    "objectClass": ["collection"],
    "collectionSize": 15847
  },
  {
    "id": "table_orders",
    "name": "Orders",
    "objectClass": ["collection"],
    "collectionSize": 8423
  }
]
```

**Get Table Metadata:**
```http
GET /objects/table_users
# Translates to: DynamoDB DescribeTable

# Response includes table schema, capacity, indexes, etc.
```

### Item Operations (Single Table)

**Get Item by Key:**
```http
GET /objects/table_users/collection/elements/user123
# Translates to: DynamoDB GetItem
# Key: { "userId": { "S": "user123" } }

# Response:
{
  "userId": "user123",
  "email": "john@example.com",
  "username": "johndoe",
  "createdAt": "2024-01-15T10:30:00.000Z",
  "status": "active",
  "profile": {
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+1-555-123-4567"
  },
  "tags": ["premium", "verified"]
}
```

**Get Item with Composite Key:**
```http
GET /objects/table_orders/collection/elements/customer123?sortKey=2025-10-24T10:30:00.000Z
# Translates to: DynamoDB GetItem
# Key: { "customerId": { "S": "customer123" }, "orderDate": { "S": "2025-10-24T10:30:00.000Z" } }

# Response: Order item with both partition and sort key
```

**Create/Update Item:**
```http
POST /objects/table_users/collection/elements
Content-Type: application/json

{
  "userId": "user999",
  "email": "alice@example.com",
  "username": "alice",
  "status": "active",
  "createdAt": "2025-10-24T10:45:00.000Z"
}

# Translates to: DynamoDB PutItem
```

**Update Specific Attributes:**
```http
PUT /objects/table_users/collection/elements/user123
Content-Type: application/json

{
  "status": "inactive",
  "profile.phone": "+1-555-999-8888"
}

# Translates to: DynamoDB UpdateItem with UpdateExpression
# SET status = :status, profile.phone = :phone
```

**Delete Item:**
```http
DELETE /objects/table_users/collection/elements/user123
# Translates to: DynamoDB DeleteItem
```

### Query Operations

**Query All Items in Partition:**
```http
GET /objects/table_orders/collection/search?filter=(customerId=customer123)
# Translates to: DynamoDB Query
# KeyConditionExpression: "customerId = :customerId"

# Returns all orders for customer123
```

**Query with Sort Key Condition:**
```http
GET /objects/table_orders/collection/search?filter=(&(customerId=customer123)(orderDate>=2025-10-01))
# Translates to: DynamoDB Query
# KeyConditionExpression: "customerId = :cid AND orderDate >= :date"

# Returns orders for customer123 from October 2025 onwards
```

**Query with Filter Expression:**
```http
GET /objects/table_orders/collection/search?filter=(&(customerId=customer123)(status=shipped))
# Translates to: DynamoDB Query
# KeyConditionExpression: "customerId = :cid"
# FilterExpression: "status = :status"

# Returns shipped orders for customer123
```

**Query Using GSI:**
```http
POST /objects/func_query_orders/invoke
{
  "indexName": "StatusIndex",
  "partitionKeyValue": "pending",
  "limit": 100
}

# Translates to: DynamoDB Query with IndexName parameter
# Queries orders by status using GSI
```

### Scan Operations

**Scan Entire Table:**
```http
GET /objects/table_users/collection/elements?scanMode=true
# Translates to: DynamoDB Scan

# Warning: Expensive operation on large tables
```

**Scan with Filter:**
```http
GET /objects/table_users/collection/search?filter=(status=active)&scanMode=true
# Translates to: DynamoDB Scan
# FilterExpression: "status = :status"

# Scans entire table, returns only active users
```

**Parallel Scan (Segmented):**
```http
GET /objects/table_users/collection/elements?scanMode=true&segment=0&totalSegments=4
# Translates to: DynamoDB Scan with Segment and TotalSegments
# Useful for parallel processing of large tables
```

### Batch Operations

**Batch Get Items:**
```http
POST /objects/func_batch_get/invoke
{
  "requestItems": {
    "Users": {
      "keys": [
        { "userId": "user123" },
        { "userId": "user456" },
        { "userId": "user789" }
      ]
    }
  }
}

# Translates to: DynamoDB BatchGetItem
# Retrieves up to 100 items from one or more tables
```

**Batch Write Items:**
```http
POST /objects/func_batch_write/invoke
{
  "requestItems": {
    "Users": [
      {
        "operation": "put",
        "item": { "userId": "user111", "email": "user111@example.com" }
      },
      {
        "operation": "delete",
        "key": { "userId": "user222" }
      }
    ]
  }
}

# Translates to: DynamoDB BatchWriteItem
# Up to 25 put/delete operations across tables
```

### Transactional Operations

**Transactional Write:**
```http
POST /objects/func_transact_write/invoke
{
  "transactItems": [
    {
      "operation": "put",
      "tableName": "Users",
      "item": { "userId": "user999", "email": "user999@example.com" }
    },
    {
      "operation": "update",
      "tableName": "Orders",
      "key": { "customerId": "customer123", "orderDate": "2025-10-24T10:00:00.000Z" },
      "updateExpression": "SET status = :status",
      "expressionAttributeValues": { ":status": "completed" }
    }
  ]
}

# Translates to: DynamoDB TransactWriteItems
# All-or-nothing transaction across multiple tables/items
```

**Transactional Read:**
```http
POST /objects/func_transact_get/invoke
{
  "transactItems": [
    {
      "tableName": "Users",
      "key": { "userId": "user123" }
    },
    {
      "tableName": "Orders",
      "key": { "customerId": "customer123", "orderDate": "2025-10-24T10:00:00.000Z" }
    }
  ]
}

# Translates to: DynamoDB TransactGetItems
# Consistent read across multiple items
```

## Translation Layer Capabilities

### 1. **DynamoDB API Mapping**

| Generic Operation | DynamoDB API | Notes |
|-------------------|--------------|-------|
| `children()` (tables) | `ListTables` | List all tables |
| `collectionElements()` | `Scan` | Full table scan (expensive) |
| `getCollectionElement()` | `GetItem` | Read single item by key |
| `addElement()` | `PutItem` | Insert/replace item |
| `updateElement()` | `UpdateItem` | Update specific attributes |
| `deleteElement()` | `DeleteItem` | Delete item by key |
| `collectionSearch()` | `Query` or `Scan` | Query by key, scan with filter |
| Batch get | `BatchGetItem` | Up to 100 items |
| Batch write | `BatchWriteItem` | Up to 25 operations |
| Transactions | `TransactWriteItems` / `TransactGetItems` | ACID transactions |

### 2. **Key Schema Handling**
- **Simple Key**: Partition key only (HASH)
- **Composite Key**: Partition key + sort key (HASH + RANGE)
- **Primary Key Mapping**: Extract from object path or query parameters
- **Key Condition Expressions**: Translate RFC4515 filters to DynamoDB conditions

### 3. **RFC4515 Filter to DynamoDB Expression Translation**

| RFC4515 Filter | DynamoDB Expression | Notes |
|----------------|---------------------|-------|
| `(userId=user123)` | `userId = :val` | Equality |
| `(status>=active)` | `status >= :val` | Comparison |
| `(&(a=1)(b=2))` | `a = :a AND b = :b` | Logical AND |
| `(\|(a=1)(b=2))` | `a = :a OR b = :b` | Logical OR |
| `(!(status=inactive))` | `NOT status = :val` | Negation |
| `(email=*@example.com)` | `contains(email, :domain)` | Pattern matching |

### 4. **Data Type Mapping**

| DynamoDB Type | Core DataType | Notes |
|---------------|--------------|-------|
| S (String) | `string` | Text values |
| N (Number) | `decimal` or `integer` | Stored as string, parsed as number |
| BOOL | `boolean` | True/false |
| B (Binary) | `byte` | Base64-encoded |
| SS (String Set) | `array` (string[]) | Unordered set |
| NS (Number Set) | `array` (decimal[]) | Unordered set |
| BS (Binary Set) | `array` (byte[]) | Unordered set |
| L (List) | `array` | Ordered list |
| M (Map) | `object` | Nested document |
| NULL | `null` | Null value |

## Implementation Considerations

### 1. **Authentication and Authorization**

**AWS IAM:**
```yaml
metadata:
  authentication: "AWS_IAM"
  accessKeyId: "AKIAIOSFODNN7EXAMPLE"
  region: "us-east-1"
  # IAM policy determines table-level permissions
```

**IAM Roles:**
```yaml
metadata:
  authentication: "AWS_IAM_ROLE"
  roleArn: "arn:aws:iam::123456789012:role/DynamoDBAccess"
```

### 2. **Capacity Management**

**Provisioned Mode:**
```yaml
metadata:
  billingMode: "PROVISIONED"
  readCapacityUnits: 100
  writeCapacityUnits: 50
  autoScalingEnabled: true
```

**On-Demand Mode:**
```yaml
metadata:
  billingMode: "PAY_PER_REQUEST"
  # No capacity units specified
```

### 3. **Performance Optimization**
- **Batch Operations**: Use BatchGetItem/BatchWriteItem for bulk operations
- **Query vs Scan**: Always prefer Query with key conditions over Scan
- **Pagination**: Handle LastEvaluatedKey for result sets > 1MB
- **Parallel Scan**: Use segmented scans for large tables
- **GSI/LSI**: Leverage secondary indexes for alternate access patterns
- **Projection**: Request only needed attributes to reduce data transfer

### 4. **Error Handling**

| DynamoDB Error | HTTP Status | Error Type |
|----------------|-------------|------------|
| `ResourceNotFoundException` | 404 | `noSuchObjectError` |
| `ConditionalCheckFailedException` | 409 | `ConflictError` |
| `ProvisionedThroughputExceededException` | 429 | `RateLimitExceededError` |
| `ValidationException` | 400 | `InvalidInputError` |
| `ItemCollectionSizeLimitExceededException` | 413 | `payload_too_large` |
| `TransactionCanceledException` | 409 | `transaction_conflict` |
| `AccessDeniedException` | 403 | `ForbiddenError` |

### 5. **Advanced Features**

**DynamoDB Streams:**
```yaml
Object:
  id: "stream_users"
  name: "UserStream"
  objectClass: ["function"]
  description: "Process changes to Users table"
  metadata:
    streamArn: "arn:aws:dynamodb:us-east-1:123456789012:table/Users/stream/..."
    streamViewType: "NEW_AND_OLD_IMAGES"
```

**Time to Live (TTL):**
```yaml
metadata:
  ttlEnabled: true
  ttlAttributeName: "expirationTime"
  # Items automatically deleted after expiration
```

**Point-in-Time Recovery:**
```yaml
metadata:
  pointInTimeRecoveryEnabled: true
  earliestRestorableDateTime: "2025-10-17T10:00:00.000Z"
  latestRestorableDateTime: "2025-10-24T10:00:00.000Z"
```

## ScyllaDB Alternator Compatibility

### 1. **Protocol Compatibility**
ScyllaDB Alternator implements DynamoDB HTTP API, enabling transparent usage:

```yaml
Object:
  id: "/"
  metadata:
    endpoint: "http://scylla-node:8000"
    provider: "scylladb"
    apiVersion: "2012-08-10"
    # Same API calls work with ScyllaDB
```

### 2. **Performance Characteristics**
- **Lower Latency**: ScyllaDB often provides sub-millisecond p99 latencies
- **Higher Throughput**: Better performance for write-heavy workloads
- **Predictable Performance**: C++ implementation vs Java (DynamoDB)

### 3. **Feature Differences**
- **Supported**: GetItem, PutItem, UpdateItem, DeleteItem, Query, Scan, BatchGetItem, BatchWriteItem
- **Limited**: Transactions, Streams, Global Tables
- **Not Supported**: TTL, PITR, DAX caching

## Limitations and Workarounds

### 1. **Item Size Limit**
- **Issue**: Maximum item size 400KB
- **Workaround**: Store large data in S3, reference via URL in item

### 2. **Query Limitations**
- **Issue**: Query requires partition key, sort key is optional
- **Workaround**: Use GSI to enable queries on non-key attributes

### 3. **No Server-Side Joins**
- **Issue**: DynamoDB doesn't support SQL-style joins
- **Workaround**: Denormalize data or implement application-level joins

### 4. **Eventual Consistency**
- **Issue**: Default reads are eventually consistent
- **Workaround**: Use ConsistentRead=true for strongly consistent reads (2x cost)

## Security Best Practices

1. **IAM Policies**: Use least-privilege IAM policies for table access
2. **Encryption**: Enable encryption at rest (AWS KMS) and in transit (TLS)
3. **VPC Endpoints**: Use VPC endpoints to avoid internet traversal
4. **Audit Logging**: Enable CloudTrail for DynamoDB API calls
5. **Fine-Grained Access**: Use condition expressions in IAM for row-level security
6. **Backup Strategy**: Enable point-in-time recovery and on-demand backups

## Conclusion

The Dynamic Data Producer Interface provides effective abstraction for DynamoDB and protocol-compatible databases through a translation layer that converts generic operations into DynamoDB API calls.

**Key Benefits:**
- **Unified Interface**: Same API patterns work across DynamoDB, SQL databases, and other sources
- **Protocol Compatibility**: Works with AWS DynamoDB, ScyllaDB Alternator, DynamoDB Local
- **NoSQL Flexibility**: Supports key-value and document data models
- **Performance**: Batch operations, transactions, and query optimization
- **Cloud Integration**: Native AWS IAM authentication and service integration

**Implementation Strategy:**
The translation layer handles DynamoDB API complexity, key schema resolution, and expression translation while providing a consistent REST-like interface. Performance optimizations include batch operations, query preference over scan, and GSI utilization.

**Perfect Use Cases:**
- Multi-cloud NoSQL database access (AWS + ScyllaDB)
- Unified data access across SQL and NoSQL databases
- DynamoDB table migration and synchronization
- Hybrid cloud data integration
- Development/testing with DynamoDB Local → production AWS DynamoDB
