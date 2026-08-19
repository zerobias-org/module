import { Schema, Property, Resource, ResourceContainer } from '../../../generated/model/index.js';
import { buildSchema } from './SchemaBuilder.js';

/**
 * DataProducerSchemas — Schema definitions for Azure Resource Graph entities.
 *
 * ARG has exactly two row shapes (the `Resources` and `ResourceContainers`
 * tables), so the shared schemas are built from the generated model classes.
 * The Function schemas are hand-built — function input/output schemas flatten
 * the call parameters rather than mirroring a single response model.
 */

export const SCHEMA_IDS = {
  RESOURCE: 'schema:shared:microsoft.azure.azureresourcegraph.resource',
  RESOURCE_CONTAINER: 'schema:shared:microsoft.azure.azureresourcegraph.resourceContainer',
  // Function input/output schemas (referenced by the Function object's inputSchema/outputSchema)
  ARG_QUERY_INPUT: 'schema:function:azure.azureresourcegraph.argQuery:input',
  ARG_QUERY_OUTPUT: 'schema:function:azure.azureresourcegraph.argQuery:output'
};

/**
 * Schema for Azure resource documents — one row of the ARG `Resources` table
 * (resultFormat objectArray). The `id` primary key is the full ARM resource ID.
 */
export const RESOURCE_SCHEMA: Schema = buildSchema(Resource, {
  schemaId: SCHEMA_IDS.RESOURCE,
  primaryKeys: ['id']
});

/**
 * Schema for Azure resource container documents — one row of the ARG
 * `ResourceContainers` table: a subscription, resource group or management
 * group. The `type` column discriminates the three container kinds.
 */
export const RESOURCE_CONTAINER_SCHEMA: Schema = buildSchema(ResourceContainer, {
  schemaId: SCHEMA_IDS.RESOURCE_CONTAINER,
  primaryKeys: ['id']
});

/**
 * Schema for the ARG Query function input.
 *
 * Mirrors the ARG query request body: a KQL query plus optional
 * subscription / management-group scoping and cursor pagination.
 */
export const ARG_QUERY_INPUT_SCHEMA: Schema = new Schema(
  SCHEMA_IDS.ARG_QUERY_INPUT,
  [],
  [
    new Property('query', 'string', 'Azure Resource Graph KQL query (e.g. "Resources | where type =~ \'microsoft.compute/virtualmachines\'")', true),
    new Property('subscriptions', 'string', 'Optional Azure subscription IDs to scope the query to', false, true),
    new Property('managementGroups', 'string', 'Optional Azure management group IDs to scope the query to', false, true),
    new Property('pageSize', 'integer', 'Page size — ARG caps $top at 1000', false),
    new Property('skipToken', 'string', 'Continuation token from a previous response', false)
  ]
);

/**
 * Schema for the ARG Query function output.
 */
export const ARG_QUERY_OUTPUT_SCHEMA: Schema = new Schema(
  SCHEMA_IDS.ARG_QUERY_OUTPUT,
  [],
  [
    new Property('data', 'json', 'Result rows (resultFormat objectArray)', false, true),
    new Property('totalRecords', 'integer', 'Total number of records matching the query', false),
    new Property('count', 'integer', 'Number of records in this page', false),
    new Property('skipToken', 'string', 'Continuation token for the next page, when truncated', false)
  ]
);

/**
 * Get schema by ID.
 */
export function getSchemaById(schemaId: string): Schema | undefined {
  const schemaMap: Record<string, Schema> = {
    [SCHEMA_IDS.RESOURCE]: RESOURCE_SCHEMA,
    [SCHEMA_IDS.RESOURCE_CONTAINER]: RESOURCE_CONTAINER_SCHEMA,
    [SCHEMA_IDS.ARG_QUERY_INPUT]: ARG_QUERY_INPUT_SCHEMA,
    [SCHEMA_IDS.ARG_QUERY_OUTPUT]: ARG_QUERY_OUTPUT_SCHEMA
  };

  return schemaMap[schemaId];
}
