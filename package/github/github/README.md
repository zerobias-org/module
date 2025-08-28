# @zerobias-org/module-github-github

A TypeScript client library for integrating with the GitHub API. Provides type-safe operations for retrieving organization information and managing organizational resources.

For credential setup, see the [User Guide](USER_GUIDE.md).

**Note**: API operations and data models are automatically appended to this README during the publishing process based on the OpenAPI specification.

## Installation

```bash
npm install @zerobias-org/module-github-github
```

Requires Node.js 18+ and a GitHub account with API access.

## Quick Start

```typescript
import { newGitHub } from '@zerobias-org/module-github-github';

const client = newGitHub();

await client.connect({
  apiToken: process.env.GITHUB_API_TOKEN
});

const result = await client.getOrganizationProducerApi().listOrganizations();
console.log(result);

await client.disconnect();
```

See [User Guide](USER_GUIDE.md) for detailed setup instructions.

## Usage

```typescript
const client = newGitHub();
await client.connect({ apiToken: 'your-token' });

// Available APIs
const organizationApi = client.getOrganizationProducerApi();
await organizationApi.listOrganizations();
await organizationApi.getOrganization({ org: 'organization-name' });
```

---

📋 **Important**: This documentation is auto-generated. Please verify code examples work with the current version.
