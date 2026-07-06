import { StellarCyberConnector } from '../generated/api/index.js';
import { StellarCyberImpl } from './StellarCyberImpl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

/** Factory function — the primary entry point for creating module instances. */
export function newStellarCyber(): StellarCyberConnector {
  return new StellarCyberImpl();
}
