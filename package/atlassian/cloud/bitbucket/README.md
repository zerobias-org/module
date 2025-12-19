# Atlassian Bitbucket Cloud Module

> A connector module for interacting with the Atlassian Bitbucket Cloud API, providing access to workspaces and repositories.

## Installation

```bash
npm install @zerobias-org/module-atlassian-cloud-bitbucket
```

### Prerequisites
- Node.js 14.x or higher
- npm or yarn
- Valid Atlassian account with Bitbucket Cloud access
- API token with appropriate scopes

## Usage

### Basic Setup

```typescript
import { newBitbucket } from '@zerobias-org/module-atlassian-cloud-bitbucket';

// Create connector instance
const bitbucket = newBitbucket();
```

### Connecting

```typescript
import { newBitbucket, ConnectionProfile } from '@zerobias-org/module-atlassian-cloud-bitbucket';
import { Email } from '@auditmation/types-core-js';

async function connect() {
  const bitbucket = newBitbucket();

  // Create connection profile
  const email = await Email.parse(process.env.BITBUCKET_EMAIL!);
  const profile = new ConnectionProfile(email, process.env.BITBUCKET_API_TOKEN!);

  // Connect to Bitbucket
  const connectionState = await bitbucket.connect(profile);
  console.log('Connected successfully');

  return bitbucket;
}
```

### Basic Operation

```typescript
// Get Workspace API
const workspaceApi = bitbucket.getWorkspaceApi();

// List all accessible workspaces
const workspaces = await workspaceApi.list();
console.log('Found', workspaces.length, 'workspaces');
```

## Authentication

### Supported Methods
- **HTTP Basic Authentication** - Uses Atlassian email and API token (App Password)

### Obtaining Credentials

See [USERGUIDE.md](./USERGUIDE.md) for detailed instructions on obtaining credentials.

