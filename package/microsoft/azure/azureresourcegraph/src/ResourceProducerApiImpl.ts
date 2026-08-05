import { PagedResults } from '@zerobias-org/types-core-js';
import { ResourceProducerApi } from '../generated/api/ResourceApi.js';
import { Resource } from '../generated/model/index.js';
import { AzureResourceGraphClient, ArgQueryRequest, ArgQueryOptions } from './AzureResourceGraphClient.js';
import { toResource } from './Mappers.js';

/** ARG caps options.$top at 1000. */
const MAX_PAGE_SIZE = 1000;

export class ResourceProducerApiImpl implements ResourceProducerApi {
  private readonly client: AzureResourceGraphClient;

  constructor(client: AzureResourceGraphClient) {
    this.client = client;
  }

  /**
   * List Azure resources from the ARG `Resources` table.
   *
   * Token-based (cursor) pagination: pass results.pageToken back as
   * options.$skipToken; ARG ignores $skip/$top when a $skipToken is present.
   * Stops when the response carries no $skipToken.
   */
  async list(
    results: PagedResults<Resource>,
    subscriptionIds?: string[],
    managementGroupIds?: string[]
  ): Promise<void> {
    const options: ArgQueryOptions = {};

    if (results.pageToken) {
      options.$skipToken = results.pageToken;
    } else if (results.pageSize) {
      options.$top = Math.min(Math.max(results.pageSize, 1), MAX_PAGE_SIZE);
    }

    const request: ArgQueryRequest = {
      query: 'Resources',
      options,
    };

    if (subscriptionIds?.length) {
      request.subscriptions = subscriptionIds;
    }
    if (managementGroupIds?.length) {
      request.managementGroups = managementGroupIds;
    }

    const response = await this.client.query(request);

    results.items = response.data.map(toResource);
    results.count = response.totalRecords || 0;
    results.pageToken = response.$skipToken;
  }
}
