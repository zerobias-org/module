import { AxiosInstance } from 'axios';
import { PagedResults, NoSuchObjectError, UnexpectedError } from '@zerobias-org/types-core-js';
import { GroupProducerApi } from '../generated/api/GroupApi.js';
import { Group, GroupInfo, User, Entry, GroupZoneGroup, GroupZone } from '../generated/model/index.js';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient.js';
import { toGroup, toGroupInfo, toUser, toEntry, toGroupZoneGroup, toGroupZone } from './Mappers.js';

export class GroupProducerApiImpl implements GroupProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async get(organizationId: string, groupId: string): Promise<GroupInfo> {
    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId}`
    );

    // Individual group response could be in response.data.data OR response.data
    const rawGroupData = response.data.data || response.data;

    if (!rawGroupData) {
      throw new NoSuchObjectError('group', groupId);
    }

    // Map to Group interface and extend to GroupInfo - use exactly what API returns
    const groupData = toGroup(rawGroupData);
    const groupInfo = toGroupInfo(groupData, rawGroupData);

    return groupInfo;
  }

  async listEntries(
    results: PagedResults<Entry>,
    organizationId: string,
    groupId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId}/entries`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toEntry(item));
    results.count = response.data.totalCount || 0;
  }

  async listUsers(
    results: PagedResults<User>,
    organizationId: string,
    groupId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId}/users`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toUser(item));
    results.count = response.data.totalCount || 0;
  }

  async list(results: PagedResults<Group>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/groups`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toGroup(item));
    results.count = response.data.totalCount || 0;
  }

  async listZoneGroups(
    results: PagedResults<GroupZoneGroup>,
    organizationId: string,
    groupId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId}/zoneGroups`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toGroupZoneGroup(item));
    results.count = response.data.totalCount || 0;
  }

  async listZones(
    results: PagedResults<GroupZone>,
    organizationId: string,
    groupId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/groups/${groupId}/zones`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toGroupZone(item));
    results.count = response.data.totalCount || 0;
  }
}
