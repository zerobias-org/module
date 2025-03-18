import { ReadyPlayerMeConnector } from '../generated/api';
import { ReadyPlayerMeImpl } from './ReadyPlayerMeImpl';

export * from '../generated/api';
export * from '../generated/model';

export function newReadyPlayerMe(): ReadyPlayerMeConnector {
  return new ReadyPlayerMeImpl();
}