Quick summary:
1. Log into [Atlassian Account Settings](https://id.atlassian.com/manage-profile/security/api-tokens)
2. Click "Create API token"
3. Give the token a descriptive label
4. Copy the generated token immediately (it will not be shown again)

### Required Scopes/Permissions

For full functionality, your API token needs these scopes:
- `read:user:bitbucket` - Read user information
- `read:workspace:bitbucket` - List and read workspaces
- `read:repository:bitbucket` - List and read repositories

### Environment Variables

Create a `.env` file in your project root:

```env
BITBUCKET_EMAIL=your-atlassian-email@example.com
BITBUCKET_API_TOKEN=ATATT3xFfGF0...your-api-token-here
```

**Security Note:** Never commit `.env` files to version control. Add `.env` to your `.gitignore`.

### Connection Profile Mapping

| Atlassian Credential | Connection Profile Field | Description |
|---------------------|-------------------------|-------------|
| Account Email | `email` | Your Atlassian account email address |
| API Token | `apiToken` | App Password from Atlassian settings |

## Operations

This module supports the following operations:

### Workspace Management (WorkspaceApi)
- `list()` - Retrieve all workspaces accessible by the authenticated user

### Repository Management (RepositoryApi)
- `list(workspace)` - Retrieve all repositories in the specified workspace

See the [Examples](#examples) section for usage examples.

## Examples

### List All Workspaces

```typescript
import { newBitbucket, ConnectionProfile } from '@zerobias-org/module-atlassian-cloud-bitbucket';
import { Email } from '@auditmation/types-core-js';

async function listAllWorkspaces() {
  const bitbucket = newBitbucket();

  try {
    // Connect
    const email = await Email.parse(process.env.BITBUCKET_EMAIL!);
    const profile = new ConnectionProfile(email, process.env.BITBUCKET_API_TOKEN!);
    await bitbucket.connect(profile);

    // Get Workspace API
    const workspaceApi = bitbucket.getWorkspaceApi();

    // List workspaces
    const workspaces = await workspaceApi.list();

    console.log('Workspaces:');
    workspaces.forEach(workspace => {
      console.log(`- ${workspace.name} (${workspace.slug})`);
    });

    // Always disconnect when done
    await bitbucket.disconnect();
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

listAllWorkspaces();
```

### List Repositories in a Workspace

```typescript
import { newBitbucket, ConnectionProfile } from '@zerobias-org/module-atlassian-cloud-bitbucket';
import { Email } from '@auditmation/types-core-js';

async function listRepositories() {
  const bitbucket = newBitbucket();

  try {
    // Connect
    const email = await Email.parse(process.env.BITBUCKET_EMAIL!);
    const profile = new ConnectionProfile(email, process.env.BITBUCKET_API_TOKEN!);
    await bitbucket.connect(profile);

    // First, get workspaces
    const workspaceApi = bitbucket.getWorkspaceApi();
    const workspaces = await workspaceApi.list();

    if (workspaces.length === 0) {
      console.log('No workspaces found');
      return;
    }

    // Get Repository API
    const repoApi = bitbucket.getRepositoryApi();

    // List repositories in the first workspace
    const workspaceSlug = workspaces[0].slug;
    const repos = await repoApi.list(workspaceSlug);

    console.log(`Repositories in ${workspaceSlug}:`);
    repos.forEach(repo => {
      console.log(`- ${repo.name} (${repo.fullName})`);
      console.log(`  Language: ${repo.language || 'Not specified'}`);
      console.log(`  Private: ${repo.isPrivate}`);
    });

    await bitbucket.disconnect();
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

listRepositories();
```

### Error Handling

```typescript
import { newBitbucket, ConnectionProfile } from '@zerobias-org/module-atlassian-cloud-bitbucket';
import { Email } from '@auditmation/types-core-js';
import {
  InvalidCredentialsError,
  NoSuchObjectError,
  RateLimitExceededError
} from '@auditmation/types-core-js';

async function handleErrors() {
  const bitbucket = newBitbucket();

  try {
    const email = await Email.parse(process.env.BITBUCKET_EMAIL!);
    const profile = new ConnectionProfile(email, process.env.BITBUCKET_API_TOKEN!);
    await bitbucket.connect(profile);

    const workspaceApi = bitbucket.getWorkspaceApi();
    const workspaces = await workspaceApi.list();

    console.log('Successfully retrieved workspaces:', workspaces.length);
  } catch (error) {
    if (error instanceof InvalidCredentialsError) {
      console.error('Invalid credentials. Check your email and API token.');
    } else if (error instanceof NoSuchObjectError) {
      console.error('Resource not found');
    } else if (error instanceof RateLimitExceededError) {
      console.error('Rate limit exceeded. Please wait before retrying.');
    } else {
      console.error('Unexpected error:', error);
    }
  } finally {
    await bitbucket.disconnect();
  }
}

handleErrors();
```

### Complete Workflow Example

```typescript
import { newBitbucket, ConnectionProfile } from '@zerobias-org/module-atlassian-cloud-bitbucket';
import { Email } from '@auditmation/types-core-js';

async function main() {
  const bitbucket = newBitbucket();

  try {
    // Connect using environment variables
    const email = await Email.parse(process.env.BITBUCKET_EMAIL!);
    const profile = new ConnectionProfile(email, process.env.BITBUCKET_API_TOKEN!);
    await bitbucket.connect(profile);

    // List all workspaces
    const workspaceApi = bitbucket.getWorkspaceApi();
    const workspaces = await workspaceApi.list();

    console.log(`Found ${workspaces.length} workspace(s)`);

    // For each workspace, list repositories
    const repoApi = bitbucket.getRepositoryApi();

    for (const workspace of workspaces) {
      console.log(`\nWorkspace: ${workspace.name}`);
      console.log(`  Slug: ${workspace.slug}`);
      console.log(`  Private: ${workspace.isPrivate}`);

      const repos = await repoApi.list(workspace.slug);
      console.log(`  Repositories: ${repos.length}`);

      repos.forEach(repo => {
        console.log(`    - ${repo.name} (${repo.scm})`);
      });
    }
  } finally {
    await bitbucket.disconnect();
  }
}

main();
```

## Testing

### Running Tests

```bash
# Run unit tests
npm test

# Run unit tests in watch mode
npm run test:watch

# Run integration tests
npm run test:integration

# Run integration tests with JSON output
npm run test:integration:json
```

### Test Environment Setup

Tests require valid credentials. Create a `.env` file in the module directory:

```env
BITBUCKET_EMAIL=your-test-account@example.com
BITBUCKET_API_TOKEN=your-test-api-token
```

**Important:** Use a dedicated test account with read-only access, NOT production credentials.

### Test Coverage

Tests cover:
- Connection and authentication
- Workspace listing operations
- Repository listing operations
- Error handling (401, 403, 404, 429, etc.)
- Data mapping between API and module types

## License

UNLICENSED

## Support

For issues, please file a report in the repository issue tracker.
