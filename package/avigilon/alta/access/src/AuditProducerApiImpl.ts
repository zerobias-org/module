import { AxiosInstance } from 'axios';
import { PagedResults, UnexpectedError } from '@auditmation/types-core-js';
import { AuditProducerApi } from '../generated/api/AuditApi';
import { AuditLogEntry } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { toAuditLogEntry } from './Mappers';

export class AuditProducerApiImpl implements AuditProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listAuditLogs(
    results: PagedResults<AuditLogEntry>,
    organizationId: string,
    filter?: string,
    options?: string
  ): Promise<void> {
    const params: Record<string, string | number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    if (filter) {
      params.filter = filter;
    }

    if (options) {
      params.options = options;
    }

    const url = `/orgs/${organizationId}/reports/auditLogs/ui`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toAuditLogEntry);
    results.count = response.data.totalCount || 0;
  }
}
