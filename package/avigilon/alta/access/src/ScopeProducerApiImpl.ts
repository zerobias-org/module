/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { ScopeProducerApi } from '../generated/api/ScopeApi';
import { ScopeResource } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class ScopeProducerApiImpl implements ScopeProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<ScopeResource>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
