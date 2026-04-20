// Re-export the generated API surface and data model.
// This package is interface-only — consumers import types and call
// `newDynamicDataProducer()` from the concrete module that implements them.
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';
