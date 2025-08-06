# Core Error Usage Guide

## Overview

All modules **MUST** use core errors from `@auditmation/types-core-js` instead of generic TypeScript `Error` class. This guide provides the correct constructor patterns for each error type.

## 🚨 CRITICAL RULES

1. **NEVER use generic `Error` class** - Always use core errors
2. **Import from `@auditmation/types-core-js`** - All core errors are available from the main package
3. **Follow exact constructor signatures** - Each error has specific required and optional parameters
4. **Timestamp is usually optional** - Most errors auto-generate timestamps if not provided

## Common Core Errors and Constructor Patterns

### Authentication Errors

#### InvalidCredentialsError
Use for authentication failures (401 errors):
```typescript
import { InvalidCredentialsError } from '@auditmation/types-core-js';

// Basic usage (timestamp auto-generated)
throw new InvalidCredentialsError();

// With custom timestamp
throw new InvalidCredentialsError(new Date());
```

#### UnauthorizedError  
Use for authorization failures (403 errors):
```typescript
import { UnauthorizedError } from '@auditmation/types-core-js';

// Basic usage (timestamp auto-generated)
throw new UnauthorizedError();

// With custom timestamp
throw new UnauthorizedError(new Date());
```

### Input/Validation Errors

#### InvalidInputError
Use for bad request data (400/422 errors):
```typescript
import { InvalidInputError } from '@auditmation/types-core-js';

// Required: type and value
throw new InvalidInputError('username', 'invalid@value');

// With examples
throw new InvalidInputError('email', 'notanemail', ['user@example.com', 'admin@domain.com']);

// With custom timestamp
throw new InvalidInputError('id', '123abc', [], new Date());
```

### Resource Errors

#### NoSuchObjectError
Use for resource not found (404 errors):
```typescript
import { NoSuchObjectError } from '@auditmation/types-core-js';

// Required: type and id
throw new NoSuchObjectError('user', 'john_doe');
throw new NoSuchObjectError('organization', '12345');

// With custom timestamp
throw new NoSuchObjectError('repository', 'my-repo', new Date());
```

### Rate Limiting Errors

#### RateLimitExceededError
Use for rate limit exceeded (429 errors):
```typescript
import { RateLimitExceededError } from '@auditmation/types-core-js';

// Basic usage
throw new RateLimitExceededError();

// With call details
throw new RateLimitExceededError(new Date(), 100, '1 hour');

// With custom timestamp only
throw new RateLimitExceededError(new Date());
```

### Generic Errors

#### UnexpectedError
Use for server errors (500+ errors) and unexpected cases:
```typescript
import { UnexpectedError } from '@auditmation/types-core-js';

// Required: message
throw new UnexpectedError('Database connection failed');

// With custom status code
throw new UnexpectedError('Service unavailable', 503);

// With custom status code and timestamp
throw new UnexpectedError('Internal error', 500, new Date());
```

## HTTP Status Code to Core Error Mapping

| Status Code | Core Error | Constructor Example |
|-------------|------------|-------------------|
| 400 | `InvalidInputError` | `new InvalidInputError('field', 'value')` |
| 401 | `InvalidCredentialsError` | `new InvalidCredentialsError()` |
| 403 | `UnauthorizedError` | `new UnauthorizedError()` |
| 404 | `NoSuchObjectError` | `new NoSuchObjectError('type', 'id')` |
| 422 | `InvalidInputError` | `new InvalidInputError('field', 'value')` |
| 429 | `RateLimitExceededError` | `new RateLimitExceededError()` |
| 500+ | `UnexpectedError` | `new UnexpectedError('message')` |

## Error Handling Pattern for HTTP Clients

```typescript
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
} from '@auditmation/types-core-js';

private handleApiError(error: any): never {
  const status = error.status || error.response?.status || 500;
  const message = error.message || 'Unknown error';
  
  switch (status) {
    case 401:
      throw new InvalidCredentialsError();
    
    case 403:
      if (message.toLowerCase().includes('rate limit')) {
        throw new RateLimitExceededError();
      }
      throw new UnauthorizedError();
    
    case 404:
      throw new NoSuchObjectError('resource', 'unknown');
    
    case 400:
    case 422:
      throw new InvalidInputError('request', message);
    
    case 429:
      throw new RateLimitExceededError();
    
    default:
      if (status >= 500) {
        throw new UnexpectedError(`Server error: ${message}`, status);
      }
      throw new UnexpectedError(`API error: ${message}`, status);
  }
}
```

## Additional Core Errors Available

- `ParameterRequiredError` - For missing required parameters
- `NotFoundError` - Alternative to NoSuchObjectError
- `TimeoutError` - For timeout scenarios
- `ConflictError` - For conflict scenarios (409)
- `ForbiddenError` - For forbidden access
- `NotConnectedError` - For connection state errors
- `IllegalArgumentError` - For programming errors
- `InvalidStateError` - For invalid state transitions

## Best Practices

1. **Choose the most specific error type** - Don't always default to UnexpectedError
2. **Provide meaningful context** - Use appropriate `type` and `id` values for NoSuchObjectError
3. **Include examples for validation errors** - Help users understand expected input format
4. **Let timestamps auto-generate** - Only provide custom timestamps when necessary
5. **Handle rate limits appropriately** - Check error messages for rate limit indicators
6. **Map HTTP status codes correctly** - Use the mapping table above as reference

## Import Statement

```typescript
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
  // Add other errors as needed
} from '@auditmation/types-core-js';
```