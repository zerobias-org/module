import { AccessImpl } from './AccessImpl.js';

/** Factory function — the primary entry point for creating module instances. */
export function newAccess(): AccessImpl {
  return new AccessImpl();
}

// Implementation Class Export
export { AccessImpl } from './AccessImpl.js';

// Client Class Export (for advanced usage)
export { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient.js';

// Mappers Export (for testing/debugging)
export * from './Mappers.js';

// Generated API and Model Exports
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';
