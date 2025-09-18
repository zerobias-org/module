import { AxiosInstance } from 'axios';
import { SiteProducerApi } from '../generated/api/SiteApi';
import { Site } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapSite } from './Mappers';

export class SiteProducerApiImpl implements SiteProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(organizationId: string): Promise<Array<Site>> {
    const response = await this.httpClient.get(`/orgs/${organizationId}/sites`);

    // Extract the data array from the response and map each site
    const sitesData = response.data.data || [];
    return sitesData.map(mapSite);
  }
}
