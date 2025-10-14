/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { IdentityProviderProducerApi } from '../generated/api/IdentityProviderApi';
import { IdentityProviderType, IdentityProvider, IdentityProviderGroupRelation, IdentityProviderGroup } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class IdentityProviderProducerApiImpl implements IdentityProviderProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async getGroupRelations(results: PagedResults<IdentityProviderGroupRelation>, organizationId: string, identityProviderId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async get(organizationId: string, identityProviderTypeId: string): Promise<IdentityProviderType> {
    throw new Error('Not implemented');
  }

  async listGroups(results: PagedResults<IdentityProviderGroup>, organizationId: string, identityProviderId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async listTypes(results: PagedResults<IdentityProviderType>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async list(results: PagedResults<IdentityProvider>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
