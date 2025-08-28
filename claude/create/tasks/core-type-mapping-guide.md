# Core Type Mapping Guide

## Overview

This guide provides the mapping between OpenAPI string formats and their corresponding TypeScript type classes for use in Tasks 5, 7, 8, and 9.

## 🚨 Critical Usage Rules

- **Task 5**: Use these mappings when creating OpenAPI specifications - set the correct `format` field
- **Task 7**: Use these mappings in data transformation and validation
- **Task 8 & 9**: Use these mappings for type assertions in tests (e.g., `instanceof Email`, `instanceof UUID`)
- **String Conversion**: All types support `.toString()` method and template literal interpolation
- **Import Sources**: Import types from the specified packages, not from Node.js built-ins

## AWS Types (@auditmation/types-amazon-js)

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `arn` | `Arn` | `toEnum(Arn, value)` or `new Arn(value)` |
| `awsPartition` | `AwsPartition` | `toEnum(AwsPartition, value)` |
| `awsService` | `AwsService` | `toEnum(AwsService, value)` |
| `awsImageId` | `AwsImageId` | `toEnum(AwsImageId, value)` |
| `awsAccessPolicy` | `AwsAccessPolicy` | `new AwsAccessPolicy(value)` |
| `awsAccessPolicyStatement` | `AwsAccessPolicyStatement` | `new AwsAccessPolicyStatement(value)` |
| `awsAccessPolicyStatementCondition` | `AwsAccessPolicyStatementCondition` | `new AwsAccessPolicyStatementCondition(value)` |
| `awsAccessPolicyStatementEffect` | `AwsAccessPolicyStatementEffect` | `toEnum(AwsAccessPolicyStatementEffect, value)` |
| `awsAccessPolicyStatementOperator` | `AwsAccessPolicyStatementOperator` | `toEnum(AwsAccessPolicyStatementOperator, value)` |

## Microsoft Azure Types (@auditmation/types-microsoft-js)

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `azureVmSize` | `AzureVmSize` | `toEnum(AzureVmSize, value)` |
| `azureResourceProvider` | `AzureResourceProvider` | `toEnum(AzureResourceProvider, value)` |
| `azureResource` | `AzureResource` | `new AzureResource(value)` |
| `azureResourceInfo` | `AzureResourceInfo` | `new AzureResourceInfo(value)` |
| `azureResourceType` | `AzureResourceType` | `toEnum(AzureResourceType, value)` |
| `azureResourcePlan` | `AzureResourcePlan` | `new AzureResourcePlan(value)` |
| `azureResourceSku` | `AzureResourceSku` | `new AzureResourceSku(value)` |
| `azureResourceSkuTier` | `AzureResourceSkuTier` | `toEnum(AzureResourceSkuTier, value)` |
| `azureResourceIdentity` | `AzureResourceIdentity` | `new AzureResourceIdentity(value)` |
| `azureResourceIdentityType` | `AzureResourceIdentityType` | `toEnum(AzureResourceIdentityType, value)` |

## Google Cloud Types (@auditmation/types-google-js)

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `gcpAccessPolicy` | `GcpAccessPolicy` | `new GcpAccessPolicy(value)` |
| `gcpAccessPolicyAuditConfig` | `GcpAccessPolicyAuditConfig` | `new GcpAccessPolicyAuditConfig(value)` |
| `gcpAccessPolicyAuditLogConfig` | `GcpAccessPolicyAuditLogConfig` | `new GcpAccessPolicyAuditLogConfig(value)` |
| `gcpAccessPolicyAuditLogConfigType` | `GcpAccessPolicyAuditLogConfigType` | `toEnum(GcpAccessPolicyAuditLogConfigType, value)` |
| `gcpAccessPolicyBinding` | `GcpAccessPolicyBinding` | `new GcpAccessPolicyBinding(value)` |
| `gcpAccessPolicyBindingCondition` | `GcpAccessPolicyBindingCondition` | `new GcpAccessPolicyBindingCondition(value)` |
| `gcpAccessPolicyVersion` | `GcpAccessPolicyVersion` | `toEnum(GcpAccessPolicyVersion, value)` |

## Core Types (@auditmation/types-core-js)

### Binary/Encoding

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `byte` | `Byte` | `new Byte(value)` |
| `b64` | `Byte` | `new Byte(value)` |
| `base64` | `Byte` | `new Byte(value)` |

