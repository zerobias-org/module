import { SmeMartImpl } from './SmeMartImpl.js';

// Factory Function - Primary way to create module instances
export function newSmeMart(): SmeMartImpl {
  return new SmeMartImpl();
}

// Implementation Class Export
export { SmeMartImpl } from './SmeMartImpl.js';

// Producer Exports (for advanced/direct usage)
export { ProviderProducerApiImpl } from './ProviderProducerApiImpl.js';
export { ProfileProducerApiImpl } from './ProfileProducerApiImpl.js';
export { ServiceProducerApiImpl } from './ServiceProducerApiImpl.js';
export { RequestProducerApiImpl } from './RequestProducerApiImpl.js';
export { AdminProducerApiImpl } from './AdminProducerApiImpl.js';

// Database schema export (for migrations/seed)
export * from './db/schema.js';

// Generated API and Model Exports
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';
