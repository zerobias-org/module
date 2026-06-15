import { PetstoreImpl } from './PetstoreImpl.js';

/** Factory function — the primary entry point for creating module instances. */
export function newPetstore(): PetstoreImpl {
  return new PetstoreImpl();
}

// Implementation Class Export
export { PetstoreImpl } from './PetstoreImpl.js';

// Client Class Export (for advanced usage)
export { PetstoreClient } from './PetstoreClient.js';

// Mappers Export (for testing/debugging)
export * from './Mappers.js';

// Generated API and Model Exports
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';
