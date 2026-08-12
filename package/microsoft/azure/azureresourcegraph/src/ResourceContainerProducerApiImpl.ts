import { PagedResults } from '@zerobias-org/types-core-js';
import { ResourceContainerProducerApi } from '../generated/api/ResourceContainerApi.js';
import { ResourceContainer } from '../generated/model/index.js';
import { AzureResourceGraphClient, ArgQueryRequest, ArgQueryOptions } from './AzureResourceGraphClient.js';
import { toResourceContainer } from './Mappers.js';

/** ARG caps options.$top at 1000. */
const MAX_PAGE_SIZE = 1000;

export class ResourceContainerProducerApiImpl implements ResourceContainerProducerApi {
  private readonly client: AzureResourceGraphClient;

  constructor(client: AzureResourceGraphClient) {
    this.client = client;
  }

  /**
   * List subscriptions / resource groups / management groups from the ARG
   * `ResourceContainers` table.
   *
   * Token-based (cursor) pagination via ARG $skipToken — see
   * ResourceProducerApiImpl.list for the pattern.
   */
  async list(
    results: PagedResults<ResourceContainer>,
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
      query: 'ResourceContainers',
      options,
    };

    if (subscriptionIds?.length) {
      request.subscriptions = subscriptionIds;
    }
    if (managementGroupIds?.length) {
      request.managementGroups = managementGroupIds;
    }

    const response = await this.client.query(request);

    results.items = response.data.map(toResourceContainer);
    results.count = response.totalRecords || 0;
    results.pageToken = response.$skipToken;
  }
}
