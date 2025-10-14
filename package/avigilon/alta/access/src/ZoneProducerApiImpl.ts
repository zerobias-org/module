/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { ZoneProducerApi } from '../generated/api/ZoneApi';
import { Zone, ZoneShare, ZoneUser } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapZone } from './Mappers';

export class ZoneProducerApiImpl implements ZoneProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listShares(results: PagedResults<ZoneShare>, organizationId: string, zoneId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async listZoneUsers(results: PagedResults<ZoneUser>, organizationId: string, zoneId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async list(organizationId: string): Promise<Array<Zone>> {
    const response = await this.httpClient.get(`/orgs/${organizationId}/zones`);

    // Extract the data array from the response and map each zone
    // This flattens the response from {data: [...], meta: {...}} to just the zones array
    const zonesData = response.data.data || [];
    return zonesData.map(mapZone);
  }
}
