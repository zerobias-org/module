import { ReadyPlayerMeConnector } from '../generated/api/index.js';
import { ReadyPlayerMeImpl } from './ReadyPlayerMeImpl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

// Shim: the generated server controller imports `Date` as if it were a local
// class ([Avatar2dApiController] for the `uat` query param `format: date-time`).
// Re-export the global Date so that `import { Date } from '../src/index.js'`
// resolves. Safe to remove once @zerobias-org/util-codegen stops emitting this
// import for date-time query parameters.
export const Date = globalThis.Date;

export function newReadyPlayerMe(): ReadyPlayerMeConnector {
  return new ReadyPlayerMeImpl();
}
