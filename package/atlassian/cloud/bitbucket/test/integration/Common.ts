import * as dotenv from 'dotenv';
import { BitbucketImpl, newBitbucket } from '../../src';

// Load environment variables from .env file
dotenv.config();

/**
 * Creates a connected BitbucketImpl instance for integration tests
 * Requires the following environment variables:
 * - BITBUCKET_ACCESS_TOKEN: OAuth access token or App Password
 * - BITBUCKET_WORKSPACE: (optional) Default workspace slug for tests
 */
export async function createConnectedImpl(): Promise<BitbucketImpl> {
  const accessToken = process.env.BITBUCKET_ACCESS_TOKEN;

  if (!accessToken) {
    throw new Error(
      'Missing required environment variables. ' +
      'Please set BITBUCKET_ACCESS_TOKEN in your .env file'
    );
  }

  const impl = newBitbucket();

  const profile = {
    accessToken
  };

  const metadata = impl.getConnectionMetadataDefaults();

  const { status, state } = await impl.connect(profile, undefined, metadata);

  if (status.status !== 'connected') {
    throw new Error(`Failed to connect: ${status.message}`);
  }

  return impl;
}

/**
 * Gets the test workspace slug from environment or uses default
 */
export function getTestWorkspace(): string {
  return process.env.BITBUCKET_WORKSPACE || 'test-workspace';
}
