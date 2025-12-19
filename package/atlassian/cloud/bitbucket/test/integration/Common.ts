import * as dotenv from 'dotenv';
import { Email } from '@auditmation/types-core-js';
import { BitbucketImpl, newBitbucket } from '../../src';
import { ConnectionProfile } from '../../generated/model';

// Load environment variables from .env file
dotenv.config();

/**
 * Creates a connected BitbucketImpl instance for integration tests
 * Requires the following environment variables:
 * - BITBUCKET_EMAIL: Atlassian account email address
 * - BITBUCKET_API_TOKEN: Atlassian API token
 * - BITBUCKET_WORKSPACE: (optional) Default workspace slug for tests
 */
export async function createConnectedImpl(): Promise<BitbucketImpl> {
  const emailStr = process.env.BITBUCKET_EMAIL;
  const apiToken = process.env.BITBUCKET_API_TOKEN;

  if (!emailStr || !apiToken) {
    throw new Error(
      'Missing required environment variables. ' +
      'Please set BITBUCKET_EMAIL and BITBUCKET_API_TOKEN in your .env file'
    );
  }

  const impl = newBitbucket();

  const email = await Email.parse(emailStr);
  const profile = new ConnectionProfile(email, apiToken);

  await impl.connect(profile);

  return impl;
}

/**
 * Gets the test workspace slug from environment or uses default
 */
export function getTestWorkspace(): string {
  return process.env.BITBUCKET_WORKSPACE || 'zerobias';
}

/**
 * Checks if integration test credentials are available
 */
export function hasCredentials(): boolean {
  return !!(process.env.BITBUCKET_EMAIL && process.env.BITBUCKET_API_TOKEN);
}
