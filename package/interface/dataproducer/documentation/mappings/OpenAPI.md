# OpenAPI/Swagger Mapping to Dynamic Data Producer Interface

## Overview

This document describes how OpenAPI/Swagger specifications map to the Dynamic Data Producer Interface, enabling unified access to REST APIs through the generic object model. The interface is implemented via a translation layer that converts generic operations (RFC4515 filters, object hierarchy navigation) into appropriate HTTP requests with OpenAPI-defined schemas and parameters.

**Supersedes**: `../rest` module by providing a higher-level abstraction with OpenAPI schema integration while maintaining low-level REST execution capabilities.

## Conceptual Mapping

### OpenAPI Specification → Object Model

```
OpenAPI API Root
├── Paths                              → Container Object (API Endpoints)
│   ├── /users                        → Collection Object (Resource Collection)
│   │   ├── GET /users                → Function Object (List Operation)
│   │   ├── POST /users               → Function Object (Create Operation)
│   │   └── GET /users/{id}           → Function Object (Get by ID)
│   ├── /posts                        → Collection Object (Resource Collection)
│   │   ├── GET /posts                → Function Object (List Operation)
│   │   ├── POST /posts               → Function Object (Create Operation)
│   │   ├── PUT /posts/{id}           → Function Object (Update Operation)
│   │   └── DELETE /posts/{id}        → Function Object (Delete Operation)
│   └── /auth                         → Container Object (Auth Endpoints)
│       ├── POST /auth/login          → Function Object (Login)
│       └── POST /auth/refresh        → Function Object (Token Refresh)
├── Components                        → Container Object (Reusable Definitions)
│   ├── Schemas                       → Container Object (Data Models)
│   │   ├── User                      → Document Object (Schema Definition)
│   │   ├── Post                      → Document Object (Schema Definition)
│   │   └── Error                     → Document Object (Schema Definition)
│   ├── Parameters                    → Container Object (Parameter Definitions)
│   └── Responses                     → Container Object (Response Definitions)
└── Operations                        → Container Object (All Operations)
    ├── listUsers                     → Function Object (Operation)
    ├── createUser                    → Function Object (Operation)
    └── getUserById                   → Function Object (Operation)
```

## Enhanced Object Mappings with REST Integration

### 1. OpenAPI Root → Root Container
```yaml
Object:
  id: "/"
  name: "REST API"
  objectClass: ["container"]
  description: "OpenAPI-defined REST API"
  tags: ["openapi", "rest", "api"]
```

### 2. Resource Path → Collection Object
```yaml
Object:
  id: "resource_users"
  name: "users"
  objectClass: ["collection"]
  description: "User resource collection from /users endpoint"
  path: ["Paths", "users"]
  collectionSchema: "user_schema"
  tags: ["resource", "users", "crud"]
```

### 3. HTTP Operation → Enhanced Function Object
```yaml
Object:
  id: "func_create_user"
  name: "createUser"
  objectClass: ["function"]
  description: "POST /users - Create new user"
  inputSchema: "create_user_input_schema"
  outputSchema: "user_schema"
  # Enhanced REST properties
  httpMethod: "POST"
  httpPath: "/users"
  httpHeaders:
    "Content-Type": "application/json"
    "Accept": "application/json"
  timeout: 30000
  retryPolicy:
    maxRetries: 3
    backoffStrategy: "exponential"
    retryableErrors: ["timeout", "5xx", "network_error"]
  throws:
    "400": "validation_error_schema"
    "409": "conflict_error_schema"
  tags: ["operation", "post", "create", "users"]
```

## Enhanced API Usage Examples

### 1. High-Level OpenAPI Operations

**Create User (Schema-Validated):**
```http
POST /objects/func_create_user/invoke
{
  "username": "johndoe",
  "email": "john@example.com",
  "profile": {
    "firstName": "John",
    "lastName": "Doe"
  }
}

# Response with enhanced metadata
{
  "data": {
    "id": 123,
    "username": "johndoe",
    "email": "john@example.com",
    "createdAt": "2024-01-20T10:30:00Z"
  },
  "context": {
    "requestId": "req_abc123",
    "timestamp": "2024-01-20T10:30:00Z",
    "duration": 245,
    "retryCount": 0,
    "cached": false
  },
  "links": {
    "self": "/objects/func_create_user/invoke",
    "related": [
      {"rel": "get_user", "href": "/objects/func_get_user/invoke?id=123"}
    ]
  }
}
```

### 2. Low-Level REST Access

**Direct REST Execution:**
```http
POST /objects/func_create_user/rest
{
  "method": "POST",
  "path": "/users",
  "headers": {
    "Content-Type": "application/json",
    "X-Custom-Header": "custom-value"
  },
  "body": {
    "username": "johndoe",
    "email": "john@example.com"
  },
  "timeout": 15000,
  "retries": 2
}

# Response with HTTP details
{
  "status": 201,
  "headers": {
    "Location": "/users/123",
    "ETag": "abc123",
    "Content-Type": "application/json"
  },
  "body": {
    "id": 123,
    "username": "johndoe",
    "email": "john@example.com"
  },
  "timing": {
    "total": 245,
    "dns": 12,
    "connect": 45,
    "ttfb": 188
  }
}
```

### 3. Input Validation

**Pre-execution Validation:**
```http
POST /objects/func_create_user/validate
{
  "input": {
    "username": "jo",  # Too short
    "email": "invalid-email",  # Invalid format
    "age": "twenty"  # Wrong type
  },
  "strict": true
}

# Validation response
{
  "valid": false,
  "errors": [
    {
      "path": "username",
      "message": "Must be at least 3 characters long",
      "code": "min_length"
    },
    {
      "path": "email",
      "message": "Must be valid email format",
      "code": "format_error"
    },
    {
      "path": "age",
      "message": "Must be integer, got string",
      "code": "type_error"
    }
  ],
  "warnings": []
}
```

