# @zerobias-org/module-avigilon-alta-access

A TypeScript client library for integrating with the Avigilon Alta Access API. Provides type-safe operations for managing people, groups, and access control units with read-only access.

For credential setup, see the [User Guide](USER_GUIDE.md).

**Note**: API operations and data models are automatically appended to this README during the publishing process based on the OpenAPI specification.

## Installation

```bash
npm install @zerobias-org/module-avigilon-alta-access
```

Requires Node.js 18+ and an Avigilon Alta Access account with API token access.

## Quick Start

```typescript
import { newAvigilonAltaAccess } from '@zerobias-org/module-avigilon-alta-access';

const client = newAvigilonAltaAccess();

await client.connect({
  accessToken: process.env.AVIGILON_ACCESS_TOKEN
});

const users = await client.getUserProducerApi().listUsers();
console.log(users);

await client.disconnect();
```

See [User Guide](USER_GUIDE.md) for detailed setup instructions.

## Usage

```typescript
const client = newAvigilonAltaAccess();
await client.connect({ accessToken: 'your-token' });

// Available APIs
const userApi = client.getUserProducerApi();
const groupApi = client.getGroupProducerApi();  
const acuApi = client.getAcuProducerApi();

// List operations
await userApi.listUsers();
await groupApi.listGroups();
await acuApi.listAcus();

// Get single item operations
await userApi.getUser('user-id');
await groupApi.getGroup('group-id');
await acuApi.getAcu('acu-id');
```

---

📋 **Important**: This documentation is auto-generated. Please verify code examples work with the current version.
