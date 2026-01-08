import * as dotenv from 'dotenv';
import * as path from 'path';
import * as fs from 'fs';
import { BitbucketImpl, newBitbucket } from '../../src';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { getLogger } from '@auditmation/util-logger';
import { URL } from '@auditmation/types-core-js';

// Load environment variables
dotenv.config();

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

export const testConfig = {
  timeout: 30000,
  fixturesDir: path.join(__dirname, '../fixtures/templates'),
};

export interface TestCredentials {
  workspace: string;
  email: string;
  apiToken: string;
}

export function getTestCredentials(): TestCredentials | null {
  const workspace = process.env.BITBUCKET_WORKSPACE;
  const email = process.env.BITBUCKET_EMAIL;
  const apiToken = process.env.BITBUCKET_API_TOKEN;

  if (!workspace || !email || !apiToken) {
    logger.warn('Missing Bitbucket credentials. Set BITBUCKET_WORKSPACE, BITBUCKET_EMAIL, BITBUCKET_API_TOKEN');
    return null;
  }

  return { workspace, email, apiToken };
}

export async function getConnectionProfile(): Promise<ConnectionProfile | null> {
  const creds = getTestCredentials();
  if (!creds) return null;

  const uri = await URL.parse('https://api.bitbucket.org/2.0');
  return {
    uri,
    username: creds.email,
    password: creds.apiToken,
  };
}

let sharedConnection: BitbucketImpl | null = null;

export async function prepareTestConnection(): Promise<BitbucketImpl> {
  if (sharedConnection) {
    const isConnected = await sharedConnection.isConnected();
    if (isConnected) {
      return sharedConnection;
    }
  }

  const profile = await getConnectionProfile();
  if (!profile) {
    throw new Error('Missing Bitbucket credentials');
  }

  const impl = newBitbucket();
  await impl.connect(profile);
  sharedConnection = impl;
  return impl;
}

export async function saveFixture(filename: string, data: unknown): Promise<void> {
  try {
    if (!fs.existsSync(testConfig.fixturesDir)) {
      fs.mkdirSync(testConfig.fixturesDir, { recursive: true });
    }
    const filepath = path.join(testConfig.fixturesDir, filename);
    fs.writeFileSync(filepath, JSON.stringify(data, null, 2));
    logger.debug(`Saved fixture: ${filename}`);
  } catch (error) {
    logger.warn(`Failed to save fixture ${filename}:`, error);
  }
}

export function skipIfNoCredentials(context: Mocha.Context): void {
  const creds = getTestCredentials();
  if (!creds) {
    context.skip();
  }
}
