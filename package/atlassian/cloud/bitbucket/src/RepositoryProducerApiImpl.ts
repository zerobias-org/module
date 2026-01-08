import { AxiosInstance } from 'axios';
import { RepositoryProducerApi } from '../generated/api/RepositoryApi';
import { Repository } from '../generated/model';
import { BitbucketClient } from './BitbucketClient';
import { toRepository } from './Mappers';

export class RepositoryProducerApiImpl implements RepositoryProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: BitbucketClient) {
    this.httpClient = client.getHttpClient();
  }

  async listRepositories(
    workspace: string,
    pageNumber?: number,
    pageSize?: number
  ): Promise<Array<Repository>> {
    const params: Record<string, number> = {};

    if (pageNumber) {
      params.page = pageNumber;
    }
    if (pageSize) {
      params.pagelen = Math.min(Math.max(pageSize, 1), 100);
    }

    const response = await this.httpClient.get(`/repositories/${workspace}`, { params });

    // Bitbucket returns paginated data in a 'values' array
    const values = response.data.values || [];
    return values.map(toRepository);
  }
}
