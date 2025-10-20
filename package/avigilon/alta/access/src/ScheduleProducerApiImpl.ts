import { getLogger } from '@auditmation/util-logger';
import { AxiosInstance } from 'axios';
import { PagedResults, UnexpectedError } from '@auditmation/types-core-js';
import { ScheduleProducerApi } from '../generated/api/ScheduleApi';
import { ScheduleType, Schedule, ScheduleEvent } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { toScheduleTypeExported, toScheduleExported, toScheduleEvent } from './Mappers';

export class ScheduleProducerApiImpl implements ScheduleProducerApi {
  private readonly httpClient: AxiosInstance;

  private readonly logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listScheduleEvents(
    results: PagedResults<ScheduleEvent>,
    organizationId: string,
    scheduleId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/schedules/${scheduleId}/events`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map(toScheduleEvent);
    results.count = response.data.totalCount || 0;
  }

  async listScheduleTypes(
    results: PagedResults<ScheduleType>,
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
      `/orgs/${organizationId}/scheduleTypes`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toScheduleTypeExported);
    results.count = response.data.totalCount || 0;
  }

  async listSchedules(
    results: PagedResults<Schedule>,
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
      `/orgs/${organizationId}/schedules`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Apply mappers and set pagination info from response structure
    results.items = response.data.data.map(toScheduleExported);
    results.count = response.data.totalCount || 0;
  }
}
