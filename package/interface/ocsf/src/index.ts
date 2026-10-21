// Re-export the generated API surface and data model (from api.yml, generated from OCSF).
// This package is interface-only — consumers get a typed client from the module that
// implements it; implementers $ref its paths (dist/module-interface-ocsf.yml).
export * from '../generated/api/index.js';
export * from '../generated/model/index.js';
