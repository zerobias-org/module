/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { CredentialActionProducerApi } from '../generated/api/CredentialActionApi';
import { CredentialActionType } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class CredentialActionProducerApiImpl implements CredentialActionProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<CredentialActionType>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
