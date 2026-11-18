// Template for src/impl/dataproducer/DataProducerImpl.ts.
// One class implements all six generated producer interfaces. It is constructed
// with the module Impl only and reaches data through the module's own wrapped
// tag producers, never through the raw client.
import {
  ObjectsProducerApi, CollectionsProducerApi, DocumentsProducerApi,
  FunctionsProducerApi, BinaryProducerApi, SchemasProducerApi,
} from '../../../generated/api/index.js';
import type { DataProducerObject, Schema } from '../../../generated/model/index.js';
import { NoSuchObjectError, UnexpectedError } from '@zerobias-org/types-core-js';
import type { AcmeImpl } from '../../AcmeImpl.js';
import * as Mappers from './DataProducerMappers.js';
import { getSchemaById } from './DataProducerSchemas.js';

/**
 * Object ID grammar:
 * - Root: "/"
 * - Organizations container: "organizations"
 * - Organization: "org_{code}"
 * - Collection: "collection_{type}_{scope}"   (types: see COLLECTIONS registry)
 * - Function: "func_{operation}"
 */
export class DataProducerImpl implements
    ObjectsProducerApi, CollectionsProducerApi, DocumentsProducerApi,
    FunctionsProducerApi, BinaryProducerApi, SchemasProducerApi {

  constructor(private readonly mod: AcmeImpl) {}

  async getObject(objectId: string): Promise<DataProducerObject> {
    if (objectId === '/') return Mappers.buildRootObject();
    if (objectId.startsWith('org_')) return Mappers.getOrganizationObject(this.mod, objectId);
    if (objectId.startsWith('collection_')) return Mappers.buildCollectionObject(this.mod, objectId);
    if (objectId === 'organizations') return Mappers.buildContainerObject(objectId);
    throw new NoSuchObjectError('object', objectId);
  }

  // getChildren / getCollectionElements / getDocumentData / ... have the same
  // shape: route on the ID prefix, delegate to one mapper per branch. Take the
  // exact signatures from the generated *ProducerApi interfaces.

  async getSchema(schemaId: string): Promise<Schema> {
    const schema = getSchemaById(schemaId);
    if (!schema) throw new NoSuchObjectError('schema', schemaId);
    return schema;
  }

  // Every write the module does not implement throws in exactly this shape.
  async createChildObject(objectId: string /* ... */): Promise<DataProducerObject> {
    throw new UnexpectedError(`Create not implemented for ${objectId}`);
  }
}
