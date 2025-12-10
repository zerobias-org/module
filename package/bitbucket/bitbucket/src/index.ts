import { BitbucketConnector } from '../generated/api';
import { BitbucketImpl } from './BitbucketImpl';

export * from '../generated/api';
export * from '../generated/model';

export function newBitbucket(): BitbucketConnector {
  return new BitbucketImpl();
}
