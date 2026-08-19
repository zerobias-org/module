import { PagedResults, UnexpectedError, NoSuchObjectError } from '@zerobias-org/types-core-js';
import {
  Schema,
  ModelObject,
  ObjectClassDef,
  CreateObjectRequest,
  UpdateObjectRequest,
  BulkOperationRequest,
  BulkOperationResponse,
  RequestFile,
  ValidateFunctionInputRequest,
  ValidationResult,
  SearchScopeDef
} from '../../../generated/model/index.js';
import type {
  ObjectsProducerApi,
  CollectionsProducerApi,
  DocumentsProducerApi,
  FunctionsProducerApi,
  BinaryProducerApi,
  SchemasProducerApi
} from '../../../generated/api/index.js';
import { AzureResourceGraphClient } from '../../AzureResourceGraphClient.js';
import * as Mappers from './DataProducerMappers.js';
import { getSchemaById } from './DataProducerSchemas.js';

/**
 * DataProducerImpl — Dynamic Data Producer Interface for Azure Resource Graph.
 *
 * Implements the six Dynamic Data Producer interfaces by delegating to mapping
 * functions (implementation logic here, mapping logic in DataProducerMappers).
 *
 * ARG is a read-only inventory service: every create/update/delete/upload/bulk
 * method throws `UnexpectedError` — no silent no-ops. There is no binary
 * content either (ARG returns rows, never files), so both Binary operations
 * throw as well.
 *
 * Object ID strategy (see DataProducerMappers for the full grammar):
 * - Root: "/"
 * - Static containers: "subscriptions", "managementgroups", "query"
 * - Subscriptions: "sub_{subscriptionId}"
 * - Resource groups: "rg_{subscriptionId}/{resourceGroupName}"
 * - Management groups: "mg_{managementGroupName}"
 * - Resources: "res_{fullArmResourceId}"
 * - Collections: "collection_resources_{scope}"
 * - Functions: "func_arg_query"
 */
