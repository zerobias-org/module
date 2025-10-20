import { AxiosInstance } from 'axios';
import { PagedResults, NoSuchObjectError, UnexpectedError } from '@auditmation/types-core-js';
import { UserProducerApi } from '../generated/api/UserApi';
import {
  User,
  UserInfo,
  Role,
  Site,
  UserZone,
  MfaCredential,
  UserPicture,
  Group,
  UserEntry,
  UserActivityEvent,
  OrgIdentity,
  OrgPicture,
  SharedUser,
  UserCredential,
  UserZoneUser
} from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import {
  toUser,
  toUserInfo,
  toRole,
  toSite,
  toUserZone,
  toMfaCredential,
  toUserPicture,
  toGroup,
  toUserEntry,
  toUserActivityEvent,
  toOrgIdentity,
  toOrgPicture,
  toSharedUser,
  toUserCredential,
  toUserZoneUser
} from './Mappers';

export class UserProducerApiImpl implements UserProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async get(organizationId: string, userId: string): Promise<UserInfo> {
    const response = await this.httpClient.get(
      `/orgs/${organizationId}/users/${userId}`
    );

    // Individual user response could be in response.data.data OR response.data
    const rawData = response.data.data || response.data;

    if (!rawData) {
      throw new NoSuchObjectError('user', userId);
    }

    return toUserInfo(rawData);
  }

  async listRoles(
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

    results.items = response.data.data.map(toRole);
    results.count = response.data.totalCount || 0;
  }

  async listSites(
    results: PagedResults<Site>,
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

    const siteUrl = `/orgs/${organizationId}/users/${userId}/sites`;
    const response = await this.httpClient.get(siteUrl, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data.map(toSite);
    results.count = response.data.totalCount || 0;
  }

  async list(results: PagedResults<User>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/users`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toUser);
    results.count = response.data.totalCount || 0;
  }

  async listZones(
    results: PagedResults<UserZone>,
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

    const url = `/orgs/${organizationId}/users/${userId}/zones`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toUserZone);
    results.count = response.data.totalCount || 0;
  }

  async listMfaCredentials(
    results: PagedResults<MfaCredential>,
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

    const url = `/orgs/${organizationId}/users/${userId}/mfaCredentials`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toMfaCredential);
    results.count = response.data.totalCount || 0;
  }

  async listPictures(
    results: PagedResults<UserPicture>,
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

    const url = `/orgs/${organizationId}/users/${userId}/userPictures`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toUserPicture);
    results.count = response.data.totalCount || 0;
  }

  async listGroups(
    results: PagedResults<Group>,
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

    const url = `/orgs/${organizationId}/users/${userId}/groups`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toGroup);
    results.count = response.data.totalCount || 0;
  }

  async listEntries(
    results: PagedResults<UserEntry>,
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

    const url = `/orgs/${organizationId}/users/${userId}/entries`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toUserEntry);
    results.count = response.data.totalCount || 0;
  }

  async listActivity(
    results: PagedResults<UserActivityEvent>,
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

    const url = `/orgs/${organizationId}/users/${userId}/activity`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toUserActivityEvent);
    results.count = response.data.totalCount || 0;
  }

  async listOrgIdentities(
    results: PagedResults<OrgIdentity>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/orgIdentities`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toOrgIdentity);
    results.count = response.data.totalCount || 0;
  }

  async listOrgPictures(
    results: PagedResults<OrgPicture>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/orgPictures`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toOrgPicture);
    results.count = response.data.totalCount || 0;
  }

  async listSharedUsers(
    results: PagedResults<SharedUser>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const url = `/orgs/${organizationId}/sharedUsers`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toSharedUser);
    results.count = response.data.totalCount || 0;
  }

  async listCredentials(
    results: PagedResults<UserCredential>,
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

    const url = `/orgs/${organizationId}/users/${userId}/credentials`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toUserCredential);
    results.count = response.data.totalCount || 0;
  }

  async listZoneUsers(
    results: PagedResults<UserZoneUser>,
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

    const url = `/orgs/${organizationId}/users/${userId}/zoneUsers`;
    const response = await this.httpClient.get(url, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toUserZoneUser);
    results.count = response.data.totalCount || 0;
  }
}
