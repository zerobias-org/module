import { AxiosInstance } from 'axios';
import { EntryProducerApi } from '../generated/api/EntryApi';
import { EntryDetails } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapEntryDetails } from './Mappers';

export class EntryProducerApiImpl implements EntryProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(organizationId: string): Promise<Array<EntryDetails>> {
    const response = await this.httpClient.get(`/orgs/${organizationId}/entries`);

    // Extract the data array from the response (flattening it, dropping metadata and pagination)
    const entriesData = response.data.data || [];
    return entriesData.map(mapEntryDetails);
  }
}