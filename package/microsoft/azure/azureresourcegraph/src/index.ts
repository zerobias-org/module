import { AzureresourcegraphImpl } from './AzureresourcegraphImpl.js';

/** Factory function — the primary entry point for creating module instances. */
export function newAzureresourcegraph(): AzureresourcegraphImpl {
  return new AzureresourcegraphImpl();
}

// Implementation Class Export
export { AzureresourcegraphImpl } from './AzureresourcegraphImpl.js';

// Client Class Export (for advanced usage)
export { AzureResourceGraphClient } from './AzureResourceGraphClient.js';

// Mappers Export (for testing/debugging)
export * from './Mappers.js';

// Generated API and Model Exports
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';
