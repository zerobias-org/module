import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { SiteProducerApi } from '../generated/api/SiteApi';
import { Site } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapSite } from './Mappers';

export class SiteProducerApiImpl implements SiteProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<Site>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/sites`, { params });

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data ? response.data.data.map(mapSite) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available
    results.pageToken = response.headers['x-next-page-token'];
  }
}