export class DataProducerImpl
implements
    ObjectsProducerApi,
    CollectionsProducerApi,
    DocumentsProducerApi,
    FunctionsProducerApi,
    BinaryProducerApi,
    SchemasProducerApi {
  private readonly client: AzureResourceGraphClient;

  constructor(client: AzureResourceGraphClient) {
    this.client = client;
  }

  // ==================== SchemasProducerApi ====================

  async getSchema(schemaId: string): Promise<Schema> {
    const schema = getSchemaById(schemaId);
    if (!schema) {
      throw new NoSuchObjectError('schema', schemaId);
    }
    return schema;
  }

  // ==================== ObjectsProducerApi ====================

  async getObject(objectId: string): Promise<ModelObject> {
    if (objectId === '/') {
      return Mappers.buildRootObject();
    }

    if (objectId === 'subscriptions' || objectId === 'managementgroups' || objectId === 'query') {
      return Mappers.buildContainerObject(objectId);
    }

    if (objectId.startsWith('collection_resources_')) {
      // Validates the scope portion before echoing the collection object back.
      Mappers.parseResourcesCollectionId(objectId);
      return Mappers.buildResourcesCollectionObject(objectId.slice('collection_resources_'.length));
    }

    if (objectId.startsWith('sub_')) {
      return Mappers.getSubscriptionObject(this.client, objectId);
    }

    if (objectId.startsWith('rg_')) {
      return Mappers.getResourceGroupObject(this.client, objectId);
    }

    if (objectId.startsWith('mg_')) {
      return Mappers.getManagementGroupObject(this.client, objectId);
    }

    if (objectId.startsWith('res_')) {
      return Mappers.getResourceObject(this.client, objectId);
    }

    if (objectId.startsWith('func_')) {
      return Mappers.buildFunctionObject(objectId);
    }

    throw new NoSuchObjectError('object', objectId);
  }

  async getRootObject(): Promise<ModelObject> {
    return Mappers.buildRootObject();
  }

  async getChildren(
    results: PagedResults<ModelObject>,
    objectId: string,
    type?: Array<ObjectClassDef>,
    tags?: Array<string>
  ): Promise<void> {
    void type;
    void tags;

    if (objectId === '/') {
      return Mappers.getRootChildren(results);
    }

    if (objectId === 'subscriptions') {
      return Mappers.getSubscriptionsChildren(this.client, results);
    }

    if (objectId === 'managementgroups') {
      return Mappers.getManagementGroupsChildren(this.client, results);
    }

    if (objectId === 'query') {
      return Mappers.getQueryChildren(results);
    }

    if (objectId.startsWith('sub_')) {
      return Mappers.getSubscriptionChildren(this.client, objectId, results);
    }

    if (objectId.startsWith('rg_')) {
      return Mappers.getResourceGroupChildren(objectId, results);
    }

    if (objectId.startsWith('mg_')) {
      return Mappers.getManagementGroupChildren(objectId, results);
    }

    // Leaves (resources, collections, functions) have no children.
    results.items = [];
  }

  async objectSearch(
    results: PagedResults<ModelObject>,
    objectId: string,
    scope?: SearchScopeDef,
    keywords?: Array<string>,
    filter?: string,
    properties?: string[]
  ): Promise<void> {
    void objectId;
    void scope;
    void filter;
    void properties;

    if (keywords && keywords.length > 0) {
      return Mappers.searchResources(this.client, keywords, results);
    }

    results.items = [];
  }

  async searchChildObjects(
    results: PagedResults<ModelObject>,
    objectId: string,
    filter?: string,
    scope?: SearchScopeDef,
    properties?: string[],
    includeCount?: boolean
  ): Promise<void> {
    void objectId;
    void filter;
    void scope;
    void properties;
    void includeCount;

    results.items = [];
  }

  async createChildObject(
    objectId: string,
    createObjectRequest: CreateObjectRequest
  ): Promise<ModelObject> {
    void createObjectRequest;
    throw new UnexpectedError(`Create child not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  async updateObject(
    objectId: string,
    updateObjectRequest: UpdateObjectRequest,
    ifMatch?: string
  ): Promise<ModelObject> {
    void updateObjectRequest;
    void ifMatch;
    throw new UnexpectedError(`Update not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  async deleteObject(objectId: string, recursive?: boolean): Promise<void> {
    void recursive;
    throw new UnexpectedError(`Delete not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  // ==================== CollectionsProducerApi ====================

  async getCollectionElements(results: PagedResults<object>, objectId: string): Promise<void> {
    return Mappers.getResourcesCollectionElements(this.client, objectId, results);
  }

  async getCollectionElement(objectId: string, elementKey: string): Promise<{ [key: string]: object }> {
    return Mappers.getResourcesCollectionElement(this.client, objectId, elementKey);
  }

  async addCollectionElement(
    objectId: string,
    requestBody: { [key: string]: object }
  ): Promise<{ [key: string]: object }> {
    void requestBody;
    throw new UnexpectedError(`Add collection element not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  async updateCollectionElement(
    objectId: string,
    elementKey: string,
    requestBody: { [key: string]: object },
    ifMatch?: string
  ): Promise<{ [key: string]: object }> {
    void elementKey;
    void requestBody;
    void ifMatch;
    throw new UnexpectedError(`Update collection element not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  async deleteCollectionElement(objectId: string, elementKey: string): Promise<void> {
    void elementKey;
    throw new UnexpectedError(`Delete collection element not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  async searchCollectionElements(
    results: PagedResults<object>,
    objectId: string,
    filter?: string
  ): Promise<void> {
    void results;
    void filter;
    throw new UnexpectedError(`Collection search not implemented for ${objectId}`);
  }

  async executeBulkOperations(
    objectId: string,
    bulkOperationRequest: BulkOperationRequest
  ): Promise<BulkOperationResponse> {
    void bulkOperationRequest;
    throw new UnexpectedError(`Bulk operations not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  // ==================== DocumentsProducerApi ====================

  async getDocumentData(objectId: string): Promise<{ [key: string]: object }> {
    if (objectId.startsWith('sub_')) {
      return Mappers.getSubscriptionDocumentData(this.client, objectId);
    }

    if (objectId.startsWith('rg_')) {
      return Mappers.getResourceGroupDocumentData(this.client, objectId);
    }

    if (objectId.startsWith('mg_')) {
      return Mappers.getManagementGroupDocumentData(this.client, objectId);
    }

    if (objectId.startsWith('res_')) {
      return Mappers.getResourceDocumentData(this.client, objectId);
    }

    throw new UnexpectedError(`Object ${objectId} is not a document`);
  }

  async updateDocumentData(
    objectId: string,
    requestBody: { [key: string]: object },
    ifMatch?: string
  ): Promise<{ [key: string]: object }> {
    void requestBody;
    void ifMatch;
    throw new UnexpectedError(`Update document not supported for ${objectId} — Azure Resource Graph is read-only`);
  }

  // ==================== FunctionsProducerApi ====================

  async invokeFunction(
    objectId: string,
    requestBody?: { [key: string]: object }
  ): Promise<{ [key: string]: object }> {
    if (objectId === 'func_arg_query') {
      return Mappers.invokeArgQueryFunction(this.client, requestBody);
    }

    throw new UnexpectedError(`Function ${objectId} not implemented`);
  }

  async validateFunctionInput(
    objectId: string,
    validateFunctionInputRequest: ValidateFunctionInputRequest
  ): Promise<ValidationResult> {
    void validateFunctionInputRequest;
    throw new UnexpectedError(`validateFunctionInput not implemented for ${objectId}`);
  }

  // ==================== BinaryProducerApi ====================

  async downloadBinary(objectId: string): Promise<RequestFile> {
    throw new UnexpectedError(`Object ${objectId} is not a binary — Azure Resource Graph has no binary content`);
  }

  async uploadBinaryContent(
    objectId: string,
    body: RequestFile
  ): Promise<ModelObject> {
    void body;
    throw new UnexpectedError(`Upload not supported for ${objectId} — Azure Resource Graph is read-only`);
  }
}
