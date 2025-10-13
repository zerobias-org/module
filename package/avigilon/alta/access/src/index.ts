import { AccessImpl } from './AccessImpl';
import { AccessConnector } from '../generated/api';

export * from '../generated/api';
export * from '../generated/model';

export function newAccess(): AccessConnector {
  return new AccessImpl();
}
