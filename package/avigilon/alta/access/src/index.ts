import { AccessImpl } from './AccessImpl';

// Factory Function - Primary way to create module instances
export function newAccess(): AccessImpl {
  return new AccessImpl();
}

// Implementation Class Export
export { AccessImpl } from './AccessImpl';

// Client Class Export (for advanced usage)
export { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

// Mappers Export (for testing/debugging)
export * from './Mappers';

// Generated API and Model Exports
export * from '../generated/api';
export * from '../generated/model';
