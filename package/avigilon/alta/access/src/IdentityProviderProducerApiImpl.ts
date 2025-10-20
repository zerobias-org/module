import { AxiosInstance } from 'axios';
import { PagedResults, NoSuchObjectError, UnexpectedError } from '@auditmation/types-core-js';
import { IdentityProviderProducerApi } from '../generated/api/IdentityProviderApi';
import {
  IdentityProviderType,
  IdentityProviderTypeInfo,
  IdentityProvider,
  IdentityProviderGroup,
  IdentityProviderGroupRelation
} from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import {
  toIdentityProviderType,
  toIdentityProviderTypeInfo,
  toIdentityProvider,
  toIdentityProviderGroup,
  toIdentityProviderGroupRelation
} from './Mappers';

export class IdentityProviderProducerApiImpl implements IdentityProviderProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async getIdentityProviderGroupRelations(
    results: PagedResults<IdentityProviderGroupRelation>,
    organizationId: string,
    identityProviderId: string
  ): Promise<void> {
    const params: Record<string, number> = {};
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/identityProviders/${identityProviderId}/groupRelations`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toIdentityProviderGroupRelation);
    results.count = response.data.totalCount || 0;
  }

  async getIdentityProviderType(
    organizationId: string,
    identityProviderTypeId: string
  ): Promise<IdentityProviderTypeInfo> {
    const response = await this.httpClient.get(
      `/orgs/${organizationId}/identityProviderTypes/${identityProviderTypeId}`
    );

    const rawData = response.data.data || response.data;

    if (!rawData) {
      throw new NoSuchObjectError('identityProviderType', identityProviderTypeId);
    }

    return toIdentityProviderTypeInfo(rawData);
  }

  async listIdentityProviderGroups(
    results: PagedResults<IdentityProviderGroup>,
    organizationId: string,
    identityProviderId: string
  ): Promise<void> {
    const params: Record<string, number> = {};
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/identityProviders/${identityProviderId}/groups`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toIdentityProviderGroup);
    results.count = response.data.totalCount || 0;

    // Handle pagination token if present
  }

  async listIdentityProviderTypes(
    results: PagedResults<IdentityProviderType>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/identityProviderTypes`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toIdentityProviderType);
    results.count = response.data.totalCount || 0;

    // Handle pagination token if present
  }

  async listIdentityProviders(
    results: PagedResults<IdentityProvider>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/identityProviders`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toIdentityProvider);
    results.count = response.data.totalCount || 0;

    // Handle pagination token if present
  }
}
