/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { CredentialProducerApi } from '../generated/api/CredentialApi';
import { CardFormat, CredentialType, Credential } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class CredentialProducerApiImpl implements CredentialProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listCardFormats(results: PagedResults<CardFormat>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async listTypes(results: PagedResults<CredentialType>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async list(results: PagedResults<Credential>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
