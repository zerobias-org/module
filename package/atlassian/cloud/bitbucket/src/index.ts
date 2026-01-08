import { BitbucketImpl } from './BitbucketImpl';

// Factory Function - Primary way to create module instances
export function newBitbucket(): BitbucketImpl {
  return new BitbucketImpl();
}

// Implementation Class Export
export { BitbucketImpl } from './BitbucketImpl';

// Client Export
export { BitbucketClient } from './BitbucketClient';

// Generated API and Model Exports (available after build)
export * from '../generated/api';
export * from '../generated/model';
