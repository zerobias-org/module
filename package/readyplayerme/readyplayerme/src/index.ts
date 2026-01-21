import { ReadyPlayerMeConnector } from '../generated/api/index.js';
import { ReadyPlayerMeImpl } from './ReadyPlayerMeImpl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

export function newReadyPlayerMe(): ReadyPlayerMeConnector {
  return new ReadyPlayerMeImpl();
}
