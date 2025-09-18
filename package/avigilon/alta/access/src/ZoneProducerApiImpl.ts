import { AxiosInstance } from 'axios';
import { ZoneProducerApi } from '../generated/api/ZoneApi';
import { Zone } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapZone } from './Mappers';

export class ZoneProducerApiImpl implements ZoneProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(organizationId: string): Promise<Array<Zone>> {
    const response = await this.httpClient.get(`/orgs/${organizationId}/zones`);

    // Extract the data array from the response and map each zone
    // This flattens the response from {data: [...], meta: {...}} to just the zones array
    const zonesData = response.data.data || [];
    return zonesData.map(mapZone);
  }
}
