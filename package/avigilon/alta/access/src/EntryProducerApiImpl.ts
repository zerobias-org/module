import { AxiosInstance } from 'axios';
import { PagedResults, NoSuchObjectError, UnexpectedError } from '@auditmation/types-core-js';
import { EntryProducerApi } from '../generated/api/EntryApi';
import { Entry, EntryInfo, EntryActivityEvent, EntryCamera, EntryStateInfo, EntryUserSchedule, EntryUser } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { toEntry, toEntryInfo, toEntryActivityEvent, toEntryCamera, toEntryStateInfo, toEntryUserSchedule, toEntryUser } from './Mappers';

export class EntryProducerApiImpl implements EntryProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<Entry>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/entries`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toEntry);
    results.count = response.data.totalCount || 0;
  }

  async get(organizationId: string, entryId: string): Promise<EntryInfo> {
    const response = await this.httpClient.get(`/orgs/${organizationId}/entries/${entryId}`);

    const rawData = response.data.data || response.data;

    if (!rawData) {
      throw new NoSuchObjectError('entry', entryId);
    }

    return toEntryInfo(rawData);
  }

  async listActivity(
    results: PagedResults<EntryActivityEvent>,
    organizationId: string,
    entryId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/entries/${entryId}/activity`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toEntryActivityEvent);
    results.count = response.data.totalCount || 0;
  }

  async listCameras(
    results: PagedResults<EntryCamera>,
    organizationId: string,
    entryId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/entries/${entryId}/cameras`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toEntryCamera);
    results.count = response.data.totalCount || 0;
  }

  async listEntryStates(
    results: PagedResults<EntryStateInfo>,
    organizationId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/entryStates`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toEntryStateInfo);
    results.count = response.data.totalCount || 0;
  }

  async listUserSchedules(
    results: PagedResults<EntryUserSchedule>,
    organizationId: string,
    entryId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/entries/${entryId}/userSchedules`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toEntryUserSchedule);
    results.count = response.data.totalCount || 0;
  }

  async listUsers(
    results: PagedResults<EntryUser>,
    organizationId: string,
    entryId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/entries/${entryId}/users`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toEntryUser);
    results.count = response.data.totalCount || 0;
  }
}
