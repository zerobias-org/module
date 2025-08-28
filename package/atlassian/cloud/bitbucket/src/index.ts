import { BitbucketImpl } from './BitbucketImpl';
import { BitbucketConnector } from '../generated/api';

export * from '../generated/api';
export * from '../generated/model';

export function newBitbucket(): BitbucketImpl {
  return new BitbucketImpl();
}

export { BitbucketImpl } from './BitbucketImpl';
export { BitbucketClient } from './BitbucketClient';
export * from './Mappers';
