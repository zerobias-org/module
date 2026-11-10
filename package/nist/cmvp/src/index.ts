import { CmvpImpl } from './CmvpImpl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

export function newCmvp() {
  return new CmvpImpl();
}