### 4. Bulk Operations

**Batch User Creation:**
```http
POST /objects/resource_users/collection/bulk
{
  "operations": [
    {
      "action": "create",
      "data": {"username": "user1", "email": "user1@example.com"}
    },
    {
      "action": "create", 
      "data": {"username": "user2", "email": "user2@example.com"}
    },
    {
      "action": "update",
      "key": "123",
      "data": {"email": "updated@example.com"}
    }
  ],
  "atomic": false,
  "continueOnError": true
}

# Bulk response
{
  "results": [
    {"success": true, "data": {"id": 456, "username": "user1"}},
    {"success": true, "data": {"id": 457, "username": "user2"}},
    {"success": false, "error": {"code": "not_found", "message": "User 123 not found"}}
  ],
  "summary": {
    "total": 3,
    "succeeded": 2,
    "failed": 1
  }
}
```

## REST Module Integration Benefits

### 1. **Unified Interface Hierarchy**
- **High-level**: Schema-validated operations via function objects
- **Mid-level**: Collection CRUD operations with type safety
- **Low-level**: Raw REST execution for advanced use cases

### 2. **Enhanced Error Handling**
```yaml
# Standardized error response with HTTP context
{
  "code": "validation_error",
  "message": "Request validation failed",
  "httpStatus": 400,
  "httpStatusText": "Bad Request",
  "headers": {"Content-Type": "application/json"},
  "details": {
    "field": "email",
    "constraint": "format"
  },
  "retryable": false
}
```

### 3. **Request/Response Lifecycle**
- **Pre-execution**: Input validation against OpenAPI schemas
- **Execution**: HTTP request with retry logic and timeout handling
- **Post-execution**: Response validation and transformation
- **Metadata**: Timing, retry count, cache status tracking

### 4. **Legacy Compatibility**
```yaml
# Backward compatibility function for ../rest module users
Object:
  id: "func_legacy_rest"
  name: "executeRestRequest"
  objectClass: ["function"]
  description: "Legacy REST execution (compatibility)"
  inputSchema: "rest_request_schema"
  outputSchema: "rest_response_schema"
  deprecated: true
  tags: ["legacy", "compatibility"]
```

## Implementation Strategy

### 1. **Function Object Enhancement**
- Add HTTP-specific properties (method, path, headers, timeout, retry)
- Support both schema-driven and raw REST execution modes
- Maintain backward compatibility with existing function patterns

### 2. **Translation Layer Architecture**
```javascript
class OpenAPIDataProducerImpl {
  async invokeFunction(functionId, input, options = {}) {
    const func = this.getFunctionMetadata(functionId);
    
    if (options.mode === 'rest' || !func.inputSchema) {
      // Raw REST execution
      return this.executeRawREST(func, input);
    } else {
      // Schema-validated execution
      return this.executeValidatedOperation(func, input);
    }
  }
  
  async executeValidatedOperation(func, input) {
    // 1. Validate input against schema
    const validation = await this.validateInput(input, func.inputSchema);
    if (!validation.valid) {
      throw new ValidationError(validation.errors);
    }
    
    // 2. Transform to REST request
    const restRequest = this.buildRestRequest(func, input);
    
    // 3. Execute with retry logic
    const response = await this.executeWithRetry(restRequest, func.retryPolicy);
    
    // 4. Transform response
    return this.transformResponse(response, func.outputSchema);
  }
}
```

### 3. **Schema Integration**
- OpenAPI schemas automatically become object schemas
- Input/output validation leverages OpenAPI type definitions
- Parameter mapping handles query, path, header, and body parameters

### 4. **Performance Optimizations**
- Connection pooling for HTTP clients
- Response caching based on HTTP cache headers
- Batch operation support for multiple REST calls
- Request deduplication for identical operations

## Migration Path from REST Module

### 1. **Immediate Benefits**
- Drop-in replacement for `/rest` endpoint functionality
- Enhanced error handling and retry logic
- Built-in input/output validation

### 2. **Progressive Enhancement**
- Start with raw REST execution mode
- Add OpenAPI schemas incrementally
- Migrate to collection-based operations
- Implement bulk operations for efficiency

### 3. **Compatibility Bridge**
```http
# Legacy REST module usage
POST /rest
{
  "method": "POST",
  "path": "/users",
  "body": {...}
}

# Equivalent dataproducer usage
POST /objects/func_legacy_rest/invoke
{
  "method": "POST", 
  "path": "/users",
  "body": {...}
}

# Enhanced dataproducer usage
POST /objects/func_create_user/invoke
{
  "username": "johndoe",
  "email": "john@example.com"
}
```

## Conclusion

The Dynamic Data Producer Interface provides a comprehensive evolution of the REST module, enabling:

**Key Enhancements over REST Module:**
- **Type Safety**: OpenAPI schema validation for all operations
- **Error Handling**: Standardized error responses with retry logic
- **Performance**: Connection pooling, caching, and bulk operations
- **Developer Experience**: Auto-generated documentation and validation
- **Unified Interface**: Consistent patterns across all data source types

**Implementation Strategy:**
The enhanced interface supersedes the REST module while maintaining compatibility, providing a migration path for existing users and significant value additions for new implementations.

**Perfect Use Cases:**
- API gateway and aggregation platforms
- Multi-service data integration with type safety
- REST API testing and validation tools
- Microservices orchestration with error handling
- Legacy system integration with modern tooling