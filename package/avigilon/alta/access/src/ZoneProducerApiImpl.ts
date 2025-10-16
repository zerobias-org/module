import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { ZoneProducerApi } from '../generated/api/ZoneApi';
import { Zone } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapZone } from './Mappers';

export class ZoneProducerApiImpl implements ZoneProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<Zone>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/zones`, { params });

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data ? response.data.data.map(mapZone) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available
    results.pageToken = response.headers['x-next-page-token'];
  }
}
