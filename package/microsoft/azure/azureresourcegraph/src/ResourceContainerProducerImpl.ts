import type { PagedResults } from '@zerobias-org/types-core-js';
import type { ResourceContainerProducerApi } from '../generated/api/index.js';
import type { ResourceContainer } from '../generated/model/index.js';
import { AzureResourceGraphClient } from './AzureResourceGraphClient.js';
import { toResourceContainer } from './Mappers.js';

export class ResourceContainerProducerImpl implements ResourceContainerProducerApi {
  constructor(private client: AzureResourceGraphClient) { }

  async list(
    results: PagedResults<ResourceContainer>,
    subscriptionIds?: Array<string>
  ): Promise<void> {
    return this.client.listQuery(
      results,
      'ResourceContainers',
      subscriptionIds?.map((id) => `${id}`),
      toResourceContainer
    );
  }
}
