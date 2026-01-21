import { AxiosInstance } from 'axios';
import { PagedResults, UnexpectedError } from '@zerobias-org/types-core-js';
import { SiteProducerApi } from '../generated/api/SiteApi.js';
import { Site } from '../generated/model/index.js';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient.js';
import { toSite } from './Mappers.js';

export class SiteProducerApiImpl implements SiteProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<Site>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/sites`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toSite(item));
    results.count = response.data.totalCount || 0;
  }
}
