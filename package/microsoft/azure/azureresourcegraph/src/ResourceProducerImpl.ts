import type { PagedResults } from '@zerobias-org/types-core-js';
import type { ResourceProducerApi } from '../generated/api/index.js';
import type { Resource } from '../generated/model/index.js';
import { AzureResourceGraphClient } from './AzureResourceGraphClient.js';
import { toResource } from './Mappers.js';

export class ResourceProducerImpl implements ResourceProducerApi {
  constructor(private client: AzureResourceGraphClient) { }

  async list(
    results: PagedResults<Resource>,
    subscriptionIds?: Array<string>
  ): Promise<void> {
    return this.client.listQuery(
      results,
      'Resources',
      subscriptionIds?.map((id) => `${id}`),
      toResource
    );
  }
}
