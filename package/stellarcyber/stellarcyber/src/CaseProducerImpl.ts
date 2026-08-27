import { PagedResults } from '@zerobias-org/types-core-js';
import { CaseProducerApi } from '../generated/api/index.js';
import { Case, CaseAlert } from '../generated/model/index.js';
import { StellarCyberClient } from './StellarCyberClient.js';
import { handleAxiosError } from './util.js';
import { toCase, toCaseAlert } from './mappers.js';

export class CaseProducerImpl implements CaseProducerApi {
  constructor(private client: StellarCyberClient) {}

  async list(results: PagedResults<Case>, status?: string): Promise<void> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: '/cases',
        method: 'get',
        params: {
          limit: results.pageSize,
          status,
        },
      })
      .catch(handleAxiosError);

    // SC may return {cases:[...]}, {data:{cases:[...]}}, or a bare array.
    const cases = data?.cases ?? data?.data?.cases ?? (Array.isArray(data) ? data : []);
    return results.ingest(cases.map((c: any) => toCase(c)));
  }

  async listAlerts(results: PagedResults<CaseAlert>, caseId: string): Promise<void> {
    const { apiClient } = this.client;

    const skip = results.pageSize * (results.pageNumber - 1);
    const { data } = await apiClient
      .request({
        url: `/cases/${caseId}/alerts`,
        method: 'get',
        // SC requires both skip and limit, or it returns 422.
        params: {
          skip,
          limit: results.pageSize,
        },
      })
      .catch(handleAxiosError);

    const docs = data?.docs ?? data?.alerts ?? data?.data?.docs ?? (Array.isArray(data) ? data : []);
    return results.ingest(docs.map((a: any) => toCaseAlert(a)));
  }
}
