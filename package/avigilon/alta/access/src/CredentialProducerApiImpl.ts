import { AxiosInstance } from 'axios';
import { PagedResults, UnexpectedError } from '@auditmation/types-core-js';
import { CredentialProducerApi } from '../generated/api/CredentialApi';
import { CredentialType, OrgCredential, CredentialAction, CredentialActionType, CardFormat } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { toCredentialType, toOrgCredential, toCredentialAction, toCredentialActionType, toCardFormat } from './Mappers';

export class CredentialProducerApiImpl implements CredentialProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listCardFormats(
    results: PagedResults<CardFormat>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/cardFormats`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toCardFormat);
    results.count = response.data.totalCount || 0;
  }

  async listCredentialActions(
    results: PagedResults<CredentialAction>,
    organizationId: string,
    credentialId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/credentials/${credentialId}/credentialActions`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toCredentialAction);
    results.count = response.data.totalCount || 0;
  }

  async listCredentialActionTypes(
    results: PagedResults<CredentialActionType>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/credentialActionTypes`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toCredentialActionType);
    results.count = response.data.totalCount || 0;
  }

  async listCredentialTypes(
    results: PagedResults<CredentialType>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/credentialTypes`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toCredentialType);
    results.count = response.data.totalCount || 0;
  }

  async listOrgCredentials(
    results: PagedResults<OrgCredential>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/credentials`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toOrgCredential);
    results.count = response.data.totalCount || 0;
  }
}
