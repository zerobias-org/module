/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { EntryStateProducerApi } from '../generated/api/EntryStateApi';
import { EntryStateDetails } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class EntryStateProducerApiImpl implements EntryStateProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<EntryStateDetails>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
