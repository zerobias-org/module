# @zerobias-org/module-avigilon-alta-access

A TypeScript client library for integrating with the Avigilon Alta Access API. Provides type-safe operations for managing people, groups, and access control units with read-only access.

For credential setup, see the [User Guide](USER_GUIDE.md).

**Note**: API operations and data models are automatically appended to this README during the publishing process based on the OpenAPI specification.

## Installation

```bash
npm install @zerobias-org/module-avigilon-alta-access
```

Requires Node.js 18+ and an Avigilon Alta Access account with email/password credentials.

## Quick Start

```typescript
import { newAvigilonAltaAccess } from '@zerobias-org/module-avigilon-alta-access';
import { Email } from '@auditmation/types-core-js';

const client = newAvigilonAltaAccess();

await client.connect({
  email: new Email(process.env.AVIGILON_EMAIL!),
  password: process.env.AVIGILON_PASSWORD!,
  totpCode: process.env.AVIGILON_TOTP_CODE // Optional, for MFA
});

const users = await client.getUserProducerApi().listUsers();
console.log(users);

await client.disconnect();
```

See [User Guide](USER_GUIDE.md) for detailed setup instructions.

## Usage

```typescript
import { Email } from '@auditmation/types-core-js';

const client = newAvigilonAltaAccess();
await client.connect({
  email: new Email('your-email@domain.com'),
  password: 'your-password',
  totpCode: '123456' // Optional, if MFA is enabled
});

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

## Development

### Testing

Run integration tests:

```bash
npm run test:integration
```

### Test Fixtures

Integration tests use fixtures stored in `test/fixtures/integration/` for consistent test data. These fixtures are **not automatically updated** during test runs.

To update fixtures when API responses change:

```bash
SAVE_FIXTURES=true npm run test:integration
```

**When to update fixtures:**
- API response format changes
- New test scenarios require different data
- Debugging integration issues

**⚠️ Important:** Only update fixtures intentionally. Review changes before committing to ensure they reflect expected API behavior.

---

📋 **Important**: This documentation is auto-generated. Please verify code examples work with the current version.