### Networking

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `cidr` | `Cidr` | `new Cidr(value)` |
| `ipAddress` | `IpAddress` | `new IpAddress(value)` |
| `ip` | `IpAddress` | `new IpAddress(value)` |
| `ipv4` | `IpAddress` | `new IpAddress(value)` |
| `ipv6` | `IpAddress` | `new IpAddress(value)` |
| `mac` | `MacAddress` | `new MacAddress(value)` |
| `macaddr` | `MacAddress` | `new MacAddress(value)` |
| `macAddress` | `MacAddress` | `new MacAddress(value)` |
| `hostname` | `Hostname` | `new Hostname(value)` |

### Date/Time

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `date-time` | `Date` | `new Date(value)` |
| `time` | `Date` | `new Date(value)` |
| `timestamp` | `Date` | `new Date(value)` |
| `date` | `Date` | `new Date(value)` |
| `duration` | `Duration` | `new Duration(value)` |

### Numeric

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `double` | `number` | `Number(value)` |
| `float` | `number` | `Number(value)` |
| `int32` | `number` | `Number(value)` |
| `int64` | `number` | `Number(value)` |
| `integer` | `number` | `Number(value)` |

### Communication/Identity

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `email` | `Email` | `new Email(value)` |
| `phoneNumber` | `PhoneNumber` | `new PhoneNumber(value)` |
| `phone` | `PhoneNumber` | `new PhoneNumber(value)` |

### Web/Protocol

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `url` | `URL` | `new URL(value)` |
| `uri` | `URL` | `new URL(value)` |
| `uuid` | `UUID` | `new UUID(value)` |
| `mimeType` | `MimeType` | `new MimeType(value)` |

### Versioning

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `semver` | `Semver` | `new Semver(value)` |
| `versionRange` | `VersionRange` | `new VersionRange(value)` |

### Misc

| String Format | TypeScript Type | Usage Example |
|---------------|------------------|---------------|
| `nmtoken` | `Nmtoken` | `new Nmtoken(value)` |
| `password` | `string` | `String(value)` |

## Usage Patterns

### Task 5: OpenAPI Specification
```yaml
properties:
  email_address:
    type: string
    format: email  # Maps to Email type
  website_url:
    type: string
    format: url    # Maps to URL type
  user_id:
    type: string
    format: uuid   # Maps to UUID type
```

### Task 7: Data Transformation
```typescript
import { Email, URL, UUID } from '@auditmation/types-core-js';
import { map } from '@auditmation/util-hub-module-utils';

function mapUser(raw: any): User {
  return {
    id: map(UUID, raw.user_id),
    email: map(Email, raw.email_address),
    website: map(URL, raw.website_url)
  };
}
```

### Task 8 & 9: Test Assertions
```typescript
import { Email, URL, UUID } from '@auditmation/types-core-js';

// Test assertions
expect(user.id).to.be.instanceof(UUID);
expect(user.email).to.be.instanceof(Email);
expect(user.website).to.be.instanceof(URL);
```

### String Conversion Examples
```typescript
// All types support toString() and template literals
const email = new Email('user@example.com');
const emailString = email.toString();
const message = `User email: ${email}`;

const uuid = new UUID('123e4567-e89b-12d3-a456-426614174000');
const uuidString = uuid.toString();
const log = `Processing user ${uuid}`;
```

## Package Import Requirements

**🚨 CRITICAL**: Always import from the specified packages:

```typescript
// Core types - ALWAYS import from @auditmation/types-core-js
import { URL, UUID, Email, IpAddress } from '@auditmation/types-core-js';

// AWS types
import { Arn, AwsService } from '@auditmation/types-amazon-js';

// Azure types  
import { AzureVmSize, AzureResource } from '@auditmation/types-microsoft-js';

// Google Cloud types
import { GcpAccessPolicy } from '@auditmation/types-google-js';

// NEVER import URL from Node.js built-ins
// ❌ import { URL } from 'url';  // WRONG
// ✅ import { URL } from '@auditmation/types-core-js';  // CORRECT
```

## Validation and Error Handling

All typed values can be validated and will throw appropriate errors for invalid formats:

```typescript
try {
  const email = new Email('invalid-email');  // Throws validation error
} catch (error) {
  // Handle validation error
}

// Safe validation with try-catch in mappers
function safeMapEmail(raw: string): Email | null {
  try {
    return new Email(raw);
  } catch {
    return null;  // Or handle as appropriate for your use case
  }
}
```