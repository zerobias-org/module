import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { GroupProducerApi } from '../generated/api/GroupApi';
import { Group, GroupInfo, User, Entry } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapGroup, mapGroupInfo, mapUser, mapEntry } from './Mappers';

export class GroupProducerApiImpl implements GroupProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async get(organizationId: string, groupId: number): Promise<GroupInfo> {
    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId.toString()}`
    );

    // Extract the actual group data from the nested response structure
    const rawGroupData = response.data.data;

    if (!rawGroupData) {
      throw new Error(`Group with ID ${groupId} not found`);
    }

    // Map to Group interface and extend to GroupInfo - use exactly what API returns
    const groupData = mapGroup(rawGroupData);
    const groupInfo = mapGroupInfo(groupData, rawGroupData);

    return groupInfo;
  }

  async listEntries(
    results: PagedResults<Entry>,
    organizationId: string,
    groupId: number
  ): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId.toString()}/entries`,
      { params }
    );

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data ? response.data.data.map(mapEntry) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }

  async listUsers(
    results: PagedResults<User>,
    organizationId: string,
    groupId: number
  ): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId.toString()}/users`,
      { params }
    );

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data ? response.data.data.map(mapUser) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }

  async list(results: PagedResults<Group>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/groups`, { params });

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data ? response.data.data.map(mapGroup) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }
}
