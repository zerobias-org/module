/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { ScheduleProducerApi } from '../generated/api/ScheduleApi';
import { Schedule, ScheduleType, ScheduleEvent } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class ScheduleProducerApiImpl implements ScheduleProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listEvents(results: PagedResults<ScheduleEvent>, organizationId: string, scheduleId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async listTypes(results: PagedResults<ScheduleType>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async list(results: PagedResults<Schedule>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
