/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { AuditProducerApi } from '../generated/api/AuditApi';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class AuditProducerApiImpl implements AuditProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async getAuditLogsUi(results: PagedResults<object>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
