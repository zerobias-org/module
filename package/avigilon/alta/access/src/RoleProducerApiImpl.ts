import { AxiosInstance } from 'axios';
import { PagedResults, UnexpectedError } from '@auditmation/types-core-js';
import { RoleProducerApi } from '../generated/api/RoleApi';
import { Role, RoleInfo, RoleUser } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { toRoleInfo, toRoleUser, toRole } from './Mappers';

export class RoleProducerApiImpl implements RoleProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listRoleUsers(
    results: PagedResults<RoleUser>,
    organizationId: string,
    roleId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/roles/${roleId}/users`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toRoleUser);
    results.count = response.data.totalCount || 0;
  }

  async listRoles(
    results: PagedResults<RoleInfo>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/roles`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toRoleInfo);
    results.count = response.data.totalCount || 0;
  }

  async listUserRoles(
    results: PagedResults<Role>,
    organizationId: string,
    userId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/users/${userId}/roles`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toRole);
    results.count = response.data.totalCount || 0;
  }
}
