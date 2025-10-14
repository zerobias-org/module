/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { EntryProducerApi } from '../generated/api/EntryApi';
import { EntryDetails, Entry, ActivityEvent, EntryCamera, UserSchedule, User } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapEntryDetails } from './Mappers';

export class EntryProducerApiImpl implements EntryProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async get(organizationId: string, entryId: string): Promise<EntryDetails> {
    throw new Error('Not implemented');
  }

  async getActivity(organizationId: string, entryId: string, startDate?: Date, endDate?: Date): Promise<Array<ActivityEvent>> {
    throw new Error('Not implemented');
  }

  async listCameras(results: PagedResults<EntryCamera>, organizationId: string, entryId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async listUserSchedules(results: PagedResults<UserSchedule>, organizationId: string, entryId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async listUsers(results: PagedResults<User>, organizationId: string, entryId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async list(organizationId: string): Promise<Array<EntryDetails>> {
    const response = await this.httpClient.get(`/orgs/${organizationId}/entries`);

    // Extract the data array from the response (flattening it, dropping metadata and pagination)
    const entriesData = response.data.data || [];
    return entriesData.map(mapEntryDetails);
  }
}
