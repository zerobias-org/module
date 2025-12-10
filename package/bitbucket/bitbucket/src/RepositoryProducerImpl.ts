import { PagedResults } from '@auditmation/types-core-js';
import { RepositoryProducerApi } from '../generated/api';
import { Repository } from '../generated/model';
import { BitbucketClient } from './BitbucketClient';
import { handleAxiosError } from './util';
import { toRepository } from './mappers';

export class RepositoryProducerImpl implements RepositoryProducerApi {
  constructor(private client: BitbucketClient) { }

  async list(results: PagedResults<Repository>, workspace: string): Promise<void> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: `/repositories/${encodeURIComponent(workspace)}`,
        method: 'get',
        params: {
          page: results.pageNumber,
          pagelen: results.pageSize,
        },
      })
      .catch(handleAxiosError);

    const parsed = typeof data === 'string' ? JSON.parse(data) : data;
    const repositories = parsed.values?.map(toRepository) || [];

    results.items = repositories;
    results.count = parsed.size || repositories.length;
  }
}
