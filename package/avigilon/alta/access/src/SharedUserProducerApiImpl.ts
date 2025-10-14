/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { SharedUserProducerApi } from '../generated/api/SharedUserApi';
import { SharedUser } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class SharedUserProducerApiImpl implements SharedUserProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<SharedUser>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
