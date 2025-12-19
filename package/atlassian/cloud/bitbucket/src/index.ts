import { BitbucketImpl } from './BitbucketImpl';

// Factory Function - Primary way to create module instances
export function newBitbucket(): BitbucketImpl {
  return new BitbucketImpl();
}

// Implementation Class Export
export { BitbucketImpl } from './BitbucketImpl';

// Client Class Export (for advanced usage)
export { AtlassianCloudBitbucketClient } from './AtlassianCloudBitbucketClient';

// Generated API and Model Exports
export * from '../generated/api';
export * from '../generated/model';
