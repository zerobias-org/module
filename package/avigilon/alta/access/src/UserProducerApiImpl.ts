import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { UserProducerApi } from '../generated/api/UserApi';
import { User, UserInfo, Role, Site } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapUser, mapUserInfo, mapRole, mapSite } from './Mappers';

export class UserProducerApiImpl implements UserProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async get(organizationId: string, userId: number): Promise<UserInfo> {
    const response = await this.httpClient.get(
      `/orgs/${organizationId}/users/${userId.toString()}`
    );

    // Individual user response is directly in response.data, not nested in data.data like list responses
    const rawData = response.data;

    return mapUserInfo(rawData);
  }

  async listRoles(
    results: PagedResults<Role>,
    organizationId: string,
    userId: number
  ): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/users/${userId.toString()}/roles`;
    const response = await this.httpClient.get(url, { params });

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data ? response.data.data.map(mapRole) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }

  async listSites(
    results: PagedResults<Site>,
    organizationId: string,
    userId: number
  ): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const siteUrl = `/orgs/${organizationId}/users/${userId.toString()}/sites`;
    const response = await this.httpClient.get(siteUrl, { params });

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data ? response.data.data.map(mapSite) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }

  async list(results: PagedResults<User>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/users`, { params });

    // Apply mappers and set pagination info - data is directly in response.data.data
    results.items = response.data.data ? response.data.data.map(mapUser) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }
}
