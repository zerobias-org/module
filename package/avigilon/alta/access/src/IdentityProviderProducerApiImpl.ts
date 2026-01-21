import { AxiosInstance } from 'axios';
import { PagedResults, NoSuchObjectError, UnexpectedError } from '@zerobias-org/types-core-js';
import { IdentityProviderProducerApi } from '../generated/api/IdentityProviderApi.js';
import {
  IdentityProviderType,
  IdentityProviderTypeInfo,
  IdentityProvider,
  IdentityProviderGroup,
  IdentityProviderGroupRelation
} from '../generated/model/index.js';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient.js';
import {
  toIdentityProviderType,
  toIdentityProviderTypeInfo,
  toIdentityProvider,
  toIdentityProviderGroup,
  toIdentityProviderGroupRelation
} from './Mappers.js';

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

    results.items = response.data.data.map((item) => toIdentityProviderGroupRelation(item));
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

    results.items = response.data.data.map((item) => toIdentityProviderGroup(item));
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

    results.items = response.data.data.map((item) => toIdentityProviderType(item));
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

    results.items = response.data.data.map((item) => toIdentityProvider(item));
    results.count = response.data.totalCount || 0;

    // Handle pagination token if present
  }
}
