import type { AzureResourceGraphConnector } from '../generated/api/index.js';
import { AzureResourceGraphImpl } from './AzureResourceGraphImpl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

/** Factory function — the primary entry point for creating module instances. */
export function newAzureResourceGraph(): AzureResourceGraphConnector {
  return new AzureResourceGraphImpl();
}
